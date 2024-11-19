package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.data.AuthorizationColumnsDescription;
import fr.inra.oresing.domain.internationalization.Internationalization;
import lombok.Getter;

import java.util.Locale;
import java.util.Map;

@Getter
public enum OperationAdditionalFileType {
    admin("admin", true, Map.of("fr", "Délégation", "en", "Delegation")),
    delete("delete", true, Map.of("fr", "Suppression", "en", "Deletion")),
    depot("depot", true, Map.of("fr", "Dépôt", "en", "Deposit")),
    extraction("extraction", true, Map.of("fr", "Gestion", "en", "Management"));

    private final AuthorizationColumnsDescription authorizationColumnsDescription;

    OperationAdditionalFileType(final String title, final boolean display, final Map<String, String> internationalizationName) {

        final Internationalization internationalization = new Internationalization();
        internationalizationName.entrySet()
                .forEach(entry -> internationalization.put(Locale.forLanguageTag(entry.getKey()), entry.getValue()));
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