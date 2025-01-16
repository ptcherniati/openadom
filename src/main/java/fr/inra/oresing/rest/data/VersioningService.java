package fr.inra.oresing.rest.data;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.file.DataFile;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.rest.data.publication.*;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.services.ServiceContainerBean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
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
        Set<BinaryFile> filesToStore = new HashSet<>();
        State state = getStoreFile(application, dataName, params, file == null ? null : file.getOriginalFilename())
                .loadOrCreateFile(file, binaryFileRepository(application), serviceContainer.binaryFileService());
        if (state instanceof UnPublishedVersions unPublishedVersions) {
            FileOrUUID fileOrUUID = unPublishedVersions
                    .unPublishVersions(filesToStore, dataRepository(application), binaryFileRepository(application), serviceContainer.synthesisService())
                    .checkPublicationRights(filesToStore, dataRepository(application), serviceContainer.binaryFileService(), binaryFileRepository(application));

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
            final List<ApplicationResult.DataSynthesis> dataSynthesis = Optional.ofNullable(serviceContainer.dataService().getReferenceSynthesis(application)).orElseGet(List::of);
            return DataVersioningResult.of(nameOrId, dataName, dataId, dataSynthesis);
        }
        final List<ApplicationResult.DataSynthesis> dataSynthesis = Optional.ofNullable(serviceContainer.dataService().getReferenceSynthesis(application)).orElseGet(List::of);
        return DataVersioningResult.of(nameOrId, dataName, state.binaryFile().getId(), dataSynthesis);

    }


    public StoreFile getStoreFile(Application application, String dataName, String params, String fileName) {
        ReportErrors errors = new ReportErrors(jsonRowMapper);
        Function<Map<String, List<Ltree>>, Map<String, List<Ltree>>> requiredAuthorizationResolver = ra -> dataRepository(application).resolveRequiredAuthorizations(ra);
        Function<UUID, Optional<BinaryFile>> resolveFileById = uuid -> binaryFileRepository(application).tryFindById(uuid);
        return AuthorizationPublicationServiceBuilder.BUILDER(
                        errors,
                        application,
                        dataName,
                        fileName,
                        params,
                        requiredAuthorizationResolver,
                        resolveFileById
                )
                .buildAuthorizationForUserService(userRepository, serviceContainer.authorizationService());
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
