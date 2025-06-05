package fr.inra.oresing.domain.repository.authorization;

import fr.inra.oresing.domain.data.AuthorizationColumnsDescription;
import fr.inra.oresing.domain.internationalization.Internationalization;
import lombok.Getter;

import java.util.Locale;
import java.util.Map;

@Getter
public enum OperationType {
    //admin("admin", true,false,false, false,false, Map.of("fr","Délégation", "en","Delegation")),
    depot("depot", true, false, false, false, false, Map.of("fr", "Dépôt", "en", "Deposit")),
    delete("delete", true, false, false, false, false, Map.of("fr", "Suppression", "en", "Deletion")),
    publication("publication", true, false, false, false, false, Map.of("fr", "Publication", "en", "Publication")),
    extraction("extraction", true, true, true, true, true, Map.of("fr", "Extraction", "en", "Extraction")),
    associate("associate", false, true, true, true, true, Map.of("fr", "Associer", "en", "Associate"));

    private final AuthorizationColumnsDescription authorizationColumnsDescription;


    OperationType(final String title, final boolean display, final boolean withPeriods, final boolean withDataGroups, final boolean forPublic, final boolean forRequest, final Map<String, String> internationalizationName) {
        final Internationalization internationalization = new Internationalization();
        internationalizationName.forEach((key, value) -> internationalization.put(Locale.forLanguageTag(key), value));
        this.authorizationColumnsDescription = new AuthorizationColumnsDescription(
                internationalization,
                display,
                title,
                withPeriods,
                withDataGroups,
                forPublic,
                forRequest
        );
    }
}