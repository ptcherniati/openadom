package fr.inra.oresing.rest.binaryFile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.DefaultValidationCheckResult;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.exceptions.data.data.BadBinaryFileDatasetQuery;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.model.additionalfiles.AdditionalBinaryFileResult;
import fr.inra.oresing.rest.model.authorization.AuthorizationParsed;
import fr.inra.oresing.rest.services.AuthorizationService;
import fr.inra.oresing.rest.services.ServiceContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@Transactional(readOnly = true)

public class BinaryFileService implements fr.inra.oresing.domain.services.file.BinaryFileService {
    @Autowired
    private OreSiRepository repository;

    private ServiceContainer serviceContainer;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private OreSiApiRequestContext request;
    @Autowired
    private JsonRowMapper jsonRowMapper;



    public static BinaryFileDataset deserialiseBinaryFileDatasetQuery(final String dataName, final String params) {
        try {
            final BinaryFileDataset binaryFileDataset = params != null ? new ObjectMapper().readValue(params, BinaryFileDataset.class) : null;
            final Optional<BinaryFileDataset> binaryFileDatasetOpt = Optional.ofNullable(binaryFileDataset);
            if (binaryFileDatasetOpt.map(BinaryFileDataset::getDatatype).isEmpty()) {
                binaryFileDatasetOpt.ifPresent(binaryFileDataset1 -> binaryFileDataset1.setDatatype(dataName));
            }
            return binaryFileDataset;
        } catch (final IOException e) {
            throw new BadBinaryFileDatasetQuery(e.getMessage());
        }
    }

    @Override
    @Transactional()
    public UUID storeFile(final Application application, final MultipartFile file, final String comment, final BinaryFileDataset binaryFileDataset) throws IOException {
        authenticationService.setRoleForClient();
        // creation du fichier
        final BinaryFile binaryFile = new BinaryFile();
        binaryFile.setApplication(application.getId());
        binaryFile.setComment(comment);
        binaryFile.setName(file.getOriginalFilename() != null ? file.getOriginalFilename() : "charte.pdf");
        binaryFile.setSize(file.getSize());
        binaryFile.setFileData(file.getInputStream());
        final BinaryFileInfos binaryFileInfos = BinaryFileInfos.forPublish(false, request.getRequestUserId(), LocalDateTime.now().toString(), binaryFileDataset);
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
        Function<BinaryFile, UUID> deleteBinaryFile = binaryFile -> getBinaryFileRepository(application).delete(binaryFile.getId()) ? binaryFile.getId() : null;
        return getFile(application.getName(), id)
                .map(BinaryFile::getId)
                .map(getBinaryFileRepository(application)::delete)
                .orElse(false)?Optional.of(id):Optional.empty();
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
        Application application= serviceContainer.applicationService().getApplication(nameOrId);
        DataRepositoryForBuffer dataRepositoryForBuffer = serviceContainer.dataService().getDataRepositoryWithBuffer(application);
        Submission.SubmissionScope submissionScope = application.findSubmission(datatype)
                .map(Submission::submissionScope)
                .orElse(null);
        return getBinaryFileRepository(nameOrId).findByBinaryFileDataset(datatype, binaryFileDataset.testrequiredAuthorizationsAndReturnHierarchicalKeys(dataRepositoryForBuffer), overlap);
    }

    @Override
    public AdditionalBinaryFileResult getAdditionalBinaryFileResult(
            final AdditionalBinaryFile additionalBinaryFile,
            final Application application) {
        Map<String, List<AuthorizationParsed>> authorizationsParsed = new HashMap<>();
        AuthorizationService.authorizationsToParsedAuthorizations(
                additionalBinaryFile.getAssociates(),
                authorizationsParsed);
        return new AdditionalBinaryFileResult(additionalBinaryFile, authorizationsParsed);
    }

    public void setServiceContainer(ServiceContainer serviceContainer) {
        this.serviceContainer = serviceContainer;
    }
}
