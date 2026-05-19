package fr.inra.oresing.domain.application.configuration.type;


import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

public record GroovyExpressionType(SectionBuilder sectionBuilder,
                                   Map<String, ConfigurationSchemaNodeType<?>> children, boolean required,
                                   boolean nullable) implements IntermediaryType.CheckerParamType {
    public GroovyExpressionType(final Map<String, ConfigurationSchemaNodeType<?>> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                true,
                false);
    }

    private GroovyExpressionType(final Map<String, ConfigurationSchemaNodeType<?>> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                true,
                false);
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withMandatorySections(
                        new LabelDescription(ConfigurationSchemaNode.OA_EXPRESSION, StringType.EMPTY_INSTANCE())
                )
                .withOptionalSections(
                        new LabelDescription(ConfigurationSchemaNode.OA_REFERENCES, StringType.EMPTY_INSTANCE()),
                        new LabelDescription(ConfigurationSchemaNode.OA_GROOVY_EXCEPTIONS, StaticMapType.I18N().type)
                );
    }

    public static GroovyExpressionType EMPTY_INSTANCE() {
        return new GroovyExpressionType(Map.of(), RootType.CHECKING.NO_CHECK);
    }

    @Override
    public String buildExample(final int level) {
        String expression = (String) children().getOrDefault(ConfigurationSchemaNode.OA_EXPRESSION, StringType.EMPTY_INSTANCE()).children();
        expression = Arrays.stream(expression.split("\n"))
                .map(line -> "%1$s%2$s".formatted(
                                Strings.repeat("  ", level + 1),
                                line
                        )
                )
                .collect(Collectors.joining("\n"));
        StringBuilder expressionExample = new StringBuilder("\n")
                .append("%1$s%2$s: >  #optional\n".formatted(
                        Strings.repeat("  ", level),
                        ConfigurationSchemaNode.OA_EXPRESSION)
                )
                .append(expression);
        ConfigurationSchemaNodeType exceptions = this.children.get(ConfigurationSchemaNode.OA_GROOVY_EXCEPTIONS);
        if (exceptions == null) {
            return expressionExample.toString();
        }
        return expressionExample
                .append(
                        " \n%1$s%2$s: %3$s\n"
                                .formatted(
                                        Strings.repeat("  ", level),
                                        ConfigurationSchemaNode.OA_GROOVY_EXCEPTIONS,
                                        exceptions.buildExample(level + 1)
                                )
                ).toString();
    }
}