package fr.inra.oresing.rest.usecases.storage.versioning;

import java.util.UUID;

/**
 * Levee par {@link PublishLifecycleService#startPhase1} lorsqu'une operation
 * lifecycle ( PUBLISH / UNPUBLISH / DELETE_FILE ) est deja en cours sur le
 * meme {@code fileId} . L'utilisateur recoit un HTTP 409 Conflict avec les
 * details ( type , correlationId , login ) pour qu'il puisse decider de
 * reessayer plus tard une fois l'operation en cours terminee .
 *
 * <p>Comportement choisi sur les alternatives :
 * <ul>
 *   <li><b>Supersede</b> ( cancel-and-restart ) : KO , UX confuse pour la
 *       victime ( mail "votre action annulee" ) + cancel mid-flight cassait
 *       l'integrite des donnees en cours d'ecriture ;</li>
 *   <li><b>Queue FIFO</b> ( attente ) : KO , user pouvait attendre 15 min
 *       sans feedback ;</li>
 *   <li><b>Reject 409</b> ( retenu ) : feedback immediat , user voit popup
 *       " operation deja en cours par X " , peut retry quand il veut .</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
public class WorkflowAlreadyInProgressException extends RuntimeException {

    private final UUID    activeCorrelationId;
    private final UUID    fileId;
    private final String  activeWorkflowType;
    private final String  activeUserLogin;

    public WorkflowAlreadyInProgressException(UUID activeCorrelationId,
                                              UUID fileId,
                                              String activeWorkflowType,
                                              String activeUserLogin) {
        super("Workflow %s already in progress on fileId=%s ( started by %s )".formatted(
                activeWorkflowType, fileId, activeUserLogin == null ? "?" : activeUserLogin));
        this.activeCorrelationId = activeCorrelationId;
        this.fileId              = fileId;
        this.activeWorkflowType  = activeWorkflowType;
        this.activeUserLogin     = activeUserLogin;
    }

    public UUID    activeCorrelationId() { return activeCorrelationId; }
    public UUID    fileId()              { return fileId; }
    public String  activeWorkflowType()  { return activeWorkflowType; }
    public String  activeUserLogin()     { return activeUserLogin; }
}
