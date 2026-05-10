package fr.inra.oresing.domain.additionalfiles;

import fr.inra.oresing.domain.data.AuthorizationColumnsDescription;
import fr.inra.oresing.domain.internationalization.Internationalization;
import lombok.Getter;

import java.util.Locale;
import java.util.Map;

/**
 * Type d'opération sur un fichier additionnel.
 * Déplacé de {@code persistence} vers {@code domain.additionalfiles} (Phase 1 — indépendance domaine).
 */
@Getter
public enum OperationAdditionalFileType {
    admin("admin", true, Map.of("fr", "Délégation", "en", "Delegation")),
    delete("delete", true, Map.of("fr", "Suppression", "en", "Deletion")),
    depot("depot", true, Map.of("fr", "Dépôt", "en", "Deposit")),
    extraction("extraction", true, Map.of("fr", "Gestion", "en", "Management"));

    private final AuthorizationColumnsDescription authorizationColumnsDescription;

    OperationAdditionalFileType(final String title, final boolean display, final Map<String, String> internationalizationName) {
        final Internationalization internationalization = new Internationalization();
        internationalizationName.forEach((key, value) -> internationalization.put(Locale.forLanguageTag(key), value));
        this.authorizationColumnsDescription = new AuthorizationColumnsDescription(
                internationalization,
                display,
                title,
                false,
                false,
                false,
                false
        );
    }
}