package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationDataWriter;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.domain.services.synthesis.SynthesisService;
import fr.inra.oresing.persistence.BinaryFileInfos;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class AuthorizationPublicationService {
    public static final String DATA_NAME_CAN_T_BE_NULL = "dataName Can't be null";
    protected final ReportErrors errors;
    protected BinaryFile binaryFile;
    protected final StandardDataDescription dataDescription;
    protected final Application application;

    @Getter
    protected final String dataName;
    @Getter
    protected FileOrUUID fileOrUUID;
    protected final ApplicationDataWriter applicationDataWriter;

    public ApplicationDataWriter applicationDataWriter() {
        return this.applicationDataWriter;
    }

    protected AuthorizationPublicationService(
            ReportErrors errors,
            final Application application,
            final String dataName,
            FileOrUUID fileOrUUID,
            ApplicationDataWriter applicationDataWriter) {
        this.errors = errors;
        this.application = application;
        this.dataName = dataName != null ? dataName : Optional.ofNullable(fileOrUUID).map(FileOrUUID::binaryfiledataset).map(BinaryFileDataset::getDatatype).orElse(null);
        this.fileOrUUID = setFileOrUUID(fileOrUUID);
        this.dataDescription = buildDataDescription(application);
        this.applicationDataWriter = applicationDataWriter;
    }

    protected StandardDataDescription buildDataDescription(Application application) {
        return Optional.ofNullable(application)
                .map(name -> application.findData(dataName)
                        .orElseThrow(() -> new IllegalArgumentException(DATA_NAME_CAN_T_BE_NULL))
                )
                .orElse(null);
    }

    protected FileOrUUID setFileOrUUID(FileOrUUID fileOrUUIDLocal) {
        Optional.ofNullable(fileOrUUIDLocal)
                .map(par -> par.binaryfiledataset() != null ?
                        fileOrUUIDLocal.binaryfiledataset() :
                        BinaryFileDataset.EMPTY_INSTANCE()
                )
                .ifPresent(binaryFileDataset -> binaryFileDataset.setDatatype(dataName));
        return fileOrUUIDLocal;
    }


    protected boolean isRepository(final Application application, final String dataName) {
        Predicate<SubmissionType> isRepository = submission -> submission == SubmissionType.OA_VERSIONING;
        Function<Map<String, StandardDataDescription>, StandardDataDescription> getDataDescription = data -> data.get(dataName);
        return application.findData(dataName)
                .map(StandardDataDescription::submission)
                .map(Submission::strategy)
                .filter(isRepository)
                .isPresent();
    }

    protected BinaryFile getPublishedVersion(BinaryFileRepository binaryFileRepository) {
        assert fileOrUUID.binaryfiledataset() != null;
        return binaryFileRepository.findPublishedVersions(fileOrUUID.binaryfiledataset()).orElse(null);
    }

    protected void unPublishVersions(
            Set<BinaryFile> filesToStore,
            final DataRepository dataRepository,
            BinaryFileRepository binaryFileRepository,
            SynthesisService synthesisService
    ) {
        filesToStore
                .forEach(file -> {
                    dataRepository.removeByFileId(file.getId());
                    file.markAsPublished(false);
                    binaryFileRepository.store(file);
                    synthesisService.buildSynthesis(application.getName(), dataName, null);
                });
        if (dataName != null) {
            synthesisService.buildSynthesis(application.getName(), dataName, null);
        }
    }

    boolean fileMustBeJustStored() {
        boolean existsFileToPublish = Optional.ofNullable(binaryFile).map(BinaryFile::getId).isPresent();
        Boolean publishIsAsked = Optional.ofNullable(fileOrUUID).map(FileOrUUID::topublish).orElse(false);
        Boolean unPublishIsAsked = !publishIsAsked && Optional.ofNullable(binaryFile).map(BinaryFile::getParams).map(BinaryFileInfos::published).orElse(false);
        return
                isRepository() &&
                        !(
                                existsFileToPublish &&
                                        (publishIsAsked || unPublishIsAsked)
                        );
    }

    protected boolean isRepository() {
        return application.findSubmission(dataName)
                .map(Submission::strategy)
                .stream().anyMatch(SubmissionType.OA_VERSIONING::equals);
    }

    boolean fileMustBePublished() {
        return !isRepository() || Optional.ofNullable(fileOrUUID).map(FileOrUUID::topublish).orElse(false);
    }
}