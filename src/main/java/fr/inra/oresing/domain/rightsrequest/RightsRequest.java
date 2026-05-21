package fr.inra.oresing.domain.rightsrequest;

import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.OreSiEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class RightsRequest extends OreSiEntity {

    UUID application;
    UUID user;
    String comment;
    transient Map<String, String> rightsRequestForm;
    OreSiAuthorization rightsRequest;
    boolean setted;
    /**
     * Identifiant du gestionnaire ( applicationManager ou userManager ) ayant
     * traité la demande. Renseigné lors de la validation du traitement ;
     * reste {@code null} tant que la demande est en attente.
     */
    UUID treatedBy;

    /** Décision rendue par le gestionnaire ( {@code APPROVED} / {@code REJECTED} ), null si non traitée. */
    String treatmentDecision;

    /** Commentaire interne saisi par le gestionnaire au moment du traitement. */
    String treatmentComment;

    /** Sujet du mail envoyé ( ou destiné à être envoyé ) au demandeur. */
    String treatmentMailSubject;

    /** Texte du mail envoyé ( ou destiné à être envoyé ) au demandeur. */
    String treatmentMailBody;

    /** Identifiants des autorisations attribuées au demandeur lors d'une approbation. */
    transient List<UUID> linkedAuthorizationIds;

    public static RightsRequest EMPTY_INSTANCE() {
        return new RightsRequest();
    }
}