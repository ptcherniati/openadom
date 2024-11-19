package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;

public sealed interface FieldDescription permits RightsRequestField, AdditionalFileField {
    boolean required();

    FieldDescriptionType type();

    CheckerDescription checker();

    enum FieldDescriptionType {
        RightsRequestField, AdditionalFileField
    }
}
