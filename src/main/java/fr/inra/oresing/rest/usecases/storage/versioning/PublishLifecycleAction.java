package fr.inra.oresing.rest.usecases.storage.versioning;

import fr.inra.oresing.mail.EmailService;
import fr.inra.oresing.workflow.OreSiWorkflowType;

/**
 * Triplet d'actions pour le cycle de vie publication / depublication /
 * suppression d'un fichier binaire .
 *
 * <p>Centralise le mapping action -&gt; type workflow_log + UPLOAD_STATE
 * mail START + UPLOAD_STATE mail END afin que {@code
 * PublishLifecycleService} n'ait pas a switcher partout sur l'enum .
 *
 * @author R.YAHIAOUI
 */
public enum PublishLifecycleAction {

    /** Toggle ON : INSERT data via cascade pipeline depuis le blob . */
    PUBLISH(OreSiWorkflowType.PUBLISH,
            EmailService.UPLOAD_STATE.PUBLISH_STARTED,
            EmailService.UPLOAD_STATE.PUBLISHED),

    /** Toggle OFF : DELETE rows referencevalue ; binaryfile conserve . */
    UNPUBLISH(OreSiWorkflowType.UNPUBLISH,
            EmailService.UPLOAD_STATE.UNPUBLISH_STARTED,
            EmailService.UPLOAD_STATE.UNPUBLISHED),

    /** Delete fichier : DELETE rows ( si publie ) + DELETE binaryfile . */
    DELETE_FILE(OreSiWorkflowType.DELETE_FILE,
            EmailService.UPLOAD_STATE.DELETE_STARTED,
            EmailService.UPLOAD_STATE.DELETED);

    private final OreSiWorkflowType workflowType;
    private final EmailService.UPLOAD_STATE startState;
    private final EmailService.UPLOAD_STATE endState;

    PublishLifecycleAction(OreSiWorkflowType workflowType,
                           EmailService.UPLOAD_STATE startState,
                           EmailService.UPLOAD_STATE endState) {
        this.workflowType = workflowType;
        this.startState   = startState;
        this.endState     = endState;
    }

    public OreSiWorkflowType workflowType()            { return workflowType; }
    public EmailService.UPLOAD_STATE startMailState()  { return startState; }
    public EmailService.UPLOAD_STATE endMailState()    { return endState; }
}
