package fr.inra.oresing.rest.model.rightsrequest;

import fr.inra.oresing.domain.rightsrequest.RightsRequest;
import fr.inra.oresing.domain.authorization.AuthorizationParsed;
import lombok.Value;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Value
public class RightsRequestResult {
    UUID id;
    UUID application;
    UUID user;
    /** Email du demandeur, résolu côté serveur pour affichage frontend ( null si non trouvé ). */
    String userEmail;
    Timestamp createDate;
    Timestamp updateDate;
    String comment;
    Map<String, String> rightsRequestForm;
    Map<String, List<AuthorizationParsed>> rightsRequest;
    boolean setted;
    /** Identifiant du gestionnaire ayant traité la demande, ou {@code null} si non traitée. */
    UUID treatedBy;
    /** Login lisible du gestionnaire, résolu côté serveur pour l'affichage frontend. */
    String treatedByLogin;
    /** Décision rendue ( {@code APPROVED} / {@code REJECTED} ), null si non traitée. */
    String treatmentDecision;
    /** Commentaire interne du gestionnaire ( lecture seule en mode consultation ). */
    String treatmentComment;
    /** Sujet du mail envoyé au demandeur ( affiché en consultation ). */
    String treatmentMailSubject;
    /** Texte du mail envoyé au demandeur ( affiché en consultation ). */
    String treatmentMailBody;
    /** Autorisations attribuées au demandeur lors d'une approbation. */
    List<UUID> linkedAuthorizationIds;

    public RightsRequestResult(
            final RightsRequest rightsRequest,
            final Map<String, List<AuthorizationParsed>> authorizationsParsed,
            final String treatedByLogin,
            final String userEmail) {
        super();
        id = rightsRequest.getId();
        application = rightsRequest.getApplication();
        user = rightsRequest.getUser();
        this.userEmail = userEmail;
        comment = rightsRequest.getComment();
        rightsRequestForm = rightsRequest.getRightsRequestForm();
        setted = rightsRequest.isSetted();
        this.rightsRequest = authorizationsParsed;
        this.createDate = Timestamp.valueOf(rightsRequest.getCreationDate());
        this.updateDate = Timestamp.valueOf(rightsRequest.getUpdateDate());
        this.treatedBy = rightsRequest.getTreatedBy();
        this.treatedByLogin = treatedByLogin;
        this.treatmentDecision = rightsRequest.getTreatmentDecision();
        this.treatmentComment = rightsRequest.getTreatmentComment();
        this.treatmentMailSubject = rightsRequest.getTreatmentMailSubject();
        this.treatmentMailBody = rightsRequest.getTreatmentMailBody();
        this.linkedAuthorizationIds = rightsRequest.getLinkedAuthorizationIds();
    }
}