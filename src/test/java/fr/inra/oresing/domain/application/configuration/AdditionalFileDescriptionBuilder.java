package fr.inra.oresing.domain.application.configuration;

import java.util.Map;

public class AdditionalFileDescriptionBuilder {

    private Map<String, FieldDescription> formFields = Map.of();

    public AdditionalFileDescriptionBuilder formFields(Map<String, FieldDescription> formFields) {
        this.formFields = formFields;
        return this;
    }

    public AdditionalFileDescription build() {
        return new AdditionalFileDescription(formFields);
    }
}