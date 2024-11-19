package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.application.configuration.section.SectionType;

public record LabelDescription(
        String label,
        SectionType sectionType,
        Boolean required,
        ConfigurationSchemaNodeType type
) {

    public LabelDescription(String label, ConfigurationSchemaNodeType type) {
        this(label, SectionType.UNDEFINED, false, type);
    }
}
