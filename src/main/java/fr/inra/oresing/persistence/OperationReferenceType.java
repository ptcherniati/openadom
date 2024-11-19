package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.data.AuthorizationColumnsDescription;
import fr.inra.oresing.domain.internationalization.Internationalization;
import lombok.Getter;

import java.util.Locale;
import java.util.Map;

@Getter
public enum OperationReferenceType {
    admin("admin", true,Map.of("fr","Délégation", "en","Delegation")),
    manage("extraction", true,Map.of("fr","Gestion", "en","Management"));

    private final AuthorizationColumnsDescription authorizationColumnsDescription;

    OperationReferenceType(final String title, final boolean display, final Map<String, String> internationalizationName) {

        final Internationalization internationalization = new Internationalization();
        internationalizationName.entrySet()
                .forEach(entry->internationalization.put(Locale.forLanguageTag(entry.getKey()), entry.getValue()));
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