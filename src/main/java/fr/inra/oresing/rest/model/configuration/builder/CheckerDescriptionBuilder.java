package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.checker.*;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationComponent;
import fr.inra.oresing.domain.application.configuration.type.CheckerEnum;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.domain.groovy.GroovyExpression;

import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.*;

public record CheckerDescriptionBuilder(RootBuilder rootBuilder) {

    Parsing<CheckerDescription> build(
            I18n i18n,
            final String componentKey,
            final boolean required,
            final String path,
            final JsonNode checkerNode,
            final String dataKey) {
        if (checkerNode == null || checkerNode.isMissingNode()) {
            if (required) {
                return new Parsing<>(i18n, new StringChecker(CheckerDescription.CheckerDescriptionType.StringChecker, Multiplicity.ONE, true, null));
            }
            return new Parsing<>(i18n, null);
        }
        CheckerEnum name = null;
        try {
            final JsonNode checkerName = checkerNode.get(ConfigurationSchemaNode.OA_NAME);
            if (checkerName == null || "null".equals(checkerName.asText())) {
                rootBuilder.buildError(ConfigurationException.MISSING_CHECKER_NAME,
                        Map.of(
                                "acceptedCheckerNames", CheckerEnum.VALUES
                        ),
                        NodeSchemaValidator.joinPath(
                                path,
                                ConfigurationSchemaNode.OA_CHECKER,
                                ConfigurationSchemaNode.OA_NAME
                        )
                );
            }
            name = rootBuilder.getMapper().convertValue(checkerName, CheckerEnum.class);
        } catch (final IllegalArgumentException e) {
            rootBuilder.buildError(ConfigurationException.UNKNOWN_CHECKER_NAME, Map.of(
                            "checkerName", checkerNode.get(ConfigurationSchemaNode.OA_NAME).asText(),
                            "acceptedCheckerNames", CheckerEnum.VALUES),
                    NodeSchemaValidator.joinPath(
                            path,
                            ConfigurationSchemaNode.OA_CHECKER,
                            ConfigurationSchemaNode.OA_NAME
                    )
            );
        }
        if (name == null) {
            // TODO -> je ne vois pas quand on passe ici
            return null;
        }
        final JsonNode params = checkerNode.findPath(ConfigurationSchemaNode.OA_PARAMS);
        I18n localI18n = new I18n(i18n.i18n());

        Parsing<Set<String>> exceptionMessagesParsing = Optional.ofNullable(params.get(ConfigurationSchemaNode.OA_GROOVY_EXCEPTIONS))
                .map(exceptionsNode -> buildMessagesExceptions(localI18n, dataKey, componentKey, path, exceptionsNode))
                .orElse(new Parsing<>(localI18n, Set.of()));
        i18n = exceptionMessagesParsing.i18n();
        final Multiplicity multiplicity = Optional.ofNullable(params.get(ConfigurationSchemaNode.OA_MULTIPLICITY))
                .map(multi -> rootBuilder.getMapper().convertValue(multi, Multiplicity.class))
                .orElse(Multiplicity.ONE);
        final CheckerDescription checkerDescription = switch (name) {
            case OA_reference -> {
                String reference = params.findPath(ConfigurationSchemaNode.OA_REFERENCE)
                        .findPath(ConfigurationSchemaNode.OA_NAME)
                        .asText("null");
                if (reference.charAt(0) == '\"' &&
                        reference.charAt(reference.length() - 1) == '\"') {
                    reference = reference.substring(1, reference.length() - 1);
                }
                if ("null".equals(reference)) {
                    rootBuilder.buildError(ConfigurationException.MISSING_REFERENCE_NAME, Map.of(
                                    "allDataNames", rootBuilder.getListDataKeys()),
                            NodeSchemaValidator.joinPath(
                                    path,
                                    ConfigurationSchemaNode.OA_CHECKER,
                                    ConfigurationSchemaNode.OA_PARAMS,
                                    ConfigurationSchemaNode.OA_REFERENCE,
                                    ConfigurationSchemaNode.OA_NAME
                            )
                    );
                } else if (!rootBuilder.getListDataKeys().contains(reference)) {
                    rootBuilder.buildError(ConfigurationException.UNKNOWN_REFERENCE_NAME, Map.of(
                                    "allDataNames", rootBuilder.getListDataKeys(),
                                    "referenceName", reference),
                            NodeSchemaValidator.joinPath(
                                    path,
                                    ConfigurationSchemaNode.OA_CHECKER,
                                    ConfigurationSchemaNode.OA_PARAMS,
                                    ConfigurationSchemaNode.OA_REFERENCE,
                                    ConfigurationSchemaNode.OA_NAME
                            )
                    );
                }
                boolean isParent = Optional.ofNullable(params.get(ConfigurationSchemaNode.OA_REFERENCE))
                        .map(j -> j.get(ConfigurationSchemaNode.OA_IS_PARENT))
                        .map(JsonNode::asBoolean)
                        .orElse(false);
                final boolean isrecursive = Optional.ofNullable(params.get(ConfigurationSchemaNode.OA_REFERENCE))
                        .map(j -> j.get(ConfigurationSchemaNode.OA_IS_RECURSIVE))
                        .map(JsonNode::asBoolean)
                        .orElse(isParent && reference.equals(dataKey));
                yield new ReferenceChecker(
                        CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                        componentKey,
                        multiplicity,
                        required,
                        reference,
                        isrecursive,
                        isParent || isrecursive);
            }
            case OA_date -> {
                final DatePattern<TemporalAccessor> datePattern;
                try {
                    datePattern = Optional.ofNullable(params.findPath(ConfigurationSchemaNode.OA_PATTERN))
                            .map(JsonNode::asText)
                            .map(DatePattern::of)
                            .orElse(null);
                } catch (final SiOreConfigurationFormatException siOreConfigurationFormatException) {
                    rootBuilder.buildError(
                            siOreConfigurationFormatException.getException(),
                            siOreConfigurationFormatException.getParams(),
                            NodeSchemaValidator.joinPath(
                                    path,
                                    ConfigurationSchemaNode.OA_CHECKER,
                                    ConfigurationSchemaNode.OA_PARAMS,
                                    ConfigurationSchemaNode.OA_PATTERN
                            )
                    );
                    yield DateChecker.BAD_CHECKER;
                }

                final Optional<String> min = Optional.ofNullable(params.get(ConfigurationSchemaNode.OA_MIN))
                        .map(JsonNode::asText);
                final Optional<String> max = Optional.ofNullable(params.get(ConfigurationSchemaNode.OA_MAX))
                        .map(JsonNode::asText);
                final Optional<String> duration = Optional.ofNullable(params.get(ConfigurationSchemaNode.OA_DURATION))
                        .map(JsonNode::asText);
                final String durationRegex = "^(?!.*(second|minute|hour|day|week|month|year).*\1)\\d+ +(?:second|minute|hour|day|week|month|year)s?(?: +\\d+ +(?:second|minute|hour|day|week|month|year)s?)*$";
                if (duration.isPresent() && !duration.get().toLowerCase().matches(durationRegex)) {
                    rootBuilder.buildError(ConfigurationException.INVALID_DURATION_CHECKER_DATE, Map.of(
                                    "declaredDuration", duration.get()),
                            NodeSchemaValidator.joinPath(
                                    path,
                                    ConfigurationSchemaNode.OA_CHECKER,
                                    ConfigurationSchemaNode.OA_PARAMS
                            )
                    );
                }
                try {
                    final TemporalAccessor minDate = min
                            .map(Objects.requireNonNull(datePattern)::format)
                            .orElse(LocalDateTime.MIN);
                    final TemporalAccessor maxDate = max
                            .map(datePattern::format)
                            .orElse(LocalDateTime.MAX);
                    yield new DateChecker(
                            CheckerDescription.CheckerDescriptionType.DateChecker,
                            multiplicity,
                            required,
                            datePattern.pattern(),
                            minDate,
                            maxDate,
                            duration.orElse(null));
                } catch (final Exception exception) {
                    rootBuilder.buildError(ConfigurationException.INVALID_MIN_MAX_FOR_CHECKER_DATE, Map.of(
                                    "declaredPattern", Objects.requireNonNull(datePattern).pattern(),
                                    "declaredMinValue", Objects.requireNonNull(min.orElse(null)),
                                    "declaredMaxValue", Objects.requireNonNull(max.orElse(null))),
                            NodeSchemaValidator.joinPath(
                                    path,
                                    ConfigurationSchemaNode.OA_CHECKER,
                                    ConfigurationSchemaNode.OA_PARAMS
                            )
                    );
                    yield DateChecker.BAD_CHECKER;
                }
            }
            case OA_float -> {
                final Float min = Optional.ofNullable(params.findPath(ConfigurationSchemaNode.OA_MIN))
                        .filter(JsonNode::isNumber)
                        .map(JsonNode::floatValue)
                        .orElse(Float.NEGATIVE_INFINITY);
                final Float max = Optional.ofNullable(params.findPath(ConfigurationSchemaNode.OA_MAX))
                        .filter(JsonNode::isNumber)
                        .map(JsonNode::floatValue)
                        .orElse(Float.POSITIVE_INFINITY);
                yield new FloatChecker(
                        CheckerDescription.CheckerDescriptionType.FloatChecker,
                        multiplicity,
                        required,
                        min,
                        max);
            }
            case OA_integer -> {
                final Integer minInteger = Optional.ofNullable(params.findPath(ConfigurationSchemaNode.OA_MIN))
                        .filter(JsonNode::isInt)
                        .map(JsonNode::intValue)
                        .orElse(Integer.MIN_VALUE);
                final Integer maxInteger = Optional.ofNullable(params.findPath(ConfigurationSchemaNode.OA_MAX))
                        .filter(JsonNode::isInt)
                        .map(JsonNode::intValue)
                        .orElse(Integer.MAX_VALUE);
                yield new IntegerChecker(
                        CheckerDescription.CheckerDescriptionType.IntegerChecker,
                        multiplicity,
                        required,
                        minInteger,
                        maxInteger);
            }
            case OA_boolean -> {
                final boolean isTrue = Optional.ofNullable(params.findPath(ConfigurationSchemaNode.OA_IS_TRUE))
                        .map(JsonNode::booleanValue)
                        .orElse(false);
                yield new BooleanChecker(CheckerDescription.CheckerDescriptionType.BooleanChecker, multiplicity, required, isTrue);
            }
            case OA_string -> {
                final String pattern = Optional.ofNullable(params.findPath(ConfigurationSchemaNode.OA_PATTERN))
                        .map(JsonNode::asText)
                        .orElse("");
                yield new StringChecker(CheckerDescription.CheckerDescriptionType.StringChecker, multiplicity, required, pattern);
            }
            case OA_groovyExpression -> {
                final String expression = params.findPath(ConfigurationSchemaNode.OA_EXPRESSION).asText();
                final Set<String> references = rootBuilder.getMapper().convertValue(params.findPath(ConfigurationSchemaNode.OA_REFERENCES), Set.class);
                exceptionMessagesParsing = buildMessagesExceptions(
                        i18n,
                        dataKey,
                        componentKey,
                        NodeSchemaValidator.joinPath(
                                path,
                                ConfigurationSchemaNode.OA_CHECKER,
                                ConfigurationSchemaNode.OA_PARAMS
                        ),
                        params.findPath(ConfigurationSchemaNode.OA_GROOVY_EXCEPTIONS)
                );

                i18n = exceptionMessagesParsing.i18n();
                try {
                    new GroovyExpression(expression);
                } catch (SiOreIllegalArgumentException siOreIllegalArgumentException) {
                    HashMap<String, Object> messages = new HashMap<>();
                    messages.put("expression", expression);
                    messages.put("message", siOreIllegalArgumentException.getMessage());
                    rootBuilder.buildError(ConfigurationException.BAD_GROOVY_EXPRESSION,
                            messages,
                            NodeSchemaValidator.joinPath(
                                    path,
                                    ConfigurationSchemaNode.OA_CHECKER,
                                    ConfigurationSchemaNode.OA_PARAMS,
                                    ConfigurationSchemaNode.OA_EXPRESSION
                            )
                    );
                }
                yield new GroovyExpressionChecker(
                        CheckerDescription.CheckerDescriptionType.GroovyExpressionChecker,
                        multiplicity,
                        required,
                        expression,
                        references,
                        exceptionMessagesParsing.result()
                );
            }
        };
        if (dataKey != null) {
            rootBuilder.getCheckers().computeIfAbsent(
                            checkerDescription.type(),
                            k -> new HashMap<>()
                    )
                    .computeIfAbsent(dataKey, k -> new HashMap<>())
                    .computeIfAbsent(componentKey, k -> new ArrayList<>())
                    .add(checkerDescription);
        }
        return new Parsing<>(i18n, checkerDescription);
    }

    public Parsing<Set<String>> buildMessagesExceptions(I18n i18n,
                                                        String dataKey,
                                                        String componentOrvalidationKey,
                                                        String path,
                                                        JsonNode exceptionsMessagesNode) {
        Set<String> exceptionMessages = new HashSet<>();

        if (exceptionsMessagesNode != null && exceptionsMessagesNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = exceptionsMessagesNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();
                if (valueNode.isObject()) {
                    try {
                        i18n = i18n.add(
                                NodeSchemaValidator.joinI18nPath(
                                        "data",
                                        dataKey,
                                        InternationalizationComponent.EXCEPTIONS,
                                        componentOrvalidationKey,
                                        key
                                ),
                                rootBuilder.getMapper().convertValue(valueNode, Map.class));

                    } catch (final IllegalArgumentException illegalArgumentException) {
                        rootBuilder.buildError(ConfigurationException.UNSUPORTED_I18N_KEY_LANGUAGE,
                                Map.of(),
                                NodeSchemaValidator.joinPath(
                                        ConfigurationSchemaNode.OA_DATA,
                                        key,
                                        ConfigurationSchemaNode.OA_I_18_N
                                )
                        );
                    }
                }
                exceptionMessages.add(key);
            }
        }
        return new Parsing<>(i18n, exceptionMessages);
    }
}