package fr.inra.oresing.workflow.cascade.history;

import fr.inrae.ore.cascade.core.monitoring.WorkflowEventBus;
import fr.inrae.ore.cascade.model.listener.WorkflowEvents.WorkflowAliveEvent;
import fr.inrae.ore.cascade.model.listener.WorkflowListener;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Pont entre le bus de cycle de vie cascade ( {@link WorkflowEventBus} ) et la
 * persistance openADOM ( {@link WorkflowLogRepository#beat} ) .
 *
 * <p>Cascade 2.2.0 emet un {@link WorkflowAliveEvent} a periode configurable
 * pour chaque workflow montrant une activite cote chunk ( SOURCE / TRANSFORM
 * / SINK ) . Ce bridge consomme l'evenement et met a jour
 * {@code oa_audit.workflow_log.last_heartbeat_at} via la fonction SQL
 * {@code oa_audit.beat_workflow ( uuid )} . Le sweeper
 * {@link WorkflowZombieSweeper} peut alors detecter de facon fiable les
 * workflows reellement bloques ( silence > threshold ) .
 *
 * <p><b>Complementaire au {@link HeartbeatService}.</b> Le HeartbeatService
 * existant couvre uniquement la phase finalize ( UPSERT staging ->
 * referencevalue ) . Le bridge cascade couvre les phases qui echappaient au
 * radar : lecture source , transformation , collecte , ecriture sink . Les
 * deux peuvent tourner en parallele sans interference ( meme UPDATE
 * idempotent ) .
 *
 * <p><b>Activation.</b> Pilotee par {@code app.workflow.cascade-heartbeat.enabled}
 * ( defaut true ) . Quand off : le bean n'est pas instancie , aucun event
 * cascade n'est consomme , la lib cascade arrete son scheduler interne quand
 * aucun listener ne reagit a l'event .
 *
 * <p><b>Periode.</b> Pilotee par
 * {@code app.workflow.cascade-heartbeat.period-ms} ( defaut 30 000 ) .
 * Propagee a la lib cascade au {@link PostConstruct} via
 * {@link WorkflowEventBus#setLifecycleHeartbeatPeriodMillis(long)} .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
@ConditionalOnProperty(
        name           = "app.workflow.cascade-heartbeat.enabled",
        havingValue    = "true",
        matchIfMissing = true)
public class CascadeHeartbeatBridge implements WorkflowListener {

    private final WorkflowLogRepository repository;
    private final long                  periodMs;

    public CascadeHeartbeatBridge(
            WorkflowLogRepository repository,
            @Value("${app.workflow.cascade-heartbeat.period-ms:30000}") long periodMs) {
        this.repository = repository;
        this.periodMs   = periodMs;
    }

    @PostConstruct
    public void init() {
        WorkflowEventBus bus = WorkflowEventBus.getInstance();
        bus.setLifecycleHeartbeatPeriodMillis(periodMs);
        bus.setLifecycleHeartbeatEnabled(true);
        bus.subscribe(this);
        log.info("CascadeHeartbeatBridge wired ( period={} ms , listener subscribed )", periodMs);
    }

    @PreDestroy
    public void shutdown() {
        try {
            WorkflowEventBus.getInstance().unsubscribe(this);
        } catch (RuntimeException e) {
            log.warn("CascadeHeartbeatBridge unsubscribe threw : {}", e.getMessage());
        }
    }

    @Override
    public void onWorkflowAlive(WorkflowAliveEvent event) {
        if (event == null || event.correlationId() == null) {
            return;
        }
        UUID corr;
        try {
            corr = UUID.fromString(event.correlationId());
        } catch (IllegalArgumentException ex) {
            log.debug("CascadeHeartbeatBridge : non-UUID correlationId '{}' ignored", event.correlationId());
            return;
        }
        repository.beat(corr);
    }
}
