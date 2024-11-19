package fr.inra.oresing.domain.application.configuration.internationalization;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

@Setter
@Getter
public class InternationalizationData {
    public static final String I18N = "i18n";
    public static final String I18N_DISPLAY_PATTERN = "i18nDisplayPattern";
    public static final String COMPONENTS = "components";
    public static final String VALIDATIONS = "validations";
    public static final String SUBMISSIONS = "submissions";

    Map<String, Map<Locale, String>> validations = Map.of();
    Map<String, Map<String, Map<Locale, String>>> exceptions = Map.of();

    Map<String, InternationalizationComponent> components = Map.of();
    InternationalizationSubmissionComponent submissions = new InternationalizationSubmissionComponent();

    InternationalizationTitle i18nDisplayPattern;
    InternationalizationTitle i18n;


}
