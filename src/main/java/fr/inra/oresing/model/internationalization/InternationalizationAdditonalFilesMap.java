package fr.inra.oresing.model.internationalization;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class InternationalizationAdditonalFilesMap {
    Internationalization internationalizationName;
    InternationalizationDisplay internationalizationDisplay;
    Map<String, Internationalization> internationalizedColumns;
    Map<String, Internationalization> format;
}