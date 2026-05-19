package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.DataWriter;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.domain.services.synthesis.SynthesisService;
import lombok.Getter;

import java.util.Optional;
import java.util.Set;

public class AuthorizationPublicationService {
    public static final String DATA_NAME_NOT_FOUND = "dataName not found";
    protected final StandardDataDescription dataDescription;
    protected final Application application;
    @Getter
    protected final String dataName;
    protected final DataWriter applicationDataWriter;
    protected BinaryFile binaryFile;
    @Getter
    protected FileOrUUID fileOrUUID;

    protected AuthorizationPublicationService(
            final Application application,
            final String dataName,
            FileOrUUID fileOrUUID,
            DataWriter applicationDataWriter) {
        this.application = application;
        this.dataName = dataName != null ? dataName : Optional.ofNullable(fileOrUUID).map(FileOrUUID::binaryfiledataset).map(BinaryFileDataset::getDatatype).orElse(null);
        this.fileOrUUID = setFileOrUUID(fileOrUUID);
        this.dataDescription = buildDataDescription(application);
        this.applicationDataWriter = applicationDataWriter;
    }

    public DataWriter applicationDataWriter() {
        return this.applicationDataWriter;
    }

    protected StandardDataDescription buildDataDescription(Application application) {
        return Optional.ofNullable(application)
                .map(name -> application.findData(dataName)
                        .orElseThrow(() -> new IllegalArgumentException(DATA_NAME_NOT_FOUND))
                )
                .orElse(null);
    }

    protected FileOrUUID setFileOrUUID(FileOrUUID fileOrUUIDLocal) {
        Optional.ofNullable(fileOrUUIDLocal)
                .map(par -> par.binaryfiledataset() != null ?
                        fileOrUUIDLocal.binaryfiledataset() :
                        BinaryFileDataset.emptyInstance()
                )
                .ifPresent(binaryFileDataset -> binaryFileDataset.setDatatype(dataName));
        return fileOrUUIDLocal;
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
                });
        // Single post-loop synthesis recompute : buildSynthesis fully
        // rebuilds the precomputed oresisynthesis rows for the dataType
        // ( DELETE + INSERT-from-scratch ) , so calling it once after all
        // files are unpublished gives the exact same final state as the
        // previous N+1 calls ( one per file inside the loop , one after )
        // without scanning referencevalue N times.
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
}