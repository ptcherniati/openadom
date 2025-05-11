package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.util.Strings;

import jakarta.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class NodeSchemaValidator {
    public static final String REFERENCE_SCOPES_FOR_FILE = "referenceScopesForFile";
    static final String PATH_SEPARATOR = " > ";
    static final String I18N_PATH_SEPARATOR = ".";
    final RootBuilder rootBuilder;

    public NodeSchemaValidator(RootBuilder rootBuilder) {
        super();
        this.rootBuilder = rootBuilder;
    }

    
    private static String joinPath(List<String> pathes) {
        return pathes.stream()
                .filter(Strings::isNotEmpty)
                .collect(Collectors.joining(PATH_SEPARATOR));
    }

    public static String joinPath(String path, Set<String> pathes) {
        List<String> allPathes = new LinkedList<>();
        allPathes.add(path);
        allPathes.addAll(pathes);
        return joinPath(allPathes);
    }

    public static String joinI18nPath(String... pathes) {
        return String.join(I18N_PATH_SEPARATOR, pathes)
                .replace(PATH_SEPARATOR, I18N_PATH_SEPARATOR);
    }

    public static String joinPath(String... pathes) {
        return String.join(PATH_SEPARATOR, pathes);
    }

    AtomicBoolean testSchema(ConfigurationSchemaNodeType parentSchema, JsonNode node, String path) {
        ConfigurationSchemaNodeType parentSchema1 = parentSchema;
        if (parentSchema1 instanceof FinalType finalType) {
            return new AtomicBoolean(testFinalNodeValuetype(finalType, node, path));
        }
        if (null == parentSchema1) throw new IllegalArgumentException("schema is not described for %s".formatted(path));
        if (parentSchema1 instanceof CheckerFactory checkerFactory) {
            parentSchema1 = testCheckerSection(node, path);
        } else if (null == parentSchema1.sectionBuilder())
            throw new IllegalArgumentException("schema is not described for %s".formatted(path));
        Set<String> labels = new HashSet<>();
        node.fieldNames().forEachRemaining(labels::add);
        if (testNodeLabels(Objects.requireNonNull(parentSchema1), path, labels)) return new AtomicBoolean(false);
        return testChildrenNodeSchema(parentSchema1, path, node, new AtomicBoolean(true));
    }

    private CheckerType testCheckerSection(JsonNode node, String path) {
        try {
            Optional.ofNullable(node)
                    .map(checkerNode -> checkerNode.findPath(ConfigurationSchemaNode.OA_NAME))
                    .map(JsonNode::asText)
                    .map(CheckerFactory::getCheckerTypeForName).
                    orElse(null);
            return null;
        } catch (SiOreConfigurationFormatException e) {
            rootBuilder.buildError(e.getException(), e.getParams(), path);
        }
        String checkerName = Optional.of(node)
                .map(checkerNode -> checkerNode.findPath(ConfigurationSchemaNode.OA_NAME))
                .map(JsonNode::asText)
                .orElse("");
        rootBuilder.buildError(ConfigurationException.UNKNOWN_CHECKER_NAME, Map.of(
                "acceptedCheckerNames", EnumType.CHECKER_NAME_ENUM.values(),
                "checkerName", checkerName
        ), path);
        return null;
    }

    private boolean testFinalNodeValuetype(FinalType finalType, JsonNode node, String path) {
        boolean noNode = null == node || node.isNull() || node.isMissingNode();
        boolean cantBeEmpty = finalType.required() || !finalType.nullable();
        if (cantBeEmpty && noNode) {
            if (finalType instanceof EnumType enumType) {
                rootBuilder.buildError(ConfigurationException.MISSING_REQUIRED_ENUM_VALUE, Map.of("acceptedValues", enumType.values()), path);
                return false;
            }
            rootBuilder.buildError(ConfigurationException.MISSING_REQUIRED_VALUE, path);
            return false;
        }
        boolean emptyNode = noNode || node.asText().isEmpty();
        if (emptyNode) {
            return true;
        }
        return switch (finalType) {
            case BooleanType booleanType
                    when !node.isBoolean() -> {
                rootBuilder.buildError(ConfigurationException.BAD_BOOLEAN_REQUIRED_SECTIONS, Map.of("givenValue", node.asText()), path);
                yield false;
            }
            case EnumType enumType
                    when !enumType.values().contains(node.asText()) -> {
                rootBuilder.buildError(ConfigurationException.BAD_ENUM_SECTION_TYPE, Map.of("givenValue", node.asText(), "acceptedValues", enumType.values()), path);
                yield false;
            }
            case FloatType floatType
                    when !node.isFloat() && !node.isDouble() -> {
                rootBuilder.buildError(ConfigurationException.BAD_FLOAT_REQUIRED_SECTIONS, Map.of("givenValue", node.asText()), path);
                yield false;
            }/*
            case I18nType i18nType -> {
                try {
                    if (!rootBuilder.getMapper().readValue(node.binaryValue(), Map.class)
                            .values().stream()
                            .allMatch(String.class::isInstance)) {
                        rootBuilder.buildError(ConfigurationException.BAD_LOCALE_SECTION_TYPE, Map.of(), path);
                        yield false;
                    }
                    yield true;
                } catch (IOException e) {
                    rootBuilder.buildError(ConfigurationException.BAD_LOCALE_SECTION_TYPE, Map.of(), path);
                    yield false;
                }
            }*/
            case IntegerType integerType
                    when !node.isInt() -> {
                rootBuilder.buildError(ConfigurationException.BAD_INTEGER_REQUIRED_SECTIONS, Map.of("givenValue", node.asText()), path);
                yield false;
            }
            case StringType stringType -> true;
            case final BooleanType booleanType -> true;
            case final EnumType enumType -> true;
            case final FloatType floatType -> true;
            case final IntegerType integerType -> true;
            case null -> true;
        };
    }

    private AtomicBoolean testChildrenNodeSchema(ConfigurationSchemaNodeType rootSchema, String path, JsonNode childNode, AtomicBoolean areChildrenValid) {
        Iterator<Map.Entry<String, JsonNode>> childrenIterator = childNode.fields();
        while (childrenIterator.hasNext()) {
            Map.Entry<String, JsonNode> entry = childrenIterator.next();
            String childLabel = entry.getKey();
            JsonNode child = entry.getValue();

            Optional<ConfigurationSchemaNodeType> schema;
            if(path.contains(ConfigurationSchemaNode.OA_FILE_NAME) && ConfigurationSchemaNode.OA_REFERENCE_SCOPES.equals(childLabel)){
                schema = rootSchema.sectionBuilder().findSchema(REFERENCE_SCOPES_FOR_FILE);

            }else{
                schema = rootSchema.sectionBuilder().findSchema(childLabel);
            }
            schema
                    .map(childrenSchema -> switch (childrenSchema) {
                        case FinalType finalType -> finalType;
                        case CheckerFactory checkerFactory
                                when Strings.isNotEmpty(childNode.findPath(ConfigurationSchemaNode.OA_NAME).asText())-> {
                                String checkerName = childNode.findPath(ConfigurationSchemaNode.OA_NAME).asText();
                                try {
                                    yield CheckerFactory.getCheckerTypeForName(
                                        checkerName);
                                } catch (final SiOreConfigurationFormatException siOreConfigurationFormatException) {
                                    rootBuilder.buildError(siOreConfigurationFormatException.getException(), siOreConfigurationFormatException.getParams(), path);
                                    yield null;
                                }
                        }
                        case CheckerFactory checkerFactory -> {
                            List<String> pathes = List.of(path, childLabel);
                            pathes = new LinkedList<>(pathes);
                            pathes.add(ConfigurationSchemaNode.OA_NAME);
                            rootBuilder.buildError(
                                    ConfigurationException.MISSING_CHECKER_NAME,
                                    Map.of(
                                            "acceptedCheckerNames", CheckerEnum.VALUES
                                    ),
                                    joinPath(pathes));
                            yield null;
                        }
                        case CollectionType.ArrayType arrayType when arrayType.type() == null -> {
                            int index = 0;
                            yield  switch (childNode.get(childLabel)){
                                case final ArrayNode arrayNode ->{
                                    for (final JsonNode jsonElement : arrayNode) {
                                        areChildrenValid.compareAndSet(false,
                                                testSchema(null,
                                                        jsonElement,
                                                        joinPath(List.of(path, childLabel, Integer.toString(index++)))).get());
                                    }
                                    yield null;
                                }
                                case final NullNode nullNode -> addErrorForExpectingValue(path, arrayType, childLabel);
                                case null -> addErrorForExpectingValue(path, arrayType, childLabel);
                                default-> addErrorForBadArrayValue(path, childLabel);
                            };
                        }
                        case CollectionType.ArrayType arrayType -> switch (childNode.get(childLabel)){
                            case final ArrayNode arrayNode -> {
                                AtomicInteger index = new AtomicInteger(0);
                                for (final JsonNode jsonElement : childNode.get(childLabel)) {
                                    List<String> componentsForIteration = List.of(ConfigurationSchemaNode.OA_COMPONENTS, ConfigurationSchemaNode.OA_COMPONENT_QUALIFIERS, ConfigurationSchemaNode.OA_COMPONENT_ADJACENTS);
                                    if (componentsForIteration.contains(childLabel)) {
                                        jsonElement.fields().forEachRemaining(nodeEntry -> areChildrenValid
                                                .compareAndSet(false,
                                                        testSchema(
                                                                arrayType.type(),
                                                                nodeEntry.getValue(),
                                                                joinPath(List.of(path, childLabel, Integer.toString(index.getAndIncrement()), nodeEntry.getKey()))
                                                        ).get()
                                                ));
                                        yield null;
                                    }
                                    areChildrenValid
                                            .compareAndSet(false,
                                                    testSchema(
                                                            arrayType.type(),
                                                            jsonElement,
                                                            joinPath(List.of(path, childLabel, Integer.toString(index.getAndIncrement())))
                                                    ).get()
                                            );
                                }
                                yield null;
                            }
                            case final NullNode nullNode -> addErrorForExpectingValue(path, arrayType, childLabel);
                            case null -> addErrorForExpectingValue(path, arrayType, childLabel);
                            default-> addErrorForBadArrayValue(path, childLabel);
                        };
                        case CollectionType.MapType mapType when mapType.type() == null -> mapType;
                        case CollectionType.MapType mapType -> {
                            List<String> identificateurs = new LinkedList<>();
                            childNode.get(childLabel).fieldNames().forEachRemaining(identificateurs::add);
                            if(!ConfigurationSchemaNode.OA_GROOVY_EXCEPTIONS.equals(childLabel)) {
                                testIdentificateurs(identificateurs, path);
                            }
                            identificateurs.forEach(label -> areChildrenValid
                                    .compareAndSet(false,
                                            testSchema(
                                                    mapType.type(),
                                                    childNode.findPath(childLabel).get(label),
                                                    joinPath(List.of(path, childLabel, label))
                                            ).get()
                                    ));
                            yield null;
                        }
                        case ApplicationType applicationType -> applicationType;
                        case IntermediaryType.CheckerParamType checkerParamType -> checkerParamType;
                    })
                    .ifPresent(schemaNode -> {
                        String newPath = childLabel;
                        if (Strings.isNotEmpty(path)) {
                            newPath = joinPath(List.of(path, childLabel));
                        }
                        areChildrenValid.compareAndSet(false, testSchema(schemaNode, child, newPath).get());
                    });
        }
        return areChildrenValid;
    }

    @Nullable
    private ConfigurationSchemaNodeType addErrorForBadArrayValue(final String path, final String childLabel) {
        rootBuilder.buildError(
                ConfigurationException.EXPECTED_ARRAY,
                Map.of(),
                joinPath(List.of(path, childLabel))
        );
        return null;
    }

    @Nullable
    private ConfigurationSchemaNodeType addErrorForExpectingValue(final String path, final CollectionType.ArrayType arrayType, final String childLabel) {
        if(arrayType.nullable() && !arrayType.required()){
            return null;
        }
        rootBuilder.buildError(
                ConfigurationException.MISSING_REQUIRED_VALUE,
                Map.of(),
                joinPath(List.of(path, childLabel))
        );
        return null;
    }

    private void testIdentificateurs(final List<String> identificateurs, final String path) {
        final Predicate<String> isInvalidPAttern = name -> !Configuration.getIsValidSectionIdentifierPattern().test(name);
        final List<String> invalidIdentificateurs = identificateurs.stream()
                .filter(isInvalidPAttern)
                .toList();
        if (CollectionUtils.isNotEmpty(invalidIdentificateurs)) {
            rootBuilder.buildError(
                    ConfigurationException.INVALID_IDENTIFICATEURS,
                    Map.of(
                            "invalidIdentificateurs", invalidIdentificateurs
                    ),
                    path
            );
        }
    }

    private boolean testNodeLabels(ConfigurationSchemaNodeType rootSchema, String path, Set<String> labels) {
        List<SiOreConfigurationFormatException> configurationFormatExceptions = new LinkedList<>();
        Consumer<SiOreConfigurationFormatException> buildLabelsErrors = e ->
                rootBuilder.buildError(e.getException(), e.getParams(), joinPath(path, labels));
        try {
            rootSchema.sectionBuilder().test(labels);
        } catch (SiOreConfigurationFormatException e) {
            configurationFormatExceptions.add(e);
        }
        if (CollectionUtils.isNotEmpty(configurationFormatExceptions)) {
            configurationFormatExceptions.forEach(buildLabelsErrors);
            return true;
        }
        return false;
    }
}