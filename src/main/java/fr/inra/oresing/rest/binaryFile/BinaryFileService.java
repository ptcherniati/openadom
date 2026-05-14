package fr.inra.oresing.rest.binaryFile;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.domain.ReferencedBinaryFiles;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFileResult;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.AuthorizationParsed;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.DefaultValidationCheckResult;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.services.AuthorizationService;
import fr.inra.oresing.rest.services.ServiceContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Slf4j
@Component
@Transactional(readOnly = true)

public class BinaryFileService implements fr.inra.oresing.domain.services.file.BinaryFileService {
    private final OreSiRepository repository;
    private final ServiceContainer serviceContainer;
    private final AuthenticationService authenticationService;
    private final JsonRowMapper<?> jsonRowMapper;

    public BinaryFileService(OreSiRepository repository, ServiceContainer serviceContainer, AuthenticationService authenticationService, JsonRowMapper jsonRowMapper) {
        this.repository = repository;
        this.serviceContainer = serviceContainer;
        this.authenticationService = authenticationService;
        this.jsonRowMapper = jsonRowMapper;
    }

    @Override
    @Transactional
    public UUID storeFile(final Application application, final DataFile file, String comment, final BinaryFileDataset binaryFileDataset) throws IOException {
        authenticationService.setRoleForClient();
        // creation du fichier
        comment = Optional.ofNullable(binaryFileDataset)
                .map(BinaryFileDataset::getComment)
                .filter(Predicate.not(Strings::isNullOrEmpty))
                .orElse(comment);
        final BinaryFile binaryFile = new BinaryFile();
        binaryFile.setApplication(application.getId());
        binaryFile.setComment(comment);
        binaryFile.setName(file.fileName() != null ? file.fileName() : "charte.pdf");
        binaryFile.setSize(file.fileSize());
        binaryFile.setFileData(file.inputStream());
        final BinaryFileInfos binaryFileInfos = BinaryFileInfos.forPublish(false, OreSiApiRequestContext.getRequestUserId(), LocalDateTime.now().toString(), binaryFileDataset);
        binaryFile.setParams(binaryFileInfos);
        return getBinaryFileRepository(application).store(binaryFile);
    }

    @Override
    public Optional<BinaryFile> getFile(final String applicationNameOrID, final UUID id) {
        authenticationService.setRoleForClient();
        return getBinaryFileRepository(applicationNameOrID).tryFindById(id);
    }

    @Override
    public Optional<BinaryFile> getFileWithData(final String applicationNameOrID, final UUID id) {
        authenticationService.setRoleForClient();
        return getBinaryFileRepository(applicationNameOrID).tryFindByIdWithData(id);
    }

    private BinaryFileRepository getBinaryFileRepository(Application application) {
        return repository.getRepository(application).binaryFile();
    }

    private BinaryFileRepository getBinaryFileRepository(String applicationNameOrId) {
        return repository.getRepository(applicationNameOrId).binaryFile();
    }

    @Transactional
    @Override
    public Optional<UUID> removeFile(Application application, UUID id) {
        return getFile(application.getName(), id)
                .map(BinaryFile::getId)
                .map(getBinaryFileRepository(application)::delete)
                .orElse(false) ? Optional.of(id) : Optional.empty();
    }

    @Override
    public ReportErrors findPublishedVersion(final String nameOrId, final String dataType, final FileOrUUID params, final Set<BinaryFile> filesToStore, final boolean searchOverlaps) {
        if (params != null && params.binaryfiledataset() != null) {
            if (searchOverlaps) {
                final List<BinaryFile> overlapingFiles = getFilesOnRepository(nameOrId, dataType, params.binaryfiledataset(), true);
                if (!overlapingFiles.isEmpty()) {
                    final List<CsvRowValidationCheckResult> errors = List.of(
                            new CsvRowValidationCheckResult(
                                    DefaultValidationCheckResult.error(
                                            "overlappingpublishedversion",
                                            ImmutableMap.of("fileOrUUID", params,
                                                    "files", overlapingFiles.stream()
                                                            .map(f -> f.getParams().binaryFiledataset().toString())
                                                            .collect(Collectors.toSet()
                                                            )
                                            ),
                                            null
                                    ),
                                    -1
                            )
                    );
                    final ReportErrors reportErrors = new ReportErrors(jsonRowMapper);
                    reportErrors.addAll(errors);
                    return reportErrors;
                }
            }
            getFilesOnRepository(nameOrId, dataType, params.binaryfiledataset(), false)
                    .stream()
                    .filter(f -> f.getParams().published())
                    .forEach(f -> {
                        f.markAsPublished(false);
                        filesToStore.add(f);
                    });
        }
        return new ReportErrors(jsonRowMapper);
    }

    @Override
    public List<BinaryFile> getFilesOnRepository(final String nameOrId, final String datatype, final BinaryFileDataset binaryFileDataset, final boolean overlap) {
        authenticationService.setRoleForClient();
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        DataRepository dataRepository = serviceContainer.dataService().getDataRepository(application);
        return getBinaryFileRepository(nameOrId).findByBinaryFileDataset(datatype, binaryFileDataset.testrequiredAuthorizationsAndReturnHierarchicalKeys(dataRepository), overlap);
    }

    @Override
    public AdditionalBinaryFileResult getAdditionalBinaryFileResult(AdditionalBinaryFile additionalBinaryFile) {

        Map<String, List<AuthorizationParsed>> authorizationsParsed = new HashMap<>();
        AuthorizationService.authorizationsToParsedAuthorizations(
                additionalBinaryFile.getAssociates(),
                authorizationsParsed);
        return new AdditionalBinaryFileResult(additionalBinaryFile, authorizationsParsed);
    }

    @Override
    public List<ReferencedBinaryFiles> getReferencedBinaryFiles(UUID applicationId, String datatype, Set<UUID> binaryFileIds) {
        return getBinaryFileRepository(applicationId.toString())
                .getReferencedBinaryFiles(datatype, binaryFileIds);
    }
}