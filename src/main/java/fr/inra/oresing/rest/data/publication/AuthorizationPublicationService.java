package fr.inra.oresing.rest.data.publication;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.domain.services.synthesis.SynthesisService;
import fr.inra.oresing.persistence.BinaryFileInfos;
import fr.inra.oresing.rest.model.authorization.AuthorizationsResult;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public class AuthorizationPublicationService {
    protected final ReportErrors errors;
    protected BinaryFile binaryFile;
    protected final StandardDataDescription dataDescription;
    protected final Application application;

    public String getDataName() {
        return this.dataName;
    }

    protected final String dataName;
    protected AuthorizationsResult authorizationsForPublic;
    protected AuthorizationsResult authorizationsForUser;
    protected FileOrUUID params;

    public AuthorizationForUser getAuthorizations() {
        return this.authorizations;
    }

    protected AuthorizationForUser authorizations;
    protected AuthorizationPublicationService(ReportErrors errors, final Application application, final String dataName, FileOrUUID params) {
        this.errors = errors;
        this.application = application;
        this.dataName = dataName!=null?dataName:Optional.ofNullable(params).map(FileOrUUID::binaryfiledataset).map(BinaryFileDataset::getDatatype).orElse(null);
        this.params = buildParams(params);
        this.dataDescription = buildDataDescription(application);
    }

    public FileOrUUID getParams() {
        return this.params;
    }

    protected StandardDataDescription buildDataDescription(Application application) {
        return application.findData(dataName)
                .orElseThrow(() -> new IllegalArgumentException("dataName Can't be null"));
    }

    protected FileOrUUID buildParams(FileOrUUID params) {
        Optional.ofNullable(params)
                .map(par -> par.binaryfiledataset() != null ?
                        params.binaryfiledataset() :
                        BinaryFileDataset.EMPTY_INSTANCE()
                )
                .ifPresent(binaryFileDataset -> binaryFileDataset.setDatatype(dataName));
        return params;
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
        assert params.binaryfiledataset() != null;
        return binaryFileRepository.findPublishedVersions(params.binaryfiledataset()).orElse(null);
    }

    protected void unPublishVersions(
            Set<BinaryFile> filesToStore,
            final DataRepository dataRepository,
            BinaryFileRepository binaryFileRepository,
            SynthesisService synthesisService
    ) {
        filesToStore.stream()
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

    boolean hasRightForPublishOrUnPublish() {
        return authorizations.hasRightForPublishOrUnPublish();
    }

    boolean hasRightForDeposit() {
        return authorizations.hasRightForDeposit();
    }

    boolean isApplicationCreator() {
        return authorizations.isApplicationCreator();
    }

    boolean isRepository() {
        return authorizations.isRepository();
    }

    boolean canDeposit() {
        return authorizations.canDeposit();
    }

    AuthorizationsResult authorizationsForUser() {
        return authorizations == null ? authorizationsForUser : authorizations.authorizationsForUserOrPublic();
    }

    boolean fileMustBeJustStored() {
        boolean existsFileToPublish = Optional.ofNullable(binaryFile).map(BinaryFile::getId).isPresent();
        Boolean publishIsAsked = Optional.ofNullable(params).map(FileOrUUID::topublish).orElse(false);
        Boolean unPublishIsAsked = !publishIsAsked && Optional.ofNullable(binaryFile).map(BinaryFile::getParams).map(BinaryFileInfos::published).orElse(false);
        return
                isRepository() &&
                !(
                        existsFileToPublish &&
                        (publishIsAsked || unPublishIsAsked)
                );
    }

    boolean fileMustBePublished() {
        return !isRepository() || Optional.ofNullable(params).map(FileOrUUID::topublish).orElse(false);
    }
}