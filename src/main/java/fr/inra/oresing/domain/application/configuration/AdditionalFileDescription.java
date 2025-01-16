package fr.inra.oresing.domain.application.configuration;

import java.util.Map;

public record AdditionalFileDescription(Map<String, FieldDescription> formFields) {
    private static final AdditionalFileDescription EMPTY_INSTANCE = new AdditionalFileDescription(Map.of());
    public static AdditionalFileDescription EMPTY_INSTANCE() {
        return EMPTY_INSTANCE;
    }
}
