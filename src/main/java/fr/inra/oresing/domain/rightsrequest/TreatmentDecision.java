package fr.inra.oresing.domain.rightsrequest;

import java.util.Optional;

/**
 * Décision rendue par le gestionnaire lors du traitement d'une demande
 * de droits ( #487 ).
 *
 * <p>Sert à la fois côté API ( valeur portée par
 * {@link fr.inra.oresing.rest.model.rightsrequest.TreatRightsRequestRequest#status()} )
 * et côté domaine ( aiguillage des notifications, futures attributions
 * d'autorisations, etc. ).</p>
 */
public enum TreatmentDecision {
    APPROVED,
    REJECTED;

    /**
     * Convertit une chaîne reçue dans le payload HTTP en décision typée.
     * Toute valeur inconnue ou nulle est interprétée comme {@link #APPROVED}
     * pour rester compatible avec les clients qui n'envoient pas le champ.
     */
    public static TreatmentDecision fromNullable(final String raw) {
        return Optional.ofNullable(raw)
                .map(String::trim)
                .map(String::toUpperCase)
                .filter(s -> !s.isEmpty())
                .map(value -> {
                    try {
                        return TreatmentDecision.valueOf(value);
                    } catch (final IllegalArgumentException e) {
                        return APPROVED;
                    }
                })
                .orElse(APPROVED);
    }
}
