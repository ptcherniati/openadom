package fr.inra.oresing.rest.usecases.storage.versioning;

import java.util.UUID;

/**
 * P0 cancel-divergence fix : levee par
 * {@code PublishLifecyclePhase2Handler.commitVisibleFlagAndSynthesis}
 * quand la row {@code workflow_log} du workflow courant a deja ete
 * marquee terminale ( CANCELLED par le {@code WorkflowZombieSweeper}
 * ou par un cancel utilisateur ) avant que Phase 2 ait pu finaliser .
 *
 * <p>Sans cette garde , la finalisation continuait sa course
 * ( {@code UPDATE binaryfile SET published = true} dans une tx separee ) ,
 * produisant la divergence dangereuse {@code workflow_log = CANCELLED}
 * vs {@code binaryfile.published = true} observee en production .
 *
 * <p>L'invariant rendu execute : {@code binaryfile.published = true}
 * implique {@code workflow_log.status = IN_PROGRESS au moment du commit
 * -> COMPLETED apres commit} . Garanti par lock {@code FOR UPDATE} sur
 * la row workflow_log au sein de la tx de finalisation .
 */
public class WorkflowAlreadyTerminalException extends RuntimeException {

    private final UUID correlationId;

    public WorkflowAlreadyTerminalException(UUID correlationId) {
        super("Workflow " + correlationId + " was marked terminal ( probably by watchdog or cancel ) "
                + "before commit could finish ; visibility toggle aborted to preserve invariant");
        this.correlationId = correlationId;
    }

    public UUID getCorrelationId() {
        return correlationId;
    }
}
