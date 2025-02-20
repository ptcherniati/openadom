package fr.inra.oresing.rest.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationDataWriter;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomain;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.exceptions.binaryfile.binaryfile.BadFileOrUUIDQuery;
import fr.inra.oresing.domain.file.DataFile;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.rest.data.publication.*;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.services.ServiceContainerBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;

@Slf4j
@Component
@Transactional(readOnly = true)

public class VersioningService implements ServiceContainerBean {
    private ServiceContainer serviceContainer;
    @Autowired
    private OreSiRepository repository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JsonRowMapper jsonRowMapper;

    @Transactional
    public DataVersioningResult createData(String nameOrId, String dataName, MultipartFile file, String params) throws IOException {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        String fileName = file == null ? null : file.getOriginalFilename();
        Optional<FileOrUUID> fileOrUUIDOpt = Optional.ofNullable(params)
                .filter(Objects::nonNull)
                .filter(Predicate.not("undefined"::equals))
                .map(json -> {
                    try {
                        return new ObjectMapper().readValue(params, FileOrUUID.class);
                    } catch (JsonProcessingException e) {
                        throw new BadFileOrUUIDQuery(e.getMessage());
                    }
                });
        Boolean toPublish = fileOrUUIDOpt
                .map(FileOrUUID::topublish)
                .orElse(false);
        ApplicationDataWriter applicationDataWriter = serviceContainer.authorizationService().getPrivilegeAssessorForApplication(PrivilegeApplicationDomain.DATA_WRITE, application)
                .forDataWrite(dataName, toPublish);
        Set<BinaryFile> filesToStore = new HashSet<>();
        DataRepositoryForBuffer dataRepositoryWithBuffer = serviceContainer.dataService().getDataRepositoryWithBuffer(application);
        State state = getStoreFile(application, dataName, fileOrUUIDOpt.orElse(null), fileName, applicationDataWriter)
                .loadOrCreateFile(file, binaryFileRepository(application), serviceContainer.binaryFileService());
        if (state instanceof UnPublishedVersions unPublishedVersions) {
            FileOrUUID fileOrUUID = unPublishedVersions
                    .unPublishVersions(filesToStore, dataRepository(application), binaryFileRepository(application), serviceContainer.synthesisService())
                    .checkAndStoreFile(filesToStore, serviceContainer.binaryFileService(), binaryFileRepository(application));

            UUID dataId = publishData(dataName, fileOrUUID, application, state);
            final List<ApplicationResult.DataSynthesis> dataSynthesis = Optional.ofNullable(serviceContainer.dataService().getReferenceSynthesis(application)).orElseGet(List::of);
            return DataVersioningResult.of(nameOrId, dataName, dataId, dataSynthesis);
        }
        final List<ApplicationResult.DataSynthesis> dataSynthesis = Optional.ofNullable(serviceContainer.dataService().getReferenceSynthesis(application)).orElseGet(List::of);
        return DataVersioningResult.of(nameOrId, dataName, state.binaryFile().getId(), dataSynthesis);

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
        return AuthorizationPublicationServiceBuilder.BUILDER(
                        errors,
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
    public DataVersioningResult unPublishVersionBeforeDelete(String applicationName, UUID id) {
        Optional<BinaryFile> storedFile = serviceContainer.binaryFileService().getFile(applicationName, id);
        if (storedFile.isPresent()) {
            Optional<String> dataName = storedFile.map(BinaryFile::getParams).map(BinaryFileInfos::binaryFiledataset).map(BinaryFileDataset::getDatatype);
            if (dataName.isPresent()) {
                try {
                    return createData(
                            applicationName,
                            dataName.get(),
                            null,
                            """
                                    {
                                       "fileid":"%1$s",
                                       "topublish":false
                                    }""".formatted(id)
                    );
                } catch (IOException e) {
                    throw new RuntimeException(e);
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

    public void setServiceContainer(ServiceContainer serviceContainer) {
        this.serviceContainer = serviceContainer;
    }
}
