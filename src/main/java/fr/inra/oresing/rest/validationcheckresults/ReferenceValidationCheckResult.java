package fr.inra.oresing.rest.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.rest.ValidationCheckResult;
import lombok.Value;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Représente le résultat d'une validation par un {@link fr.inra.oresing.checker.ReferenceLineChecker}.
 */
@Value
public class ReferenceValidationCheckResult implements ValidationCheckResult {
    CheckerTarget target;
    ValidationLevel level;

    /**
     * La valeur qui a donné ce résultat de validation
     */
    String rawValue;

    /**
     * En cas de succès, l'identifiant naturel du référentiel correspondant à la valeur qui a été validée
     */
    Set<Ltree> matchedReferenceHierarchicalKey;

    /**
     * En cas de succès, l'identifiant technique du référentiel correspondant à la valeur qui a été validée
     */
    Set<UUID> matchedReferenceId;

    /**
     * En cas d'erreur, la clé i18n du message d'erreur
     */
    String message;

    /**
     * En cas d'erreur, les paramètres utiles pour le message d'erreur
     */
    Map<String, Object> messageParams;

    public static ReferenceValidationCheckResult success(CheckerTarget target, String rawValue, Set<Ltree> matchedReferenceHierarchicalKey, Set<UUID> matchedReferenceId) {
        return new ReferenceValidationCheckResult(target, ValidationLevel.SUCCESS, rawValue, matchedReferenceHierarchicalKey, matchedReferenceId, null, null);
    }

    public static ReferenceValidationCheckResult error(CheckerTarget target, String rawValue, String message, ImmutableMap<String, Object> messageParams) {
        return new ReferenceValidationCheckResult(target, ValidationLevel.ERROR, rawValue, null, null, message, messageParams);
    }
}