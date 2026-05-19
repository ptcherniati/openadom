package fr.inra.oresing.rest.usecases.storage.versioning;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fr.inra.oresing.workflow.cascade.history.WorkflowLogRepository;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Coordonne les workflows publish / unpublish / delete_file concurrents :
 *
 * <ul>
 *   <li><b>Cancellation flags</b> : un drapeau par {@code correlationId}
 *       que la supersedure ( phase 1 ) leve pour signaler au handler
 *       phase 2 deja en cours qu'il doit s'arreter au prochain
 *       checkpoint . Reactif , sans interruption brutale de thread .</li>
 *   <li><b>Locks par (app, datatype)</b> : serialise les sections
 *       critiques ( DELETE rows referencevalue + recompute synthesis )
 *       pour eviter le {@code lock_timeout} cote PostgreSQL quand
 *       plusieurs phases 2 visent le meme datatype simultanement .
 *       Different (app, datatype) -&gt; pas de contention , parallelisme
 *       conserve .</li>
 * </ul>
 *
 * <p>Component Spring singleton ; les maps sont thread-safe . On ne
 * persiste rien : si l'application redemarre , les phases 2 en vol
 * sont de toute facon perdues ( cas zombie deja gere par
 * {@code WorkflowZombieSweeper} ) .
 *
 * @author R.YAHIAOUI
 */
@Slf4j
@Component
public class PublishLifecycleCoordinator {

    private final ConcurrentMap<UUID, AtomicBoolean>     cancellationFlags = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ReentrantLock>   synthesisLocks    = new ConcurrentHashMap<>();

    /**
     * Mapping parent workflow cid -> child cascade IMPORT cid . Fix BUG-1
     * decouvert E2E : un cancel sur le PUBLISH parent ne propageait pas au
     * sub-IMPORT car les registries ( ChunkCancellationRegistry , BackendPidRegistry )
     * connaissent UNIQUEMENT le cid sub-IMPORT . Resultat : sub continue son
     * UPSERT pendant 10+ min apres cancel utilisateur , trompeur .
     *
     * <p>Cycle de vie : {@link #registerChildImport} appele par
     * {@link fr.inra.oresing.workflow.cascade.CascadeImportPipeline} a son entree
     * via {@link fr.inra.oresing.domain.cancel.CancellationContext} ThreadLocal .
     * {@link #unregisterChildImport} appele en finally du Phase 2 Handler . Si
     * crash entre register et unregister , la map fuite un entry ( cleanup au
     * zombie sweeper indirect ) .
     */
    private final ConcurrentMap<UUID, UUID> childImportByParent = new ConcurrentHashMap<>();

    /**
     * P0 cancel-divergence fix : reverse mapping child cascade IMPORT cid ->
     * parent PUBLISH cid . Necessaire car oa-live affiche les workflows par
     * type ( utilisateur clique le row IMPORT visible , pas le PUBLISH parent
     * qui contient peu d'info ) . Quand le cancel arrive sur le child cid ,
     * {@code DashboardService.cancelWorkflow} doit propager au parent pour
     * que Phase 2 voit {@code coordinator.isCancelled(parent) = true} et
     * abandonne le commit . Sans cette propagation , Phase 2 ignorait le
     * cancel CHILD et finissait par committer binaryfile.published=true
     * ( divergence frontend=published vs oa-live=CANCELLED reportee ) .
     *
     * <p>Maintenance synchronisee avec {@link #childImportByParent} :
     * insertion / suppression dans les memes methodes
     * ( {@link #registerChildImport} / {@link #unregisterChildImport} /
     * {@link #releaseCancellation} ) pour eviter desynchronisation .
     */
    private final ConcurrentMap<UUID, UUID> parentByChildImport = new ConcurrentHashMap<>();

    /**
     * Optional - used to persist {@code metadata.parentCorrelationId} on the
     * child IMPORT workflow_log row at register time . Persistence lets the
     * audit history endpoint hide cascade child rows that exist only for
     * accounting purposes , so a single publish surfaces as 1 row in the
     * UI instead of 2 ( parent PUBLISH + cascade IMPORT child ) .
     * {@code required=false} : harmless if absent ( tests , minimal Spring
     * contexts ) - the in-memory mapping above still works .
     */
    @Autowired(required = false)
    private WorkflowLogRepository workflowLogRepository;

    // ------------------------------------------------------------
    // Cancellation flags ( supersedure -> handler phase 2 )
    // ------------------------------------------------------------

    /**
     * Marque le workflow {@code correlationId} comme annule par la
     * supersedure . Si le handler phase 2 correspondant est deja en
     * cours d'execution , il verra {@code isCancelled = true} au
     * prochain {@link #checkCancellation(UUID)} et abandonnera proprement
     * ( pas de DELETE inutile , pas de buildSynthesis inutile ) .
     */
    public void markCancelled(UUID correlationId) {
        if (correlationId == null) return;
        cancellationFlags.computeIfAbsent(correlationId, k -> new AtomicBoolean()).set(true);
        log.debug("Cancellation flag set for workflow {}", correlationId);
    }

    /**
     * Renvoie {@code true} si le workflow {@code correlationId} a ete
     * supersede ( cancellation flag arme ) . Le handler phase 2 doit
     * appeler cette methode entre chaque etape lourde et abandonner si
     * elle renvoie true .
     */
    public boolean isCancelled(UUID correlationId) {
        if (correlationId == null) return false;
        AtomicBoolean flag = cancellationFlags.get(correlationId);
        return flag != null && flag.get();
    }

    /**
     * Liberer la memoire associee au workflow termine ( ou supersede ) .
     * A appeler en fin de phase 2 dans un finally pour eviter une fuite
     * a long terme ( N workflows -&gt; N entries ConcurrentHashMap ) .
     */
    public void releaseCancellation(UUID correlationId) {
        if (correlationId == null) return;
        cancellationFlags.remove(correlationId);
        // Garder symetrie : si correlationId est parent , libere aussi la
        // child entry du reverse map ; sinon , si c'est un child orphelin
        // ( rare ) , libere just sa propre entry .
        UUID childCid = childImportByParent.remove(correlationId);
        if (childCid != null) {
            parentByChildImport.remove(childCid);
        } else {
            parentByChildImport.remove(correlationId);
        }
    }

    // ------------------------------------------------------------
    // Mapping parent ( PUBLISH/UNPUBLISH ) -> child ( IMPORT cascade )
    // ------------------------------------------------------------

    /**
     * Enregistre le cid sub-IMPORT lance par cascade pour le compte d'un
     * workflow PUBLISH parent . Permet a {@code DashboardService.cancelWorkflow}
     * de cancel le child quand l'utilisateur cancel le parent .
     */
    public void registerChildImport(UUID parentCid, UUID childImportCid) {
        if (parentCid == null || childImportCid == null) return;
        childImportByParent.put(parentCid, childImportCid);
        parentByChildImport.put(childImportCid, parentCid);
        // metadata.parentCorrelationId tagging is done by CascadeImportPipeline
        // RIGHT AFTER registerWorkflowStart ( see CascadeImportPipeline:321+ ) :
        // calling setParentCorrelationId from here races the cascade INSERT and
        // UPDATEs zero rows ( row not yet visible ) , leaving the child row
        // un-tagged and double-rendered in the history page .
    }

    /**
     * Resout l'arbre de workflows lies au cid donne ( parent + son child
     * cascade IMPORT s'il existe ) . API unique utilisee par {@code
     * DashboardService.cancelWorkflow} pour propager le cancel de facon
     * symetrique : que l'utilisateur cancel le parent ou le child depuis
     * oa-live , le cancel atteint tous les workflows de l'arbre .
     *
     * <p>Resolution :
     * <ul>
     *   <li>Si {@code cid} est un parent connu : retourne {parent, child}</li>
     *   <li>Si {@code cid} est un child connu : retourne {parent, child}</li>
     *   <li>Si {@code cid} n'est dans aucun mapping : retourne {cid} seul
     *       ( cas workflow standalone , ex import direct sans publish parent )</li>
     * </ul>
     *
     * <p>Le Set retourne est immuable et toujours non-vide ( contient au
     * moins {@code cid} ) .
     */
    public java.util.Set<UUID> getRelatedWorkflows(UUID cid) {
        if (cid == null) return java.util.Set.of();
        UUID childOfThisParent = childImportByParent.get(cid);
        if (childOfThisParent != null) {
            return java.util.Set.of(cid, childOfThisParent);
        }
        UUID parentOfThisChild = parentByChildImport.get(cid);
        if (parentOfThisChild != null) {
            return java.util.Set.of(parentOfThisChild, cid);
        }
        return java.util.Set.of(cid);
    }

    /**
     * Renvoie {@code true} si {@code cid} est enregistre comme child cascade
     * IMPORT d'un parent PUBLISH/UNPUBLISH/DELETE_FILE . Utilise par
     * {@code DashboardService.listInProgress} pour cacher les childs IMPORT
     * de la liste UI ( seul le parent metier est affiche - facade pattern ) .
     * Si {@code cid} est un parent standalone ou un workflow IMPORT sans
     * parent ( raw upload ) , retourne {@code false} : il reste visible .
     */
    public boolean isKnownChild(UUID cid) {
        if (cid == null) return false;
        return parentByChildImport.containsKey(cid);
    }

    /**
     * Lookup direct du child cascade IMPORT cid pour un parent donne .
     * Utilise par {@code DashboardService.findDetail} pour aggreger les
     * stats progression du child ( chunks , lignes , progress % ) dans le
     * DTO du parent - 1 row UI = 1 operation utilisateur ( facade pattern ) .
     * Empty si pas de child enregistre ( parent solo ou pas encore demarre ) .
     */
    public java.util.Optional<UUID> getChildImport(UUID parentCid) {
        if (parentCid == null) return java.util.Optional.empty();
        return java.util.Optional.ofNullable(childImportByParent.get(parentCid));
    }

    /** Cleanup manuel apres Phase 2 finished ( evite fuite long-terme ) . */
    public void unregisterChildImport(UUID parentCid) {
        if (parentCid == null) return;
        UUID childCid = childImportByParent.remove(parentCid);
        if (childCid != null) {
            parentByChildImport.remove(childCid);
        }
    }

    // ------------------------------------------------------------
    // Locks par (app, datatype) - serialisation buildSynthesis
    // ------------------------------------------------------------

    /**
     * Renvoie le lock dedie au couple {@code (applicationName, dataName)} .
     * Cree paresseusement ; reutilise par tous les phases 2 ciblant ce
     * datatype . Le caller doit faire {@code lock.lock() / try / finally
     * lock.unlock()} .
     *
     * <p>Note : {@code dataName} peut etre null ( workflow sans datatype ,
     * ex. delete d'un fichier hors datatype ) ; on utilise alors une cle
     * generique " {app}:__no_datatype__ " pour ne pas serialiser ces cas
     * entre eux avec les vrais datatypes .
     */
    public ReentrantLock synthesisLock(String applicationName, String dataName) {
        String key = applicationName + ":" + (dataName == null ? "__no_datatype__" : dataName);
        return synthesisLocks.computeIfAbsent(key, k -> new ReentrantLock());
    }
}
