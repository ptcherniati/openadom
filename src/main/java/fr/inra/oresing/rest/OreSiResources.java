package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.BinaryFileDataset;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.ApplicationInformation;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.RefsLinkedToValue;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.read.ouput.KeepAliveZipOutputStream;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQueryOnlyMetadata;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.application.BadLabelNameException;
import fr.inra.oresing.domain.exceptions.authentication.authentication.NotApplicationCanDeleteRightsException;
import fr.inra.oresing.domain.exceptions.binaryfile.binaryfile.BadFileOrUUIDQuery;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import fr.inra.oresing.domain.exceptions.data.data.DeleteOnrepositoryApplicationNotAllowedException;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;
import fr.inra.oresing.rest.application.ApplicationService;
import fr.inra.oresing.rest.binaryFile.BinaryFileService;
import fr.inra.oresing.rest.data.VersioningService;
import fr.inra.oresing.rest.data.publication.*;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
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
import fr.inra.oresing.rest.reactive.ReactiveProgression;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeResult;
import fr.inra.oresing.rest.rightsrequest.BadRightsRequestInfosQuery;
import fr.inra.oresing.rest.rightsrequest.BadRightsRequestOrUUIDQuery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.Explode;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.io.output.TeeOutputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;
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
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@RestController
@RequestMapping("/api/v1")
public class OreSiResources {
    public static Locale getDefaultLocale(){
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
        String acceptLanguage = request.getHeader("Accept-Language");

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

    public static final String DATA_SERVICE_PATH_PATTERN = "/applications/%s/data/%s";


    @Autowired
    private OreSiRepository repo;

    @Autowired
    private JsonRowMapper jsonRowMapper;

    @Autowired
    private ApplicationService applicationService;

    @Autowired
    private VersioningService versioningService;

    @Autowired
    private BinaryFileService binaryFileService;

    @Autowired
    private OreSiService service;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OreSiApiRequestContext request;

    private static Flux<ReactiveResult> buildFluxRequestJDJson(final Consumer<FluxSink<ReactiveResult>> fluxSink) {
        return Flux.create(fluxSink);
    }

    private static CreateRightsRequestRequest deserialiseRightsRequestOrUUIDQuery(final String params) {
        try {
            final CreateRightsRequestRequest createRightsRequestRequest = params != null && !"undefined".equals(params) ? new ObjectMapper().readValue(params, CreateRightsRequestRequest.class) : null;
            return createRightsRequestRequest;
        } catch (final IOException e) {
            throw new BadRightsRequestOrUUIDQuery(e.getMessage());
        }
    }

    private static RightsRequestInfos deserialiseRightsRequestQuery(final String params) {
        try {
            final RightsRequestInfos createRightsRequestInfos = params != null && !"undefined".equals(params) ? new ObjectMapper().readValue(params, RightsRequestInfos.class) : null;
            return createRightsRequestInfos;
        } catch (final IOException e) {
            throw new BadRightsRequestInfosQuery(e.getMessage());
        }
    }

    private static CreateAdditionalFileRequest deserialiseAdditionalFileOrUUIDQuery(final String params) {
        try {
            final CreateAdditionalFileRequest createAdditionalFileRequest =
                    params != null && !"undefined".equals(params) ?
                            new JsonRowMapper<CreateAdditionalFileRequest>().readValue(params, CreateAdditionalFileRequest.class) :
                            null;
            return createAdditionalFileRequest;
        } catch (final IOException e) {
            throw new BadFileOrUUIDQuery(e.getMessage());
        }
    }

    private static AdditionalFilesInfos deserialiseAdditionalFilesInfos(final String params) {
        try {
            final AdditionalFilesInfos additionalFilesInfos = params != null && !"undefined".equals(params) ? new ObjectMapper().readValue(params, AdditionalFilesInfos.class) : null;
            return additionalFilesInfos;
        } catch (final IOException e) {
            throw new BadFileOrUUIDQuery(e.getMessage());
        }
    }

    @DeleteMapping(value = "/applications/{name}/file/{id}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> removeFile(@PathVariable("name") final String applicationName,
                                             @PathVariable("id") final UUID id) {
        Application application = applicationService.getApplication(applicationName);
        StoreFile storeFile = versioningService.getStoreFile(application,
                null,
                """
                        {"fileid": "%s"}""".formatted(id), null);
        Optional<UUID> fileId = Optional.ofNullable(storeFile)
                .map(State::params)
                .map(FileOrUUID::fileid);
        if (fileId.isEmpty()) {
            throw new SiOreIllegalArgumentException(SiOreIllegalArgumentException.NO_FILE_To_DELETE, Map.of("fileId", id));
        }
        Boolean canDelete = Optional.ofNullable(storeFile)
                .map(StoreFile::builder)
                .map(AuthorizationPublicationService::getAuthorizations)
                .map(AuthorizationForUser::canDelete)
                .orElse(false);
        if (!canDelete) {
            String dataName = Optional.ofNullable(storeFile)
                    .map(StoreFile::builder)
                    .map(AuthorizationPublicationService::getDataName)
                    .orElse("notFoundDataname");
            throw new NotApplicationCanDeleteRightsException(applicationName, dataName);
        }
        DataVersioningResult dataVersioningResult = versioningService.unPublishVersionBeforeDelete(applicationName, id);
        Optional<UUID> uuid = binaryFileService.removeFile(application, id);
        if (uuid.isPresent()) {
            return ResponseEntity.ok(id.toString());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/applications/{nameOrId}/filesOnRepository/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<BinaryFile>> getFilesOnRepository(@PathVariable("nameOrId") final String nameOrId,
                                                                 @PathVariable("dataType") final String dataType,
                                                                 @RequestParam("repositoryId") final String repositoryId) {
        final BinaryFileDataset binaryFileDataset = BinaryFileService.deserialiseBinaryFileDatasetQuery(dataType, repositoryId);
        final List<BinaryFile> files = service.getFilesOnRepository(nameOrId, dataType, binaryFileDataset, false);
        return ResponseEntity.ok(files);
    }

    @GetMapping(value = "/applications/{name}/file/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getFile(@PathVariable("name") final String name, @PathVariable("id") final UUID id) {
        final Optional<BinaryFile> optionalBinaryFile = binaryFileService.getFileWithData(name, id);
        if (optionalBinaryFile.isPresent()) {
            final BinaryFile binaryFile = optionalBinaryFile.get();
            final HttpHeaders headers = new HttpHeaders();
            headers.setContentLength(binaryFile.getSize());
            headers.set("Content-disposition", "attachment;filename=" + binaryFile.getName());
            return new ResponseEntity(binaryFile.getFileData(), headers, HttpStatus.OK);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/applications", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> getApplications(@RequestParam(required = false, defaultValue = "") final String[] filter) {
        final List<ApplicationInformation> filters = Arrays.stream(filter)
                .map(s -> ApplicationInformation.valueOf(s))
                .collect(Collectors.toList());
        return buildFluxRequestJDJson(fluxSink -> {
            final ReactiveProgression.GetApplicationProgression progression = new ReactiveProgression.GetApplicationProgression(0L, fluxSink);
            service.getApplications(progression, filters);
        });
    }

    @PostMapping(value = "/validate-configuration", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> validateConfiguration(@RequestParam("file") final MultipartFile file) {
        try {
            return buildFluxRequestJDJson(fluxSink -> {
                final ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(0l, fluxSink);
                final Application application = service.validateConfiguration(progression, file);
                fluxSink.next(new ReactiveTypeResult(application));
                progression.complete();
            });
        } catch (Exception e) {
            throw e;
        }
    }

    @PostMapping(value = "/applications/{name}", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Parameter(examples = @ExampleObject(
            name = "fichier de configuration",
            description = "<a href= 'https://anaee-dev.pages.mia.inra.fr/si-ore-v2/schemaExample.yaml'>Fichier d'example</a>"
    ))
    public Flux<ReactiveResult> createApplication(@PathVariable("name") final String name,
                                                  @RequestParam(name = "comment", defaultValue = "") final String comment,
                                                  @RequestParam("file") final MultipartFile file) throws BadApplicationConfigurationException {

        final Application application;
        try {
            application = applicationService.getApplicationOrApplicationAccordingToRights(name);
            log.info("Modification de l'application %s".formatted(name));
            return changeConfiguration(name, file, comment);
        } catch (final Exception e) {
            log.info("Création de l'application %s".formatted(name));
        }

        if (!RelationalService.IdentifierTest.identifierForApplicationName(name)) {
            //TODO test à faire
            throw new BadLabelNameException(BadLabelNameException.LabelType.APPLICATION, name);
        }
        return buildFluxRequestJDJson(fluxSink -> {
            final ReactiveProgression.CreateApplicationProgression progression = new ReactiveProgression.CreateApplicationProgression(0L, fluxSink);
            try {
                service.createApplication(progression, name, file, comment);
            } catch (Exception technicalException) {
                fluxSink.error(technicalException);
            }
        });
    }

    @GetMapping(value = "/applications/{nameOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApplicationResult getApplication(@PathVariable("nameOrId") final String nameOrId, @RequestParam(required = false, defaultValue = "") final String[] filter) {
        final Application application = applicationService.getApplicationOrApplicationAccordingToRights(nameOrId);
        return service.buildOpenAdom(application, filter);
    }

    @GetMapping(value = "/applications/{nameOrId}/configuration", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getConfiguration(@PathVariable("nameOrId") final String nameOrId) {
        final Application application = applicationService.getApplication(nameOrId);
        final UUID configFileId = application.getConfigFile();
        return getFile(nameOrId, configFileId);
    }

    @PostMapping(value = "/applications/{nameOrId}/configuration", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> changeConfiguration(@PathVariable("nameOrId") final String nameOrId,
                                                    @RequestParam("file") final MultipartFile file,
                                                    @RequestParam(name = "comment", defaultValue = "") final String comment) throws BadApplicationConfigurationException {

        return buildFluxRequestJDJson(fluxSink -> {
            if (file.isEmpty()) {
                fluxSink.error(new IllegalArgumentException("EmptyFile"));
            }
            final ReactiveProgression.ChangeApplicationProgression progression = new ReactiveProgression.ChangeApplicationProgression(0D, fluxSink);
            final UUID uuid = service.changeApplicationConfiguration(progression, nameOrId, file, comment);
            progression.fluxSink().next(new ReactiveTypeResult(uuid));
            progression.complete();
        });
    }

    /**
     * Liste toutes les valeurs possibles pour un type de referenciel
     *
     * @param nameOrId l'id ou le nom de l'application
     * @param params
     * @return un tableau de chaine
     */
    @GetMapping(value = "/applications/{nameOrId}/rightsRequest", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Get a rightsRequest with their description using search params")
    public ResponseEntity<GetRightsRequestResult> listRightsRequest(
            @PathVariable("nameOrId") final String nameOrId,
            @RequestParam(value = "params", required = false) final String params) {
        final RightsRequestInfos rightsRequestInfos = deserialiseRightsRequestQuery(params);
        final GetRightsRequestResult list = service.findRightsRequest(nameOrId, rightsRequestInfos);
        return ResponseEntity.ok(list);
    }

    @PostMapping(value = "/applications/{nameOrId}/rightsRequest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createRightsRequest(@PathVariable("nameOrId") final String nameOrId,
                                                 @RequestBody final CreateRightsRequestRequest createRightsRequestRequest) {
        //CreateRightsRequestRequest createRightsRequestRequest = Strings.isNullOrEmpty(params) || "undefined".equals(params) ? null : deserialiseRightsRequestOrUUIDQuery(params);
        final UUID fileUUID = service.createOrUpdate(createRightsRequestRequest, nameOrId);
        return ResponseEntity.ok(fileUUID);


    }

    /**
     * Liste les noms des types de referenciels disponible
     *
     * @param nameOrId l'id ou le nom de l'application
     * @return les noms triés selon l’ordre dans lequel il faut faire les imports (selon les dépendances entre
     * référentiels).
     */
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
     * @param params
     * @return un tableau de chaine
     */
    @GetMapping(value = "/applications/{nameOrId}/references/{refType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetReferenceResult> listDataForColumn(
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("refType") final String refType,
            @RequestParam final MultiValueMap<String, String> params) {
        final List<DataValue> list = service.findReference(nameOrId, refType, params);

        final Map<String, Map<String, LineChecker>> checkedFormatColumns = service.getFormatChecked(nameOrId, refType);
        Set<String> listOfReferenceIds = list.stream()
                .map(DataValue::getReferenceType)
                .collect(Collectors.toSet());
        final Map<Ltree, List<DataValue>> requiredReferencesValues = service.getReferenceDisplaysById(applicationService.getApplicationOrApplicationAccordingToRights(nameOrId), listOfReferenceIds);
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

    @GetMapping(value = "/applications/{nameOrId}/data/{refType}/csv", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> listDataCsv(
            final HttpServletResponse response,
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("refType") final String refType) {
        Locale language = OreSiResources.getDefaultLocale();

        final StreamingResponseBody streamResponseBody = out -> {
            service.getDataCsvStream(out, nameOrId, refType, language);

        };
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);
        response.setHeader("Content-Disposition", String.format("attachment; filename=%s.csv", refType));
        response.addHeader("Pragma", "no-cache");
        response.addHeader("Expires", "0");
        response.setContentType(MediaType.APPLICATION_OCTET_STREAM_VALUE);

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(streamResponseBody);
    }

    @GetMapping(value = "/applications/{nameOrId}/data/{refType}/{column}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<List<String>>> listDataForColumn(@PathVariable("nameOrId") final String nameOrId, @PathVariable("refType") final String refType, @PathVariable("column") final String column) {
        final Application application = applicationService.getApplication(nameOrId);
        final List<List<String>> result = service.getDataColumn(application, refType, column);
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/applications/{nameOrId}/data/{dataName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createData(
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("dataName") final String dataName,
            @RequestParam(value = "file", required = false) final MultipartFile file,
            @RequestParam(value = "params", required = false) final String params) throws IOException {
        DataVersioningResult dataVersioningResult = versioningService.createData(nameOrId, dataName, file, params);
        return ResponseEntity.created(URI.create(dataVersioningResult.uri())).body(Map.of("id", dataVersioningResult.dataId().toString(), "referenceSynthesis", dataVersioningResult.dataSynthesis()));
    }

    @GetMapping(value = "/applications/{nameOrId}/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> listData(@PathVariable("nameOrId") final String nameOrId) {
        final Application application = applicationService.getApplication(nameOrId);
        List<String> allDataNames = application.getAllDataNames();


        return ResponseEntity.ok(allDataNames);
    }

    /**
     * Liste toutes les valeurs possibles pour un type de referenciel
     *
     * @param nameOrId           l'id ou le nom de l'application
     * @param additionalFileName le type du referenciel
     * @param params
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
        final GetAdditionalFilesResult list = service.findAdditionalFile(nameOrId, additionalFilesInfos);
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
        final AdditionalFilesInfos additionalFilesInfos = Strings.isNullOrEmpty(params) || "undefined".equals(params) ? null : deserialiseAdditionalFilesInfos(params);

        final StreamingResponseBody streamResponseBody;
        if ("__charte__".equals(additionalFilesInfos.getFiletype())) {
            response.setHeader("Content-type", "application/pdf");
            response.setHeader("Accept-Ranges", "bytes");
            streamResponseBody = out -> {
                service.getCharte(out, response, nameOrId, additionalFilesInfos);
            };
        } else {
            streamResponseBody = out -> {
                try (final ZipOutputStream zipOutputStream = new KeepAliveZipOutputStream(out)) {
                    service.getAdditionalFilesNamesZipStream(zipOutputStream, nameOrId, additionalFilesInfos);
                } catch (final IOException ioe) {
                    switch (OreSiResources.getDefaultLocale().getLanguage()) {
                        case "fr" -> log.error("Exception lors de la lecture et du streaming de données {} ", ioe);
                        case "en" -> log.error("Exception while reading and streaming data {} ", ioe);
                        case null, default -> log.error("Exception while reading and streaming data {} ", ioe);
                    }
                }
            };
            response.setHeader("Content-Disposition", "attachment; filename=additionalFiles.zip");
            response.setHeader("Content-type", "application/zip;charset=UTF-8");
        }
        response.addHeader("Pragma", "no-cache");
        response.addHeader("Expires", "0");

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
            IOException, BadAdditionalFileParamsSearchException {
        final AdditionalFilesInfos additionalFilesInfos = Strings.isNullOrEmpty(params) || "undefined".equals(params) ? null : deserialiseAdditionalFilesInfos(params);
        final List<UUID> deletedFiles = service.deleteAdditionalFiles(nameOrId, additionalFilesInfos);
        if (deletedFiles != null && !deletedFiles.isEmpty()) {
            return ResponseEntity.ok(deletedFiles.stream().map(UUID::toString).collect(Collectors.joining(",")));
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(value = "/applications/{nameOrId}/additionalFiles/{additionalFileName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<UUID> createAdditionalFile(@PathVariable("nameOrId") final String nameOrId,
                                                     @PathVariable("additionalFileName") final String additionalFileName,
                                                     @RequestParam(value = "file", required = false) final MultipartFile file,
                                                     @RequestParam(value = "params") final String params) {
        final CreateAdditionalFileRequest createAdditionalFileRequest = Strings.isNullOrEmpty(params) || "undefined".equals(params) ? null : deserialiseAdditionalFileOrUUIDQuery(params);
        final UUID fileUUID = service.createOrUpdate(createAdditionalFileRequest, additionalFileName, nameOrId, file);
        return ResponseEntity.ok(fileUUID);


    }

    /**
     * export as JSON
     *
     * @param nameOrId
     * @param dataName
     * @param params
     * @return
     */
    @Operation(parameters = @Parameter(
            name = "downloadDatasetQuery",
            ref = "fr.inra.oresing.persistence.requestBuilder.datatype.DownloadDatasetQuery",
            required = false,
            explode = Explode.TRUE

    ), description = "Return an extraction of data of datatType 'dataName' of application 'nameOrId'")

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
                                    value = "General case. You can provide an optional json with componentSelects, rowIds, authorizationDescriptions, componentFilters, componentOrderBy",
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
                                    value = "Select by. You can provide an optional json with componentSelects",
                                    description =
                                            """
                                                            {
                                                                "componentSelects": [sites, date]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "order by",
                                    ref = "fr.inra.oresing.model.data.read.DownloadDatasetQuery.class",
                                    value = "Order by. You can provide an optional json with componentOrderBy",
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
                                    value = "Find by RowIds. You can provide an optional json with rowIds",
                                    description =
                                            """
                                                            {
                                                                "rowIds" : [ "59ae5fec-8ed5-495b-b201-5475ea50906e"]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "select by naturalKeys",
                                    ref = "fr.inra.oresing.model.data.read.DownloadDatasetQuery.class",
                                    value = "Find by naturalKeys. You can provide an optional json with naturalKeys",
                                    description =
                                            """
                                                            {
                                                                "naturalKey" : ["projet_manche__oir__p1__03_01_1984__lpm"]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "select by hierarchicalKey",
                                    ref = "fr.inra.oresing.model.data.read.DownloadDatasetQuery.class",
                                    value = "Find by naturalKeys. You can provide an optional json with naturalKeys",
                                    description =
                                            """
                                                            {
                                                                "naturalKey" : ["pemKprojet_manche__oir__p1__03_01_1984__lpm"]
                                                            }
                                                    """),
                            @ExampleObject(
                                    name = "select by submissionScope",
                                    ref = "fr.inra.oresing.model.data.authorizationDescriptions.class",
                                    value = "Find by authorizations. You can provide an optional json with submissionScope",
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
                                    value = "Select by filter. You can provide an optional json with componentFilters",
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
                                    value = "Select by RegExp. You can provide an optional json with componentFilters. Can be apply only on text not for reference.",
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
                                    value = "Select by date. You can provide an optional json with componentFilters",
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
                                    value = "Select by interval of date. You can provide an optional json with componentFilters",
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
                                    value = "Select by numeric. You can provide an optional json with componentFilters",
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
                                    value = "Select by interval of numeric. You can provide an optional json with componentFilters",
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
            @RequestParam(defaultValue = "false") boolean onlyMetadata) {

        Application application = applicationService.getApplication(nameOrId);
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery downloadDatasetQuery =
                deserialiseParamDownloadDatasetQuery(params, nameOrId, dataName, onlyMetadata);

        final Locale locale = Optional.ofNullable(downloadDatasetQuery)
                .map(fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery::getLanguage)
                .map(Locale::new)
                .orElseGet(OreSiResources::getDefaultLocale);
        final Set<String> orderedVariables = buildOrderedVariables(nameOrId, dataName);
        final List<DataRow> list = onlyMetadata ? List.of() : service.findData(downloadDatasetQuery);
        Predicate<ComponentDescription> isHidden = componentDescription -> componentDescription.isHiddenOrHasLangRestriction(downloadDatasetQuery.getLanguage());
        Predicate<String> isHiddenComponent = componentName -> application.findComponentOfData(dataName, componentName).stream()
                .anyMatch(isHidden);
        Predicate<String> isNotVariable = variable -> variable.startsWith("_");
        final ImmutableSet<String> variables = list.stream()
                .limit(1)
                .map(DataRow::getValues)
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
        final Long totalRows = list.stream().limit(1).map(dataRow -> dataRow.getTotalRows()).findFirst().orElse(-1L);
        final Map<String, Map<String, LineCheckerResult>> checkedFormatcomponents = service.getCheckedFormatComponents(nameOrId, dataName);
        Set<String> listOfDataIds = list.stream()
                .map(DataRow::getRowId)
                .flatMap(List::stream)
                .collect(Collectors.toSet());
        final Map<Ltree, List<DataValue>> requiredreferencesValues = service.getReferenceDisplaysById(applicationService.getApplication(nameOrId), listOfDataIds);
        Map<String, LineCheckerResult> lineCheckers = checkedFormatcomponents.get(ReferenceType.class.getSimpleName());
        if (MapUtils.isNotEmpty(lineCheckers)) {
            for (final Map.Entry<String, LineCheckerResult> lineCheckerEntry : lineCheckers.entrySet()) {
                final String componentKey = lineCheckerEntry.getKey();
                final LineCheckerResult referenceLineChecker = lineCheckerEntry.getValue();

                ((ReferenceType) referenceLineChecker.fieldTypeForOne()).getReferenceValues().entrySet().stream()
                        .filter(e -> requiredreferencesValues.containsKey(e.getKey().naturalKey()))
                        .forEach(e ->
                                list.stream()
                                        .limit(1)
                                        .forEach(dataRow -> {
                                            final Map<String, RefsLinkedToValue> refsLinkedToValues = dataRow.getRefsLinkedTo().get(((ReferenceType) referenceLineChecker.fieldTypeForOne()).getRefType());
                                            if (refsLinkedToValues != null && refsLinkedToValues.containsKey(lineCheckerEntry.getKey())) {
                                                final Set<UUID> refIds = refsLinkedToValues
                                                        .get(lineCheckerEntry.getKey()).uuids();
                                                requiredreferencesValues.values().stream()
                                                        .filter(k -> refIds != null)
                                                        .map(l ->
                                                                l.stream()
                                                                        .filter(referenceValue -> refIds.contains(referenceValue.getId()))
                                                                        .findFirst())
                                                        .filter(Optional::isPresent)
                                                        .map(Optional::get)
                                                        .findFirst()
                                                        .ifPresent(referenceValue -> {
                                                            lineCheckers.put(
                                                                    componentKey,
                                                                    new LineCheckerResultDisplay<>(
                                                                            (DefaultLineCheckerResult) referenceLineChecker,
                                                                            referenceValue
                                                                    ));
                                                        });
                                            }
                                        })
                        );
            }
        } else {
            //TODO on est dans le cas ou aucun computationChecker reference n'est décrit : authorizationscope  n'est pas un referentiel
        }
        DataRepositoryWithBuffer dataRepositoryWithBuffer = service.getNewDataRepositoryWithBuffer(application);

        final List<DataRowResult> dataRowResults = list.stream()
                .map(dataRow -> DataRowResult.of(
                        dataRow,
                        variables,
                        locale.getLanguage(),
                        dataRepositoryWithBuffer))
                .collect(Collectors.toList());
        final Map<String, String> referenceTypeForReferencingColumns =
                Optional.ofNullable(checkedFormatcomponents.get(ReferenceType.class.getSimpleName()))
                        .map(checkedFormatColumn -> checkedFormatColumn.entrySet()
                                .stream()
                                .collect(Collectors.toMap(
                                                Map.Entry::getKey,
                                                e -> Optional.of(e)
                                                        .map(Map.Entry::getValue)
                                                        .map(LineCheckerResult::fieldTypeForOne)
                                                        .map(c -> (ReferenceType) c)
                                                        .map(ReferenceType::getRefType)
                                                        .orElse("erreur")
                                        )
                                )
                        )
                        .orElseGet(LinkedHashMap::new);
        Map<String, List<GetGrantableResult.ReferenceScope>> referenceScopes = service.getAuthorizationScopes(application, MenuType.submission);

        return ResponseEntity.ok(new GetDataResult(
                variables,
                dataRowResults,
                totalRows,
                checkedFormatcomponents,
                referenceTypeForReferencingColumns,
                referenceScopes));
    }

    /**
     * export as JSON
     *
     * @param nameOrId
     * @param dataType
     * @param params
     * @return
     */
    @DeleteMapping(value = "/applications/{nameOrId}/data/{dataType}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> deleteData(
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("dataType") final String dataType,
            @RequestParam(value = "downloadDatasetQuery", required = false) final String params) {

        final ResponseEntity<String> resposeEntity = null;
        final boolean deleteOnrepositoryApplicationNotAllowed = applicationService.getApplication(nameOrId)
                .findSubmission(dataType)
                .map(Submission::strategy)
                .map(SubmissionType.OA_VERSIONING::equals)
                .isPresent();
        if (deleteOnrepositoryApplicationNotAllowed) {
            throw new DeleteOnrepositoryApplicationNotAllowedException();
        }

        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery downloadDatasetQuery = deserialiseParamDownloadDatasetQuery(params, nameOrId, dataType, false);
        final List<UUID> deletedData = service.deleteData(downloadDatasetQuery);
        return ResponseEntity.ok(deletedData.stream().map(UUID::toString).collect(Collectors.joining(",")));

    }

    private Set<String> buildOrderedVariables(final String nameOrId, final String dataName) {
        final Submission.SubmissionScope authorization = applicationService
                .getApplication(nameOrId)
                .findSubmission(dataName)
                .map(Submission::submissionScope)
                .orElse(null);
        final LinkedHashSet<String> orderedComponents = new LinkedHashSet<String>();
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
     *
     * @param response
     * @param nameOrId
     * @param dataType
     * @param params
     * @return
     * @throws IOException
     */
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}/zip", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> getAllDataZip(
            final HttpServletResponse response,
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("dataType") final String dataType,
            @RequestParam(value = "downloadDatasetQuery", required = false) final String params) {

        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery downloadDatasetQuery = deserialiseParamDownloadDatasetQuery(params, nameOrId, dataType, false);

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", "attachment; filename=\"data.zip\"");

        AtomicReference<OreSiUser> user = new AtomicReference<>();
        StreamingResponseBody responseBody = outputStream -> {
            ZipOutputStream zipOutputStream = null;
            Path tempFile = null;
            try {
                user.set(userRepository.findById(request.getRequestClient().id()));
                tempFile = Files.createTempFile(Paths.get("/tmp"), "data-" + UUID.randomUUID().toString(), ".zip");

                try (OutputStream fileOutputStream = Files.newOutputStream(tempFile);
                     TeeOutputStream teeOutputStream = new TeeOutputStream(outputStream, fileOutputStream)) {

                    zipOutputStream = new KeepAliveZipOutputStream(new BufferedOutputStream(teeOutputStream, 2000));
                    service.buildDataZip(zipOutputStream, downloadDatasetQuery);
                } catch (IOException e) {
                    log.error("Error writing to one of the outputs", e);
                    // Handle specific output stream errors if necessary
                }

                // Exécuter l'envoi d'e-mail dans un thread séparé après avoir retourné la réponse
                ExecutorService executorService = Executors.newSingleThreadExecutor();
                Path finalTempFile = tempFile;
                executorService.submit(() -> {
                    try {
                        service.sendZipLinkByMail(finalTempFile, downloadDatasetQuery, user.get());
                    } catch (Exception e) {
                        log.error("Erreur lors de l'envoi du lien ZIP par e-mail", e);
                    } finally {
                        try {
                            Files.deleteIfExists(finalTempFile);
                        } catch (IOException e) {
                            log.error("Erreur lors de la suppression du fichier temporaire", e);
                        }
                        executorService.shutdown();
                    }
                });

            } catch (Exception e) {
                if (zipOutputStream != null) {
                    try {
                        addErrorFileToZip(zipOutputStream, e);
                    } catch (IOException ioe) {
                        log.error("Error adding error file to ZIP", ioe);
                    }
                }
                throw new RuntimeException("Erreur lors de l'écriture des données CSV", e);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"data.zip\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(responseBody);
    }


    private void addErrorFileToZip(ZipOutputStream zipOutputStream, Exception e) throws IOException {
        ZipEntry errorEntry = new ZipEntry("error.txt");
        zipOutputStream.putNextEntry(errorEntry);

        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(zipOutputStream, StandardCharsets.UTF_8))) {
            String errorMessage = switch (OreSiResources.getDefaultLocale().getLanguage()) {
                case "fr" -> "Une erreur s'est produite lors du téléchargement.";
                case "en" -> "An error occurred during download.";
                default -> "An error occurred during download.";
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

        zipOutputStream.closeEntry();
    }

    private fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery deserialiseParamDownloadDatasetQuery(
            final String params, final String applicationNameOrID, final String dataType, boolean onlyMetadata) {
        try {
            final DownloadDatasetQuery downloadDatasetQuery = params != null ? new JsonRowMapper<DownloadDatasetQuery>().toObject(params, DownloadDatasetQuery.class) : new DownloadDatasetQuery();
            final Application application = applicationService.getApplication(applicationNameOrID);
            downloadDatasetQuery.setApplication(application);
            downloadDatasetQuery.setDataName(dataType);
            final Locale locale = Optional.ofNullable(downloadDatasetQuery)
                    .map(DownloadDatasetQuery::getLocale)
                    .map(Locale::new)
                    .orElseGet(OreSiResources::getDefaultLocale);
            fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery buildDownloadDatasetQuery = DownloadDatasetQuery.build(downloadDatasetQuery);
            if (onlyMetadata) {
                return DownloadDatasetQueryOnlyMetadata.of(buildDownloadDatasetQuery);
            }
            return buildDownloadDatasetQuery;
        } catch (final Exception e) {
            throw new BadDownloadDatasetQuery(e.getMessage());
        }
    }

    @GetMapping(value = "/applications/{nameOrId}/synthesis/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSynthesis(@PathVariable("nameOrId") final String nameOrId,
                                          @PathVariable("dataType") final String dataType) {
        try {
            final Map<String, List<OreSiSynthesis>> synthesis = service.getSynthesis(nameOrId, dataType);
            final String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s", nameOrId, dataType), Charset.defaultCharset());
            Map<String, List<SynthesisResult>> synthesisResults = synthesis.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                                    e -> e.getKey(),
                                    e -> e.getValue().stream().map(SynthesisResult::new).collect(Collectors.toList())
                            )
                    );
            return ResponseEntity.created(URI.create(uri)).body(synthesisResults);
        } catch (final InvalidDatasetContentException e) {
            final List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }

    @GetMapping(value = "/applications/{nameOrId}/synthesis/{dataType}/{variable}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSynthesis(@PathVariable("nameOrId") final String nameOrId,
                                          @PathVariable("dataType") final String dataType,
                                          @PathVariable("variable") final String variable) {
        try {
            final Map<String, List<OreSiSynthesis>> synthesis = service.getSynthesis(nameOrId, dataType, variable);
            final String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s/%s", nameOrId, dataType, variable), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(synthesis);
        } catch (final InvalidDatasetContentException e) {
            final List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }

    @PutMapping(value = "/applications/{nameOrId}/synthesis/{dataType}/{variable}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> buidSynthesis(@PathVariable("nameOrId") final String nameOrId,
                                           @PathVariable("dataType") final String dataType,
                                           @PathVariable("variable") final String variable) {
        try {
            final Map<String, List<OreSiSynthesis>> synthesis = service.buildSynthesis(nameOrId, dataType, variable);
            final String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s%s", nameOrId, dataType, variable != null ? "/" + variable : ""), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(synthesis);
        } catch (final InvalidDatasetContentException e) {
            final List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }

    @PutMapping(value = "/applications/{nameOrId}/synthesis/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> buidSynthesis(@PathVariable("nameOrId") final String nameOrId,
                                           @PathVariable("dataType") final String dataType) throws IOException {
        return buidSynthesis(nameOrId, dataType, null);
    }


    @GetMapping(value = "/applications/{nameOrId}/upload-bundle", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<StreamingResponseBody> getUploadBundle(
            @PathVariable("nameOrId") String nameOrId,
            @RequestParam(value = "withData", required = false, defaultValue = "false") boolean withData,
            @RequestParam(value = "locale", required = false) Locale locale,
            HttpServletRequest request,
            HttpServletResponse response) {

        String instanceUrl = "%s://%s:%s".formatted(
                request.getScheme(),
                request.getServerName(),
                request.getServerPort()
        );

        response.setContentType("application/zip");
        String fileName = "%s-upload-bundle.zip".formatted(nameOrId);
        response.setHeader("Content-Disposition", "attachment; filename=%s".formatted(fileName));
        response.addHeader("Pragma", "no-cache");
        response.addHeader("Expires", "0");

        AtomicReference<BuildBundleReport> reportRef = new AtomicReference<>();

        StreamingResponseBody responseBody = outputStream -> {
            ZipOutputStream zipOutputStream = null;
            Path tempFile = null;
            AtomicReference<OreSiUser> user = new AtomicReference<>();
            try {
                user.set(userRepository.findById(this.request.getRequestClient().id()));
                tempFile = Files.createTempFile(Paths.get("/tmp"), "upload-bundle-" + UUID.randomUUID().toString(), ".zip");

                try (OutputStream fileOutputStream = Files.newOutputStream(tempFile);
                     TeeOutputStream teeOutputStream = new TeeOutputStream(outputStream, fileOutputStream)) {

                    zipOutputStream = new KeepAliveZipOutputStream(new BufferedOutputStream(teeOutputStream, 2000));
                    BuildBundleReport report = service.writeUploadBundle(instanceUrl, nameOrId, withData, locale, zipOutputStream);
                    reportRef.set(report);
                } catch (IOException e) {
                    log.error("Error writing to one of the outputs", e);
                }

                if (reportRef.get() != null && reportRef.get().referentielsEnErreur().isEmpty()) {
                    // Exécuter l'envoi d'e-mail dans un thread séparé après avoir retourné la réponse
                    ExecutorService executorService = Executors.newSingleThreadExecutor();
                    Path finalTempFile = tempFile;
                    executorService.submit(() -> {
                        try {
                            service.sendZipLinkByMail(finalTempFile, reportRef.get(), user.get());
                        } catch (Exception e) {
                            log.error("Erreur lors de l'envoi du lien ZIP par e-mail", e);
                        } finally {
                            try {
                                Files.deleteIfExists(finalTempFile);
                            } catch (IOException e) {
                                log.error("Erreur lors de la suppression du fichier temporaire", e);
                            }
                            executorService.shutdown();
                        }
                    });
                } else {
                    log.warn("Le rapport est incomplet ou contient des erreurs. L'e-mail n'a pas été envoyé.");
                    try {
                        Files.deleteIfExists(tempFile);
                    } catch (IOException e) {
                        log.error("Erreur lors de la suppression du fichier temporaire", e);
                    }
                }

            } catch (Exception e) {
                if (zipOutputStream != null) {
                    try {
                        addErrorFileToZip(zipOutputStream, e);
                    } catch (IOException ioe) {
                        log.error("Error adding error file to ZIP", ioe);
                    }
                }
                throw new RuntimeException("Erreur lors de la création du bundle de téléchargement", e);
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(responseBody);
    }
}