package fr.inra.oresing.workflow.cascade.preparation;

import fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository;
import fr.inrae.ore.cascade.model.interceptor.preparation.PreparationInterceptor;
import fr.inrae.ore.cascade.model.interceptor.preparation.PreparationInterceptorContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.UUID;

/**
 * Pont entre les evenements du SPI cascade {@link PreparationInterceptor}
 * et la table {@code oa_audit.workflow_log} d'openADOM .
 *
 * <p>Cascade 3.3.0 a introduit le SPI {@code DataPreparator} qui s'execute
 * a l'interieur du scope du workflow ( apres l'INSERT workflow_log ,
 * avant le SOURCE stage ) . Le preparator peut emettre des sous-phases
 * nommees via {@code PreparationContext.emitSubPhase(...)} ; cascade
 * dispatche ces emissions a tout {@link PreparationInterceptor}
 * enregistre . Cet interceptor est notre listener : il route chaque
 * emission vers {@code workflowLogRepository.updatePhase(cid, phase)}
 * pour que oa-live affiche la sous-phase en direct dans la pill de
 * statut du workflow .
 *
 * <h2>Wiring</h2>
 *
 * <p>Spring instancie un singleton via {@code @Component} . Le bean est
 * ensuite attache a chaque workflow cascade via
 * {@link fr.inra.oresing.workflow.cascade.CascadeImportPipeline} ( cf
 * {@code .withInterceptor(preparationPhaseListenerInterceptor)} ) . Aucun
 * preparator n'est attache par defaut donc l'interceptor reste silencieux
 * tant que la migration des chemins openADOM n'a pas attache un
 * {@code CsvDataPreparator} dedie .
 *
 * <h2>Pourquoi un interceptor et pas un appel direct ?</h2>
 *
 * <ul>
 *   <li><b>Decouplage</b> : le preparator ( logique metier , potentiellement
 *       partage entre flux ) n'a pas a connaitre workflow_log . Il signale
 *       sa progression ; cascade dispatche ; openADOM persiste . SRP
 *       preserve .</li>
 *   <li><b>Reuse</b> : tout futur preparator ( decompression archive ,
 *       validation schema , prewarm cache custom ) profite du listener
 *       sans wirage supplementaire .</li>
 *   <li><b>Observabilite gracieuse</b> : si workflow_log est temporairement
 *       indisponible , l'interceptor swallow + log ; le preparator ne
 *       casse pas pour autant ( best-effort identique aux autres
 *       interceptors observability cascade ) .</li>
 * </ul>
 *
 * <h2>Idempotence</h2>
 *
 * <p>{@code workflowLogRepository.updatePhase} est un simple UPDATE sur
 * une row identifiee par correlation_id . Emettre la meme sous-phase
 * deux fois est inoffensif ( idempotent ) . Emettre une sous-phase pour
 * un cid qui n'existe pas ( ex : test sans persistance ) retourne 0
 * rows updatees ; logge en DEBUG , aucune exception .
 *
 * @author R.YAHIAOUI
 * @since openadom adoption cascade 3.3.0
 */
@Slf4j
@Component
public class PreparationPhaseListenerInterceptor implements PreparationInterceptor {

    /**
     * Repository workflow_log pour persister la phase emise . Optionnel
     * via {@code @Autowired(required=false)} pour ne pas casser les
     * profils de test qui chargent cascade sans la stack persistence
     * openADOM ( ex : tests unitaires WorkflowBuilder isoles ) .
     */
    @Autowired(required = false)
    private WorkflowLogRepository workflowLogRepository;

    @Override
    public void onSubPhase(String correlationId, String subPhase, Instant emittedAt) {
        if (workflowLogRepository == null) {
            log.trace("[{}] sub-phase {} ignoree ( WorkflowLogRepository absent du contexte )",
                    correlationId, subPhase);
            return;
        }
        if (correlationId == null || subPhase == null) {
            return;
        }
        UUID cid;
        try {
            cid = UUID.fromString(correlationId);
        } catch (IllegalArgumentException ex) {
            log.debug("Correlation id non UUID , sub-phase {} ignoree : {}",
                    subPhase, correlationId);
            return;
        }
        try {
            workflowLogRepository.updatePhase(cid, subPhase);
            log.debug("[{}] sub-phase {} persistee", correlationId, subPhase);
        } catch (RuntimeException ex) {
            // Best-effort : ne JAMAIS casser le preparator pour un probleme
            // de persistence de la phase . Si la BDD est indisponible , la
            // phase reste celle precedemment publiee ; le user verra le
            // statut s'actualiser au prochain tick reussi .
            log.warn("[{}] echec persistence sub-phase {} ( best-effort , workflow continue ) : {}",
                    correlationId, subPhase, ex.getMessage());
        }
    }

    @Override
    public void beforePreparation(PreparationInterceptorContext ctx) {
        log.debug("[{}] preparation stage demarre", ctx.correlationId());
    }

    @Override
    public void afterPreparation(PreparationInterceptorContext ctx) {
        log.debug("[{}] preparation stage terminee", ctx.correlationId());
    }

    @Override
    public void onPreparationError(Throwable error, PreparationInterceptorContext ctx) {
        log.warn("[{}] preparation stage failed : {}",
                ctx.correlationId(),
                error != null ? error.getMessage() : "( null cause )");
    }
}
