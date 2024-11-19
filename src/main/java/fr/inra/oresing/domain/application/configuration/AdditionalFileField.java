package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;

public record AdditionalFileField(int order, FieldDescriptionType type, boolean required,
                                  CheckerDescription checker) implements FieldDescription {
}
