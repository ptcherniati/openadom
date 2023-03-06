package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.internationalization.Internationalization;

import java.util.Locale;
import java.util.Map;

public enum OperationType {
    admin("admin", true,false,false, false, Map.of("fr","Délégation", "en","Delegation")),
    depot("depot", true,false,false,false,  Map.of("fr","Dépôt", "en","Deposit")),
    delete("delete", true,false,false, false, Map.of("fr","Suppression", "en","Deletion")),
    publication("publication", true,false,false,false,  Map.of("fr","Publication", "en","Publication")),
    extraction("extraction", true,true,true,true,  Map.of("fr","Extraction", "en","Extraction"));

    public Configuration.AuthorizationColumnsDescription getAuthorizationColumnsDescription() {
        return authorizationColumnsDescription;
    }

    private final Configuration.AuthorizationColumnsDescription authorizationColumnsDescription;

    OperationType(String title, boolean display, boolean withPeriods, boolean withDataGroups, boolean forPublic,  Map<String, String> internationalizationName) {
        final Configuration.AuthorizationColumnsDescription authorizationColumnsDescription = new Configuration.AuthorizationColumnsDescription();
        authorizationColumnsDescription.setDisplay(display);
        authorizationColumnsDescription.setTitle(title);
        Internationalization internationalization = new Internationalization();
        internationalizationName.entrySet().stream()
                .forEach(entry->internationalization.put(Locale.forLanguageTag(entry.getKey()), entry.getValue()));
        authorizationColumnsDescription.setInternationalizationName(internationalization);
        authorizationColumnsDescription.setWithPeriods(withPeriods);
        authorizationColumnsDescription.setWithDataGroups(withDataGroups);
        authorizationColumnsDescription.setForPublic(forPublic);
        this.authorizationColumnsDescription = authorizationColumnsDescription;
    }
}