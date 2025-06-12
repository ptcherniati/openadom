package fr.inra.oresing.domain.application.configuration.section;

import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;
import fr.inra.oresing.domain.application.configuration.type.LabelDescription;

public sealed interface Section
        permits Section.MandatorySection, Section.OptionalSection, Section.AnyOfMandatorySection {
    String label();

    <C extends ConfigurationSchemaNodeType<?>> C type();

    boolean required();

    SectionType sectionType();

    default boolean matches(final String label) {
        return label().equals(label);
    }

    record MandatorySection(String label,
                            SectionType sectionType,
                            boolean required,
                            ConfigurationSchemaNodeType<?> type
    ) implements Section {

        public static MandatorySection buildInstance(final LabelDescription labelDescription) {
            return new MandatorySection(labelDescription.label(), labelDescription.sectionType(), true, labelDescription.type());
        }
    }

    record AnyOfMandatorySection(String label,
                                 SectionType sectionType,
                                 boolean required,
                                 ConfigurationSchemaNodeType<?> type) implements Section {

        public AnyOfMandatorySection(final LabelDescription labelDescription) {
            this(labelDescription.label(), labelDescription.sectionType(), labelDescription.required(), labelDescription.type());
        }

        public static AnyOfMandatorySection buildInstance(final LabelDescription labelDescription) {
            return new AnyOfMandatorySection(labelDescription);
        }
    }

    record OptionalSection(
            String label,
            SectionType sectionType,
            boolean required,
            ConfigurationSchemaNodeType<?> type
    ) implements Section {
        public static OptionalSection buildInstance(final LabelDescription labelDescription) {
            return new OptionalSection(
                    labelDescription.label(),
                    labelDescription.sectionType(),
                    labelDescription.required(),
                    labelDescription.type());
        }
    }

}