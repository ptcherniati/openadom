package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.common.base.Strings;
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
import fr.inra.oresing.domain.exceptions.binaryfile.binaryfile.BadFileOrUUIDQuery;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.binaryFile.BinaryFileService;
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
import fr.inra.oresing.rest.rightsrequest.BadRightsRequestInfosQuery;
import fr.inra.oresing.rest.rightsrequest.BadRightsRequestOrUUIDQuery;
import fr.inra.oresing.rest.services.AdditionalFileService;
import fr.inra.oresing.rest.services.RelationalService;
import fr.inra.oresing.rest.services.ServiceContainer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
    public static final String HEADER_ACCEPT_RANGES = "Accept-Ranges";
    public static final String HEADER_BYTES = "bytes";
    public static final String IO_ERROR_FR = "Exception lors de la lecture et du streaming de données ";
    public static final String IO_ERROR_EN = "Exception while reading and streaming data  ";
    public static final String HEADER_ZIP = "attachment; filename=additionalFiles.zip";
    public static final String HEADER_APPLICATION_ZIP_CHARSET_UTF_8 = "application/zip;charset=UTF-8";
    public static final String LIST_DELIMITER = ",";
    public static final String IO_DELETE_ERROR = "Erreur lors de la suppression du fichier temporaire";
    public static final String IO_ERROR_WRITE = "Error writing to one of the outputs";
    public static final DateTimeFormatter TIMESTAMP_FORMATER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    public static final String HEADER_ATTACHMENT_FILENAME_DATA_ZIP = "attachment; filename=\"%1$s-%2$s-%3$s.zip\"";
    public static final String HEADER_ZIP_MIME = "application/zip";
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
    private ExecutorService executorService;

    public OreSiResources(
            ExecutorService executorService,
            UserRepository userRepository,
            ServiceContainer serviceContainer,
            LocaleResolver localeResolver,
            JsonRowMapper mapper,
            @Value("${allowed.origin}") String frontendOrigin
    ) {
        this.userRepository = userRepository;
        this.serviceContainer = serviceContainer;
        this.localeResolver = localeResolver;
        this.frontendOrigin = frontendOrigin;
        this.mapper = mapper;
        this.executorService = executorService;
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

    private static CreateRightsRequestRequest deserialiseRightsRequestOrUUIDQuery(final String params) {
        try {
            return params != null && !JS_UNDEFINED.equals(params) ? new ObjectMapper().readValue(params, CreateRightsRequestRequest.class) : null;
        } catch (final IOException e) {
            throw new BadRightsRequestOrUUIDQuery(e.getMessage());
        }
    }

    private static RightsRequestInfos deserialiseRightsRequestQuery(final String params) {
        try {
            return params != null && !JS_UNDEFINED.equals(params) ? new ObjectMapper().readValue(params, RightsRequestInfos.class) : null;
        } catch (final IOException e) {
            throw new BadRightsRequestInfosQuery(e.getMessage());
        }
    }

    private static CreateAdditionalFileRequest deserialiseAdditionalFileOrUUIDQuery(final String params) {
        try {
            return params != null && !JS_UNDEFINED.equals(params) ?
                    new JsonRowMapper<CreateAdditionalFileRequest>().readValue(params, CreateAdditionalFileRequest.class) :
                    null;
        } catch (final IOException e) {
            throw new BadFileOrUUIDQuery(e.getMessage());
        }
    }

    private static AdditionalFilesInfos deserialiseAdditionalFilesInfos(final String params) {
        try {
            return params != null && !JS_UNDEFINED.equals(params) ? new ObjectMapper().readValue(params, AdditionalFilesInfos.class) : null;
        } catch (final IOException e) {
            throw new BadFileOrUUIDQuery(e.getMessage());
        }

    }

    private Flux<ReactiveResult> buildFluxRequestNDJson(Consumer<FluxSink<ReactiveResult>> fluxSink) {
        final SecurityContext context = SecurityContextHolder.getContext();
        return Flux.create(sink -> {
            executorService.submit(() -> {
                try {
                    SecurityContextHolder.setContext(context);
                    fluxSink.accept(sink);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            });
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
        StoreFile storeFile = serviceContainer.versioningService().getStoreFile(
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
            DataVersioningResult dataVersioningResult = serviceContainer.versioningService()
                    .unPublishVersionBeforeDelete(locale, applicationName, id, true);
        }
        Optional<UUID> uuid = serviceContainer.binaryFileService().removeFile(application, id);
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
                                                                       @RequestParam("repositoryId") final String repositoryId) {
        final BinaryFileDataset binaryFileDataset = BinaryFileService.deserialiseBinaryFileDatasetQuery(dataType, repositoryId);
        Map<UUID, UserDescriptionResult> users = serviceContainer.authorizationService().getAllUsers()
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
                serviceContainer.binaryFileService()
                        .getFilesOnRepository(nameOrId, dataType, binaryFileDataset, false).stream()
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
                .map(bf -> serviceContainer.binaryFileService()
                        .getReferencedBinaryFiles(
                                bf.getApplication(),
                                bf.getParams().binaryFiledataset().getDatatype(),
                                Set.of(bf.getId()))
                )
                .orElseGet(List::of);
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{name}/file/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> getFile(
            @PathVariable("name") final String name,
            @PathVariable("id") final UUID id) {
        final Optional<BinaryFile> optionalBinaryFile = serviceContainer.binaryFileService().getFileWithData(name, id);
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

    @PreAuthorize("isFullyAuthenticated()")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping(value = "/applications", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> getApplications(@RequestParam(required = false, defaultValue = "") final String[] filter) {
        final List<ApplicationInformation> filters = Arrays.stream(filter)
                .map(ApplicationInformation::valueOf)
                .toList();
        return buildFluxRequestNDJson(fluxSink -> {
            final ReactiveProgression.GetApplicationProgression progression = new ReactiveProgression.GetApplicationProgression(0L, fluxSink);
            serviceContainer.applicationService().getApplications(progression, filters);
        });
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/validate-configuration", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> validateConfiguration(@RequestParam("file") final MultipartFile file) throws IOException {
        DataFile dataFile = null;
        try {
            final File physicalFileOrCopy = getPhysicalFileOrCopy(file);
            dataFile = file == null ? null : new DataFile(physicalFileOrCopy, (long) file.getInputStream().available(), file.getOriginalFilename());
        } catch (IOException e) {
            throw OreSiIOException.ORE_SI_IOEXCEPTION_CANT_LOAD_FILE();
        }
        DataFile finalDataFile = dataFile;
        return buildFluxRequestNDJson(fluxSink -> {
            final ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(0L, fluxSink);
            final Application application = serviceContainer.applicationService().validateConfiguration(progression, finalDataFile);
            fluxSink.next(new ReactiveTypeResult(application));
            progression.complete();
        });
    }

    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_APPLICATION_CREATE')")
    @PostMapping(value = "/applications/{name}", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Parameter(examples = @ExampleObject(
            name = "fichier de configuration",
            description = "<a href= 'https://anaee-dev.pages.mia.inra.fr/si-ore-v2/schemaExample.yaml'>Fichier d'example</a>"
    ))
    public Flux<ReactiveResult> createApplication(@PathVariable("name") final String name,
                                                  @RequestParam(name = "comment", defaultValue = "") final String comment,
                                                  @RequestParam("file") final MultipartFile file) throws BadApplicationConfigurationException {
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
            final ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(0L, fluxSink);
            try {
                serviceContainer.applicationService().createApplication(progression, name, finalDataFile, comment);
            } catch (Exception technicalException) {
                fluxSink.error(technicalException);
            }
        });
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/applications/{nameOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApplicationResult getApplication(@PathVariable("nameOrId") final String nameOrId, @RequestParam(required = false, defaultValue = "") final String[] filter) {
        final Application application = serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(nameOrId);
        return serviceContainer.applicationService().buildOpenAdom(application, filter);
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD')")
    @GetMapping(value = "/applications/{nameOrId}/configuration", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> getConfiguration(@PathVariable("nameOrId") final String nameOrId) {
        final Application application = serviceContainer.applicationService().getApplication(nameOrId);
        final UUID configFileId = application.getConfigFile();
        return getFile(nameOrId, configFileId);
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_APPLICATION_MODIFY')")
    @PostMapping(value = "/applications/{nameOrId}/configuration", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> changeConfiguration(@PathVariable("nameOrId") final String nameOrId,
                                                    @RequestParam("file") final MultipartFile file,
                                                    @RequestParam(name = "comment", defaultValue = "") final String comment) throws BadApplicationConfigurationException {

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

            final ReactiveProgression.ChangeApplicationProgression progression = new ReactiveProgression.ChangeApplicationProgression(0D, fluxSink);
            final UUID uuid = serviceContainer.applicationService().changeApplicationConfiguration(progression, nameOrId, dataFile, comment);
            progression.fluxSink().next(new ReactiveTypeResult(uuid));
            progression.complete();
        });
    }

    /**
     * Liste toutes les valeurs possibles pour un type de referenciel
     *
     * @param nameOrId l'id ou le nom de l'application
     * @return un tableau de chaine
     */
    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/applications/{nameOrId}/rightsRequest", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Get a rightsRequest with their description using search params")
    public ResponseEntity<GetRightsRequestResult> listRightsRequest(
            @PathVariable("nameOrId") final String nameOrId,
            @RequestParam(value = "params", required = false) final String params) {
        final RightsRequestInfos rightsRequestInfos = deserialiseRightsRequestQuery(params);
        final GetRightsRequestResult list = serviceContainer.rightsRequestService().findRightsRequest(nameOrId, rightsRequestInfos);
        return ResponseEntity.ok(list);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/applications/{nameOrId}/rightsRequest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createRightsRequest(@PathVariable("nameOrId") final String nameOrId,
                                                 @RequestBody final CreateRightsRequestRequest createRightsRequestRequest) {
        //CreateRightsRequestRequest createRightsRequestRequest = Strings.isNullOrEmpty(params) || "undefined".equals(params) ? null : deserialiseRightsRequestOrUUIDQuery(params);
        final UUID fileUUID = serviceContainer.rightsRequestService().createOrUpdate(createRightsRequestRequest, nameOrId);
        return ResponseEntity.ok(fileUUID);


    }

    /**
     * Liste les noms des types de referenciels disponible
     *
     * @param nameOrId l'id ou le nom de l'application
     * @return les noms triés selon l’ordre dans lequel il faut faire les imports (selon les dépendances entre
     * référentiels).
     */

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/references", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> listNameReferences(@PathVariable("nameOrId") String nameOrId) {
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
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/references/{refType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetReferenceResult> listDataForColumn(
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("refType") final String refType,
            @RequestParam final MultiValueMap<String, String> params) {
        final List<DataValue> list = serviceContainer.dataService().findReference(nameOrId, refType, params);

        final Map<String, Map<String, LineChecker>> checkedFormatColumns = serviceContainer.dataService().getFormatChecked(nameOrId, refType);
        Set<String> listOfReferenceIds = list.stream()
                .map(DataValue::getReferenceType)
                .collect(Collectors.toSet());
        final Map<Ltree, List<DataValue>> requiredReferencesValues = serviceContainer.dataService().getReferenceDisplaysById(serviceContainer.applicationService()
                .getApplicationOrApplicationAccordingToRights(nameOrId), listOfReferenceIds);
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

        final StreamingResponseBody streamResponseBody = out -> serviceContainer.dataService().getDataCsvStream(out, nameOrId, refType, language, false);
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
        final Application application = serviceContainer.applicationService().getApplication(nameOrId);
        final List<List<String>> result = serviceContainer.dataService().getDataColumn(application, refType, column);
        return ResponseEntity.ok(result);
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_WRITE')")
    @PostMapping(value = "/applications/{nameOrId}/data/{dataName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createData(
            HttpServletRequest request,
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("dataName") final String dataName,
            @RequestParam(value = "file", required = false) final MultipartFile file,
            @RequestParam(value = "params", required = false) final String params) throws JsonProcessingException {
        Locale locale = localeResolver.resolveLocale(request);
        DataFile dataFile = null;
        try {
            final File physicalFileOrCopy = getPhysicalFileOrCopy(file);
            dataFile = file == null ? null : new DataFile(physicalFileOrCopy, (long) file.getInputStream().available(), file.getOriginalFilename());
        } catch (IOException e) {
            throw OreSiIOException.ORE_SI_IOEXCEPTION_CANT_LOAD_FILE();
        }
        Future<DataVersioningResult> futureOfDdataVersioningResult = null;
        try {
            DataFile finalDataFile = dataFile;
            final SecurityContext context = SecurityContextHolder.getContext();
            futureOfDdataVersioningResult = executorService.submit(() -> {
                SecurityContextHolder.setContext(context);
                try {
                    return serviceContainer.versioningService().createData(
                            locale, nameOrId, dataName, finalDataFile, false, true);
                } catch (InvalidDatasetContentException invalidDatasetContentException) {
                    List<ValidationCheckResultRest> validations = invalidDatasetContentException.getErrors()
                            .stream()
                            .map(row -> {
                                long lineNumber = row.lineNumber();
                                return row.validationCheckResult().validationCheckResultToRest(row.lineNumber());
                            })
                            .toList();
                    Application application = serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(nameOrId);
                    String errorsToJson = new ObjectMapper()
                            .registerModule(new JavaTimeModule())
                            .writeValueAsString(validations);
                    String localizedApplicationName = application.getLocalizedLocalName(locale);
                    String localizedDataName = application.getLocalizedDataName(locale, dataName);
                    OreSiUser currentUser = serviceContainer.authenticationService().getCurrentUser();
                    serviceContainer.emailService().sendUpoadErrorsMail(
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
            });
            final DataVersioningResult dataVersioningResult = futureOfDdataVersioningResult.get();
            return ResponseEntity
                    .created(URI.create(dataVersioningResult.uri()))
                    .body(Map.of("id", dataVersioningResult.dataId().toString(), "referenceSynthesis", dataVersioningResult.dataSynthesis()));
        } catch (ExecutionException e) {
            throw switch (e.getCause()) {
                case OreSiTechnicalException oreSiTechnicalException -> oreSiTechnicalException;
                default -> throw new IllegalStateException("Unexpected value: " + e.getCause());
            };
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @GetMapping(value = "/applications/{nameOrId}/data", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ_SOME')")
    public ResponseEntity<List<String>> listData(@PathVariable("nameOrId") final String nameOrId) {
        final Application application = serviceContainer.applicationService().getApplication(nameOrId);
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
    @GetMapping(value = "/applications/{nameOrId}/additionalFiles/{additionalFileName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetAdditionalFilesResult> listAdditionalFilesNames(
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("additionalFileName") final String additionalFileName,
            @RequestParam(required = false) final String params) {
        AdditionalFilesInfos additionalFilesInfos = deserialiseAdditionalFilesInfos(params);
        if (additionalFilesInfos == null) {
            additionalFilesInfos = new AdditionalFilesInfos();
        }
        additionalFilesInfos.setFiletype(additionalFilesInfos.getFiletype() == null ? additionalFileName : additionalFilesInfos.getFiletype());
        final GetAdditionalFilesResult list = serviceContainer.additionalFileService().findAdditionalFile(nameOrId, additionalFilesInfos);
        return ResponseEntity.ok(list);
    }

    @GetMapping(value = "/applications/{nameOrId}/additionalFiles", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    @Operation(description = "Get a additionalFiles with their description using search params", summary = "Returns a zip containing additional files and their description")
    public ResponseEntity<StreamingResponseBody> getAdditionalFilesNamesZip(
            final HttpServletResponse response,
            //@ApiParam(required = true, value = "The name or uuid of an application")
            @PathVariable("nameOrId") final String nameOrId,
            //@ApiParam(required = false, value = "The parameters for filter the search")
            @RequestParam(value = "params", required = false) final String params) throws
            BadAdditionalFileParamsSearchException {
        final AdditionalFilesInfos additionalFilesInfos = Strings.isNullOrEmpty(params) || JS_UNDEFINED.equals(params) ? null : deserialiseAdditionalFilesInfos(params);

        final StreamingResponseBody streamResponseBody;
        if (AdditionalFileService.CHARTE.equals(Objects.requireNonNull(additionalFilesInfos).getFiletype())) {
            response.setHeader("Content-type", "application/pdf");
            response.setHeader("Content-Security-Policy", "frame-ancestors %s".formatted(frontendOrigin));
            streamResponseBody = out -> serviceContainer.additionalFileService().getCharte(out, response, nameOrId, additionalFilesInfos);
        } else {
            streamResponseBody = out -> {
                try (final ZipOutputStream zipOutputStream = new KeepAliveZipOutputStream(out)) {
                    serviceContainer.additionalFileService().getAdditionalFilesNamesZipStream(zipOutputStream, nameOrId, additionalFilesInfos);
                } catch (final IOException ioe) {
                    switch (OreSiResources.getDefaultLocale().getLanguage()) {
                        case FR -> log.error(IO_ERROR_FR, ioe);
                        case EN -> log.error(IO_ERROR_EN, ioe);
                        default -> log.error(IO_ERROR_EN, ioe);
                    }
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

    @DeleteMapping(value = "/applications/{nameOrId}/additionalFiles", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(description = "Delete a additionalFiles ", summary = "Delete additional file based on params search")
    public ResponseEntity<String> removeAdditionalFiles(
            //@ApiParam(required = true, value = "The name or uuid of an application")
            @PathVariable("nameOrId") final String nameOrId,
            //@ApiParam(required = false, value = "The parameters for filter the search")
            @RequestParam(value = "params", required = false) final String params) throws
            BadAdditionalFileParamsSearchException {
        final AdditionalFilesInfos additionalFilesInfos = Strings.isNullOrEmpty(params) || JS_UNDEFINED.equals(params) ? null : deserialiseAdditionalFilesInfos(params);
        final List<UUID> deletedFiles = serviceContainer.additionalFileService().deleteAdditionalFiles(nameOrId, additionalFilesInfos);
        if (deletedFiles != null && !deletedFiles.isEmpty()) {
            return ResponseEntity.ok(deletedFiles.stream().map(UUID::toString).collect(Collectors.joining(LIST_DELIMITER)));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value = "/applications/{nameOrId}/additionalFiles/{additionalFileName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UUID> createAdditionalFile(@PathVariable("nameOrId") final String nameOrId,
                                                     @PathVariable("additionalFileName") final String additionalFileName,
                                                     @RequestParam(value = "file", required = false) final MultipartFile file,
                                                     @RequestParam(value = "params") final String params) {
        final CreateAdditionalFileRequest createAdditionalFileRequest = Strings.isNullOrEmpty(params) || JS_UNDEFINED.equals(params) ? null : deserialiseAdditionalFileOrUUIDQuery(params);
        final UUID fileUUID = serviceContainer.additionalFileService().createOrUpdate(createAdditionalFileRequest, additionalFileName, nameOrId, file);
        return ResponseEntity.ok(fileUUID);


    }

    /**
     * export as JSON
     */
    @Operation(parameters = @Parameter(
            name = "downloadDatasetQuery",
            ref = "fr.inra.oresing.persistence.requestBuilder.datatype.DownloadDatasetQuery",
            explode = Explode.TRUE

    ), description = "Return an extraction of data of datatType 'dataName' of application 'nameOrId'")

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetDataResult> getAllDataJson(
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("dataType") final String dataName,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(examples = @ExampleObject(
                            name = "general case",
                            summary = "You can provide an optional json with componentSelects, componentFilters, componentOrderBy",
                            value =
                                    """
                                                    {
                                                        "componentSelects": [...],
                                                        "componentFilters": [...],
                                                        "componentOrderBy": [...]
                                                    }
                                            """)),
                    ref = "fr.inra.oresing.persistence.requestBuilder.datatype.DownloadDatasetQuery",
                    description = "A parameter that can reduce, filter or order the result"
            )
            @Parameter(
                    examples = {
                            @ExampleObject(
                                    name = "general case",
                                    ref = "fr.inra.oresing.model.data.read.DownloadDatasetQuery.class",
                                    value = """
                                            "General case. You can provide an optional json with componentSelects, rowIds, authorizationDescriptions, componentFilters, componentOrderBy\"""",
                                    description =
                                            """
                                                            {
                                                                "componentSelects": [...],
                                                                "componentFilters": [...],
                                                                "componentOrderBy": [...]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "reduce by select",
                                    ref = "fr.inra.oresing.model.data.read.DownloadDatasetQuery.class",
                                    value = """
                                            "Select by. You can provide an optional json with componentSelects\"""",
                                    description =
                                            """
                                                            {
                                                                "componentSelects": [sites, date]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "order by",
                                    ref = "fr.inra.oresing.model.data.read.DownloadDatasetQuery.class",
                                    value = """
                                            "Order by. You can provide an optional json with componentOrderBy\"""",
                                    description =
                                            """
                                                            {
                                                                "componentOrderBy": [
                                                                      {
                                                                         "componentKey": "espece",,
                                                                         "order": "ASC"
                                                                      }
                                                                  ]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "select by rowIds",
                                    ref = "fr.inra.oresing.model.data.read.DownloadDatasetQuery.class",
                                    value = """
                                            "Find by RowIds. You can provide an optional json with rowIds\"""",
                                    description =
                                            """
                                                            {
                                                                "rowIds" : [ "59ae5fec-8ed5-495b-b201-5475ea50906e"]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "select by naturalKeys",
                                    ref = "fr.inra.oresing.model.data.read.DownloadDatasetQuery.class",
                                    value = """
                                            "Find by naturalKeys. You can provide an optional json with naturalKeys\"""",
                                    description =
                                            """
                                                            {
                                                                "naturalKey" : ["projet_manche__oir__p1__03_01_1984__lpm"]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "select by hierarchicalKey",
                                    ref = "fr.inra.oresing.model.data.read.DownloadDatasetQuery.class",
                                    value = """
                                            "Find by naturalKeys. You can provide an optional json with naturalKeys\"""",
                                    description =
                                            """
                                                            {
                                                                "naturalKey" : ["pemKprojet_manche__oir__p1__03_01_1984__lpm"]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "select by submissionScope",
                                    ref = "fr.inra.oresing.model.data.authorizationDescriptions.class",
                                    value = """
                                            "Find by authorizations. You can provide an optional json with submissionScope\"""",
                                    description =
                                            """
                                                            {
                                                                    "authorizationDescriptions": [
                                                                              {
                                                                                "timeScope": {
                                                                                  "from": "1984-01-01",
                                                                                  "to": "1984-01-02"
                                                                                },
                                                                                "requiredAuthorizations": {
                                                                                    "projet": "projetKprojet_manche",
                                                                                    "sites": "type_de_sitesKplateforme.sitesKoir.sitesKoir__p1"
                                                                                  }
                                                                              },
                                                                              {
                                                                                "timeScope": {
                                                                                  "from": "1984-01-03",
                                                                                  "to": "1984-01-04"
                                                                                },
                                                                                "requiredAuthorizations": {
                                                                                    "projet": "projetKprojet_atlantique",
                                                                                    "sites": "type_de_sitesKplateforme.sitesKoir.sitesKoir__p1"
                                                                                  }
                                                                              }
                                                                   ]
                                                                 }
                                                    """),
                            @ExampleObject(
                                    name = "select filter by filter (like '%filter%')",
                                    ref = "fr.inra.oresing.model.data.authorizationDescriptions.class",
                                    value = """
                                            "Select by filter. You can provide an optional json with componentFilters\"""",
                                    description =
                                            """
                                                            {
                                                                "componentFilters": [
                                                                   {
                                                                     "componentKey": "bassin",
                                                                     "filters": ["nivelle"]
                                                                   },
                                                                   {
                                                                     "componentKey": "espece",,
                                                                     "filters": ["tr"]
                                                                   }
                                                                ]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "select filter by filter with regexp (~ '^[ao]m+)",
                                    ref = "fr.inra.oresing.model.data.authorizationDescriptions.class",
                                    value = """
                                            "Select by RegExp. You can provide an optional json with componentFilters. Can be apply only on text not for reference.\"""",
                                    description =
                                            """
                                                            {
                                                                "componentFilters": [
                                                                   "componentFilters": [
                                                                         {
                                                                           "componentKey": "espece",
                                                                           "filters": ["^[ao]m+"],
                                                                           "isRegExp": "true"
                                                                         }
                                                                ]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "select filter by filter date (With declared pattern)",
                                    ref = "fr.inra.oresing.model.data.authorizationDescriptions.class",
                                    value = """
                                            "Select by date. You can provide an optional json with componentFilters\"""",
                                    description =
                                            """
                                                            {
                                                                "componentFilters": [
                                                                   {
                                                                     "componentKey":"date",,
                                                                     "filters": ["12:45:00"]
                                                                   }
                                                                ]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "select filter by filter date by interval (With declared pattern)",
                                    ref = "fr.inra.oresing.model.data.authorizationDescriptions.class",
                                    value = """
                                            "Select by interval of date. You can provide an optional json with componentFilters\"""",
                                    description =
                                            """
                                                            {
                                                                "componentFilters": [
                                                                   {
                                                                         "componentKey": "date",
                                                                         "intervalsValues": [
                                                                            "from": "01/01/1984",
                                                                            "to": "04/01/1984"
                                                                         ]
                                                                       }
                                                                ]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "select filter by filter numeric)",
                                    ref = "fr.inra.oresing.model.data.authorizationDescriptions.class",
                                    value = """
                                            "Select by numeric. You can provide an optional json with componentFiltersé\"""",
                                    description =
                                            """
                                                            {
                                                                    "componentSelects": ["individusNumbervalue"],
                                                                    "componentFilters": [
                                                                              {
                                                                                "componentKey": "individusNumbervalue",
                                                                                "filters": [41]
                                                                              }
                                                                   ]
                                                                 }
                                                    """),
                            @ExampleObject(
                                    name = "select filter by filter numeric by interval)",
                                    ref = "fr.inra.oresing.model.data.authorizationDescriptions.class",
                                    value = """
                                            "Select by interval of numeric. You can provide an optional json with componentFilters\"""",
                                    description =
                                            """
                                                            {
                                                                "componentFilters": [
                                                                   {
                                                                     "componentKey": {
                                                                       "variable": "Nombre d'individus",
                                                                       "component": "value"
                                                                     },
                                                                     "intervalsValues":[ {
                                                                        "from": 25,
                                                                        "to": 30
                                                                     }]
                                                                   }
                                                                ]
                                                            }
                                                    """)
                    },
                    name = "downloadDatasetQuery",
                    description = "An object for reduce, filter and order result"
            )
            @RequestParam(value = "downloadDatasetQuery", required = false) final String params,
            @RequestParam(defaultValue = "false") boolean loadExample) {

        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery downloadDatasetQuery =
                deserialiseParamDownloadDatasetQuery(params, nameOrId, dataName, loadExample);

        final Locale locale = Optional.of(downloadDatasetQuery)
                .map(fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery::getLanguage)
                .map(Locale::of)
                .orElseGet(OreSiResources::getDefaultLocale);
        final Set<String> orderedVariables = buildOrderedVariables(nameOrId, dataName);
        final List<DataRow> data = serviceContainer.dataService().findData(downloadDatasetQuery);
        final List<FilterList> filterLists = serviceContainer.dataService()
                .filterList(downloadDatasetQuery.application(), downloadDatasetQuery.dataName())
                .collect(Collectors.toList()).block();
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
        final Map<String, Map<String, LineCheckerResult>> checkedFormatcomponents = serviceContainer.dataService().getCheckedFormatComponents(nameOrId, dataName);
        final List<DataRowResult> dataRowResults = data.stream()
                .map(dataRow -> DataRowResult.of(
                        dataRow,
                        variables,
                        locale.getLanguage()
                ))
                .toList();
        Map<String, List<GetGrantableResult.ReferenceScope>> referenceScopes = serviceContainer.authorizationService().getAuthorizationScopes(application, MenuType.submission);

        return ResponseEntity.ok(new GetDataResult(
                downloadDatasetQuery.patternDefinitionCount(),
                variables,
                dataRowResults,
                filterLists,
                checkedFormatcomponents,
                referenceScopes));
    }

    /**
     * export as JSON
     */
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DELETE_FILE')")
    @DeleteMapping(value = "/applications/{nameOrId}/data/{dataType}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> deleteData(
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("dataType") final String dataName,
            @RequestParam(value = "downloadDatasetQuery", required = false) final String params) {
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery downloadDatasetQuery = deserialiseParamDownloadDatasetQuery(params, nameOrId, dataName, false);
        final List<UUID> deletedData = serviceContainer.dataService().deleteData(downloadDatasetQuery);
        return ResponseEntity.ok(deletedData.stream().map(UUID::toString).collect(Collectors.joining(LIST_DELIMITER)));

    }

    private Set<String> buildOrderedVariables(final String nameOrId, final String dataName) {
        final Submission.SubmissionScope authorization = serviceContainer.applicationService()
                .getApplication(nameOrId)
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
     * export as CSV
     */
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}/zip", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> getAllDataZip(
            final HttpServletResponse response,
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("dataType") final String dataType,
            @RequestParam(value = "downloadDatasetQuery", required = false) final String params) throws InterruptedException, ExecutionException, FileNotFoundException {

        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery downloadDatasetQuery = deserialiseParamDownloadDatasetQuery(params, nameOrId, dataType, false);

        AtomicReference<OreSiUser> user = new AtomicReference<>();
        AtomicReference<Path> zipFile = new AtomicReference<>();
        SecurityContext securityContext = SecurityContextHolder.getContext();
        SecurityContextHolder.setContext(securityContext);
        String fileName = DATA_ZIP.formatted(nameOrId, LocalDateTime.now().format(TIMESTAMP_FORMATER));

        final ResponseEntity.BodyBuilder header = ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + fileName + "\".zip");

        AtomicReference<Path> tempDirectory = new AtomicReference<>();
        try {
            executorService.submit(() -> {
                try {
                    SecurityContextHolder.setContext(securityContext);
                    user.set(userRepository.findById(OreSiApiRequestContext.getRequestClient().id()));
                    tempDirectory.set(Files.createTempDirectory(Paths.get(TMP), fileName));
                    serviceContainer.dataService().buildDataZip(tempDirectory.get(), downloadDatasetQuery);
                    Path finalTempZipDirectory = tempDirectory.get();
                    try {
                        zipFile.set(finalTempZipDirectory.resolveSibling(finalTempZipDirectory.getFileName() + ".zip"));
                        ZipUtils.zipDirectory(finalTempZipDirectory, zipFile.get()); //
                    } catch (Exception e) {
                        log.error(EMAIL_ERROR, e);
                    } finally {
                        removeRepository(finalTempZipDirectory);
                    }

                } catch (Exception e) {
                    if (zipFile != null) {
                        try {
                            addErrorFileToZip(zipFile.get(), e);
                        } catch (IOException ioe) {
                            log.error(IO_ADDING_ERROR, ioe);
                        }
                    }
                    throw new OreSiTechnicalException(IO_WRITING_CSV_ERROR, e);
                }
            }).get();
            executorService.submit(() -> {
                SecurityContextHolder.setContext(securityContext);
                try {
                    serviceContainer.dataService().sendZipLinkByMail(zipFile.get(), downloadDatasetQuery, user.get());
                } catch (Exception e) {
                    log.error(EMAIL_ERROR, e);
                }
            }).get();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } catch (ExecutionException e) {
            throw new RuntimeException(e);
        }
        final Path source = zipFile.get();
        StreamingResponseBody body = outputStream -> {
            Files.copy(source, outputStream);
            outputStream.flush(); // Important pour garantir le flush
            Files.deleteIfExists(source); // Nettoyage juste après la copie
        };
        return header

                .body(body);

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

    private fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery deserialiseParamDownloadDatasetQuery(
            final String params, final String applicationNameOrID, final String dataType, boolean loadExample) {
        try {
            final DownloadDatasetQuery downloadDatasetQuery = params != null ? new JsonRowMapper<DownloadDatasetQuery>().toObject(params, DownloadDatasetQuery.class) : new DownloadDatasetQuery();
            if (loadExample) {
                downloadDatasetQuery.setLimit(500L);
            }
            final Application application = serviceContainer.applicationService().getApplication(applicationNameOrID);
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
        } catch (final Exception e) {
            throw new BadDownloadDatasetQuery(e.getMessage());
        }
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/synthesis/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSynthesis(@PathVariable("nameOrId") final String nameOrId,
                                          @PathVariable("dataType") final String dataType) {
        try {
            final Map<String, List<OreSiSynthesis>> synthesis = serviceContainer.synthesisService().getSynthesis(nameOrId, dataType);
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

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/synthesis/{dataType}/{variable}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSynthesis(@PathVariable("nameOrId") final String nameOrId,
                                          @PathVariable("dataType") final String dataType,
                                          @PathVariable("variable") final String variable) {
        try {
            final Map<String, List<OreSiSynthesis>> synthesis = serviceContainer.synthesisService().getSynthesis(nameOrId, dataType, variable);
            final String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s/%s", nameOrId, dataType, variable), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(synthesis);
        } catch (final InvalidDatasetContentException e) {
            final List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_APPLICATION_MODIFY')")
    @PutMapping(value = "/applications/{nameOrId}/synthesis/{dataType}/{variable}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> buidSynthesis(@PathVariable("nameOrId") final String nameOrId,
                                           @PathVariable("dataType") final String dataType,
                                           @PathVariable("variable") final String variable) {
        try {
            final Map<String, List<OreSiSynthesis>> synthesis = serviceContainer.synthesisService().buildSynthesis(nameOrId, dataType, variable);
            final String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s%s", nameOrId, dataType, variable != null ? "/" + variable : ""), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(synthesis);
        } catch (final InvalidDatasetContentException e) {
            final List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_APPLICATION_MODIFY')")
    @PutMapping(value = "/applications/{nameOrId}/synthesis/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> buidSynthesis(@PathVariable("nameOrId") final String nameOrId,
                                           @PathVariable("dataType") final String dataType) {
        return buidSynthesis(nameOrId, dataType, null);
    }


    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_APPLICATION_MODIFY')")
    @GetMapping(value = "/applications/{nameOrId}/upload-bundle")
    public ResponseEntity<?> getUploadBundle(
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

        executorService.submit(() -> {
            Path tempZipDirectory = null;
            try {
                SecurityContextHolder.setContext(securityContext);
                tempZipDirectory = Files.createTempDirectory(Paths.get(TMP), fileName);

                BuildBundleReport report = null;
                try {
                    report = serviceContainer.dataService()
                            .writeUploadBundle(instanceUrl, nameOrId, withData, locale, tempZipDirectory);
                } catch (Exception e) {
                    log.error(IO_ERROR_WRITE, e);
                }

                if (report != null && report.referentielsEnErreur().isEmpty()) {
                    try {
                        Path zipFile = tempZipDirectory.resolveSibling(tempZipDirectory.getFileName() + ".zip");
                        ZipUtils.zipDirectory(tempZipDirectory, zipFile);
                        serviceContainer.dataService().sendZipLinkByMail(zipFile, report, user);
                    } catch (Exception e) {
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

            } catch (Exception e) {
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

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_DOWNLOAD_BUNDLE')")
    @PostMapping(value = "/applications/{nameOrId}/download-bundle", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> uploadBundle(
            HttpServletRequest request,
            @PathVariable String nameOrId,
            @RequestParam("file") MultipartFile zipBundle) {

        Locale locale = localeResolver.resolveLocale(request);
        final String origin = request.getHeader("Origin");
        final OreSiUser currentUser = serviceContainer.authenticationService().getCurrentUser();
        final SecurityContext context = SecurityContextHolder.getContext();
        return Flux.create(sink -> {
            executorService.submit(() -> {
                SecurityContextHolder.setContext(context);
                File zipFile = null;
                try {
                    zipFile = getPhysicalFileOrCopy(zipBundle);
                } catch (IOException e) {
                    sink.error(new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e));
                }
                sink.next(new ReactiveTypeProgress(0L));
                final Application application = serviceContainer.applicationService().getApplication(nameOrId);

                BundleReport rapport = new BundleReport(locale, origin, application);

                try {
                    ObjectMapper mapper = new ObjectMapper();
                    File finalZipFile = zipFile;
                    AtomicReference<Map<String, List<String>>> manifest = new AtomicReference<>();
                    serviceContainer.dataService().readEntry(zipFile, DataService.MANIFEST_JSON,
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
                    serviceContainer.dataService().readEntry(zipFile, DataService.REFERENCES_JSON,
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
                    final RegisterReactiveResult registerReactiveResult = new RegisterReactiveResult(sink, countFiles, rapport);
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
                } catch (StreamReadException e) {
                    sink.error(e);
                    throw new RuntimeException(e);
                } catch (DatabindException e) {
                    sink.error(e);
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    sink.error(e);
                    throw new RuntimeException(e);
                }
                sink.complete();
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
                                    executorService.submit(() -> {
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
                        serviceContainer.dataService().sendZipLinkByMail(
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
                serviceContainer.dataService().readEntry(finalZipFile, "%s/%s".formatted(dataName, fileName),
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
                                final DataVersioningResult data = serviceContainer.versioningService().createData(
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
            application.set(serviceContainer.applicationService().getApplication(nameOrId));
        } catch (Exception e) {
            try {
                serviceContainer.dataService().readEntry(zipFile, DataService.MANIFEST_JSON,
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
                            application.set(serviceContainer.applicationService().getApplication(nameOrId));
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