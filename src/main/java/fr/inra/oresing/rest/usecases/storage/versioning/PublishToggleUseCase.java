package fr.inra.oresing.rest.usecases.storage.versioning;

import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.BinaryFileRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.services.ApplicationService;
import fr.inra.oresing.workflow.OreSiWorkflowType;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogEntry;
import fr.inra.oresing.workflow.cascade.history.WorkflowLogWriter;
import fr.inrae.ore.cascade.api.Sinks;
import fr.inrae.ore.cascade.api.Sources;
import fr.inrae.ore.cascade.api.workflow.builder.WorkflowBuilder;
import fr.inrae.ore.cascade.model.workflow.Workflow;
import fr.inrae.ore.cascade.model.workflow.WorkflowResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Toggles the {@code published} flag of a binary file as a discrete
 * cascade workflow .
 *
 * <p>Models the operation as a degenerate 1-item pipeline using
 * {@link Sources#single} as the source and {@link Sinks#action} as
 * the sink ( cascade 3.1.0 Action Pattern ) . The action body issues
 * a focused {@code UPDATE} on the {@code params} jsonb column via
 * {@link BinaryFileRepository#togglePublishedFlag} .
 *
 * <p>Wraps the action in the regular workflow lifecycle so that :
 *
 * <ul>
 *   <li>an audit row is persisted in {@code oa_audit.workflow_log}
 *       with type {@link OreSiWorkflowType#PUBLISH_TOGGLE} ;</li>
 *   <li>cascade emits the usual lifecycle events ( consumed by the
 *       {@code WorkflowActiveRegistry} via the global event bus ) so
 *       the dashboard sees this workflow like any other ;</li>
 *   <li>downstream listeners ( mail , metrics ) react through the
 *       standard subscriber pattern .</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
public class PublishToggleUseCase {

    private final ApplicationService    applicationService;
    private final AuthenticationService authenticationService;
    private final OreSiRepository       repository;
    private final WorkflowLogWriter     logWriter;

    public PublishToggleUseCase(
            ApplicationService    applicationService,
            AuthenticationService authenticationService,
            OreSiRepository       repository,
            WorkflowLogWriter     logWriter) {
        this.applicationService    = applicationService;
        this.authenticationService = authenticationService;
        this.repository            = repository;
        this.logWriter             = logWriter;
    }

    /**
     * Executes the publish toggle as a cascade workflow .
     *
     * @param applicationName name or id of the owning application
     * @param fileId          target binary file id
     * @param published       desired published state
     * @return the affected file id
     * @throws IllegalArgumentException if the file does not exist
     * @throws IllegalStateException    if the underlying SQL update
     *                                  affected zero rows
     */
    @Transactional
    public UUID execute(String applicationName, UUID fileId, boolean published) {
        Application application = applicationService.getApplication(applicationName);
        BinaryFileRepository binaryFileRepository = repository.getRepository(application).binaryFile();
        BinaryFile binaryFile = binaryFileRepository.tryFindById(fileId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Binary file %s not found in application %s".formatted(fileId, application.getName())));

        UUID    correlationId = UUID.randomUUID();
        UUID    userId        = OreSiApiRequestContext.getRequestUserId();
        String  userLogin     = authenticationService.getCurrentUserRoles().userLogin();
        Instant startedAt     = Instant.now();
        String  fileName      = binaryFile.getName();
        String  dataName      = resolveDataName(binaryFile);

        PublishTogglePayload payload = new PublishTogglePayload(
                fileId, published, userId, userLogin,
                application.getName(), dataName, fileName, startedAt);

        logWriter.recordStart(WorkflowLogEntry.startMarker(
                correlationId,
                OreSiWorkflowType.PUBLISH_TOGGLE.name(),
                userId, userLogin,
                application.getName(), dataName, fileName,
                startedAt, 0L));

        Workflow workflow = WorkflowBuilder.create()
                .forUser(userId.toString())
                .from(Sources.single(payload))
                .to(Sinks.action((PublishTogglePayload p) -> {
                    int rows = binaryFileRepository.togglePublishedFlag(p.fileId(), p.published(), p.userId());
                    if (rows != 1) {
                        throw new IllegalStateException(
                                "togglePublishedFlag affected %d rows ( expected 1 ) for fileId=%s"
                                        .formatted(rows, p.fileId()));
                    }
                }))
                .build();

        WorkflowResult result;
        Throwable      failure = null;
        try {
            result = workflow.execute();
        } catch (RuntimeException ex) {
            failure = ex;
            result  = null;
        }

        recordEnd(correlationId, userId, userLogin, application.getName(),
                dataName, fileName, startedAt, result, failure);

        if (failure != null) {
            if (failure instanceof RuntimeException re) throw re;
            throw new IllegalStateException(failure);
        }
        return fileId;
    }

    private void recordEnd(
            UUID correlationId, UUID userId, String userLogin,
            String applicationName, String dataName, String fileName,
            Instant startedAt, WorkflowResult result, Throwable failure) {
        Instant  endTime  = Instant.now();
        Duration duration = Duration.between(startedAt, endTime);
        boolean  success  = failure == null
                && result != null
                && result.status() == fr.inrae.ore.cascade.model.workflow.ProcessingStatus.SUCCESS;
        String   status   = success ? WorkflowLogEntry.STATUS_COMPLETED : WorkflowLogEntry.STATUS_FAILED;
        String   fatal    = failure == null ? null : failure.getClass().getSimpleName() + " : " + failure.getMessage();
        String   stage    = result == null
                ? null
                : result.failedStage().map(Enum::name).orElse(null);

        try {
            logWriter.recordEnd(new WorkflowLogEntry(
                    correlationId,
                    OreSiWorkflowType.PUBLISH_TOGGLE.name(),
                    userId, userLogin,
                    applicationName, dataName, fileName,
                    startedAt, endTime, duration, status,
                    success ? 1L : 0L,    // recordsProcessed
                    success ? 0L : 1L,    // recordsFailed
                    1,                    // chunksProcessed ( single chunk )
                    0L,                   // bytesTotal
                    List.of(),
                    fatal,
                    null,
                    stage,
                    null));
        } catch (RuntimeException e) {
            log.warn("recordEnd failed for PUBLISH_TOGGLE {} : {}", correlationId, e.getMessage());
        }
    }

    private static String resolveDataName(BinaryFile binaryFile) {
        try {
            return binaryFile.getParams() != null
                    && binaryFile.getParams().binaryFiledataset() != null
                    ? binaryFile.getParams().binaryFiledataset().getDatatype()
                    : null;
        } catch (RuntimeException e) {
            return null;
        }
    }
}
