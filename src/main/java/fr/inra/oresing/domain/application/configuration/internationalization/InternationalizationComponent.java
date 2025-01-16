package fr.inra.oresing.domain.application.configuration.internationalization;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class InternationalizationComponent {
    public static final String EXPORT_HEADER = "exportHeader";
    public static final String EXCEPTIONS = "exceptions";
    InternationalizationTitle exportHeader;

}
