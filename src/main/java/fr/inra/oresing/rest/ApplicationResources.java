package fr.inra.oresing.rest;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.ApplicationInformation;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.domain.exceptions.application.BadLabelNameException;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.rest.exceptions.OreSiIOException;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.model.rightsrequest.CreateRightsRequestRequest;
import fr.inra.oresing.rest.model.rightsrequest.GetRightsRequestResult;
import fr.inra.oresing.rest.model.rightsrequest.RightsRequestInfos;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeResult;
import fr.inra.oresing.rest.services.RelationalService;
import fr.inra.oresing.rest.services.ServiceContainer;
import fr.inra.oresing.rest.usecases.application.*;
import fr.inra.oresing.rest.usecases.metadata.rightsrequest.CreateOrUpdateRightsRequestUseCase;
import fr.inra.oresing.rest.usecases.metadata.rightsrequest.FindRightsRequestUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;

@Slf4j
@RestController
@RequestMapping("/api/v1/applications")
@SecurityRequirement(name = "Bearer Authentication")
public class ApplicationResources {

    private final GetApplicationsUseCase getApplicationsUseCase;
    private final ValidateConfigurationUseCase validateConfigurationUseCase;
    private final CreateApplicationUseCase createApplicationUseCase;
    private final GetApplicationOrAccordingToRightsUseCase getApplicationOrAccordingToRightsUseCase;
    private final BuildOpenAdomUseCase buildOpenAdomUseCase;
    private final GetApplicationUseCase getApplicationUseCase;
    private final ChangeApplicationConfigurationUseCase changeApplicationConfigurationUseCase;
    private final FindRightsRequestUseCase findRightsRequestUseCase;
    private final CreateOrUpdateRightsRequestUseCase createOrUpdateRightsRequestUseCase;
    private final LocaleResolver localeResolver;
    private final ServiceContainer serviceContainer;
    private final ExecutorService heavyExecutorService;

    public ApplicationResources(
            GetApplicationsUseCase getApplicationsUseCase,
            ValidateConfigurationUseCase validateConfigurationUseCase,
            CreateApplicationUseCase createApplicationUseCase,
            GetApplicationOrAccordingToRightsUseCase getApplicationOrAccordingToRightsUseCase,
            BuildOpenAdomUseCase buildOpenAdomUseCase,
            GetApplicationUseCase getApplicationUseCase,
            ChangeApplicationConfigurationUseCase changeApplicationConfigurationUseCase,
            FindRightsRequestUseCase findRightsRequestUseCase,
            CreateOrUpdateRightsRequestUseCase createOrUpdateRightsRequestUseCase,
            LocaleResolver localeResolver,
            ServiceContainer serviceContainer,
            @Qualifier("heavyExecutorService") ExecutorService heavyExecutorService) {
        this.getApplicationsUseCase = getApplicationsUseCase;
        this.validateConfigurationUseCase = validateConfigurationUseCase;
        this.createApplicationUseCase = createApplicationUseCase;
        this.getApplicationOrAccordingToRightsUseCase = getApplicationOrAccordingToRightsUseCase;
        this.buildOpenAdomUseCase = buildOpenAdomUseCase;
        this.getApplicationUseCase = getApplicationUseCase;
        this.changeApplicationConfigurationUseCase = changeApplicationConfigurationUseCase;
        this.findRightsRequestUseCase = findRightsRequestUseCase;
        this.createOrUpdateRightsRequestUseCase = createOrUpdateRightsRequestUseCase;
        this.localeResolver = localeResolver;
        this.serviceContainer = serviceContainer;
        this.heavyExecutorService = heavyExecutorService;
    }

    @PreAuthorize("isFullyAuthenticated()")
    @SecurityRequirement(name = "Bearer Authentication")
    @GetMapping(produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> getApplications(@RequestParam(required = false, defaultValue = "") final String[] filter) {
        final var filters = Arrays.stream(filter)
                .map(ApplicationInformation::valueOf)
                .toList();
        return getApplicationsUseCase.execute(filters);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/validate-configuration", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> validateConfiguration(@RequestParam("file") final MultipartFile file) throws IOException {
        DataFile dataFile = null;
        try {
            final File physicalFileOrCopy = OreSiResources.getPhysicalFileOrCopy(file);
            dataFile = file == null ? null : new DataFile(physicalFileOrCopy, (long) file.getInputStream().available(), file.getOriginalFilename());
        } catch (IOException e) {
            throw OreSiIOException.ORE_SI_IOEXCEPTION_CANT_LOAD_FILE();
        }
        DataFile finalDataFile = dataFile;
        return buildFluxRequestNDJson(sink -> {
            final Application application = validateConfigurationUseCase.execute(sink::next, finalDataFile);
            sink.next(new ReactiveTypeResult(application));
            sink.complete();
        });
    }

    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_APPLICATION_CREATE')")
    @PostMapping(value = "/{name}", produces = MediaType.APPLICATION_NDJSON_VALUE)
    @Parameter(examples = @ExampleObject(
            name = "fichier de configuration",
            description = "<a href= 'https://anaee-dev.pages.mia.inra.fr/si-ore-v2/schemaExample.yaml'>Fichier d'example</a>"
    ))
    public Flux<ReactiveResult> createApplication(@PathVariable("name") final String name,
                                                  @RequestParam(name = "comment", defaultValue = "") final String comment,
                                                  @RequestParam("file") final MultipartFile file) throws BadApplicationConfigurationException {
        DataFile dataFile = null;
        try {
            final File physicalFileOrCopy = OreSiResources.getPhysicalFileOrCopy(file);
            dataFile = file == null ? null : new DataFile(physicalFileOrCopy, (long) file.getInputStream().available(), file.getOriginalFilename());
        } catch (IOException e) {
            throw OreSiIOException.ORE_SI_IOEXCEPTION_CANT_LOAD_FILE();
        }
        if (!RelationalService.IdentifierTest.identifierForApplicationName(name)) {
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

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/{nameOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApplicationResult getApplication(@PathVariable("nameOrId") final String nameOrId,
                                            @RequestParam(required = false, defaultValue = "") final String[] filter) {
        final Application application = getApplicationOrAccordingToRightsUseCase.execute(nameOrId);
        return buildOpenAdomUseCase.execute(application, filter);
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD')")
    @GetMapping(value = "/{nameOrId}/configuration", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<?> getConfiguration(@PathVariable("nameOrId") final String nameOrId) {
        final Application application = getApplicationUseCase.execute(nameOrId);
        final UUID configFileId = application.getConfigFile();
        // TODO: Delegate to FileResources.getFile() - for now returning placeholder
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_APPLICATION_MODIFY')")
    @PostMapping(value = "/{nameOrId}/configuration", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> changeConfiguration(@PathVariable("nameOrId") final String nameOrId,
                                                    @RequestParam("file") final MultipartFile file,
                                                    @RequestParam(name = "comment", defaultValue = "") final String comment) throws BadApplicationConfigurationException {

        return buildFluxRequestNDJson(fluxSink -> {
            if (file.isEmpty()) {
                fluxSink.error(new IllegalArgumentException("EmptyFile"));
            }
            DataFile dataFile = null;
            try {
                final File physicalFileOrCopy = OreSiResources.getPhysicalFileOrCopy(file);
                dataFile = file == null ? null : new DataFile(physicalFileOrCopy, (long) file.getInputStream().available(), file.getOriginalFilename());
            } catch (IOException e) {
                throw OreSiIOException.ORE_SI_IOEXCEPTION_CANT_LOAD_FILE();
            }

            final UUID uuid = changeApplicationConfigurationUseCase.execute(fluxSink::next, nameOrId, dataFile, comment);
            fluxSink.next(new ReactiveTypeResult(uuid));
            fluxSink.complete();
        });
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

    @PreAuthorize("isAuthenticated()")
    @GetMapping(value = "/{nameOrId}/rightsRequest", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(description = "Get a rightsRequest with their description using search params")
    public ResponseEntity<GetRightsRequestResult> listRightsRequest(
            @PathVariable("nameOrId") final String nameOrId,
            @JsonParam(value = "params", required = false) final RightsRequestInfos rightsRequestInfos) {
        final GetRightsRequestResult list = findRightsRequestUseCase.execute(nameOrId, rightsRequestInfos);
        return ResponseEntity.ok(list);
    }

    @PreAuthorize("isAuthenticated()")
    @PostMapping(value = "/{nameOrId}/rightsRequest", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createRightsRequest(@PathVariable("nameOrId") final String nameOrId,
                                                 @RequestBody final CreateRightsRequestRequest createRightsRequestRequest) {
        final UUID fileUUID = createOrUpdateRightsRequestUseCase.execute(createRightsRequestRequest, nameOrId);
        return ResponseEntity.ok(fileUUID);
    }
}