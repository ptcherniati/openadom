package fr.inra.oresing.rest.model.rightsrequest;

import java.util.List;
import java.util.UUID;

/**
 * Données envoyées par le frontend lors de la validation du traitement
 * d'une demande de droits ( #487 Phase 3 ).
 *
 * <p>Le backend en consomme uniquement les champs nécessaires à l'audit
 * minimal du traitement ( marquage {@code setted=true} + identité du
 * gestionnaire ). Les champs liés au mail / commentaire / sélection
 * d'autorisations sont conservés dans la signature pour l'évolution
 * future ( attribution effective des autorisations, envoi du mail au
 * demandeur ) sans casser le contrat actuel du frontend.</p>
 *
 * @param status                  {@code APPROVED} ou {@code REJECTED}
 * @param linkedAuthorizationIds  identifiants des autorisations sélectionnées
 *                                par le gestionnaire ( vide si refus )
 * @param treatmentComment        commentaire interne saisi par le gestionnaire
 * @param mailSubject             sujet libre du mail envoyé au demandeur
 * @param mailBody                texte libre du mail envoyé au demandeur
 * @param suppressMail            désactivation explicite de l'envoi de mail
 */
public record TreatRightsRequestRequest(
        String status,
        List<UUID> linkedAuthorizationIds,
        String treatmentComment,
        String mailSubject,
        String mailBody,
        boolean suppressMail
) {
}
