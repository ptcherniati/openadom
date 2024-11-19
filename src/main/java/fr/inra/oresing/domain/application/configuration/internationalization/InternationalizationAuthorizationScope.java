package fr.inra.oresing.domain.application.configuration.internationalization;

import lombok.Getter;
import lombok.Setter;

import java.util.Locale;
import java.util.Map;

@Setter
@Getter
public class InternationalizationAuthorizationScope {
    InternationalizationTitle exportHeader;
    InternationalizationTitle i18n;

}
