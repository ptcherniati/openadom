package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.domain.data.deposit.bundle.RegisterReactiveResult;
import fr.inra.oresing.domain.data.rapport.BundleReport;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import fr.inra.oresing.rest.filesenderclient.BuildBundleReport;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeError;
import fr.inra.oresing.rest.reactive.ReactiveTypeInfo;
import fr.inra.oresing.rest.reactive.ReactiveTypeProgress;
import fr.inra.oresing.rest.usecases.application.GetApplicationUseCase;
import fr.inra.oresing.rest.usecases.data.ReadEntryUseCase;
import fr.inra.oresing.rest.usecases.data.SendZipLinkByMailUseCase;
import fr.inra.oresing.rest.usecases.data.WriteUploadBundleUseCase;
import fr.inra.oresing.rest.usecases.security.authentication.GetCurrentUserUseCase;
import fr.inra.oresing.rest.usecases.storage.binaryfile.CreateDataUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.LocaleResolver;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "Bearer Authentication")
public class BundleResources {

    // ── constantes ──────────────────────────────────────────────────────────
    private static final String TMP = "/tmp";
    private static final String BUNDLE_NAME = "%s-%s-upload-bundle";
    private static final DateTimeFormatter TIMESTAMP_FORMATER = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String FILE_ERROR = "error.txt";
    private static final String IO_ERROR_WRITE   = "Error writing to one of the outputs";
    private static final String IO_DELETE_ERROR  = "Erreur lors de la suppression du fichier temporaire";
    private static final String EMAIL_ERROR      = "Erreur lors de l'envoi du lien ZIP par e-mail";
    private static final String IO_ADDING_ERROR  = "Error adding error file to ZIP";
    private static final String BAD_REPORT       = "Le rapport est incomplet ou contient des erreurs. L'e-mail n'a pas été envoyé.";
    private static final String BAD_BUNDLE       = "Erreur lors de la création du bundle de téléchargement";
    private static final String IO_UPOAD_ERROR_FR = "Une erreur s'est produite lors du téléchargement.";
    private static final String IO_UPOAD_ERROR_EN = "An error occurred during download.";
    private static final String PARAM_FILE_NAME  = "fileName";
    private static final String PARAM_DATA_NAME  = "dataName";
    private static final String FR = "fr";

    // ── contrôle de concurrence ──────────────────────────────────────────────
    private final ConcurrentHashMap<String, Boolean> runningBundleCreation = new ConcurrentHashMap<>();

    // ── use cases ────────────────────────────────────────────────────────────
    private final GetApplicationUseCase    getApplicationUseCase;
    private final GetCurrentUserUseCase    getCurrentUserUseCase;
    private final WriteUploadBundleUseCase writeUploadBundleUseCase;
    private final SendZipLinkByMailUseCase sendZipLinkByMailUseCase;
    private final ReadEntryUseCase         readEntryUseCase;
    private final CreateDataUseCase        createDataUseCase;

    // ── services ──────────────────────────────────────────────────────────────
    private final DataService              dataService;
    private final LocaleResolver           localeResolver;

    // ── executors ─────────────────────────────────────────────────────────────
    private final ExecutorService normalExecutorService;
    private final ExecutorService heavyExecutorService;

    public BundleResources(
            GetApplicationUseCase getApplicationUseCase,
            GetCurrentUserUseCase getCurrentUserUseCase,
            WriteUploadBundleUseCase writeUploadBundleUseCase,
            SendZipLinkByMailUseCase sendZipLinkByMailUseCase,
            ReadEntryUseCase readEntryUseCase,
            CreateDataUseCase createDataUseCase,
            DataService dataService,
            LocaleResolver localeResolver,
            @Qualifier("normalExecutorService") ExecutorService normalExecutorService,
            @Qualifier("heavyExecutorService")  ExecutorService heavyExecutorService) {
        this.getApplicationUseCase    = getApplicationUseCase;
        this.getCurrentUserUseCase    = getCurrentUserUseCase;
        this.writeUploadBundleUseCase = writeUploadBundleUseCase;
        this.sendZipLinkByMailUseCase = sendZipLinkByMailUseCase;
        this.readEntryUseCase         = readEntryUseCase;
        this.createDataUseCase        = createDataUseCase;
        this.dataService              = dataService;
        this.localeResolver           = localeResolver;
        this.normalExecutorService    = normalExecutorService;
        this.heavyExecutorService     = heavyExecutorService;
    }

    // ── GET /applications/{nameOrId}/upload-bundle ────────────────────────────

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_APPLICATION_MODIFY')")
    @GetMapping(value = "/applications/{nameOrId}/upload-bundle")
    public ResponseEntity<?> getUploadBundle(
            @PathVariable("nameOrId") String nameOrId,
            @RequestParam(value = "withData", required = false, defaultValue = "false") boolean withData,
            @RequestParam(value = "locale", required = false) Locale locale,
            HttpServletRequest request) {

        final OreSiUser user = getCurrentUserUseCase.execute();
        if (runningBundleCreation.get(user.getLogin()) != null) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body("Opération déjà en cours");
        }
        runningBundleCreation.put(user.getLogin(), true);

        String instanceUrl = String.format("%s://%s:%s",
                request.getScheme(),
                request.getServerName(),
                request.getServerPort());

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

    // ── POST /applications/{nameOrId}/download-bundle ─────────────────────────

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_DOWNLOAD_BUNDLE')")
    @PostMapping(value = "/applications/{nameOrId}/download-bundle", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> uploadBundle(
            HttpServletRequest request,
            @PathVariable String nameOrId,
            @RequestParam("file") MultipartFile zipBundle) {

        Locale locale = localeResolver.resolveLocale(request);
        final String origin = request.getHeader("Origin");
        final OreSiUser currentUser = getCurrentUserUseCase.execute();
        final SecurityContext context = SecurityContextHolder.getContext();

        return Flux.create(sink -> normalExecutorService.submit(() -> {
            SecurityContextHolder.setContext(context);
            File zipFile = null;
            try {
                zipFile = OreSiResources.getPhysicalFileOrCopy(zipBundle);
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
                                manifest.set(mapper.readValue(manifestStream,
                                        new TypeReference<Map<String, List<String>>>() {}));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                int countFiles = manifest.get().values().stream().mapToInt(List::size).sum();

                AtomicReference<Map<String, List<String>>> references = new AtomicReference<>();
                readEntryUseCase.execute(zipFile, DataService.REFERENCES_JSON,
                        referencesStream -> {
                            try {
                                references.set(mapper.readValue(referencesStream,
                                        new TypeReference<Map<String, List<String>>>() {}));
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

                @SuppressWarnings("unchecked")
                final RegisterReactiveResult registerReactiveResult =
                        new RegisterReactiveResult((FluxSink<ReactiveResult<?>>) (Object) sink, countFiles, rapport);
                final ReactiveTypeInfo reactiveTypeInfo = new ReactiveTypeInfo("MANIFEST", Map.of("manifest", manifest.get()));
                registerReactiveResult.add(reactiveTypeInfo, false);
                rapport.add(reactiveTypeInfo);

                final int MAX_DB_CONCURRENCY = 30;
                readManifestAndSendMailTopological(
                        manifest.get(), references.get(),
                        registerReactiveResult, finalZipFile,
                        locale, application, rapport,
                        currentUser, MAX_DB_CONCURRENCY
                ).block();

                completedSuccessfully = true;
                // #58 - Reconstruire le cache des filtres pour les dataTypes importés
                for (String dataName : manifest.get().keySet()) {
                    dataService.refreshFilterListCache(application, dataName);
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
        }));
    }

    // ── traitement topologique du manifest ────────────────────────────────────

    public Mono<Void> readManifestAndSendMailTopological(
            Map<String, List<String>> manifest,
            Map<String, List<String>> references,
            RegisterReactiveResult registerReactiveResult,
            File finalZipFile,
            Locale locale,
            Application application,
            BundleReport rapport,
            OreSiUser currentUser,
            int MAX_DB_CONCURRENCY) {

        SecurityContext secCtx = SecurityContextHolder.getContext();
        Set<String> processed = ConcurrentHashMap.newKeySet();
        Set<String> remaining = ConcurrentHashMap.newKeySet();
        remaining.addAll(manifest.keySet());

        Function<String, Mono<Void>> processReference = dataName ->
                Flux.fromIterable(manifest.getOrDefault(dataName, List.of()))
                        .flatMap(fileName -> Mono.create(mono -> {
                            try {
                                heavyExecutorService.submit(() -> {
                                    SecurityContextHolder.setContext(secCtx);
                                    try {
                                        readManifestEntryAndLoadData(
                                                Map.entry(dataName, List.of(fileName)),
                                                registerReactiveResult, finalZipFile,
                                                locale, application, rapport);
                                    } catch (Exception e) {
                                        log.error("Erreur inattendue lors du chargement de {}/{}: {}",
                                                dataName, fileName, e.getMessage(), e);
                                        try {
                                            registerReactiveResult.add(new ReactiveTypeError(
                                                    Map.of(PARAM_DATA_NAME, dataName,
                                                           PARAM_FILE_NAME, fileName,
                                                           "errorType", "ERROR_LOADING_DATA")), true);
                                        } catch (Exception ignored) {}
                                    }
                                        mono.success();
                                });
                            } catch (RuntimeException e) {
                                mono.error(e);
                            }
                        }), MAX_DB_CONCURRENCY)
                        .then()
                        .doOnSuccess(v -> {
                            processed.add(dataName);
                            log.info("Référence {} traitée. Processed: {}/{}", dataName, processed.size(), manifest.size());
                        });

        return processBatchTopological(processed, remaining, processReference, references, MAX_DB_CONCURRENCY)
                .then(Mono.fromRunnable(() -> {
                    try {
                        sendZipLinkByMailUseCase.execute(
                                registerReactiveResult.bundleReport().attachmentFile(),
                                registerReactiveResult.bundleReport(),
                                currentUser);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    } finally {
                        registerReactiveResult.add(new ReactiveTypeProgress(1), false);
                    }
                }));
    }

    /* package-private pour les tests unitaires */
    Mono<Void> processBatchTopological(
            Set<String> processed,
            Set<String> remaining,
            Function<String, Mono<Void>> processReference,
            Map<String, List<String>> references,
            int MAX_DB_CONCURRENCY) {

        List<String> ready;
        synchronized (remaining) {
            ready = remaining.stream()
                    .filter(ref -> {
                        List<String> deps = references.getOrDefault(ref, List.of())
                                .stream()
                                .filter(dep -> !dep.equals(ref))
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

        if (ready.isEmpty()) {
            if (!remaining.isEmpty()) {
                log.error("Cycle détecté ou dépendances non satisfaites pour: {}", remaining);
                throw new RuntimeException("Cycle détecté dans les dépendances: " + remaining);
            }
            log.info("Toutes les références ont été traitées");
            return Mono.empty();
        }

        log.info("Traitement de la vague: {} (concurrence: {})", ready, ready.size());

        return Flux.fromIterable(ready)
                .flatMap(processReference, MAX_DB_CONCURRENCY)
                .then(Mono.defer(() -> {
                    synchronized (remaining) {
                        remaining.removeAll(ready);
                    }
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
                            File tempFile;
                            try {
                                tempFile = File.createTempFile(split[0], split[1]);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            tempFile.deleteOnExit();
                            try (OutputStream out = new FileOutputStream(tempFile);
                                 InputStream in = fileToUpload) {
                                in.transferTo(out);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            try {
                                // Bundle import : pas de mail individuel
                                // ( withEmail=false ) , DataVersioningResult
                                // ignore ( pas de count expose dans le
                                // rapport bundle ) . Le seul side-effect
                                // attendu est l execution de la cascade
                                // pipeline + UPSERT staging -> table finale
                                // ( synchrone ou differe selon la tx en cours ) .
                                createDataUseCase.execute(
                                        locale,
                                        application.getName(),
                                        dataName,
                                        new DataFile(tempFile, (long) tempFile.length(), fileName),
                                        false,
                                        false);
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                            final ReactiveResult reactiveResult = new ReactiveTypeInfo("LOADED_DATA",
                                    Map.of(PARAM_DATA_NAME, dataName, PARAM_FILE_NAME, fileName));
                            registerReactiveResult.add(reactiveResult, true);
                            rapport.add(reactiveResult);
                        });
            } catch (InvalidDatasetContentException e) {
                final ReactiveTypeError reactiveTypeError = new ReactiveTypeError(
                        Map.of(PARAM_DATA_NAME, dataName,
                                PARAM_FILE_NAME, fileName,
                                "errorType", e.getMessage(),
                                "message", e.getErrors().stream().limit(1)
                                        .map(firstError -> firstError.validationCheckResult().message())
                                        .findFirst().orElse(""),
                                "params", e.getErrors().stream().limit(1)
                                        .map(firstError -> firstError.validationCheckResult().messageParams())
                                        .findFirst().orElse(Map.of())));
                registerReactiveResult.add(reactiveTypeError, true);
            } catch (IOException e) {
                log.error("Erreur IO lors du chargement de {}/{}: {}", dataName, fileName, e.getMessage(), e);
                final ReactiveTypeError reactiveTypeError = new ReactiveTypeError(
                        Map.of(PARAM_DATA_NAME, dataName, PARAM_FILE_NAME, fileName,
                                "errorType", "ERROR_LOADING_DATA"));
                registerReactiveResult.add(reactiveTypeError, true);
                // Ne pas re-throw : l'erreur est déjà signalée côté frontend, on continue avec les autres fichiers
            } catch (RuntimeException e) {
                // Capture les exceptions inattendues, ex. rejet du rate-limiter d'import
                log.error("Erreur inattendue lors du chargement de {}/{}: {}", dataName, fileName, e.getMessage(), e);
                final ReactiveTypeError reactiveTypeError = new ReactiveTypeError(
                        Map.of(PARAM_DATA_NAME, dataName, PARAM_FILE_NAME, fileName,
                                "errorType", "ERROR_LOADING_DATA"));
                registerReactiveResult.add(reactiveTypeError, true);
                // Ne pas re-throw : évite de terminer prématurément le flux NDJSON
            }
        });
    }

    // ── utilitaires ───────────────────────────────────────────────────────────

    private static void removeRepository(Path finalTempZipDirectory) {
        if (Files.exists(finalTempZipDirectory)) {
            try (Stream<Path> walk = Files.walk(finalTempZipDirectory)) {
                walk.sorted(Comparator.reverseOrder()).forEach(path -> {
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
            String errorMessage = FR.equals(OreSiResources.getDefaultLocale().getLanguage())
                    ? IO_UPOAD_ERROR_FR : IO_UPOAD_ERROR_EN;
            writer.write(errorMessage);
            writer.newLine();
            if (e.getMessage() != null) {
                writer.write(e.getMessage());
                writer.newLine();
            }
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
            writer.write(sw.toString());
        }
    }
}