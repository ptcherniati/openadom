package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;

import java.util.*;
import java.util.stream.Collectors;

public record ComputationBuilder(RootBuilder rootBuilder) {
    public static final String GROOVY_TEMPLATE_FOR_ONE = """
            return OA_buildCompositeKey([%1$s]);
            """;
    public static final String GROOVY_TEMPLATE_FOR_MANY = """
            return OA_buildManyCompositeKey([%1$s]);
            """;

    Parsing<ComputationChecker> build(
            I18n i18n,
            final boolean required,
            Multiplicity multiplicity,
            final String computationPath,
            final JsonNode computationNode) {
        List<String> pathNodes = Arrays.stream(computationPath.split(" > ")).toList();
        final String expression = computationNode.findPath(ConfigurationSchemaNode.OA_EXPRESSION).asText("return \"\";");
        Parsing<Set<String>> exceptionMessagesParsing = rootBuilder().checkerDescriptionBuilder.buildMessagesExceptions(
                i18n,
                pathNodes.get(1),
                pathNodes.get(pathNodes.size()-2),
                NodeSchemaValidator.joinPath(
                        computationPath,
                        ConfigurationSchemaNode.OA_GROOVY_EXCEPTIONS
                ),
                computationNode.findPath(ConfigurationSchemaNode.OA_GROOVY_EXCEPTIONS)
        );

        i18n = exceptionMessagesParsing.i18n();
        final Set<String> references = rootBuilder.getMapper().convertValue(computationNode.findPath(ConfigurationSchemaNode.OA_REFERENCES), Set.class);
        return new Parsing<>(i18n,
                new ComputationChecker(
                        CheckerDescription.CheckerDescriptionType.ComputationChecker,
                        multiplicity,
                        required,
                        expression,
                        references,
                        exceptionMessagesParsing.result())
        );
    }

    ComputationChecker buildFromNaturalKey(final boolean required, Multiplicity multiplicity,
                                           final String naturalKeyColumnsPath,
                                           final JsonNode computationNode, List<String> availableComponentKeys) {
        List<String> keyElementLabels = new ArrayList<>();
        for (JsonNode labelNode : computationNode) {
            String label = labelNode.asText();
            if (!availableComponentKeys.contains(label)) {
                rootBuilder().buildError(
                        ConfigurationException.MISSING_COMPONENT_FOR_NATURAL_KEY_COMPUTATION,
                        Map.of("naturalKeyElement", label,
                                "availableComponents", availableComponentKeys),
                        naturalKeyColumnsPath
                );
            } else {
                keyElementLabels.add(label);
            }
        }
        final String expression = (Multiplicity.MANY == multiplicity ? GROOVY_TEMPLATE_FOR_MANY : GROOVY_TEMPLATE_FOR_ONE)
                .formatted(keyElementLabels.stream().collect(Collectors.joining("','", "'", "'")));
        final Set<String> references = rootBuilder.getMapper().convertValue(computationNode.findPath(ConfigurationSchemaNode.OA_REFERENCES), Set.class);
        return new ComputationChecker(
                CheckerDescription.CheckerDescriptionType.ComputationChecker,
                multiplicity,
                required,
                expression,
                references,
                Set.of());
    }
}