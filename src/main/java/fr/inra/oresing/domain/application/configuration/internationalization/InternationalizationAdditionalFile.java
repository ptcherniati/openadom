package fr.inra.oresing.domain.application.configuration.internationalization;

import lombok.Getter;
import lombok.Setter;

import java.util.Locale;
import java.util.Map;

@Setter
@Getter
public class InternationalizationAdditionalFile {
    public static final String I_18_N = "i18n";
    InternationalizationTitle i18n;
    Map<String, InternationalizationTitle> fields = Map.of();

}
