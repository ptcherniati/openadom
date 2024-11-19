package fr.inra.oresing.domain.application.configuration.internationalization;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Setter
@Getter
public class InternationalizationRightrequest {
    public static final String FIELDS = "fields";
    public static final String I_18_N = "i18n";
    Map<String, InternationalizationTitle> fields = Map.of();
    InternationalizationTitle i18n;

}
