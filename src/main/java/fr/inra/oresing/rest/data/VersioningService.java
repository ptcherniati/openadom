package fr.inra.oresing.rest.data;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.DataWriter;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.file.DataFile;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.domain.repository.file.BinaryFileRepository;
import fr.inra.oresing.mail.EmailService;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.data.publication.*;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.Function;

@Slf4j
@Component
@Transactional(readOnly = true)

public class VersioningService {
    private final ServiceContainer serviceContainer;
    private final OreSiRepository repository;
    private final UserRepository userRepository;
    private final JsonRowMapper jsonRowMapper;
    private final fr.inra.oresing.monitoring.compensation.CompensationLogService compensationLogService;
    private final PlatformTransactionManager txManager;

    public VersioningService(ServiceContainer serviceContainer, OreSiRepository repository,
                             UserRepository userRepository, JsonRowMapper jsonRowMapper,
                             fr.inra.oresing.monitoring.compensation.CompensationLogService compensationLogService,
                             PlatformTransactionManager txManager) {
        this.serviceContainer = serviceContainer;
        this.repository = repository;
        this.userRepository = userRepository;
        this.jsonRowMapper = jsonRowMapper;
        this.compensationLogService = compensationLogService;
        this.txManager = txManager;
    }

    @Transactional
    public DataVersioningResult createData(
            Locale locale,
            String nameOrId,
            String dataName,
            fr.inra.oresing.domain.data.DataFile file,
            boolean beforeDelete,
            boolean withEmail) throws IOException {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        String fileName = file == null ? null : file.fileName();
        Optional<FileOrUUID> fileOrUUIDOpt = OreSiApiRequestContext.getAuthentication()
                .map(OreSiAuthenticationToken::getFileOrUUID);
        Set<BinaryFile> filesToStore = new HashSet<>();
        DataWriter applicationDataWriter = OreSiApiRequestContext.getAuthentication()
                .map(OreSiAuthenticationToken::getApplicationPersona)
                .filter(DataWriter.class::isInstance)
                .map(DataWriter.class::cast)
                .orElse(null);

        // Le binaryfile + son fileData doivent etre committes AVANT que la
        // cascade lance ses workers ( cascade ouvre des connexions fresh
        // sur le pool , elles ne voient pas une row uncommitted dans la
        // tx Spring courante -> referencevalue_binaryfile_fkey violation
        // dans le FinalizeHook ) . On force REQUIRES_NEW : la sous-tx
        // commit immediatement , le binaryfile devient visible cross-conn .
        // Le compensation_log ( track plus bas , aussi REQUIRES_NEW ) garantit
        // qu'un orphan sera nettoye en cas d'echec ulterieur ( cascade /
        // publishData / mail ) , conformement au pattern outbox documente
        // en tete de CompensationLogService .
        TransactionTemplate storeFileTx = new TransactionTemplate(txManager);
        storeFileTx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        State state = storeFileTx.execute(status -> {
            try {
                return getStoreFile(application, dataName, fileOrUUIDOpt.orElse(null), fileName, applicationDataWriter)
                        .loadOrCreateFile(file, binaryFileRepository(application), serviceContainer.binaryFileService());
            } catch (FileNotFoundException e) {
                throw new UncheckedIOException(e);
            }
        });

        // Compensation log : binaryfile committe par REQUIRES_NEW dans
        // BinaryFileService.storeFile -> visible cross-connection mais
        // expose au risque d'orphan si la suite du flow ( cascade /
        // publish ) echoue . On tracke l'op pour cleanup automatique
        // ( finally + sweeper ) .
        UUID compId = null;
        BinaryFile newBinaryFile = state.binaryFile();
        if (newBinaryFile != null && state instanceof UnPublishedVersions) {
            try {
                java.util.Map<String, Object> payload = new java.util.LinkedHashMap<>();
                payload.put("fileName",   fileName);
                payload.put("dataName",   dataName);
                payload.put("appName",    application.getName());
                payload.put("sizeBytes",  file != null ? file.fileSize() : null);
                UUID userIdForCompLog = OreSiApiRequestContext.getRequestUserId();
                String userLoginForCompLog = serviceContainer.authenticationService()
                        .getCurrentUserRoles().userLogin();
                compId = compensationLogService.record(
                        fr.inra.oresing.monitoring.compensation.handlers.BinaryFileCompensationHandler.OP_TYPE,
                        application.getName(),    // target_schema = nom application
                        "binaryfile",
                        newBinaryFile.getId().toString(),
                        null,                     // correlationId : pas dispo a ce niveau ; cascade le set ailleurs
                        userIdForCompLog,
                        userLoginForCompLog,
                        payload);
            } catch (RuntimeException ex) {
                log.warn("CompensationLog.record failed ( best-effort ) : {}", ex.getMessage());
            }
        }

        try {
        EmailService.UPLOAD_STATE uploadState;
        if (state instanceof UnPublishedVersions unPublishedVersions) {
            FileOrUUID fileOrUUID = unPublishedVersions
                    .unPublishVersions(filesToStore, dataRepository(application), binaryFileRepository(application), serviceContainer.synthesisService())
                    .checkAndStoreFile(filesToStore, serviceContainer.binaryFileService(), binaryFileRepository(application));

            UUID dataId = publishData(dataName, fileOrUUID, application, state);
            // Cascade 3.0.0 deferred : le UPSERT staging -> table finale
            // tourne dans afterCommit ; lire le compteur ici donnerait une
            // valeur stale . On differe le calcul de dataSynthesis a
            // finalizePostCommit ( appele par la couche REST post .get() ) .
            // De meme pour le mail : sinon il porterait un compteur stale .
            if (unPublishedVersions.isRepository()) {
                uploadState = fileOrUUIDOpt.map(FileOrUUID::topublish).orElse(false) ? EmailService.UPLOAD_STATE.PUBLISHED :
                        (beforeDelete ? EmailService.UPLOAD_STATE.DELETED : EmailService.UPLOAD_STATE.UNPUBLISHED);
            } else {
                uploadState = EmailService.UPLOAD_STATE.UPLOADED;
            }
            DataVersioningResult dataVersioningResult = DataVersioningResult.of(nameOrId, dataName, dataId, List.of(), uploadState);
            if (withEmail) {
                safeSendUploadSuccessMail(application, dataName, fileName, uploadState, locale, dataVersioningResult);
                if (compId != null) compensationLogService.confirm(compId);
                return dataVersioningResult;
            }
        }
        if (state instanceof JustStoredFile justStoredFile && file == null) {
            uploadState = EmailService.UPLOAD_STATE.DELETED;
        } else {
            uploadState = EmailService.UPLOAD_STATE.UPLOADED;
        }
        DataVersioningResult dataVersioningResult = DataVersioningResult.of(nameOrId, dataName, state.binaryFile().getId(), List.of(), uploadState);
        if (withEmail) {
            safeSendUploadSuccessMail(application, dataName, fileName, uploadState, locale, dataVersioningResult);
        }
        if (compId != null) compensationLogService.confirm(compId);
        // Invalidation cache referencedFiles : toute mutation reelle du
        // binaryfile / des referencevalue rows associees rend le cache
        // potentiellement obsolete. Le toggle publish ( PublishToggleUseCase )
        // ne passe pas ici donc il ne deborde pas le cache pour rien.
        if (serviceContainer.binaryFileService() instanceof fr.inra.oresing.rest.binaryFile.BinaryFileService bfs) {
            bfs.invalidateReferencedFilesCache(application.getName());
        }
        return dataVersioningResult;

        } catch (RuntimeException | IOException ex) {
            // Best-effort cleanup synchrone : tente la compensation immediatement
            // ( smart-check protege contre data loss ) . Si fail , le sweeper
            // rattrapera apres TTL .
            if (compId != null) {
                try {
                    compensationLogService.compensateNow(compId);
                } catch (RuntimeException compErr) {
                    log.warn("compensateNow failed for {} ( sweeper will retry ) : {}",
                            compId, compErr.getMessage());
                }
            }
            throw ex;
        }
    }

    /**
     * Finalisation post-commit du cycle createData : recalcule
     * {@code dataSynthesis} ( compteur de lignes ) sur la table finale a
     * jour ( apres que le UPSERT differe cascade 3.0.0 ait fire dans
     * afterCommit ) , optionnellement envoie le mail de notification avec
     * cette valeur fraiche , et renvoie le {@link DataVersioningResult}
     * enrichi pour la reponse HTTP .
     *
     * <p>A appeler par la couche REST APRES {@code Future.get()} ( ou
     * apres le retour de {@code createData} en mode synchrone ) ; pas
     * pendant la transaction Spring qui fait le publish , sinon la
     * lecture verrait l etat pre-storeAll et le compteur serait stale .
     *
     * @param sendMail {@code true} pour envoyer le mail de notification
     *                 ( appel le plus courant depuis OreSiResources ) ;
     *                 {@code false} pour les flows qui ne notifient pas
     *                 ( admin , tests )
     */
    public DataVersioningResult finalizePostCommit(Locale locale,
                                                    String nameOrId,
                                                    String dataName,
                                                    String fileName,
                                                    DataVersioningResult dataVersioningResult,
                                                    boolean sendMail) {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        List<ApplicationResult.DataSynthesis> freshSynthesis = Optional
                .ofNullable(serviceContainer.dataService().getReferenceSynthesis(application))
                .orElseGet(List::of);
        DataVersioningResult fresh = dataVersioningResult.withDataSynthesis(freshSynthesis);
        if (sendMail && fresh.uploadState() != null) {
            safeSendUploadSuccessMail(application, dataName, fileName,
                    fresh.uploadState(), locale, fresh);
        }
        return fresh;
    }

    /**
     * Sends the post-import notification mail without ever propagating an
     * exception. Mail failure is best-effort : an unreachable SMTP server
     * ( the previous behaviour caused MailSendException to bubble up ,
     * tripping the surrounding @Transactional and rolling back the entire
     * import - dropping the workflow_log row for big files like the
     * 274 706-line case ) must NOT abort a successful upload.
     */
    private void safeSendUploadSuccessMail(Application application,
                                           String dataName,
                                           String fileName,
                                           EmailService.UPLOAD_STATE uploadState,
                                           Locale locale,
                                           DataVersioningResult dataVersioningResult) {
        try {
            serviceContainer.emailService().sendUpoadSuccessMail(
                    application, dataName, fileName, uploadState, locale,
                    dataVersioningResult,
                    serviceContainer.authenticationService().getCurrentUser());
        } catch (RuntimeException mailFailure) {
            log.warn("Notification mail post-import failed (non blocking) for application={} dataName={} fileName={} : {}",
                    application != null ? application.getName() : null, dataName, fileName,
                    mailFailure.getMessage());
        }
    }

    private UUID publishData(String dataName, FileOrUUID fileOrUUID, Application application, State state) throws IOException {
        UUID dataId;
        if (fileOrUUID.topublish()) {
            dataId = serviceContainer.dataService().addData(application, dataName, new DataFile(fileOrUUID, state.binaryFile().getFileData()));
        } else {
            dataId = state.binaryFile().getId();
        }
        if (dataId != null && state.isRepository()) {
            BinaryFile binaryFile = serviceContainer.binaryFileService()
                    .getFile(application.getName(), state.binaryFile().getId())
                    .orElse(state.binaryFile());
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
            DataWriter applicationDataWriter) {
        DataRepository dataRepository = serviceContainer.dataService().getDataRepository(application);
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
                .testAndBuild(dataRepository);
    }

    @Transactional
    public DataVersioningResult unPublishVersionBeforeDelete(
            Locale locale, String applicationName, UUID id, boolean withEmail) throws IOException {
        Optional<BinaryFile> storedFile = serviceContainer.binaryFileService().getFile(applicationName, id);
        if (storedFile.isPresent()) {
            Optional<String> dataName = storedFile
                    .map(BinaryFile::getParams)
                    .map(BinaryFileInfos::binaryFiledataset)
                    .map(BinaryFileDataset::getDatatype);
            if (dataName.isPresent()) {
                return createData(
                        locale,
                        applicationName,
                        dataName.get(),
                        null,
                        true,
                        withEmail
                );
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
        });
        // Single post-loop recompute ( see AuthorizationPublicationService.unPublishVersions
        // for rationale ) - buildSynthesis is idempotent , one call after all
        // files are unpublished gives the same final state as one per file.
        if (dataType != null) {
            serviceContainer.synthesisService().buildSynthesis(application.getName(), dataType, null);
        }
    }

}