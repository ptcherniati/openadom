package fr.inra.oresing.domain.application.configuration.type;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.application.configuration.SubmissionType;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.Map;
import java.util.Set;

public record EnumType(String children,
                       Set<String> values,
                       boolean required) implements FinalType<String> {
    public static final EnumType MULTIPLICITY_ENUM = new EnumType(Multiplicity.ONE.name(), Multiplicity.VALUES);
    public static final EnumType CHECKER_NAME_ENUM = new EnumType(CheckerEnum.OA_string.name(), CheckerEnum.VALUES);
    public static final EnumType STRATEGY_ENUM = new EnumType(SubmissionType.OA_VERSIONING.name(), SubmissionType.VALUES);
    //public static final EnumType MANDATORY = new EnumType(ComponentPresenceConstraint.MANDATORY.name(), ComponentPresenceConstraint.VALUES);
    public EnumType(final String children, final Set<String> values) {
        this(children, values, true);
    }

    public EnumType {
        if (!values.contains(children)) {
            throw new SiOreConfigurationFormatException(ConfigurationException.BAD_ENUM_SECTION_TYPE, Map.of("acceptedValues", values()));
        }
    }

}
