package fr.inra.oresing.domain.application.configuration;

import java.util.Map;

public record RightRequestDescription(Map<String, FieldDescription> formFields) {
}
