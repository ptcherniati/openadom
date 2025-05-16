package fr.inra.oresing.domain.application.configuration.type;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.Map;

public record CheckerFactory() implements CheckerType {
    public static CheckerType getCheckerTypeForName(final String checkerName) {
        if (Strings.isNullOrEmpty(checkerName) || checkerName.equals("null")) {
            throw new SiOreConfigurationFormatException(
                    ConfigurationException.MISSING_CHECKER_NAME,
                    Map.of(
                            "acceptedCheckerNames", CheckerEnum.VALUES
                    )
            );
        }
        CheckerEnum checkerType;
        try {
            checkerType = CheckerEnum.valueOf(checkerName);
        } catch (final Exception e) {
            throw new SiOreConfigurationFormatException(
                    ConfigurationException.UNKNOWN_CHECKER_NAME,
                    Map.of(
                            "checkerName", checkerName,
                            "acceptedCheckerNames", CheckerEnum.VALUES
                    )
            );
        }
        return switch (checkerType) {
            case OA_groovyExpression -> GroovyCheckerType.EMPTY_INSTANCE();
            case OA_boolean -> BooleanCheckerType.EMPTY_INSTANCE();
            case OA_date -> DateCheckerType.EMPTY_INSTANCE();
            case OA_float -> FloatCheckerType.EMPTY_INSTANCE();
            case OA_integer -> IntegerCheckerType.EMPTY_INSTANCE();
            case OA_reference -> ReferenceCheckerType.EMPTY_INSTANCE();
            case OA_string -> StringCheckerType.EMPTY_INSTANCE();
        };
    }

    @Override
    public Object children() {
        return null;
    }

    @Override
    public String buildExample(final int level) {
        return null;
    }

    @Override
    public SectionBuilder sectionBuilder() {
        return null;
    }

    @Override
    public CheckerEnum getChecker() {
        return null;
    }
}