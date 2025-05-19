package fr.inra.oresing.rest.data;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationDataWriter;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.file.DataFile;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.mail.EmailService;
import fr.inra.oresing.persistence.BinaryFileInfos;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.data.publication.*;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.services.ServiceContainerBean;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;

@Slf4j
@Component
@Transactional(readOnly = true)

public class VersioningService implements ServiceContainerBean {
    @Setter
    private ServiceContainer serviceContainer;
    @Autowired
    private OreSiRepository repository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JsonRowMapper jsonRowMapper;

    @Transactional
    public DataVersioningResult createData(Locale locale, String nameOrId, String dataName, MultipartFile file, String params, boolean beforeDelete) throws IOException {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        String fileName = file == null ? null : file.getOriginalFilename();
        Optional<FileOrUUID> fileOrUUIDOpt = OreSiApiRequestContext.getAuthentication()
                .map(OreSiAuthenticationToken::getFileOrUUID);
        Set<BinaryFile> filesToStore = new HashSet<>();
        ApplicationDataWriter applicationDataWriter = OreSiApiRequestContext.getAuthentication()
                .map(OreSiAuthenticationToken::getApplicationPersona)
                .filter(ApplicationDataWriter.class::isInstance)
                .map(ApplicationDataWriter.class::cast)
                .orElse(null);

        DataRepositoryForBuffer dataRepositoryWithBuffer = serviceContainer.dataService().getDataRepositoryWithBuffer(application);
        State state = getStoreFile(application, dataName, fileOrUUIDOpt.orElse(null), fileName, applicationDataWriter)
                .loadOrCreateFile(file, binaryFileRepository(application), serviceContainer.binaryFileService());
        EmailService.UPLOAD_STATE uploadState;
        if (state instanceof UnPublishedVersions unPublishedVersions) {
            FileOrUUID fileOrUUID = unPublishedVersions
                    .unPublishVersions(filesToStore, dataRepository(application), binaryFileRepository(application), serviceContainer.synthesisService())
                    .checkAndStoreFile(filesToStore, serviceContainer.binaryFileService(), binaryFileRepository(application));

            UUID dataId = publishData(dataName, fileOrUUID, application, state);
            final List<ApplicationResult.DataSynthesis> dataSynthesis = Optional.ofNullable(serviceContainer.dataService().getReferenceSynthesis(application)).orElseGet(List::of);
            DataVersioningResult dataVersioningResult = DataVersioningResult.of(nameOrId, dataName, dataId, dataSynthesis);
            if (unPublishedVersions.isRepository()) {
                uploadState = fileOrUUIDOpt.map(FileOrUUID::topublish).orElse(false) ? EmailService.UPLOAD_STATE.PUBLISHED :
                        (beforeDelete ? EmailService.UPLOAD_STATE.DELETED : EmailService.UPLOAD_STATE.UNPUBLISHED);
            } else {
                uploadState = EmailService.UPLOAD_STATE.UPLOADED;
            }
            serviceContainer.emailService().sendUpoadSuccessMail(application, dataName, uploadState, locale, dataVersioningResult, serviceContainer.authenticationService().getCurrentUser());
            return dataVersioningResult;
        }
        if (state instanceof JustStoredFile justStoredFile && file == null) {
            uploadState = EmailService.UPLOAD_STATE.DELETED;
        } else {
            uploadState = EmailService.UPLOAD_STATE.UPLOADED;
        }
        final List<ApplicationResult.DataSynthesis> dataSynthesis = Optional.ofNullable(serviceContainer.dataService().getReferenceSynthesis(application)).orElseGet(List::of);
        DataVersioningResult dataVersioningResult = DataVersioningResult.of(nameOrId, dataName, state.binaryFile().getId(), dataSynthesis);
        serviceContainer.emailService().sendUpoadSuccessMail(application, dataName, uploadState, locale, dataVersioningResult, serviceContainer.authenticationService().getCurrentUser());
        return dataVersioningResult;

    }

    private UUID publishData(String dataName, FileOrUUID fileOrUUID, Application application, State state) throws IOException {
        UUID dataId;
        if (fileOrUUID.topublish()) {
            dataId = serviceContainer.dataService().addData(application, dataName, new DataFile(fileOrUUID, state.binaryFile().getFileData()));
        } else {
            dataId = state.binaryFile().getId();
        }
        if (dataId != null && state.isRepository()) {
            BinaryFile binaryFile = state.binaryFile();
            binaryFile.markAsPublished(fileOrUUID.topublish());
            dataId = binaryFileRepository(application).store(binaryFile);
        }
        return dataId;
    }


    public StoreFile getStoreFile(
            Application application,
            String dataName,
            FileOrUUID fileOrUUID,
            String fileName,
            ApplicationDataWriter applicationDataWriter) {
        DataRepositoryForBuffer dataRepositoryWithBuffer = serviceContainer.dataService().getDataRepositoryWithBuffer(application);
        ReportErrors errors = new ReportErrors(jsonRowMapper);
        Function<UUID, Optional<BinaryFile>> resolveFileById = uuid -> binaryFileRepository(application).tryFindById(uuid);
        return AuthorizationPublicationServiceBuilder.builder(
                        application,
                        dataName,
                        fileName,
                        fileOrUUID,
                        applicationDataWriter,
                        resolveFileById
                )
                .testAndBuild(dataRepositoryWithBuffer);
    }

    @Transactional
    public DataVersioningResult unPublishVersionBeforeDelete(Locale locale, String applicationName, UUID id) {
        Optional<BinaryFile> storedFile = serviceContainer.binaryFileService().getFile(applicationName, id);
        if (storedFile.isPresent()) {
            Optional<String> dataName = storedFile
                    .map(BinaryFile::getParams)
                    .map(BinaryFileInfos::binaryFiledataset)
                    .map(BinaryFileDataset::getDatatype);
            if (dataName.isPresent()) {
                try {
                    return createData(
                            locale,
                            applicationName,
                            dataName.get(),
                            null,
                            """
                                    {
                                       "fileid":"%1$s",
                                       "topublish":false
                                    }""".formatted(id),
                            true
                    );
                } catch (IOException e) {
                    throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
                }
            }

        }
        return null;
    }

    private DataRepository dataRepository(Application application) {
        return repository.getRepository(application).data();
    }

    private BinaryFileRepository binaryFileRepository(Application application) {
        return repository.getRepository(application).binaryFile();
    }

    private void unPublishVersions(final Application application, final Set<BinaryFile> filesToStore, final String dataType) {
        filesToStore.forEach(f -> {
            dataRepository(application).removeByFileId(f.getId());
            f.markAsPublished(false);
            binaryFileRepository(application).store(f);
            serviceContainer.synthesisService().buildSynthesis(application.getName(), dataType, null);
        });
        if (dataType != null) {
            serviceContainer.synthesisService().buildSynthesis(application.getName(), dataType, null);
        }
    }

}