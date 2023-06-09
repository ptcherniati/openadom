package fr.inra.oresing.model.internationalization;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class InternationalizationReferenceMap {
    Internationalization internationalizationName;
    Map<String, Internationalization> internationalizedColumns;
    Map<String, Internationalization> internationalizedDynamicColumns;
    InternationalizationDisplay internationalizationDisplay;
    Map<String, Internationalization> internationalizedValidations;
    Map<String, Internationalization> internationalizedTags;
}