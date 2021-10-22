package fr.inra.oresing.model.internationalization;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class InternationalizationDataTypeMap {
    Internationalization internationalizationName;
    Map<String, Internationalization> internationalizedColumns;
    InternationalizationAuthorisationMap authorization;
    Map<String, InternationalizationDisplay> internationalizationDisplay;
}