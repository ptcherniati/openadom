package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.internationalization.Internationalization;

import java.util.Locale;
import java.util.Map;

public enum OperationReferenceType {
    admin("admin", true,Map.of("fr","Délégation", "en","Delegation")),
    manage("extraction", true,Map.of("fr","Gestion", "en","Management"));

    public Configuration.AuthorizationColumnsDescription getAuthorizationColumnsDescription() {
        return authorizationColumnsDescription;
    }

    private final Configuration.AuthorizationColumnsDescription authorizationColumnsDescription;

    OperationReferenceType(String title, boolean display, Map<String, String> internationalizationName) {
        final Configuration.AuthorizationColumnsDescription authorizationColumnsDescription = new Configuration.AuthorizationColumnsDescription();
        authorizationColumnsDescription.setDisplay(display);
        authorizationColumnsDescription.setTitle(title);
        Internationalization internationalization = new Internationalization();
        internationalizationName.entrySet().stream()
                .forEach(entry->internationalization.put(Locale.forLanguageTag(entry.getKey()), entry.getValue()));
        authorizationColumnsDescription.setInternationalizationName(internationalization);
        this.authorizationColumnsDescription = authorizationColumnsDescription;
    }
}