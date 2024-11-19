package fr.inra.oresing.domain.application.configuration.internationalization;

import lombok.Getter;
import lombok.Setter;

import java.util.Locale;
import java.util.Map;

@Setter
@Getter
public class InternationalizationSubmissionComponent {
    public static final String REFERENCE_SCOPES = "referenceScopes";

    Map<String, InternationalizationTitle> referenceScopes = Map.of();

}
