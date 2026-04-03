package fr.inra.oresing.domain.application.configuration;

import java.util.Map;

public class RightRequestDescriptionBuilder {

    private Map<String, FieldDescription> formFields = Map.of();

    public RightRequestDescriptionBuilder formFields(Map<String, FieldDescription> formFields) {
        this.formFields = formFields;
        return this;
    }

    public RightRequestDescription build() {
        return new RightRequestDescription(formFields);
    }
}