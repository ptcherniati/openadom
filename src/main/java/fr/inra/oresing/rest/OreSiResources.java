package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Range;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.BinaryFileInfos;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.ApplicationInformation;
import fr.inra.oresing.domain.application.SqlIdentifierUtils;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.authorization.GetGrantableResult;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCanDeleteRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterForPublishException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationDataDelete;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationUser;
import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResultRest;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.read.ouput.KeepAliveZipOutputStream;
import fr.inra.oresing.domain.data.read.query.OutPut;
import fr.inra.oresing.domain.exceptions.ExceptionMessage;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.application.BadLabelNameException;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import fr.inra.oresing.rest.data.publication.State;
import fr.inra.oresing.rest.data.publication.StoreFile;
import fr.inra.oresing.rest.exceptions.OreSiIOException;
import fr.inra.oresing.rest.model.additionalfiles.CreateAdditionalFileRequest;
import fr.inra.oresing.rest.model.additionalfiles.exceptions.BadAdditionalFileParamsSearchException;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.model.data.*;
import fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery;
import fr.inra.oresing.rest.model.reference.GetReferenceResult;
import fr.inra.oresing.rest.model.rightsrequest.CreateRightsRequestRequest;
import fr.inra.oresing.rest.model.rightsrequest.GetAdditionalFilesResult;
import fr.inra.oresing.rest.model.rightsrequest.GetRightsRequestResult;
import fr.inra.oresing.rest.model.rightsrequest.RightsRequestInfos;
import fr.inra.oresing.rest.model.synthesis.SynthesisResult;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeProgress;
import fr.inra.oresing.rest.reactive.ReactiveTypeResult;
import fr.inra.oresing.rest.services.AdditionalFileService;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.usecases.application.*;
import fr.inra.oresing.rest.usecases.data.*;
import fr.inra.oresing.rest.usecases.messaging.SendUploadErrorsMailUseCase;
import fr.inra.oresing.rest.usecases.metadata.rightsrequest.CreateOrUpdateRightsRequestUseCase;
import fr.inra.oresing.rest.usecases.metadata.rightsrequest.FindRightsRequestUseCase;
import fr.inra.oresing.rest.usecases.metadata.synthesis.BuildSynthesisUseCase;
import fr.inra.oresing.rest.usecases.metadata.synthesis.GetSynthesisUseCase;
import fr.inra.oresing.rest.usecases.metadata.synthesis.GetSynthesisWithVariableUseCase;
import fr.inra.oresing.rest.usecases.security.authentication.GetCurrentUserUseCase;
import fr.inra.oresing.rest.usecases.security.authorization.GetAllUsersUseCase;
import fr.inra.oresing.rest.usecases.security.authorization.GetAuthorizationScopesUseCase;
import fr.inra.oresing.rest.usecases.storage.additionalfile.CreateOrUpdateAdditionalFileUseCase;
import fr.inra.oresing.rest.usecases.storage.additionalfile.DeleteAdditionalFilesUseCase;
import fr.inra.oresing.rest.usecases.storage.additionalfile.FindAdditionalFileUseCase;
import fr.inra.oresing.rest.usecases.storage.additionalfile.GetAdditionalFilesZipStreamUseCase;
import fr.inra.oresing.rest.usecases.storage.binaryfile.*;
import fr.inra.oresing.rest.usecases.storage.versioning.PublishLifecycleService;
import fr.inra.oresing.workflow.cascade.ExtractionRateLimiter;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import fr.inra.oresing.workflow.cascade.metrics.OpenadomMetrics;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.TeeOutputStream;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "Bearer Authentication")
public class OreSiResources {
    public static final String HEADER_ACCEPT_LANGUAGE = "Accept-Language";
    public static final String JS_UNDEFINED = "undefined";
    public static final String NOT_FOUND_DATA_NAME = "notFoundDataName";
    public static final String FILE_ID = "fileId";
    public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    public static final String HEADER_ATTACHMENT_FILENAME = "attachment;filename=%1$s";
    public static final String ERROR_EMPTY_FILE = "EmptyFile";
    public static final String HEADER_ATTACHMENT_FILENAME_S_CSV = "attachment; filename=%s.csv";
    public static final String HEADER_PRAGMA = "Pragma";
    public static final String HEADER_EXPIRES = "Expires";
    public static final String EXPIRED_TIME = "0";
    public static final String HEADER_NO_CACHE = "no-cache";
    public static final String IO_ERROR_FR = "Exception lors de la lecture et du streaming de données ";
    public static final String IO_ERROR_EN = "Exception while reading and streaming data  ";
    public static final String HEADER_ZIP = "attachment; filename=additionalFiles.zip";
    public static final String HEADER_APPLICATION_ZIP_CHARSET_UTF_8 = "application/zip;charset=UTF-8";
    public static final String LIST_DELIMITER = ",";
    public static final String IO_DELETE_ERROR = "Erreur lors de la suppression du fichier temporaire";
    public static final DateTimeFormatter TIMESTAMP_FORMATER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    public static final String EMAIL_ERROR = "Erreur lors de l'envoi du lien ZIP par e-mail";
    public static final String IO_ADDING_ERROR = "Error adding error file to ZIP";
    public static final String IO_WRITING_CSV_ERROR = "Erreur lors de l'écriture des données CSV";
    public static final String IO_UPOAD_ERROR_FR = "Une erreur s'est produite lors du téléchargement.";
    public static final String IO_UPOAD_ERROR_EN = "An error occurred during download.";
    public static final String FILE_ERROR = "error.txt";
    public static final String FR = "fr";
    public static final String EN = "en";
    public static final String TMP = "/tmp";
    public static final String DATA_ZIP = "%s-%s-data";
    public static final String DATA_SERVICE_PATH_PATTERN = "/applications/%s/data/%s";
    final UserRepository userRepository;
    final ServiceContainer serviceContainer;
    final LocaleResolver localeResolver;
    final String frontendOrigin;
    private final JsonRowMapper mapper;
    private final FindRightsRequestUseCase findRightsRequestUseCase;
    private final CreateOrUpdateRightsRequestUseCase createOrUpdateRightsRequestUseCase;
    private final FindReferenceUseCase findReferenceUseCase;
    private final FindAdditionalFileUseCase findAdditionalFileUseCase;
    private final DeleteAdditionalFilesUseCase deleteAdditionalFilesUseCase;
    private final CreateOrUpdateAdditionalFileUseCase createOrUpdateAdditionalFileUseCase;
    private final GetCharteUseCase getCharteUseCase;
    private final GetAdditionalFilesZipStreamUseCase getAdditionalFilesZipStreamUseCase;
    private final CreateDataUseCase createDataUseCase;
    private final GetFileWithDataUseCase getFileWithDataUseCase;
    private final GetFilesOnRepositoryUseCase getFilesOnRepositoryUseCase;
    private final GetStoreFileUseCase getStoreFileUseCase;
    private final PublishLifecycleService publishLifecycleService;
    private final GetSynthesisUseCase getSynthesisUseCase;
    private final GetSynthesisWithVariableUseCase getSynthesisWithVariableUseCase;
    private final BuildSynthesisUseCase buildSynthesisUseCase;
    private final GetAllUsersUseCase getAllUsersUseCase;
    /**
     * ObjectMapper dédié à la sérialisation manuelle de {@code GetDataResult}
     * dans {@link #getAllDataJson} ( ETag content-aware ). Static + final
     * pour le partage thread-safe entre requêtes ; configuré une fois avec
     * {@link JavaTimeModule} pour gérer les dates ISO de manière cohérente
     * avec le converter Spring par défaut.
     */
    private static final ObjectMapper jacksonObjectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private final GetAuthorizationScopesUseCase getAuthorizationScopesUseCase;
    private final GetApplicationOrAccordingToRightsUseCase getApplicationOrAccordingToRightsUseCase;
    private final BuildOpenAdomUseCase buildOpenAdomUseCase;
    private final GetApplicationUseCase getApplicationUseCase;
    private final GetFormatCheckedUseCase getFormatCheckedUseCase;
    private final GetReferenceDisplaysByIdUseCase getReferenceDisplaysByIdUseCase;
    private final GetDataCsvStreamUseCase getDataCsvStreamUseCase;
    private final FindDataUseCase findDataUseCase;
    private final GetCheckedFormatComponentsUseCase getCheckedFormatComponentsUseCase;
    private final DeleteDataUseCase deleteDataUseCase;
    private final GetApplicationsUseCase getApplicationsUseCase;
    private final ValidateConfigurationUseCase validateConfigurationUseCase;
    private final CreateApplicationUseCase createApplicationUseCase;
    private final ChangeApplicationConfigurationUseCase changeApplicationConfigurationUseCase;
    private final BuildDataZipUseCase buildDataZipUseCase;
    private final SendZipLinkByMailUseCase sendZipLinkByMailUseCase;
    private final ReadEntryUseCase readEntryUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final SendUploadErrorsMailUseCase sendUploadErrorsMailUseCase;
    private final ExtractionRateLimiter extractionRateLimiter;
    private final OpenadomMetrics metrics;
    private final WorkflowLogWriter workflowLogWriter;
    private final fr.inra.oresing.workflow.extraction.ExtractionLifecycle extractionLifecycle;

    /**
     * Rate limiter sliding-window pour /filters . Injecté en field
     * @Autowired pour éviter de toucher le constructor géant ( ~80
     * dépendances ) . Le service est singleton stateless ( hormis son
     * {@code ConcurrentHashMap} interne ) , l'injection field est sûre .
     */
    @org.springframework.beans.factory.annotation.Autowired
    private fr.inra.oresing.rest.data.FilterListRateLimiter filterListRateLimiter;

    /**
     * Timeout applique au {@code Future.get()} des taches soumises a
     * {@code heavyExecutorService}. Garantit qu'un import bloque ne
     * tient pas indefiniment le thread HTTP / connexion DB . En test
     * on reduit via {@code openadom.http.streaming.timeout} pour
     * faire echouer rapidement les tests qui hangeraient autrement
     * ( cf TestReferencesErrors , app_test_monsoere ) .
     */
    @Value("${openadom.http.streaming.timeout:6h}")
    private Duration heavyTaskTimeout;
    Executor fastExecutor;
    Executor normalExecutor;
    Executor heavyExecutor;
    Executor backupExecutor;
    ExecutorService fastExecutorService;
    ExecutorService normalExecutorService;
    ExecutorService heavyExecutorService;
    ExecutorService backupExecutorService;

    public OreSiResources(
            UserRepository userRepository,
            ServiceContainer serviceContainer,
            LocaleResolver localeResolver,
            JsonRowMapper mapper,
            @Value("${allowed.origin}") String frontendOrigin,
            @Qualifier("fastServiceExecutor") Executor fastExecutor,      // ✅ Fast executor
            @Qualifier("normalServiceExecutor") Executor normalExecutor,  // ✅ Normal executor
            @Qualifier("heavyServiceExecutor") Executor heavyExecutor,    // ✅ Heavy executor
            @Qualifier("backupExecutor") Executor backupExecutor,
            @Qualifier("fastExecutorService") ExecutorService fastExecutorService,      // ✅ Fast executor
            @Qualifier("normalExecutorService") ExecutorService normalExecutorService,  // ✅ Normal executor
            @Qualifier("heavyExecutorService") ExecutorService heavyExecutorService,    // ✅ Heavy executor
            @Qualifier("backupExecutorService") ExecutorService backupExecutorService,
            FindRightsRequestUseCase findRightsRequestUseCase,
            CreateOrUpdateRightsRequestUseCase createOrUpdateRightsRequestUseCase,
            FindReferenceUseCase findReferenceUseCase,
            FindAdditionalFileUseCase findAdditionalFileUseCase,
            DeleteAdditionalFilesUseCase deleteAdditionalFilesUseCase,
            CreateOrUpdateAdditionalFileUseCase createOrUpdateAdditionalFileUseCase,
            GetCharteUseCase getCharteUseCase,
            GetAdditionalFilesZipStreamUseCase getAdditionalFilesZipStreamUseCase,
            CreateDataUseCase createDataUseCase,
            GetStoreFileUseCase getStoreFileUseCase,
            PublishLifecycleService publishLifecycleService,
            GetSynthesisUseCase getSynthesisUseCase,
            GetSynthesisWithVariableUseCase getSynthesisWithVariableUseCase,
            BuildSynthesisUseCase buildSynthesisUseCase,
            GetAllUsersUseCase getAllUsersUseCase,
            GetAuthorizationScopesUseCase getAuthorizationScopesUseCase,
            GetApplicationOrAccordingToRightsUseCase getApplicationOrAccordingToRightsUseCase,
            BuildOpenAdomUseCase buildOpenAdomUseCase,
            GetApplicationUseCase getApplicationUseCase,
            GetFormatCheckedUseCase getFormatCheckedUseCase,
            GetReferenceDisplaysByIdUseCase getReferenceDisplaysByIdUseCase,
            GetDataCsvStreamUseCase getDataCsvStreamUseCase,
            GetFileWithDataUseCase getFileWithDataUseCase,
            GetFilesOnRepositoryUseCase getFilesOnRepositoryUseCase,
            FindDataUseCase findDataUseCase,
            GetCheckedFormatComponentsUseCase getCheckedFormatComponentsUseCase,
            DeleteDataUseCase deleteDataUseCase,
            GetApplicationsUseCase getApplicationsUseCase,
            ValidateConfigurationUseCase validateConfigurationUseCase,
            CreateApplicationUseCase createApplicationUseCase,
            ChangeApplicationConfigurationUseCase changeApplicationConfigurationUseCase,
            BuildDataZipUseCase buildDataZipUseCase,
            SendZipLinkByMailUseCase sendZipLinkByMailUseCase,
            ReadEntryUseCase readEntryUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase,
            SendUploadErrorsMailUseCase sendUploadErrorsMailUseCase,
            ExtractionRateLimiter extractionRateLimiter,
            OpenadomMetrics metrics,
            WorkflowLogWriter workflowLogWriter,
            fr.inra.oresing.workflow.extraction.ExtractionLifecycle extractionLifecycle
    ) {
        this.userRepository = userRepository;
        this.serviceContainer = serviceContainer;
        this.localeResolver = localeResolver;
        this.frontendOrigin = frontendOrigin;
        this.mapper = mapper;
        this.fastExecutor = fastExecutor;
        this.normalExecutor = normalExecutor;
        this.heavyExecutor = heavyExecutor;
        this.backupExecutor = backupExecutor;
        this.fastExecutorService = fastExecutorService;
        this.normalExecutorService = normalExecutorService;
        this.heavyExecutorService = heavyExecutorService;
        this.backupExecutorService = backupExecutorService;
        this.findRightsRequestUseCase = findRightsRequestUseCase;
        this.createOrUpdateRightsRequestUseCase = createOrUpdateRightsRequestUseCase;
        this.findReferenceUseCase = findReferenceUseCase;
        this.findAdditionalFileUseCase = findAdditionalFileUseCase;
        this.deleteAdditionalFilesUseCase = deleteAdditionalFilesUseCase;
        this.createOrUpdateAdditionalFileUseCase = createOrUpdateAdditionalFileUseCase;
        this.getCharteUseCase = getCharteUseCase;
        this.getAdditionalFilesZipStreamUseCase = getAdditionalFilesZipStreamUseCase;
        this.createDataUseCase = createDataUseCase;
        this.getStoreFileUseCase = getStoreFileUseCase;
        this.publishLifecycleService = publishLifecycleService;
        this.getSynthesisUseCase = getSynthesisUseCase;
        this.getSynthesisWithVariableUseCase = getSynthesisWithVariableUseCase;
        this.buildSynthesisUseCase = buildSynthesisUseCase;
        this.getAllUsersUseCase = getAllUsersUseCase;
        this.getAuthorizationScopesUseCase = getAuthorizationScopesUseCase;
        this.getApplicationOrAccordingToRightsUseCase = getApplicationOrAccordingToRightsUseCase;
        this.buildOpenAdomUseCase = buildOpenAdomUseCase;
        this.getApplicationUseCase = getApplicationUseCase;
        this.getFormatCheckedUseCase = getFormatCheckedUseCase;
        this.getReferenceDisplaysByIdUseCase = getReferenceDisplaysByIdUseCase;
        this.getDataCsvStreamUseCase = getDataCsvStreamUseCase;
        this.getFileWithDataUseCase = getFileWithDataUseCase;
        this.getFilesOnRepositoryUseCase = getFilesOnRepositoryUseCase;
        this.findDataUseCase = findDataUseCase;
        this.getCheckedFormatComponentsUseCase = getCheckedFormatComponentsUseCase;
        this.deleteDataUseCase = deleteDataUseCase;
        this.getApplicationsUseCase = getApplicationsUseCase;
        this.validateConfigurationUseCase = validateConfigurationUseCase;
        this.createApplicationUseCase = createApplicationUseCase;
        this.changeApplicationConfigurationUseCase = changeApplicationConfigurationUseCase;
        this.buildDataZipUseCase = buildDataZipUseCase;
        this.sendZipLinkByMailUseCase = sendZipLinkByMailUseCase;
        this.readEntryUseCase = readEntryUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.sendUploadErrorsMailUseCase = sendUploadErrorsMailUseCase;
        this.extractionRateLimiter = extractionRateLimiter;
        this.metrics = metrics;
        this.workflowLogWriter = workflowLogWriter;
        this.extractionLifecycle = extractionLifecycle;
    }


    /**
     * Best-effort resolution of the caller login from the current request
     * context. Returns null if no user is bound to the thread , in which
     * case the dashboard falls back to showing the UUID alone.
     */
    private String resolveCurrentLogin() {
        try {
            return serviceContainer.authenticationService().getCurrentUserRoles().userLogin();
        } catch (RuntimeException e) {
            return null;
        }
    }


    /**
     * Code langue par défaut quand le header {@code Accept-Language} est absent
     * ou mal formé . Choix "fr" aligné sur le défaut historique de l'application
     * ( cf {@link #getDefaultLocale} ) .
     */
    static final String DEFAULT_LANGUAGE = "fr";

    /**
     * Résout le code langue ( "fr" / "en" / ... ) depuis le header
     * {@code Accept-Language} de la requête . Fallback sur
     * {@link #DEFAULT_LANGUAGE} si le header est absent ou mal formé .
     *
     * <p>Utilisé par {@code getDataFilters} pour propager la locale au
     * calcul des {@code variables} ( filtre {@code isHiddenOrHasLangRestriction} ) .
     *
     * <p>Pure function ( static , aucun side effect , aucun accès au
     * SecurityContext ou HttpServletRequest ) -&gt; testable en unit test
     * sans MockMvc .
     */
    static String resolveLanguage(final String acceptLanguageHeader) {
        if (acceptLanguageHeader == null || acceptLanguageHeader.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        try {
            // Accept-Language peut contenir des q-values ( ex "fr-FR,fr;q=0.9,en;q=0.8" ) .
            // On extrait juste le 1er token et on garde seulement les 2 premiers chars .
            final String firstToken = acceptLanguageHeader.split(",")[0].trim();
            final String lang = firstToken.split("-")[0].trim().toLowerCase();
            return lang.isBlank() ? DEFAULT_LANGUAGE : lang;
        } catch (Exception e) {
            return DEFAULT_LANGUAGE;
        }
    }

    public static Locale getDefaultLocale() {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String acceptLanguage = request.getHeader(HEADER_ACCEPT_LANGUAGE);

        if (acceptLanguage != null && !acceptLanguage.isEmpty()) {
            return Locale.LanguageRange.parse(acceptLanguage)
                    .stream()
                    .findFirst()
                    .map(Locale.LanguageRange::getRange)
                    .map(Locale::of)
                    .orElse(Locale.ENGLISH);
        }

        return Locale.ENGLISH;
    }

    private Flux<ReactiveResult> buildFluxRequestNDJson(Consumer<FluxSink<ReactiveResult>> fluxSink) {
        final SecurityContext context = SecurityContextHolder.getContext();
        return Flux.create(sink -> {
            try {
                heavyExecutorService.submit(() -> {
                    try {
                        SecurityContextHolder.setContext(context);
                        fluxSink.accept(sink);
                    } catch (Throwable e) {
                        // Without this log, any exception thrown inside the
                        // NDJSON producer is converted to a Flux error signal
                        // and the HTTP stream closes silently mid-way - the
                        // client sees a clean disconnection with no diagnostic.
                        // Logging here surfaces the root cause for ops.
                        log.error("Error in NDJSON flux task", e);
                        sink.error(e);
                    } finally {
                        SecurityContextHolder.clearContext();
                    }
                });
            } catch (RuntimeException e) {
                log.error("Failed to submit NDJSON flux task", e);
                sink.error(e);
            }
        });
    }


    /**
     * Suppression d'un fichier binaire ( 2-phase async ) .
     *
     * <p>Phase 1 ( synchrone , dans cet endpoint ) : check droits ,
     * supersedure d'un workflow concurrent sur ce {@code fileId} , flag
     * visuel ( unpublish si etait publie ) + audit workflow_log
     * IN_PROGRESS + mail START + COMMIT . Phase 2 ( asynchrone ) :
     * DELETE rows referencevalue si etait publie + DELETE row binaryfile
     * + recompute synthesis + mail END . Voir
     * {@code PublishLifecycleService} et {@code PUBLISH_UNPUBLISH.md} .
     *
     * <p>Reponse HTTP 202 + body = id du fichier ( retro-compat avec le
     * frontend qui attendait juste le UUID texte ) ; le correlationId
     * pour suivre la phase 2 est lisible dans {@code workflow_log} .
     */
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DELETE_FILE')")
    @DeleteMapping(value = "/applications/{name}/file/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> removeFile(
            HttpServletRequest request,
            @PathVariable("name") final String applicationName,
            @PathVariable("id") final UUID id) {

        Locale locale = localeResolver.resolveLocale(request);

        Optional<FileOrUUID> fileId = OreSiApiRequestContext.getAuthentication()
                .map(OreSiAuthenticationToken::getStoreFile)
                .map(State::fileOrUuid);
        if (fileId.isEmpty()) {
            throw new SiOreIllegalArgumentException(SiOreIllegalArgumentException.NO_FILE_To_DELETE, Map.of(FILE_ID, id));
        }
        String dataName = OreSiApiRequestContext.getAuthentication()
                .map(OreSiAuthenticationToken::getDataName)
                .orElse(NOT_FOUND_DATA_NAME);
        Optional<ApplicationUser> applicationUser = OreSiApiRequestContext.getAuthentication()
                .map(OreSiAuthenticationToken::getApplicationPersona)
                .filter(ApplicationUser.class::isInstance)
                .map(ApplicationUser.class::cast);
        Application application = applicationUser
                .map(ApplicationUser::application)
                .orElse(null);
        Optional<ApplicationDataDelete> applicationDataDelete = applicationUser
                .filter(ApplicationDataDelete.class::isInstance)
                .map(ApplicationDataDelete.class::cast);
        StoreFile storeFile = getStoreFileUseCase.execute(
            application,
            dataName,
            fileId.orElse(null),
            null,
            applicationDataDelete
                .orElse(null)
        );

        boolean canDelete = applicationDataDelete.get().canDelete(storeFile.fileOrUuid());
        if (!canDelete) {
            throw new NotApplicationCanDeleteRightsException(applicationName, dataName);
        }
        // topublish() est vrai si le fichier ETAIT publie au moment du
        // delete -> on doit avoir le droit de depublier en cascade .
        if (!storeFile.builder().getFileOrUUID().topublish()
                && !applicationDataDelete.get().hasRightForPublishOrUnPublish(storeFile.fileOrUuid())) {
            throw new NotApplicationDataWriterForPublishException(applicationName, dataName);
        }

        publishLifecycleService.startDeleteFile(applicationName, id, locale);

        return ResponseEntity.status(org.springframework.http.HttpStatus.ACCEPTED).body(id.toString());
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/filesOnRepository/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getFilesOnRepository(@PathVariable("nameOrId") final String nameOrId,
                                                        @PathVariable("dataType") final String dataType,
                                                        @JsonParam("repositoryId") final BinaryFileDataset binaryFileDataset,
                                                        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) final String ifNoneMatch) {
        Optional.ofNullable(binaryFileDataset)
                .ifPresent(binaryFileDataset1 -> binaryFileDataset1.setIfNotPresentDatatype(dataType));
        Map<UUID, UserDescriptionResult> users = getAllUsersUseCase.execute()
                .stream()
                .map(UserDescriptionResult::of)
                .collect(Collectors.toMap(UserDescriptionResult::id, Function.identity()));
        final Application application = OreSiApiRequestContext.getAuthenticationToken().getApplicationPersona().application();
        final String dataName = OreSiApiRequestContext.getAuthenticationToken().getDataName();
        final DatePattern submissionDatePattern = application.findSubmissionDatePattern(dataName);
        final LocalDateTimeRange localDateTimeRange = LocalDateTimeRange.of(
                submissionDatePattern,
                binaryFileDataset.getFrom(),
                binaryFileDataset.getTo());
        String from = Optional.ofNullable(localDateTimeRange)
                .map(LocalDateTimeRange::getRange)
                .filter(Range::hasLowerBound)
                .map(Range::lowerEndpoint)
                .map(LocalDateTimeRange.DATE_TIME_FORMATTER::format)
                .orElse(binaryFileDataset.getFrom());
        binaryFileDataset.setFrom(from);
        String to = Optional.ofNullable(localDateTimeRange)
                .map(LocalDateTimeRange::getRange)
                .filter(Range::hasUpperBound)
                .map(Range::upperEndpoint)
                .map(LocalDateTimeRange.DATE_TIME_FORMATTER::format)
                .orElse(binaryFileDataset.getTo());
        binaryFileDataset.setTo(to);

        final List<BinaryFile> rawFiles = getFilesOnRepositoryUseCase.execute(nameOrId, dataType, binaryFileDataset, false);

        // Audit OA_FULL_REVIEW (8/5/26) - fix N+1 : avant , chaque BinaryFile
        // déclenchait sa propre requête getReferencedBinaryFiles ( SQL par
        // fichier ). Sur 50 fichiers = 50 SQL en plus du listing principal.
        // Désormais : 1 seule SQL avec WHERE binaryfile IN ( ids... ) , puis
        // lookup en mémoire par fichier. Le SQL `getReferencedBinaryFiles`
        // accepte déjà un Set<UUID> ( cf. BinaryFileRepository:50 ) , il
        // suffisait d'arrêter de l'appeler 1-par-1.
        Set<UUID> publishedIds = rawFiles.stream()
                .filter(bf -> bf.getParams() != null && bf.getParams().published())
                .map(BinaryFile::getId)
                .collect(Collectors.toSet());
        // Optimisation B / scaling 900M : on remplace l'enumeration complete
        // du graphe ( getReferencedBinaryFiles ) par un EXISTS qui retourne
        // juste le set des fileIds qui ont des liaisons sortantes . Cote
        // front , isLinked() lit directement BinaryFileResult.hasLinks .
        // Le champ legacy referencedFiles reste null ( backward compat
        // pour les callers qui itereraient encore l'ancien shape ; aucun
        // dans openADOM aujourd'hui ).
        final Set<UUID> idsWithLinks;
        if (publishedIds.isEmpty() || rawFiles.isEmpty()) {
            idsWithLinks = Set.of();
        } else {
            UUID applicationId = rawFiles.getFirst().getApplication();
            idsWithLinks = serviceContainer.binaryFileService()
                    .findBinaryFileIdsWithLinks(applicationId, dataType, publishedIds);
        }

        final List<BinaryFileResult> files = rawFiles.stream()
                .map(binaryFile -> BinaryFileResult.of(
                        binaryFile,
                        Optional.ofNullable(binaryFile)
                                .map(BinaryFile::getParams)
                                .map(BinaryFileInfos::createuser)
                                .map(users::get)
                                .orElse(null),
                        Optional.ofNullable(binaryFile)
                                .map(BinaryFile::getParams)
                                .map(BinaryFileInfos::publisheduser)
                                .map(users::get)
                                .orElse(null),
                        idsWithLinks.contains(binaryFile.getId()),
                        null
                ))
                .toList();

        // Audit OA_FULL_REVIEW (8/5/26) - ETag content-based pour permettre au
        // browser de se contenter d'un 304 quand le payload n'a pas changé
        // ( navigation rapide site -> site -> site sur même datatype ).
        // Le SQL est déjà rapide ( <1ms , GIN index ) donc on n'ajoute pas
        // de cache mémoire backend ; juste un hash sur la sérialisation pour
        // skipper le retransfert + parse JSON côté frontend si possible.
        final String json;
        try {
            json = jacksonObjectMapper.writeValueAsString(files);
        } catch (JsonProcessingException e) {
            throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
        }
        final String etag = computeWeakEtag(json);
        org.springframework.http.CacheControl cacheControl =
                org.springframework.http.CacheControl.noCache().mustRevalidate().cachePrivate();
        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(cacheControl)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(cacheControl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    private ResponseEntity<StreamingResponseBody> getFile(
            @PathVariable("name") final String name,
            @PathVariable("id") final UUID id) {
        final Optional<BinaryFile> optionalBinaryFile = getFileWithDataUseCase.execute(name, id);
        if (optionalBinaryFile.isPresent()) {
            final BinaryFile binaryFile = optionalBinaryFile.get();

            StreamingResponseBody body;// Ton InputStream depuis la BDD
            String filename;
            try (InputStream inputStream = binaryFile.getFileData()) { // Ton InputStream depuis la BDD
                filename = binaryFile.getName();
                body = outputStream -> FileCopyUtils.copy(inputStream, outputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            return ResponseEntity.ok()
                    .contentLength(binaryFile.getSize())
                    .header(HEADER_CONTENT_DISPOSITION, HEADER_ATTACHMENT_FILENAME.formatted(filename))
                    .body(body);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    private Flux<ReactiveResult> getApplications(final String[] filter) {
        final List<ApplicationInformation> filters = Arrays.stream(filter)
                .map(ApplicationInformation::valueOf)
                .toList();

        return getApplicationsUseCase.execute(filters);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/validate-configuration", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> validateConfiguration(@RequestParam("file") final MultipartFile file) throws IOException {
        return validateConfigurationInternal(file);
    }

    private Flux<ReactiveResult> validateConfigurationInternal(final MultipartFile file) throws IOException {
        DataFile dataFile = null;
        try {
            final File physicalFileOrCopy = getPhysicalFileOrCopy(file);
            dataFile = file == null ? null : new DataFile(physicalFileOrCopy, (long) file.getInputStream().available(), file.getOriginalFilename());
        } catch (IOException e) {
            throw OreSiIOException.ORE_SI_IOEXCEPTION_CANT_LOAD_FILE();
        }
        DataFile finalDataFile = dataFile;
        return buildFluxRequestNDJson(fluxSink -> {
            final Application application = validateConfigurationUseCase.execute(fluxSink::next, finalDataFile);
            fluxSink.next(new ReactiveTypeResult(application));
            // Final progress=1.0 chunk is required for the frontend progress
            // bar to disappear and reveal the next-step button. Pre-merge
            // (e392636) the wrapper ReactiveProgression.complete() emitted
            // this automatically; the modernization replaced it with a raw
            // sink.complete() and forgot to keep the final progress signal.
            fluxSink.next(new ReactiveTypeProgress(1D));
            fluxSink.complete();
        });
    }

        private Flux<ReactiveResult> createApplication(final String name,
                               final String comment,
                               final MultipartFile file) throws BadApplicationConfigurationException {
        DataFile dataFile = null;
        try {
            final File physicalFileOrCopy = getPhysicalFileOrCopy(file);
            dataFile = file == null ? null : new DataFile(physicalFileOrCopy, (long) file.getInputStream().available(), file.getOriginalFilename());
        } catch (IOException e) {
            throw OreSiIOException.ORE_SI_IOEXCEPTION_CANT_LOAD_FILE();
        }
        if (!SqlIdentifierUtils.IdentifierTest.identifierForApplicationName(name)) {
            //TODO test à faire
            throw new BadLabelNameException(BadLabelNameException.LabelType.APPLICATION, name);
        }
        DataFile finalDataFile = dataFile;
        return buildFluxRequestNDJson(fluxSink -> {
            try {
                createApplicationUseCase.execute(fluxSink::next, name, finalDataFile, comment);
                fluxSink.complete();
            } catch (Exception technicalException) {
                fluxSink.error(technicalException);
            }
        });
    }

    private ApplicationResult getApplication(final String nameOrId, final String[] filter) {
        final Application application = getApplicationOrAccordingToRightsUseCase.execute(nameOrId);
        return buildOpenAdomUseCase.execute(application, filter);
    }

    private ResponseEntity<StreamingResponseBody> getConfiguration(final String nameOrId) {
        final Application application = getApplicationUseCase.execute(nameOrId);
        final UUID configFileId = application.getConfigFile();
        return getFile(nameOrId, configFileId);
    }

    private Flux<ReactiveResult> changeConfiguration(final String nameOrId,
                                                     final MultipartFile file,
                                                     final String comment) throws BadApplicationConfigurationException {

        return buildFluxRequestNDJson(fluxSink -> {
            if (file.isEmpty()) {
                fluxSink.error(new IllegalArgumentException(ERROR_EMPTY_FILE));
            }
            DataFile dataFile = null;
            try {
                final File physicalFileOrCopy = getPhysicalFileOrCopy(file);
                dataFile = file == null ? null : new DataFile(physicalFileOrCopy, (long) file.getInputStream().available(), file.getOriginalFilename());
            } catch (IOException e) {
                throw OreSiIOException.ORE_SI_IOEXCEPTION_CANT_LOAD_FILE();
            }

            final UUID uuid = changeApplicationConfigurationUseCase.execute(fluxSink::next, nameOrId, dataFile, comment);
            // #58 - Reconstruire le cache des filtres de tous les dataTypes de cette application
            // après un changement de configuration ( la config peut modifier les références )
            Application application = serviceContainer.applicationService().getApplication(nameOrId);
            for (String dataName : application.getAllDataNames()) {
                serviceContainer.dataService().refreshFilterListCache(application, dataName);
            }
            fluxSink.next(new ReactiveTypeResult(uuid));
            fluxSink.complete();
        });
    }

    /**
     * Liste toutes les valeurs possibles pour un type de referenciel
     *
     * @param nameOrId l'id ou le nom de l'application
     * @return un tableau de chaine
     */
        private ResponseEntity<GetRightsRequestResult> listRightsRequest(
            final String nameOrId,
            final RightsRequestInfos rightsRequestInfos) {
        final GetRightsRequestResult list = findRightsRequestUseCase.execute(nameOrId, rightsRequestInfos);
        return okResponse(list);
    }

    private ResponseEntity<?> createRightsRequest(final String nameOrId,
                                                  final CreateRightsRequestRequest createRightsRequestRequest) {
        final UUID fileUUID = createOrUpdateRightsRequestUseCase.execute(createRightsRequestRequest, nameOrId);
        return okResponse(fileUUID);
    }

    /**
     * Liste les noms des types de referenciels disponible
     *
     * @param nameOrId l'id ou le nom de l'application
     * @return les noms triés selon l’ordre dans lequel il faut faire les imports (selon les dépendances entre
     * référentiels).
     */

    private ResponseEntity<List<String>> listNameReferences(String nameOrId) {
        String[] filter = {ApplicationInformation.ALL.name()};
        final ApplicationResult application = getApplication(nameOrId, filter);

        return okResponse(application.getOrderedReferences());
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/data/{refType}/csv", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> listDataCsv(
            final HttpServletResponse response,
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("refType") final String refType) {
        Locale language = OreSiResources.getDefaultLocale();

        // Rate-limit partage avec l'endpoint ZIP ( meme quota par utilisateur ).
        final String userId = OreSiApiRequestContext.getRequestClient().id().toString();
        extractionRateLimiter.acquireOrThrow(userId, "csv");

        final StreamingResponseBody streamResponseBody = out -> {
            final org.apache.commons.io.output.CountingOutputStream counting =
                    new org.apache.commons.io.output.CountingOutputStream(out);
            try (var lc = extractionLifecycle.start(
                    WorkflowLogEntry.TYPE_EXTRACT_CSV,
                    java.util.UUID.fromString(userId), resolveCurrentLogin(),
                    nameOrId, refType, refType + ".csv", 0L)) {
                try {
                    getDataCsvStreamUseCase.execute(counting, nameOrId, refType, language, false);
                    lc.complete(counting.getByteCount());
                } catch (RuntimeException ex) {
                    lc.fail(ex);
                    throw ex;
                }
            } finally {
                extractionRateLimiter.release(userId);
            }
        };
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader(HEADER_CONTENT_DISPOSITION, String.format(HEADER_ATTACHMENT_FILENAME_S_CSV, refType));
        response.addHeader(HEADER_PRAGMA, HEADER_NO_CACHE);
        response.addHeader(HEADER_EXPIRES, EXPIRED_TIME);
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(streamResponseBody);
    }

    protected ResponseEntity<Map<String, Object>> createData(
            HttpServletRequest request,
            final String nameOrId,
            final String dataName,
            final MultipartFile file,
            final String params) throws JsonProcessingException {
        Locale locale = localeResolver.resolveLocale(request);
        DataFile dataFile = null;
        try {
            final File physicalFileOrCopy = getPhysicalFileOrCopy(file);
            dataFile = file == null ? null : new DataFile(physicalFileOrCopy, (long) file.getInputStream().available(), file.getOriginalFilename());
        } catch (IOException e) {
            throw OreSiIOException.ORE_SI_IOEXCEPTION_CANT_LOAD_FILE();
        }
        try {
            DataFile finalDataFile = dataFile;
            final SecurityContext context = SecurityContextHolder.getContext();
            final DataVersioningResult dataVersioningResult = heavyExecutorService.submit(() -> {
                SecurityContextHolder.setContext(context);
                try {
                    // withEmail=false : le mail est envoye post-.get() dans
                    // VersioningService.finalizePostCommit pour qu il porte
                    // le compteur frais ( cascade 3.0.0 deferred fait tourner
                    // le UPSERT dans afterCommit , donc lire la table finale
                    // ICI pendant le @Transactional donne un compteur stale ) .
                    return createDataUseCase.execute(
                            locale, nameOrId, dataName, finalDataFile, false, false);
                } catch (InvalidDatasetContentException invalidDatasetContentException) {
                    List<ValidationCheckResultRest> validations = invalidDatasetContentException.getErrors()
                            .stream()
                            .map(row -> row.validationCheckResult().validationCheckResultToRest(row.lineNumber()))
                            .toList();
                    Application application = getApplicationOrAccordingToRightsUseCase.execute(nameOrId);
                    String errorsToJson = new ObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .writeValueAsString(validations);
                    String localizedApplicationName = application.getLocalizedLocalName(locale);
                    String localizedDataName = application.getLocalizedDataName(locale, dataName);
                    OreSiUser currentUser = getCurrentUserUseCase.execute();
                    // #477 - Distinction référentiel / type de données et
                    // nom du fichier CSV en erreur pour le template d'email
                    boolean isReference = !application.isData(dataName);
                    String errorFileName = finalDataFile == null ? null : finalDataFile.fileName();
                    sendUploadErrorsMailUseCase.execute(
                            locale,
                            localizedApplicationName,
                            localizedDataName,
                            errorFileName,
                            isReference,
                            currentUser,
                            errorsToJson
                    );
                    throw invalidDatasetContentException;
                } catch (IOException e) {
                    throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
                }
            }).get(heavyTaskTimeout.toMillis(), TimeUnit.MILLISECONDS);
            // Post-commit : afterCommit du @Transactional interne au .get() a
            // fire , le UPSERT staging -> table finale ( cascade 3.0.0 deferred )
            // est termine . On recalcule dataSynthesis sur la table finale a
            // jour , et on envoie le mail avec ce compteur frais . Reconstruire
            // aussi le cache des filtres ( #58 ) .
            Application application = serviceContainer.applicationService().getApplication(nameOrId);
            serviceContainer.dataService().refreshFilterListCache(application, dataName);
            String fileName = file == null ? null : file.getOriginalFilename();
            DataVersioningResult finalized = serviceContainer.versioningService()
                    .finalizePostCommit(locale, nameOrId, dataName, fileName, dataVersioningResult, true);
            return ResponseEntity
                    .created(URI.create(finalized.uri()))
                    .body(Map.of("id", finalized.dataId().toString(), "referenceSynthesis", finalized.dataSynthesis()));
        } catch (ExecutionException e) {
            Throwable cause = unwrapException(e.getCause());
            throw switch (cause) {
                case InvalidDatasetContentException invalidEx -> invalidEx;
                case OreSiTechnicalException oreSiTechnicalException -> oreSiTechnicalException;
                case RuntimeException runtimeEx -> runtimeEx;
                default -> new IllegalStateException("Unexpected value: " + cause);
            };
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new OreSiTechnicalException("Thread interrompu", e);
        } catch (TimeoutException e) {
            log.error("Import bloque - timeout apres {} pour application={} dataName={}",
                    heavyTaskTimeout, nameOrId, dataName);
            // Diagnostic : dumper les stacks des threads heavy-*/cache-preload-*
            // pour localiser exactement OU le thread est bloque ( DB lock ,
            // attente Future , I/O , ... ) . Sans cela on a juste "timeout"
            // sans cause , et le bug se reproduit dans un environnement
            // different a chaque fois ( cf TestReferencesErrors , monsoere ) .
            dumpRelevantThreads();
            throw new OreSiTechnicalException(
                    "Import bloque (timeout " + heavyTaskTimeout + ") - voir thread dump dans les logs", e);
        }
    }

    /**
     * Affiche les stacks des threads des pools applicatifs ( heavy , normal ,
     * fast , cache-preload ) et ceux marques "scheduling" . Utilise lorsqu'un
     * Future.get() timeout pour identifier la cause racine du blocage sans
     * avoir a se brancher avec jstack en production .
     */
    private void dumpRelevantThreads() {
        try {
            java.lang.management.ThreadMXBean bean = java.lang.management.ManagementFactory.getThreadMXBean();
            java.lang.management.ThreadInfo[] infos = bean.dumpAllThreads(true, true);
            StringBuilder sb = new StringBuilder("=== Thread dump on import timeout ===\n");
            for (java.lang.management.ThreadInfo ti : infos) {
                String name = ti.getThreadName();
                if (name.startsWith("heavy-") || name.startsWith("normal-")
                        || name.startsWith("fast-") || name.startsWith("backup-")
                        || name.startsWith("cache-preload-") || name.startsWith("scheduling-")
                        || name.contains("cascade") || name.contains("workflow")) {
                    sb.append(ti).append('\n');
                }
            }
            sb.append("=== End thread dump ===");
            log.error(sb.toString());
        } catch (RuntimeException dumpEx) {
            log.warn("Echec du thread dump diagnostic", dumpEx);
        }
    }

    private Throwable unwrapException(Throwable throwable) {
        while (throwable instanceof ExecutionException ||
               throwable instanceof CompletionException) {
            Throwable cause = throwable.getCause();
            if (cause == null || cause == throwable) {
                break;  // Éviter boucle infinie
            }
            throwable = cause;
        }
        return throwable;
    }

    protected ResponseEntity<List<String>> listData(final String nameOrId) {
        final Application application = getApplicationUseCase.execute(nameOrId);
        List<String> allDataNames = application.getAllDataNames();
        return okResponse(allDataNames);
    }

    /**
     * Liste toutes les valeurs possibles pour un type de referenciel
     *
     * @param nameOrId           l'id ou le nom de l'application
     * @param additionalFileName le type du referenciel
     * @return un tableau de chaine
     */
    private ResponseEntity<GetAdditionalFilesResult> listAdditionalFilesNames(
            final String nameOrId,
            final String additionalFileName,
            AdditionalFilesInfos additionalFilesInfos) {
        if (additionalFilesInfos == null) {
            additionalFilesInfos = new AdditionalFilesInfos();
        }
        additionalFilesInfos.setFiletype(additionalFilesInfos.getFiletype() == null ? additionalFileName : additionalFilesInfos.getFiletype());
        final GetAdditionalFilesResult list = findAdditionalFileUseCase.execute(nameOrId, additionalFilesInfos);
        return okResponse(list);
    }

    @GetMapping(value = "/applications/{nameOrId}/additionalFiles", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(description = "Get a additionalFiles with their description using search params", summary = "Returns a zip containing additional files and their description")
    public ResponseEntity<StreamingResponseBody> getAdditionalFilesNamesZip(
            final HttpServletResponse response,
            //@ApiParam(required = true, value = "The name or uuid of an application")
            @PathVariable("nameOrId") final String nameOrId,
            //@ApiParam(required = false, value = "The parameters for filter the search")
            @JsonParam(value = "params", required = false) final AdditionalFilesInfos additionalFilesInfos) throws
            BadAdditionalFileParamsSearchException {

        // Rate-limit partage avec les autres extractions ( meme quota par utilisateur ).
        final String userId = OreSiApiRequestContext.getRequestClient().id().toString();
        final String extractionType = AdditionalFileService.CHARTE.equals(
                Objects.requireNonNull(additionalFilesInfos).getFiletype()) ? "charte" : "additional_files";
        extractionRateLimiter.acquireOrThrow(userId, extractionType);

        // StreamingResponseBody runs sur le thread async-dispatch Spring ,
        // pas sur le thread HTTP servlet courant . SecurityContextHolder
        // est un ThreadLocal => le contexte d'authentification ne se
        // propage PAS automatiquement vers la lambda . Sans capture
        // explicite , {@code AdditionalFileService.getCharte} declenche
        // {@code setRoleForClient()} sur un thread sans authentification ,
        // tombe en role anonyme , et le SELECT sur la table
        // {@code application} echoue avec NoSuchApplicationException
        // ( "application inconnue 'X'" ) . On snapshot ici puis re-set
        // dans la lambda , avec clearContext() en finally pour eviter
        // la fuite du context sur le thread du pool .
        final org.springframework.security.core.context.SecurityContext capturedSecurityCtx =
                org.springframework.security.core.context.SecurityContextHolder.getContext();

        final String dataType = additionalFilesInfos.getFiletype();
        final StreamingResponseBody streamResponseBody;
        if (AdditionalFileService.CHARTE.equals(additionalFilesInfos.getFiletype())) {
            response.setHeader("Content-type", "application/pdf");
            response.setHeader("Content-Security-Policy", "frame-ancestors %s".formatted(frontendOrigin));
            streamResponseBody = out -> {
                org.springframework.security.core.context.SecurityContextHolder.setContext(capturedSecurityCtx);
                final org.apache.commons.io.output.CountingOutputStream counting =
                        new org.apache.commons.io.output.CountingOutputStream(out);
                try (var lc = extractionLifecycle.start(
                        WorkflowLogEntry.TYPE_EXTRACT_CHARTE,
                        java.util.UUID.fromString(userId), resolveCurrentLogin(),
                        nameOrId, dataType, "charte.pdf", 0L)) {
                    try {
                        getCharteUseCase.execute(counting, response, nameOrId, additionalFilesInfos);
                        lc.complete(counting.getByteCount());
                    } catch (RuntimeException ex) {
                        lc.fail(ex);
                        throw ex;
                    }
                } finally {
                    extractionRateLimiter.release(userId);
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            };
        } else {
            streamResponseBody = out -> {
                org.springframework.security.core.context.SecurityContextHolder.setContext(capturedSecurityCtx);
                final org.apache.commons.io.output.CountingOutputStream counting =
                        new org.apache.commons.io.output.CountingOutputStream(out);
                try (var lc = extractionLifecycle.start(
                        WorkflowLogEntry.TYPE_EXTRACT_ADDITIONAL_FILES,
                        java.util.UUID.fromString(userId), resolveCurrentLogin(),
                        nameOrId, dataType, "additionalFiles.zip", 0L)) {
                    try (final ZipOutputStream zipOutputStream = new KeepAliveZipOutputStream(counting)) {
                        getAdditionalFilesZipStreamUseCase.execute(zipOutputStream, nameOrId, additionalFilesInfos);
                        lc.complete(counting.getByteCount());
                    } catch (final IOException ioe) {
                        switch (OreSiResources.getDefaultLocale().getLanguage()) {
                            case FR -> log.error(IO_ERROR_FR, ioe);
                            case EN -> log.error(IO_ERROR_EN, ioe);
                            default -> log.error(IO_ERROR_EN, ioe);
                        }
                        lc.fail(ioe);
                    }
                } finally {
                    extractionRateLimiter.release(userId);
                    org.springframework.security.core.context.SecurityContextHolder.clearContext();
                }
            };
            response.setHeader(HEADER_CONTENT_DISPOSITION, HEADER_ZIP);
            response.setContentType(HEADER_APPLICATION_ZIP_CHARSET_UTF_8);
        }
        response.addHeader(HEADER_PRAGMA, HEADER_NO_CACHE);
        response.addHeader(HEADER_EXPIRES, EXPIRED_TIME);

        return ResponseEntity.ok()
                .body(streamResponseBody);
    }

    private ResponseEntity<String> removeAdditionalFiles(
            final String nameOrId,
            final AdditionalFilesInfos additionalFilesInfos) throws
            BadAdditionalFileParamsSearchException {
        final List<UUID> deletedFiles = deleteAdditionalFilesUseCase.execute(nameOrId, additionalFilesInfos);
        if (deletedFiles != null && !deletedFiles.isEmpty()) {
            return okResponse(deletedFiles.stream().map(UUID::toString).collect(Collectors.joining(LIST_DELIMITER)));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<UUID> createAdditionalFile(final String nameOrId,
                                                       final String additionalFileName,
                                                       final MultipartFile file,
                                                       final CreateAdditionalFileRequest createAdditionalFileRequest) {
        final UUID fileUUID = createOrUpdateAdditionalFileUseCase.execute(createAdditionalFileRequest, additionalFileName, nameOrId, file);
        return okResponse(fileUUID);


    }

    /**
     * export as JSON
     */
        protected ResponseEntity<String> getAllDataJson(
            final String nameOrId,
            final String dataName,
            final DownloadDatasetQuery params,
            boolean loadExample,
            final String ifNoneMatch) {

        Application application = getApplicationUseCase.execute(nameOrId);
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery downloadDatasetQuery =
                buildDownloadDatasetQuery(params, nameOrId, dataName, loadExample);

        final Locale locale = Optional.of(downloadDatasetQuery)
                .map(fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery::getLanguage)
                .map(Locale::of)
                .orElseGet(OreSiResources::getDefaultLocale);
        final Set<String> orderedVariables = buildOrderedVariables(nameOrId, dataName);
        final List<DataRow> data = findDataUseCase.execute(downloadDatasetQuery);
        // PERF #465 — filterLists chargés via l'endpoint séparé GET /filters (asynchrone)
        Predicate<ComponentDescription> isHidden = componentDescription -> componentDescription.isHiddenOrHasLangRestriction(downloadDatasetQuery.getLanguage());
        Predicate<String> isHiddenComponent = componentName -> application.findComponentOfData(dataName, componentName).stream()
                .anyMatch(isHidden);
        Predicate<String> isNotVariable = variable -> variable.startsWith("_");
        final ImmutableSet<String> variables = data.stream()
                .limit(1)
                .map(DataRow::values)
                .map(Map::keySet)
                .flatMap(Set::stream)
                .filter(Predicate.not(isNotVariable))
                .filter(Predicate.not(isHiddenComponent))
                .sorted((a, b) -> {
                    if (a.equals(b)) {
                        return 0;
                    }
                    return orderedVariables
                            .stream()
                            .dropWhile(i -> !i.equals(a) && !i.equals(b))
                            .findFirst()
                            .orElse("")
                            .equals(a) ? -1 : 1;
                })
                .collect(ImmutableSet.toImmutableSet());
        final Map<String, Map<String, LineCheckerResult>> checkedFormatcomponents = getCheckedFormatComponentsUseCase.execute(nameOrId, dataName);
        final List<DataRowResult> dataRowResults = data.stream()
                .map(dataRow -> DataRowResult.of(
                        dataRow,
                        variables,
                        locale.getLanguage()
                ))
                .toList();
        Map<String, List<GetGrantableResult.ReferenceScope>> referenceScopes = getAuthorizationScopesUseCase.execute(application, MenuType.submission);

        // PERF #465 — filterLists est désormais une liste vide ici.
        // Les filtres sont chargés via l'endpoint séparé GET /filters (voir getDataFilters ci-dessous).
        // Cela permet d'afficher les données immédiatement sans attendre la requête lente des filtres (~54s).
        GetDataResult result = new GetDataResult(
                downloadDatasetQuery.patternDefinitionCount(),
                variables,
                dataRowResults,
                List.of(),
                checkedFormatcomponents,
                referenceScopes);

        // PERF audit (8/5/26) - sérialisation manuelle pour pouvoir hasher
        // le JSON et émettre un ETag stable. Le hash dépend uniquement du
        // contenu sérialisé , donc tout changement ( import , delete ,
        // grant ) qui modifie l'arbre / les rows / les checkers se traduit
        // en mismatch d'ETag -&gt; 200 fresh ; cohérence garantie sans
        // invalidation explicite côté browser.
        final String json;
        try {
            json = jacksonObjectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
        }
        final String etag = computeWeakEtag(json);

        org.springframework.http.CacheControl cacheControl =
                org.springframework.http.CacheControl.noCache().mustRevalidate().cachePrivate();

        if (etag.equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .cacheControl(cacheControl)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(etag)
                .cacheControl(cacheControl)
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    /**
     * Calcule un ETag faible ( {@code W/"…"} ) à partir d'un JSON. SHA-256
     * tronqué 64 bits suffit en pratique pour distinguer les variantes de
     * payload sans collision.
     */
    private static String computeWeakEtag(final String json) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(20).append("W/\"");
            for (int i = 0; i < 8; i++) {
                sb.append(String.format("%02x", hash[i]));
            }
            return sb.append('"').toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponible", e);
        }
    }

    /**
     * PERF #465 — Endpoint dédié pour le chargement asynchrone des listes de filtres.
     *
     * Pourquoi un endpoint séparé ?
     *   Avant, les filtres étaient chargés dans getAllDataJson() en même temps que les données.
     *   La requête SQL des filtres (getFilterList dans DataRepository.java) prend ~54 secondes
     *   pour 100K lignes, ce qui bloquait l'affichage des données (~80ms) pendant toute la durée.
     *   En séparant, le frontend affiche les données immédiatement et charge les filtres en
     *   arrière-plan. L'utilisateur voit ses données en ~1-2s au lieu de ~55s.
     *
     * Le résultat est mis en cache par DataService.filterList() (TTL 10 min).
     * Au 2ème appel, la réponse est quasi-instantanée (< 10ms).
     *
     * @param nameOrId nom ou ID de l'application
     * @param dataName nom du dataType (ex: "t_soil_analysis_sana")
     * @return la liste des FilterList contenant les valeurs distinctes des dropdowns de filtres
     */
    @Operation(
            description = "Return the filter payload for dataType 'dataType' of application 'nameOrId' . "
                    + "Response shape : `{entries: FilterListEntry[], variables: string[]}` . "
                    + "  - `entries` : FilterList values per filterable column ( ReferenceChecker dropdowns , "
                    + "                FILTER_LIST distinct values , FILTER_TEXT hasEmpty flag ) . "
                    + "  - `variables` : column keys filtrables ( permet au frontend de rendre le bloc filtre "
                    + "                  immédiatement sans attendre /data/json ) . "
                    + "Separated from /json for asynchronous loading : data displays immediately while filters "
                    + "load in background . Results cached server-side ( openadom.cache.filter-list.enabled ; "
                    + "default 10 minutes ) . Use refresh=true to force fresh reload . "
                    + "ETag honors If-None-Match for 304 responses ( ~30ms vs ~1-3s on ACBB ) . "
                    + "Variables list is lang-aware ( filter isHiddenOrHasLangRestriction ) , so ETag differs "
                    + "across Accept-Language values for the same datatype .",
            parameters = {
                    @Parameter(name = "nameOrId", description = "The name or uuid of an application", required = true),
                    @Parameter(name = "dataType", description = "The name of the dataType (e.g. 't_soil_analysis_sana')", required = true),
                    @Parameter(name = "refresh", description = "If true, invalidates the cache and forces a fresh reload from the database", required = false)
            }
    )
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}/filters", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getDataFilters(
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("dataType") final String dataName,
            @RequestParam(defaultValue = "false") boolean refresh,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) final String ifNoneMatch,
            @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) final String acceptLanguage) {
        // Rate limit avant tout travail : empêche un user spammeur de
        // saturer le pool Hikari ou le CPU backend en multipliant les
        // appels /filters avec refType différents ( singleflight ne
        // dédupe que les requêtes pour la MÊME clé ; un spam de N
        // refType différents = N computes parallèles ) . Throw 429 si
        // quota dépassé , identifié par l'userId courant ( null en mode
        // anonymous = pas de rate-limit appliqué ) .
        String currentUserId = serviceContainer.authenticationService().getCurrentUserRoles() != null
                ? String.valueOf(serviceContainer.authenticationService().getCurrentUserRoles().userId())
                : null;
        filterListRateLimiter.acquireOrThrow(currentUserId);

        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        // Si refresh=true , on invalide le cache pour forcer un rechargement depuis la base.
        // L'ETag change après recompute car le payload a changé , donc 304 sera systématiquement
        // évité au prochain hit ( comportement attendu d'un refresh manuel ).
        if (refresh) {
            serviceContainer.dataService().invalidateFilterListCache(application, dataName);
        }
        // Récupère JSON + ETag : sur cache hit , les entries Java + JSON
        // pré-sérialisé sont stockés ; les variables ( colonnes filtrables
        // exposées au frontend ) sont calculées à chaque appel via
        // FilterListVariablesExtractor ( ops O(N) sur ~50 leaves , <1ms ) .
        // La langue de la requête ( Accept-Language ) gouverne le filtre
        // isHiddenOrHasLangRestriction sur les variables exposées .
        final String language = resolveLanguage(acceptLanguage);
        fr.inra.oresing.rest.data.DataService.FilterListResult result =
                serviceContainer.dataService().getFilterListResult(application, dataName, language);
        // PERF audit (8/5/26) - HTTP 304 si le browser détient déjà cette
        // version. Évite le retransfert de ~1.7 MB sur les datasets riches
        // ( ACBB ) : Tomcat répond ~30 ms sans body au lieu de 1-3 s avec gzip.
        //
        // Cache-Control : "no-cache , must-revalidate , private" - le browser
        // STOCKE la réponse ( contrairement à no-store ) , mais doit la
        // revalider via If-None-Match à chaque navigation. private = pas de
        // CDN / proxy cache ( payload contient des données métier protégées
        // par ACL ). Sans ce header , Spring Security applique son default
        // "no-store" qui empêche le browser de garder l'ETag.
        org.springframework.http.CacheControl cacheControl =
                org.springframework.http.CacheControl.noCache().mustRevalidate().cachePrivate();
        if (result.etag().equals(ifNoneMatch)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(result.etag())
                    .cacheControl(cacheControl)
                    .build();
        }
        return ResponseEntity.ok()
                .eTag(result.etag())
                .cacheControl(cacheControl)
                .body(result.json());
    }

    /**
     * export as JSON
     */
    protected ResponseEntity<String> deleteData(
            final String nameOrId,
            final String dataName,
            final DownloadDatasetQuery params) {
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery downloadDatasetQuery = buildDownloadDatasetQuery(params, nameOrId, dataName, false);
        final List<UUID> deletedData = deleteDataUseCase.execute(downloadDatasetQuery);
        // #58 - Reconstruire le cache des filtres après une suppression réussie
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        serviceContainer.dataService().refreshFilterListCache(application, dataName);
        return okResponse(deletedData.stream().map(UUID::toString).collect(Collectors.joining(LIST_DELIMITER)));

    }

    private Set<String> buildOrderedVariables(final String nameOrId, final String dataName) {
        final Submission.SubmissionScope authorization = getApplicationUseCase.execute(nameOrId)
                .findSubmission(dataName)
                .map(Submission::submissionScope)
                .orElse(null);
        final LinkedHashSet<String> orderedComponents = new LinkedHashSet<>();
        if (authorization != null && authorization.timescope() != null) {
            orderedComponents.add(authorization.timescope().component());
        }
        if (authorization != null && authorization.referenceScopes() != null) {
            authorization.referenceScopes()
                    .stream()
                    .filter(vc -> !orderedComponents.contains(vc.component()))
                    .forEach(vc -> orderedComponents.add(vc.component()));
        }
        return orderedComponents;
    }

    /**
     * Export CSV zippe en streaming direct vers le client.
     *
     * <p>Phase 1c-full ( issue #62 ) : le ZIP est ecrit simultanement dans
     * la reponse HTTP ( streaming direct , pas de materialisation disque
     * prealable ) ET dans un fichier temporaire via {@link TeeOutputStream}.
     * Le fichier temporaire sert ensuite a l'upload FileSender + mail , en
     * tache asynchrone une fois le streaming termine.
     *
     * <p>Rate-limit per-user via {@link ExtractionRateLimiter} (acquis sur le
     * thread Tomcat , libere a la fin du streaming dans le thread async).
     */
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}/zip", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> getAllDataZip(
            final HttpServletResponse response,
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("dataType") final String dataType,
            @JsonParam(value = "downloadDatasetQuery", required = false) final DownloadDatasetQuery params) {

        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery downloadDatasetQuery =
                buildDownloadDatasetQuery(params, nameOrId, dataType, false);

        // Capture etat Tomcat-thread avant de passer en async
        final SecurityContext securityContext = SecurityContextHolder.getContext();
        final String userId = OreSiApiRequestContext.getRequestClient().id().toString();
        final OreSiUser currentUser = userRepository.findById(OreSiApiRequestContext.getRequestClient().id());
        final String fileName = DATA_ZIP.formatted(nameOrId, LocalDateTime.now().format(TIMESTAMP_FORMATER));

        // Rate-limit : 429 Too Many Requests immediat si quota utilisateur atteint
        extractionRateLimiter.acquireOrThrow(userId, "zip");

        StreamingResponseBody body = outputStream -> {
            SecurityContextHolder.setContext(securityContext);
            // ExtractionLifecycle = recordStart + activeRegistry.start + heartbeat
            // + métriques + recordEnd ( synchrone retry ) . AutoCloseable :
            // si exception non gérée , le close() force fail() . Garantit
            // qu'aucun workflow ne reste bloqué en IN_PROGRESS .
            try (var lc = extractionLifecycle.start(
                    WorkflowLogEntry.TYPE_EXTRACT_ZIP,
                    java.util.UUID.fromString(userId), resolveCurrentLogin(),
                    nameOrId, dataType, fileName + ".zip", 0L)) {

                Path diskCopy = null;
                boolean streamedOk = false;
                long bytesStreamed = 0L;
                Throwable streamError = null;

                try {
                    diskCopy = Files.createTempFile(Paths.get(TMP), fileName + "-", ".zip");

                    try (OutputStream diskOut  = new BufferedOutputStream(Files.newOutputStream(diskCopy));
                         TeeOutputStream tee   = new TeeOutputStream(
                                 org.apache.commons.io.output.CloseShieldOutputStream.wrap(outputStream),
                                 diskOut);
                         ZipOutputStream zip   = new ZipOutputStream(tee)) {

                        try {
                            serviceContainer.dataService().streamDataZipTo(zip, downloadDatasetQuery);
                            streamedOk = true;
                        } catch (Exception buildEx) {
                            log.error(IO_WRITING_CSV_ERROR, buildEx);
                            streamError = buildEx;
                            writeErrorEntryToZip(zip, buildEx);
                        }
                    }

                    if (diskCopy != null) {
                        try { bytesStreamed = Files.size(diskCopy); }
                        catch (IOException ignored) { /* metrics best-effort */ }
                    }

                    if (streamedOk) {
                        final Path finalDiskCopy = diskCopy;
                        final OreSiUser finalUser = currentUser;
                        heavyExecutorService.submit(() -> {
                            SecurityContextHolder.setContext(securityContext);
                            try {
                                sendZipLinkByMailUseCase.execute(finalDiskCopy, downloadDatasetQuery, finalUser);
                            } catch (RuntimeException ex) {
                                log.error(EMAIL_ERROR, ex);
                            } finally {
                                try { Files.deleteIfExists(finalDiskCopy); }
                                catch (IOException ex) { log.warn(IO_DELETE_ERROR, ex); }
                            }
                        });
                        diskCopy = null; // ownership transferee au submit async
                    }
                } finally {
                    if (diskCopy != null) {
                        try { Files.deleteIfExists(diskCopy); }
                        catch (IOException ex) { log.warn(IO_DELETE_ERROR, ex); }
                    }
                }

                if (streamedOk) {
                    lc.complete(bytesStreamed);
                } else {
                    lc.fail(streamError);
                }
            } finally {
                SecurityContextHolder.clearContext();
                extractionRateLimiter.release(userId);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + ".zip\"")
                .body(body);
    }

    /**
     * Ajoute une entree {@code error.txt} au ZIP avec le message d'erreur +
     * la stack. Remplace l'ancien {@link #addErrorFileToZip} qui operait sur
     * un repertoire. Ecrit best-effort : les erreurs IO sur l'ecriture de
     * l'entree sont loguees mais pas propagees.
     */
    private void writeErrorEntryToZip(ZipOutputStream zip, Exception cause) {
        try {
            zip.putNextEntry(new ZipEntry(FILE_ERROR));
            try (OutputStreamWriter writer = new OutputStreamWriter(
                    org.apache.commons.io.output.CloseShieldOutputStream.wrap(zip),
                    StandardCharsets.UTF_8)) {
                String lang = OreSiResources.getDefaultLocale().getLanguage();
                String errorMessage = switch (lang) {
                    case FR -> IO_UPOAD_ERROR_FR;
                    case EN -> IO_UPOAD_ERROR_EN;
                    default -> IO_UPOAD_ERROR_EN;
                };
                writer.write(errorMessage);
                writer.write("\n\n");
                if (cause.getMessage() != null) {
                    writer.write(cause.getMessage());
                    writer.write("\n\n");
                }
                StringWriter sw = new StringWriter();
                cause.printStackTrace(new PrintWriter(sw));
                writer.write(sw.toString());
            }
            zip.closeEntry();
        } catch (IOException ioe) {
            log.error(IO_ADDING_ERROR, ioe);
        }
    }

    private static void removeRepository(Path finalTempZipDirectory) {
        if (Files.exists(finalTempZipDirectory)) {
            try (Stream<Path> walk = Files.walk(finalTempZipDirectory)) {
                walk.sorted(Comparator.reverseOrder())
                        .forEach(path -> {
                            try {
                                Files.delete(path);
                            } catch (IOException e) {
                                log.error(IO_DELETE_ERROR, e);
                            }
                        });
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

        }
    }


    private void addErrorFileToZip(Path zipDirectoryPath, Exception e) throws IOException {
        Path errorFilePath = zipDirectoryPath.resolve(FILE_ERROR);

        try (BufferedWriter writer = Files.newBufferedWriter(errorFilePath, StandardCharsets.UTF_8)) {
            String errorMessage = switch (OreSiResources.getDefaultLocale().getLanguage()) {
                case FR -> IO_UPOAD_ERROR_FR;
                case EN -> IO_UPOAD_ERROR_EN;
                default -> IO_UPOAD_ERROR_EN;
            };
            writer.write(errorMessage);
            writer.newLine();
            writer.write(e.getMessage());
            writer.newLine();

            // Écrire la stack trace
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            writer.write(sw.toString());
        }
    }

    private fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery buildDownloadDatasetQuery(
            DownloadDatasetQuery downloadDatasetQuery, final String applicationNameOrID, final String dataType, boolean loadExample) {
            downloadDatasetQuery = Optional.ofNullable(downloadDatasetQuery)
                .orElseGet(DownloadDatasetQuery::new);
        try {
            if (loadExample) {
                downloadDatasetQuery.setLimit(500L);
            }
            final Application application = getApplicationUseCase.execute(applicationNameOrID);
            downloadDatasetQuery.setApplication(application);
            downloadDatasetQuery.setDataName(dataType);
            final Locale locale = Optional.of(downloadDatasetQuery)
                    .map(DownloadDatasetQuery::getOutPut)
                    .map(OutPut::locale)
                    .orElseGet(OreSiResources::getDefaultLocale);
            Optional.of(downloadDatasetQuery)
                    .map(DownloadDatasetQuery::getOutPut)
                    .or(() -> Optional.of(new OutPut(locale, null, null)))
                    .map(outPut -> new OutPut(locale, outPut.offset(), outPut.limit()))
                    .ifPresent(downloadDatasetQuery::setOutPut);
            return DownloadDatasetQuery.build(downloadDatasetQuery);
        } catch (final RuntimeException e) {
            throw new BadDownloadDatasetQuery(e.getMessage(), e);
        }
    }

    private ResponseEntity<?> getSynthesis(final String nameOrId,
                                           final String dataType) {
        try {
            final Map<String, List<OreSiSynthesis>> synthesis = getSynthesisUseCase.execute(nameOrId, dataType);
            final String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s", nameOrId, dataType), Charset.defaultCharset());
            Map<String, List<SynthesisResult>> synthesisResults = synthesis.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                                    Map.Entry::getKey,
                                    e -> e.getValue().stream().map(SynthesisResult::new).toList()
                            )
                    );
            return ResponseEntity.created(URI.create(uri)).body(synthesisResults);
        } catch (final InvalidDatasetContentException e) {
            final List<CsvRowValidationCheckResult> errors = e.getErrors();
            return badRequestResponse(errors);
        }
    }

    private ResponseEntity<?> getSynthesis(final String nameOrId,
                                           final String dataType,
                                           final String variable) {
        try {
            final Map<String, List<OreSiSynthesis>> synthesis = getSynthesisWithVariableUseCase.execute(nameOrId, dataType, variable);
            final String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s/%s", nameOrId, dataType, variable), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(synthesis);
        } catch (final InvalidDatasetContentException e) {
            final List<CsvRowValidationCheckResult> errors = e.getErrors();
            return badRequestResponse(errors);
        }
    }

    private ResponseEntity<?> buidSynthesis(final String nameOrId,
                                            final String dataType,
                                            final String variable) {
        try {
            final Map<String, List<OreSiSynthesis>> synthesis = buildSynthesisUseCase.execute(nameOrId, dataType, variable);
            final String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s%s", nameOrId, dataType, variable != null ? "/" + variable : ""), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(synthesis);
        } catch (final InvalidDatasetContentException e) {
            final List<CsvRowValidationCheckResult> errors = e.getErrors();
            return badRequestResponse(errors);
        }
    }

    private ResponseEntity<?> buidSynthesis(final String nameOrId,
                                            final String dataType) {
        return buidSynthesis(nameOrId, dataType, null);
    }
    private StreamingResponseBody getStreamingResponseBody(Flux<ReactiveResult> reactiveResultFlux) {
        return outputStream -> {
            reactiveResultFlux
                    .subscribe(result -> {
                        try {
                            outputStream.write(mapper.toJson(result).getBytes());
                            outputStream.write('\n');
                            outputStream.flush();
                        } catch (IOException e) {
                            log.warn("closed stream");
                        }
                    });
        };
    }

    // --- Méthodes utilitaires pour factoriser les réponses HTTP ---
    private <T> ResponseEntity<T> okResponse(T body) {
        return ResponseEntity.ok(body);
    }

    private ResponseEntity<Void> okResponse() {
        return ResponseEntity.ok().build();
    }

    private ResponseEntity<Void> notFoundResponse() {
        return ResponseEntity.notFound().build();
    }

    private <T> ResponseEntity<T> badRequestResponse(T errors) {
        return ResponseEntity.badRequest().body(errors);
    }

    public static File getPhysicalFileOrCopy(MultipartFile multipartFile) throws IOException {
        if (multipartFile == null || multipartFile.isEmpty()) {
            return null;
        }
        try {
            return multipartFile.getResource().getFile();
        } catch (IOException e) {
            // Cas où le MultipartFile est en mémoire ou inaccessible en tant que fichier
            File tempFile = File.createTempFile("upload-", ".zip");
            tempFile.deleteOnExit(); // Optionnel selon ta politique de nettoyage
            try (InputStream in = multipartFile.getInputStream();
                 OutputStream out = new FileOutputStream(tempFile)) {
                in.transferTo(out); // Copie tout le flux, optimal et idiomatique
            }
            return tempFile;
        }
    }

}