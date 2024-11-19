package fr.inra.oresing.domain.application.configuration.internationalization;

import lombok.Getter;
import lombok.Setter;

import java.util.Locale;
import java.util.Map;

@Setter
@Getter
public class InternationalizationTitle {

    public static final String TITLE = "title";
    public static final String DESCRIPTION = "description";

    public static final String REFERENCE_SCOPES = "referenceScopes";

    Map<Locale, String> title = Map.of();
    Map<Locale, String> description = Map.of();

}
