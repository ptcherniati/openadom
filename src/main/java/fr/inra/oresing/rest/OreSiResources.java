package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Range;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.ReferencedBinaryFiles;
import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.ApplicationInformation;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
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
import fr.inra.oresing.domain.data.deposit.bundle.RegisterReactiveResult;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResultRest;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.rapport.BundleReport;
import fr.inra.oresing.domain.data.read.ouput.KeepAliveZipOutputStream;
import fr.inra.oresing.domain.data.read.query.OutPut;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.application.BadLabelNameException;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.data.publication.DataVersioningResult;
import fr.inra.oresing.rest.data.publication.State;
import fr.inra.oresing.rest.data.publication.StoreFile;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import fr.inra.oresing.rest.exceptions.OreSiIOException;
import fr.inra.oresing.rest.filesenderclient.BuildBundleReport;
import fr.inra.oresing.rest.model.additionalfiles.CreateAdditionalFileRequest;
import fr.inra.oresing.rest.model.additionalfiles.exceptions.BadAdditionalFileParamsSearchException;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;
import fr.inra.oresing.rest.model.data.*;
import fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery;
import fr.inra.oresing.rest.model.reference.GetReferenceResult;
import fr.inra.oresing.rest.model.rightsrequest.CreateRightsRequestRequest;
import fr.inra.oresing.rest.model.rightsrequest.GetAdditionalFilesResult;
import fr.inra.oresing.rest.model.rightsrequest.GetRightsRequestResult;
import fr.inra.oresing.rest.model.rightsrequest.RightsRequestInfos;
import fr.inra.oresing.rest.model.synthesis.SynthesisResult;
import fr.inra.oresing.rest.reactive.*;
import fr.inra.oresing.rest.services.AdditionalFileService;
import fr.inra.oresing.rest.services.RelationalService;
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
import fr.inra.oresing.rest.usecases.storage.versioning.UnPublishVersionBeforeDeleteUseCase;
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
import reactor.core.publisher.Mono;

import java.io.*;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.io.output.TeeOutputStream;

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
    public static final String IO_ERROR_WRITE = "Error writing to one of the outputs";
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
    public static final String BAD_REPORT = "Le rapport est incomplet ou contient des erreurs. L'e-mail n'a pas été envoyé.";
    public static final String BAD_BUNDLE = "Erreur lors de la création du bundle de téléchargement";
    public static final String BUNDLE_NAME = "%s-%s-upload-bundle";
    public static final String DATA_ZIP = "%s-%s-data";
    public static final String DATA_SERVICE_PATH_PATTERN = "/applications/%s/data/%s";
    private final ConcurrentHashMap<String, Boolean> runningBundleCreation = new ConcurrentHashMap<>();
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
    private final RemoveFileUseCase removeFileUseCase;
    private final GetFileWithDataUseCase getFileWithDataUseCase;
    private final GetFilesOnRepositoryUseCase getFilesOnRepositoryUseCase;
    private final GetReferencedBinaryFilesUseCase getReferencedBinaryFilesUseCase;
    private final GetStoreFileUseCase getStoreFileUseCase;
    private final UnPublishVersionBeforeDeleteUseCase unPublishVersionBeforeDeleteUseCase;
    private final GetSynthesisUseCase getSynthesisUseCase;
    private final GetSynthesisWithVariableUseCase getSynthesisWithVariableUseCase;
    private final BuildSynthesisUseCase buildSynthesisUseCase;
    private final GetAllUsersUseCase getAllUsersUseCase;
    private final GetAuthorizationScopesUseCase getAuthorizationScopesUseCase;
    private final GetApplicationOrAccordingToRightsUseCase getApplicationOrAccordingToRightsUseCase;
    private final BuildOpenAdomUseCase buildOpenAdomUseCase;
    private final GetApplicationUseCase getApplicationUseCase;
    private final GetFormatCheckedUseCase getFormatCheckedUseCase;
    private final GetReferenceDisplaysByIdUseCase getReferenceDisplaysByIdUseCase;
    private final GetDataCsvStreamUseCase getDataCsvStreamUseCase;
    private final GetDataColumnUseCase getDataColumnUseCase;
    private final FindDataUseCase findDataUseCase;
    private final GetCheckedFormatComponentsUseCase getCheckedFormatComponentsUseCase;
    private final DeleteDataUseCase deleteDataUseCase;
    private final GetApplicationsUseCase getApplicationsUseCase;
    private final ValidateConfigurationUseCase validateConfigurationUseCase;
    private final CreateApplicationUseCase createApplicationUseCase;
    private final ChangeApplicationConfigurationUseCase changeApplicationConfigurationUseCase;
    private final BuildDataZipUseCase buildDataZipUseCase;
    private final SendZipLinkByMailUseCase sendZipLinkByMailUseCase;
    private final WriteUploadBundleUseCase writeUploadBundleUseCase;
    private final ReadEntryUseCase readEntryUseCase;
    private final GetCurrentUserUseCase getCurrentUserUseCase;
    private final SendUploadErrorsMailUseCase sendUploadErrorsMailUseCase;
    private final ExtractionRateLimiter extractionRateLimiter;
    private final OpenadomMetrics metrics;
    private final WorkflowLogWriter workflowLogWriter;
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
            UnPublishVersionBeforeDeleteUseCase unPublishVersionBeforeDeleteUseCase,
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
            GetDataColumnUseCase getDataColumnUseCase,
            RemoveFileUseCase removeFileUseCase,
            GetFileWithDataUseCase getFileWithDataUseCase,
            GetFilesOnRepositoryUseCase getFilesOnRepositoryUseCase,
            GetReferencedBinaryFilesUseCase getReferencedBinaryFilesUseCase,
            FindDataUseCase findDataUseCase,
            GetCheckedFormatComponentsUseCase getCheckedFormatComponentsUseCase,
            DeleteDataUseCase deleteDataUseCase,
            GetApplicationsUseCase getApplicationsUseCase,
            ValidateConfigurationUseCase validateConfigurationUseCase,
            CreateApplicationUseCase createApplicationUseCase,
            ChangeApplicationConfigurationUseCase changeApplicationConfigurationUseCase,
            BuildDataZipUseCase buildDataZipUseCase,
            SendZipLinkByMailUseCase sendZipLinkByMailUseCase,
            WriteUploadBundleUseCase writeUploadBundleUseCase,
            ReadEntryUseCase readEntryUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase,
            SendUploadErrorsMailUseCase sendUploadErrorsMailUseCase,
            ExtractionRateLimiter extractionRateLimiter,
            OpenadomMetrics metrics,
            WorkflowLogWriter workflowLogWriter
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
        this.unPublishVersionBeforeDeleteUseCase = unPublishVersionBeforeDeleteUseCase;
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
        this.getDataColumnUseCase = getDataColumnUseCase;
        this.removeFileUseCase = removeFileUseCase;
        this.getFileWithDataUseCase = getFileWithDataUseCase;
        this.getFilesOnRepositoryUseCase = getFilesOnRepositoryUseCase;
        this.getReferencedBinaryFilesUseCase = getReferencedBinaryFilesUseCase;
        this.findDataUseCase = findDataUseCase;
        this.getCheckedFormatComponentsUseCase = getCheckedFormatComponentsUseCase;
        this.deleteDataUseCase = deleteDataUseCase;
        this.getApplicationsUseCase = getApplicationsUseCase;
        this.validateConfigurationUseCase = validateConfigurationUseCase;
        this.createApplicationUseCase = createApplicationUseCase;
        this.changeApplicationConfigurationUseCase = changeApplicationConfigurationUseCase;
        this.buildDataZipUseCase = buildDataZipUseCase;
        this.sendZipLinkByMailUseCase = sendZipLinkByMailUseCase;
        this.writeUploadBundleUseCase = writeUploadBundleUseCase;
        this.readEntryUseCase = readEntryUseCase;
        this.getCurrentUserUseCase = getCurrentUserUseCase;
        this.sendUploadErrorsMailUseCase = sendUploadErrorsMailUseCase;
        this.extractionRateLimiter = extractionRateLimiter;
        this.metrics = metrics;
        this.workflowLogWriter = workflowLogWriter;
    }

    /**
     * Helper : construit et soumet async un {@link WorkflowLogEntry}
     * pour une extraction. Best-effort : erreurs UUID loguees puis
     * swallowed , ne casse pas le streaming.
     */
    private void logExtractionEvent(
            String workflowType, String userId,
            String applicationName, String dataType, String resourceName,
            Instant startedAt, Duration duration, String status, long bytesTotal,
            String fatalError) {
        try {
            workflowLogWriter.logAsync(new WorkflowLogEntry(
                    java.util.UUID.randomUUID(),
                    workflowType,
                    java.util.UUID.fromString(userId),
                    resolveCurrentLogin(),
                    applicationName,
                    dataType,
                    resourceName,
                    startedAt,
                    startedAt.plus(duration),
                    duration,
                    status,
                    0L, 0L, 0,
                    bytesTotal,
                    java.util.List.of(),
                    fatalError));
        } catch (IllegalArgumentException e) {
            log.warn("Format UUID utilisateur invalide , skip log extraction [userId={}]", userId);
        }
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


    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DELETE_FILE')")
    @DeleteMapping(value = "/applications/{name}/file/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> removeFile(
            HttpServletRequest request,
            @PathVariable("name") final String applicationName,
            @PathVariable("id") final UUID id) throws IOException {

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
        if (!storeFile.builder().getFileOrUUID().topublish()) {
            if (!applicationDataDelete.get().hasRightForPublishOrUnPublish(storeFile.fileOrUuid())) {
                throw new NotApplicationDataWriterForPublishException(applicationName, dataName);
            }
                DataVersioningResult dataVersioningResult = unPublishVersionBeforeDeleteUseCase
                    .execute(locale, applicationName, id, true);
        }
        Optional<UUID> uuid = removeFileUseCase.execute(application, id);
        if (uuid.isPresent()) {
            return ResponseEntity.ok(id.toString());
        } else {
            throw new NotApplicationCanDeleteRightsException(applicationName, dataName);
        }
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/filesOnRepository/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<BinaryFileResult>> getFilesOnRepository(@PathVariable("nameOrId") final String nameOrId,
                                                                       @PathVariable("dataType") final String dataType,
                                                                       @JsonParam("repositoryId") final BinaryFileDataset binaryFileDataset) {
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
        final List<BinaryFileResult> files =
                getFilesOnRepositoryUseCase.execute(nameOrId, dataType, binaryFileDataset, false).stream()
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
                                getReferencedFiles(binaryFile)
                        ))
                        .toList();
        return ResponseEntity.ok(files);
    }

    private List<ReferencedBinaryFiles> getReferencedFiles(BinaryFile binaryFile) {
        if (Optional.ofNullable(binaryFile)
                .map(BinaryFile::getParams)
                .stream().noneMatch(BinaryFileInfos::published)) {
            return null;
        }
        return Optional.ofNullable(binaryFile)
                .map(bf -> getReferencedBinaryFilesUseCase.execute(
                                bf.getApplication(),
                                bf.getParams().binaryFiledataset().getDatatype(),
                                Set.of(bf.getId()))
                )
                .orElseGet(List::of);
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
        if (!RelationalService.IdentifierTest.identifierForApplicationName(name)) {
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
        return ResponseEntity.ok(list);
    }

    private ResponseEntity<?> createRightsRequest(final String nameOrId,
                                                  final CreateRightsRequestRequest createRightsRequestRequest) {
        final UUID fileUUID = createOrUpdateRightsRequestUseCase.execute(createRightsRequestRequest, nameOrId);
        return ResponseEntity.ok(fileUUID);
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

        return ResponseEntity.ok(application.getOrderedReferences());
    }

    /**
     * Liste toutes les valeurs possibles pour un type de referenciel
     *
     * @param nameOrId l'id ou le nom de l'application
     * @param refType  le type du referenciel
     * @return un tableau de chaine
     */
    private ResponseEntity<GetReferenceResult> listDataForColumn(
            final String nameOrId,
            final String refType,
            final MultiValueMap<String, String> params) {
        final List<DataValue> list = findReferenceUseCase.execute(nameOrId, refType, params);

        final Map<String, Map<String, LineChecker>> checkedFormatColumns = getFormatCheckedUseCase.execute(nameOrId, refType);
        Set<String> listOfReferenceIds = list.stream()
                .map(DataValue::getReferenceType)
                .collect(Collectors.toSet());
        final Map<Ltree, List<DataValue>> requiredReferencesValues = getReferenceDisplaysByIdUseCase.execute(
            getApplicationOrAccordingToRightsUseCase.execute(nameOrId),
            listOfReferenceIds);
        Map<String, LineChecker> referenceLineCheckers = checkedFormatColumns.get(ReferenceType.class.getSimpleName());
        Map<String, String> referenceTypeForReferencingColumns =
                Optional.ofNullable(checkedFormatColumns.get(ReferenceType.class.getSimpleName()))
                        .map(checkedFormatColumn -> checkedFormatColumn.entrySet()
                                .stream()
                                .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                e -> Optional.of(e)
                                                        .map(Map.Entry::getValue)
                                                        .map(LineChecker::underlyingType)
                                                        .filter(ReferenceType.class::isInstance)
                                                        .map(c -> (ReferenceType) c)
                                                        .map(ReferenceType::getRefType)
                                                        .orElse("erreur")
                                        )
                                )
                        )
                        .orElseGet(LinkedHashMap::new);
        final ImmutableSet<GetReferenceResult.ReferenceValue> referenceValues = list.stream()
                .map(referenceValue ->
                        new GetReferenceResult.ReferenceValue(
                                referenceValue.getId().toString(),
                                referenceValue.getPatternColumnName(),
                                referenceValue.getHierarchicalKey().getSql(),
                                referenceValue.getNaturalKey().getSql(),
                                referenceValue.getRefValues().toJsonForFrontend(),
                                referenceValue.getRefsLinkedTo(),
                                referenceValue.getReferencingreferences()
                        )
                )
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetReferenceResult.ReferenceValue::commparingValue)));
        return ResponseEntity.ok(new GetReferenceResult(referenceValues,
                referenceTypeForReferencingColumns));
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
            final Instant startedAt = Instant.now();
            final org.apache.commons.io.output.CountingOutputStream counting =
                    new org.apache.commons.io.output.CountingOutputStream(out);
            String finalStatus = WorkflowLogEntry.STATUS_FAILED;
            String fatalError = null;
            metrics.markExtractionStart("csv");
            try {
                getDataCsvStreamUseCase.execute(counting, nameOrId, refType, language, false);
                finalStatus = WorkflowLogEntry.STATUS_COMPLETED;
            } catch (RuntimeException ex) {
                fatalError = ex.getMessage();
                throw ex;
            } finally {
                Duration duration = Duration.between(startedAt, Instant.now());
                long bytes = counting.getByteCount();
                metrics.recordExtractionCompleted("csv", nameOrId, refType, finalStatus, duration, bytes);
                metrics.markExtractionEnd("csv");
                logExtractionEvent(WorkflowLogEntry.TYPE_EXTRACT_CSV,
                        userId, nameOrId, refType, refType + ".csv",
                        startedAt, duration, finalStatus, bytes, fatalError);
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

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/data/{refType}/{column}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<List<String>>> listDataForColumn(@PathVariable("nameOrId") final String nameOrId, @PathVariable("refType") final String refType, @PathVariable("column") final String column) {
        final Application application = getApplicationUseCase.execute(nameOrId);
        final List<List<String>> result = getDataColumnUseCase.execute(application, refType, column);
        return ResponseEntity.ok(result);
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
                    return createDataUseCase.execute(
                            locale, nameOrId, dataName, finalDataFile, false, true);
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
                    sendUploadErrorsMailUseCase.execute(
                            locale,
                            localizedApplicationName,
                            localizedDataName,
                            currentUser,
                            errorsToJson
                    );
                    throw invalidDatasetContentException;
                } catch (IOException e) {
                    throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
                }
            }).get();
            // #58 - Reconstruire le cache des filtres après un dépôt réussi ( asynchrone )
            Application application = serviceContainer.applicationService().getApplication(nameOrId);
            serviceContainer.dataService().refreshFilterListCache(application, dataName);
            return ResponseEntity
                    .created(URI.create(dataVersioningResult.uri()))
                    .body(Map.of("id", dataVersioningResult.dataId().toString(), "referenceSynthesis", dataVersioningResult.dataSynthesis()));
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
        return ResponseEntity.ok(allDataNames);
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
        return ResponseEntity.ok(list);
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

        final String dataType = additionalFilesInfos.getFiletype();
        final StreamingResponseBody streamResponseBody;
        if (AdditionalFileService.CHARTE.equals(additionalFilesInfos.getFiletype())) {
            response.setHeader("Content-type", "application/pdf");
            response.setHeader("Content-Security-Policy", "frame-ancestors %s".formatted(frontendOrigin));
            streamResponseBody = out -> {
                final Instant startedAt = Instant.now();
                final org.apache.commons.io.output.CountingOutputStream counting =
                        new org.apache.commons.io.output.CountingOutputStream(out);
                String finalStatus = WorkflowLogEntry.STATUS_FAILED;
                String fatalError = null;
                metrics.markExtractionStart(extractionType);
                try {
                    getCharteUseCase.execute(counting, response, nameOrId, additionalFilesInfos);
                    finalStatus = WorkflowLogEntry.STATUS_COMPLETED;
                } catch (RuntimeException ex) {
                    fatalError = ex.getMessage();
                    throw ex;
                } finally {
                    Duration duration = Duration.between(startedAt, Instant.now());
                    long bytes = counting.getByteCount();
                    metrics.recordExtractionCompleted(extractionType, nameOrId, dataType, finalStatus, duration, bytes);
                    metrics.markExtractionEnd(extractionType);
                    logExtractionEvent(WorkflowLogEntry.TYPE_EXTRACT_CHARTE,
                            userId, nameOrId, dataType, "charte.pdf",
                            startedAt, duration, finalStatus, bytes, fatalError);
                    extractionRateLimiter.release(userId);
                }
            };
        } else {
            streamResponseBody = out -> {
                final Instant startedAt = Instant.now();
                final org.apache.commons.io.output.CountingOutputStream counting =
                        new org.apache.commons.io.output.CountingOutputStream(out);
                String finalStatus = WorkflowLogEntry.STATUS_FAILED;
                String fatalError = null;
                metrics.markExtractionStart(extractionType);
                try (final ZipOutputStream zipOutputStream = new KeepAliveZipOutputStream(counting)) {
                    getAdditionalFilesZipStreamUseCase.execute(zipOutputStream, nameOrId, additionalFilesInfos);
                    finalStatus = WorkflowLogEntry.STATUS_COMPLETED;
                } catch (final IOException ioe) {
                    fatalError = ioe.getMessage();
                    switch (OreSiResources.getDefaultLocale().getLanguage()) {
                        case FR -> log.error(IO_ERROR_FR, ioe);
                        case EN -> log.error(IO_ERROR_EN, ioe);
                        default -> log.error(IO_ERROR_EN, ioe);
                    }
                } finally {
                    Duration duration = Duration.between(startedAt, Instant.now());
                    long bytes = counting.getByteCount();
                    metrics.recordExtractionCompleted(extractionType, nameOrId, dataType, finalStatus, duration, bytes);
                    metrics.markExtractionEnd(extractionType);
                    logExtractionEvent(WorkflowLogEntry.TYPE_EXTRACT_ADDITIONAL_FILES,
                            userId, nameOrId, dataType, "additionalFiles.zip",
                            startedAt, duration, finalStatus, bytes, fatalError);
                    extractionRateLimiter.release(userId);
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
            return ResponseEntity.ok(deletedFiles.stream().map(UUID::toString).collect(Collectors.joining(LIST_DELIMITER)));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    private ResponseEntity<UUID> createAdditionalFile(final String nameOrId,
                                                       final String additionalFileName,
                                                       final MultipartFile file,
                                                       final CreateAdditionalFileRequest createAdditionalFileRequest) {
        final UUID fileUUID = createOrUpdateAdditionalFileUseCase.execute(createAdditionalFileRequest, additionalFileName, nameOrId, file);
        return ResponseEntity.ok(fileUUID);


    }

    /**
     * export as JSON
     */
        protected ResponseEntity<GetDataResult> getAllDataJson(
            final String nameOrId,
            final String dataName,
            final DownloadDatasetQuery params,
            boolean loadExample) {

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
        return ResponseEntity.ok(new GetDataResult(
                downloadDatasetQuery.patternDefinitionCount(),
                variables,
                dataRowResults,
                List.of(),
                checkedFormatcomponents,
                referenceScopes));
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
            description = "Return the list of available filters (reference dropdowns) for dataType 'dataType' of application 'nameOrId'. "
                    + "Separated from the /json endpoint for asynchronous loading: data is displayed immediately while filters load in the background. "
                    + "Results are cached server-side for 10 minutes. Use refresh=true to force a cache reload.",
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
            @RequestParam(defaultValue = "false") boolean refresh) {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        // Si refresh=true, on invalide le cache pour forcer un rechargement depuis la base
        if (refresh) {
            serviceContainer.dataService().invalidateFilterListCache(application, dataName);
        }
        // Retourne le JSON sérialisé directement depuis le cache (pas de re-sérialisation Jackson)
        String json = serviceContainer.dataService().filterListAsJson(application, dataName);
        return ResponseEntity.ok(json);
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
        return ResponseEntity.ok(deletedData.stream().map(UUID::toString).collect(Collectors.joining(LIST_DELIMITER)));

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
            Path diskCopy = null;
            boolean streamedOk = false;
            final Instant startedAt = Instant.now();
            long bytesStreamed = 0L;
            String fatalError = null;
            metrics.markExtractionStart("zip");

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
                        fatalError = buildEx.getMessage();
                        writeErrorEntryToZip(zip, buildEx);
                    }
                }

                // Taille finale du ZIP produit ( identique cote client et cote disque )
                if (diskCopy != null) {
                    try {
                        bytesStreamed = Files.size(diskCopy);
                    } catch (IOException ignored) {
                        // metrics best-effort
                    }
                }

                if (streamedOk) {
                    // Streaming OK : le fichier disque est complet , on lance
                    // l'upload FileSender + mail en async. Le client a deja
                    // recu tous les bytes , cette tache n'influe plus sur la
                    // reponse HTTP.
                    final Path finalDiskCopy = diskCopy;
                    final OreSiUser finalUser = currentUser;
                    heavyExecutorService.submit(() -> {
                        SecurityContextHolder.setContext(securityContext);
                        try {
                            sendZipLinkByMailUseCase.execute(finalDiskCopy, downloadDatasetQuery, finalUser);
                        } catch (RuntimeException ex) {
                            log.error(EMAIL_ERROR, ex);
                        } finally {
                            try {
                                Files.deleteIfExists(finalDiskCopy);
                            } catch (IOException ex) {
                                log.warn(IO_DELETE_ERROR, ex);
                            }
                        }
                    });
                    diskCopy = null; // ownership transferee au submit async
                }
            } finally {
                // Nettoyage du fichier disque si le streaming a echoue ou si
                // on n'a pas pu atteindre le submit async ( ex : RuntimeException
                // pendant createTempFile ).
                if (diskCopy != null) {
                    try {
                        Files.deleteIfExists(diskCopy);
                    } catch (IOException ex) {
                        log.warn(IO_DELETE_ERROR, ex);
                    }
                }
                Duration duration = Duration.between(startedAt, Instant.now());
                String finalStatus = streamedOk
                        ? WorkflowLogEntry.STATUS_COMPLETED
                        : WorkflowLogEntry.STATUS_FAILED;
                metrics.recordExtractionCompleted("zip", nameOrId, dataType,
                        finalStatus, duration, bytesStreamed);
                metrics.markExtractionEnd("zip");
                logExtractionEvent(WorkflowLogEntry.TYPE_EXTRACT_ZIP,
                        userId, nameOrId, dataType, fileName + ".zip",
                        startedAt, duration, finalStatus, bytesStreamed, fatalError);
                SecurityContextHolder.clearContext();
                extractionRateLimiter.release(userId);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\".zip")
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
            return ResponseEntity.badRequest().body(errors);
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
            return ResponseEntity.badRequest().body(errors);
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
            return ResponseEntity.badRequest().body(errors);
        }
    }

    private ResponseEntity<?> buidSynthesis(final String nameOrId,
                                            final String dataType) {
        return buidSynthesis(nameOrId, dataType, null);
    }


    private ResponseEntity<?> getUploadBundle(
            @PathVariable("nameOrId") String nameOrId,
            @RequestParam(value = "withData", required = false, defaultValue = "false") boolean withData,
            @RequestParam(value = "locale", required = false) Locale locale,
            HttpServletRequest request) {

        OreSiUser user = userRepository.findById(OreSiApiRequestContext.getRequestClient().id());
        if (runningBundleCreation.get(user.getLogin()) != null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Opération déjà en cours");
        } else {
            runningBundleCreation.put(user.getLogin(), true);
        }

        String instanceUrl = String.format("%s://%s:%s",
                request.getScheme(),
                request.getServerName(),
                request.getServerPort()
        );

        String fileName = BUNDLE_NAME.formatted(nameOrId, LocalDateTime.now().format(TIMESTAMP_FORMATER));

        SecurityContext securityContext = SecurityContextHolder.getContext();

        heavyExecutorService.submit(() -> {
            Path tempZipDirectory = null;
            try {
                SecurityContextHolder.setContext(securityContext);
                tempZipDirectory = Files.createTempDirectory(Paths.get(TMP), fileName);

                BuildBundleReport report = null;
                try {
                    report = writeUploadBundleUseCase.execute(instanceUrl, nameOrId, withData, locale, tempZipDirectory);
                } catch (RuntimeException e) {
                    log.error(IO_ERROR_WRITE, e);
                }

                if (report != null && report.referentielsEnErreur().isEmpty()) {
                    try {
                        Path zipFile = tempZipDirectory.resolveSibling(tempZipDirectory.getFileName() + ".zip");
                        ZipUtils.zipDirectory(tempZipDirectory, zipFile);
                        sendZipLinkByMailUseCase.execute(zipFile, report, user);
                    } catch (IOException | RuntimeException e) {
                        log.error(EMAIL_ERROR, e);
                    }
                } else {
                    log.warn(BAD_REPORT);
                }

                try {
                    removeRepository(tempZipDirectory);
                    Files.deleteIfExists(tempZipDirectory);
                } catch (IOException e) {
                    log.error(IO_DELETE_ERROR, e);
                }

            } catch (IOException | RuntimeException e) {
                if (tempZipDirectory != null) {
                    try {
                        addErrorFileToZip(tempZipDirectory, e);
                    } catch (IOException ioe) {
                        log.error(IO_ADDING_ERROR, ioe);
                    }
                }
                log.error(BAD_BUNDLE, e);
            } finally {
                runningBundleCreation.remove(user.getLogin());
            }
        });

        return ResponseEntity.ok().build();
    }

    private Flux<ReactiveResult> uploadBundle(
            HttpServletRequest request,
            @PathVariable String nameOrId,
            @RequestParam("file") MultipartFile zipBundle) {

        Locale locale = localeResolver.resolveLocale(request);
        final String origin = request.getHeader("Origin");
        final OreSiUser currentUser = getCurrentUserUseCase.execute();
        final SecurityContext context = SecurityContextHolder.getContext();
        return Flux.create(sink -> {
            normalExecutorService.submit(() -> {
                SecurityContextHolder.setContext(context);
                File zipFile = null;
                try {
                    zipFile = getPhysicalFileOrCopy(zipBundle);
                } catch (IOException e) {
                    sink.error(new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e));
                    return;
                }
                sink.next(new ReactiveTypeProgress(0L));
                final Application application = getApplicationUseCase.execute(nameOrId);

                BundleReport rapport = new BundleReport(locale, origin, application);

                boolean completedSuccessfully = false;
                try {
                    ObjectMapper mapper = new ObjectMapper();
                    File finalZipFile = zipFile;
                    AtomicReference<Map<String, List<String>>> manifest = new AtomicReference<>();
                    readEntryUseCase.execute(zipFile, DataService.MANIFEST_JSON,
                            manifestStream -> {
                                try {
                                    manifest.set(mapper.readValue
                                            (manifestStream,
                                                    new TypeReference<Map<String, List<String>>>() {
                                                    }
                                            ));
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                    );

                    int countFiles = manifest.get().values().stream()
                            .mapToInt(List::size)
                            .sum();

                    AtomicReference<Map<String, List<String>>> references = new AtomicReference<>();
                    readEntryUseCase.execute(zipFile, DataService.REFERENCES_JSON,
                            referencesStream -> {
                                try {
                                    final Map<String, List<String>> reference = mapper.readValue
                                            (referencesStream,
                                                    new TypeReference<Map<String, List<String>>>() {
                                                    }
                                            );
                                    references.set(reference);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                            }
                    );
                    @SuppressWarnings("unchecked")
                    final RegisterReactiveResult registerReactiveResult = new RegisterReactiveResult((FluxSink<ReactiveResult<?>>) (Object) sink, countFiles, rapport);
                    final ReactiveTypeInfo reactiveTypeInfo = new ReactiveTypeInfo("MANIFEST", Map.of("manifest", manifest));
                    registerReactiveResult.add(reactiveTypeInfo, false);
                    rapport.add(reactiveTypeInfo);
                    int MAX_DB_CONCURRENCY = 30; // ou ton pool JDBC size
                    readManifestAndSendMailTopological(
                            manifest.get(),
                            references.get(),
                            registerReactiveResult,
                            finalZipFile,
                            locale,
                            application,
                            rapport,
                            currentUser,
                            MAX_DB_CONCURRENCY
                    ).block();
                    completedSuccessfully = true;
                    // #58 - Reconstruire le cache des filtres uniquement pour les dataTypes importés par le bundle
                    for (String dataName : manifest.get().keySet()) {
                        serviceContainer.dataService().refreshFilterListCache(application, dataName);
                    }
                } catch (StreamReadException e) {
                    sink.error(e);
                } catch (DatabindException e) {
                    sink.error(e);
                } catch (IOException e) {
                    sink.error(e);
                } catch (RuntimeException e) {
                    sink.error(e);
                }
                if (completedSuccessfully && !sink.isCancelled()) {
                    sink.complete();
                }
            });
        });
    }

    public Mono<Void> readManifestAndSendMailTopological(
            Map<String, List<String>> manifest,
            Map<String, List<String>> references,
            RegisterReactiveResult registerReactiveResult,
            File finalZipFile,
            Locale locale,
            Application application,
            BundleReport rapport,
            OreSiUser currentUser,
            int MAX_DB_CONCURRENCY
    ) {
        SecurityContext secCtx = SecurityContextHolder.getContext();
        Set<String> processed = ConcurrentHashMap.newKeySet();
        Set<String> remaining = ConcurrentHashMap.newKeySet();
        remaining.addAll(manifest.keySet());

        Function<String, Mono<Void>> processReference = dataName ->
                Flux.fromIterable(manifest.getOrDefault(dataName, List.of()))
                        .flatMap(fileName ->
                                Mono.create(sink -> {
                                    try {
                                        heavyExecutorService.submit(() -> {
                                            SecurityContextHolder.setContext(secCtx);
                                            try {
                                                readManifestEntryAndLoadData(
                                                        Map.entry(dataName, List.of(fileName)),
                                                        registerReactiveResult,
                                                        finalZipFile,
                                                        locale,
                                                        application,
                                                        rapport
                                                );
                                                sink.success();
                                            } catch (Exception e) {
                                                sink.error(e);
                                            }
                                        });
                                    } catch (RuntimeException e) {
                                        sink.error(e);
                                    }
                                }), MAX_DB_CONCURRENCY
                        )
                        .then()
                        .doOnSuccess(v -> {
                            processed.add(dataName);
                            log.info("Référence {} traitée. Processed: {}/{}", dataName, processed.size(), manifest.size());
                        });

        // Traitement par vagues en respectant les dépendances
        return processBatchTopological(processed, remaining, processReference, references, MAX_DB_CONCURRENCY)
                .then(Mono.fromRunnable(() -> {
                    try {
                        sendZipLinkByMailUseCase.execute(
                                registerReactiveResult.bundleReport().attachmentFile(),
                                registerReactiveResult.bundleReport(),
                                currentUser
                        );
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        registerReactiveResult.add(new ReactiveTypeProgress(1), false);
                    }
                }));
    }

    private Mono<Void> processBatchTopological(
            Set<String> processed,
            Set<String> remaining,
            Function<String, Mono<Void>> processReference,
            Map<String, List<String>> references,
            int MAX_DB_CONCURRENCY) {

        // Trouve les références "prêtes" (toutes leurs dépendances sont traitées)
        List<String> ready;
        synchronized (remaining) {
            ready = remaining.stream()
                    .filter(ref -> {
                        List<String> deps = references.getOrDefault(ref, List.of())
                                .stream()
                                .filter(dep -> !dep.equals(ref)) // EXCLURE l'auto-référence
                                .collect(Collectors.toList());
                        boolean allDepsProcessed = deps.isEmpty() || processed.containsAll(deps);
                        if (allDepsProcessed) {
                            log.debug("Référence {} est prête (dépendances externes: {})", ref, deps);
                        }
                        return allDepsProcessed;
                    })
                    .limit(MAX_DB_CONCURRENCY)
                    .collect(Collectors.toList());
        }

        // Si aucune référence n'est prête, on a terminé (ou il y a un cycle)
        if (ready.isEmpty()) {
            if (!remaining.isEmpty()) {
                log.error("Cycle détecté ou dépendances non satisfaites pour: {}", remaining);
                throw new RuntimeException("Cycle détecté dans les dépendances: " + remaining);
            }
            log.info("Toutes les références ont été traitées");
            return Mono.empty();
        }

        log.info("Traitement de la vague: {} (concurrence: {})", ready, ready.size());

        // Traite cette vague en parallèle
        return Flux.fromIterable(ready)
                .flatMap(processReference, MAX_DB_CONCURRENCY)
                .then(Mono.defer(() -> {
                    // Retire les références traitées
                    synchronized (remaining) {
                        remaining.removeAll(ready);
                    }
                    // Récursion : traite la vague suivante
                    return processBatchTopological(processed, remaining, processReference, references, MAX_DB_CONCURRENCY);
                }));
    }


    private void readManifestEntryAndLoadData(
            Map.Entry<String, List<String>> entry,
            RegisterReactiveResult registerReactiveResult,
            File finalZipFile,
            Locale locale,
            Application application,
            BundleReport rapport) {
        String dataName = entry.getKey();
        log.info(dataName);
        entry.getValue().forEach(fileName -> {
            try {
                readEntryUseCase.execute(finalZipFile, "%s/%s".formatted(dataName, fileName),
                        fileToUpload -> {

                            final String[] split = fileName.split("\\.");
                            File tempFile = null;
                            try {
                                tempFile = File.createTempFile(split[0], split[1]);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            tempFile.deleteOnExit();
                            try (OutputStream out = new FileOutputStream(tempFile);
                                 InputStream in = fileToUpload) {
                                in.transferTo(out); // Transfert direct du flux, pas de gestion de byte[] manuelle
                            } catch (FileNotFoundException e) {
                                throw new RuntimeException(e);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            try {
                                final DataVersioningResult data = createDataUseCase.execute(
                                        locale,
                                        application.getName(),
                                        dataName,
                                        fileToUpload == null ? null : new DataFile(
                                                tempFile,
                                                (long) tempFile.length(),
                                                fileName
                                        ),
                                        false,
                                        false
                                );
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            final ReactiveResult reactiveResult = new ReactiveTypeInfo("LOADED_DATA", Map.of("dataName", dataName, "fileName", fileName));
                            registerReactiveResult.add(reactiveResult, true);
                            rapport.add(reactiveResult);
                        }
                );
            } catch (IOException e) {
                final ReactiveTypeError reactiveTypeError = new ReactiveTypeError(Map.of("dataName", dataName, "fileName", fileName, "errorType", "ERROR_LOADING_DATA"));
                registerReactiveResult.add(reactiveTypeError, true);
                throw new RuntimeException(e);
            } catch (InvalidDatasetContentException e) {
                final ReactiveTypeError reactiveTypeError = new ReactiveTypeError(
                        Map.of(
                                "dataName", dataName,
                                "fileName", fileName,
                                "errorType", e.getMessage(),
                                "message", e.getErrors().stream().limit(1).map(firstError -> firstError.validationCheckResult().message()).findFirst().orElse(""),
                                "params", e.getErrors().stream().limit(1).map(firstError -> firstError.validationCheckResult().messageParams()).findFirst().orElse(Map.of())
                        )
                );
                registerReactiveResult.add(reactiveTypeError, true);
            }
        });
    }

    private Application getOrLoadApplication(String nameOrId, File zipFile) {
        AtomicReference<Application> application = null;
        try {
            application.set(getApplicationUseCase.execute(nameOrId));
        } catch (Exception e) {
            try {
                readEntryUseCase.execute(zipFile, DataService.MANIFEST_JSON,
                        configurationFile -> {
                            MultipartFile tmpConfigurationFile = new MultipartFile() {
                                @Override
                                public String getName() {
                                    return DataService.CONFIGURATION_FILE;
                                }

                                @Override
                                public String getOriginalFilename() {
                                    return DataService.CONFIGURATION_FILE;
                                }

                                @Override
                                public String getContentType() {
                                    return "application/x-yaml";
                                }

                                @Override
                                public boolean isEmpty() {
                                    return false;
                                }

                                @Override
                                public long getSize() {
                                    try {
                                        return configurationFile.available();
                                    } catch (IOException ex) {
                                        return 0L;
                                    }
                                }

                                @Override
                                public byte[] getBytes() throws IOException {
                                    return configurationFile.readAllBytes();
                                }

                                @Override
                                public InputStream getInputStream() throws IOException {
                                    return configurationFile;
                                }

                                @Override
                                public void transferTo(File dest) throws IOException, IllegalStateException {

                                }
                            };
                            createApplication(nameOrId, "uploadBundle", tmpConfigurationFile);
                            application.set(getApplicationUseCase.execute(nameOrId));
                        }
                );

            } catch (IOException ex) {
                throw new RuntimeException(ex);
            }
        }
        return application.get();
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
