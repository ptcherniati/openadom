package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import jakarta.annotation.Nullable;

import java.util.*;

public record ConstantComponentsBuilder(RootBuilder rootBuilder) {


    private ColumnConstantHeaderByHeaderName getColumnConstantHeaderByHeaderColumnName(final String path, final JsonNode importHeaderColumnName, final int constantRowNumber, final String dataKey) {
        final String constantHeaderName = importHeaderColumnName.asText();
        final List<String> listComponentKeys = rootBuilder.getListComponentKeys(dataKey);
        if (!listComponentKeys.contains(constantHeaderName)) {
            rootBuilder.buildError(ConfigurationException.UNKNOWN_COMPONENT_FOR_COMPONENT_NAME, Map.of(
                            "unknownComponent", constantHeaderName,
                            "knownComponents", listComponentKeys),
                    NodeSchemaValidator.joinPath(path, ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_COLUMN_NAME)
            );
        }
        return new ColumnConstantHeaderByHeaderName(
                ConstantImportHeader.ConstantImportHeaderType.ColumnConstantHeaderByHeaderName,
                constantRowNumber,
                constantHeaderName,
                rootBuilder().getLangRestrictions(path, importHeaderColumnName)
        );
    }

    I18n build(final String componentPath,
               final ImmutableMap.Builder<String, ComponentDescription> componentDescriptionBuilder,
               final String key,
               I18n i18n,
               final JsonNode jsonNode,
               final Integer headerLine,
               final Integer firstRowLine) {
        final Iterator<Map.Entry<String, JsonNode>> fields = jsonNode.findPath(ConfigurationSchemaNode.OA_CONSTANT_COMPONENTS).fields();

        while (fields.hasNext()) {
            final Map.Entry<String, JsonNode> constantComponentEntry = fields.next();
            ConstantComponentFields fieldsExtracted = extractConstantComponentFields(constantComponentEntry);
            final String constantComponentKey = fieldsExtracted.constantComponentKey();
            final JsonNode componentNodeValue = fieldsExtracted.componentNodeValue();
            final JsonNode defaultValueNode = fieldsExtracted.defaultValueNode();
            final boolean required = fieldsExtracted.required();
            final Parsing<CheckerDescription> checkerDescriptionParsing = rootBuilder
                .getCheckerDescriptionBuilder()
                .build(
                    i18n,
                    constantComponentKey,
                    required,
                    NodeSchemaValidator.joinPath(
                        componentPath,
                        ConfigurationSchemaNode.OA_CONSTANT_COMPONENTS,
                        constantComponentKey
                    ),
                    componentNodeValue
                        .get(ConfigurationSchemaNode.OA_CHECKER),
                    key);
            i18n = Objects.requireNonNull(checkerDescriptionParsing).i18n();
            Multiplicity multiplicity = Optional.ofNullable(checkerDescriptionParsing.result())
                .map(CheckerDescription::multiplicity)
                .orElse(Multiplicity.ONE);
            Parsing<ComputationChecker> defaultValueParsing;
            if (defaultValueNode != null && !defaultValueNode.isMissingNode()) {
                defaultValueParsing = rootBuilder
                    .getComputationBuilder()
                    .build(
                        i18n,
                        required, multiplicity,
                        NodeSchemaValidator.joinPath(
                            componentPath,
                            ConfigurationSchemaNode.OA_CONSTANT_COMPONENTS,
                            constantComponentKey,
                            ConfigurationSchemaNode.OA_DEFAULT_VALUE
                        ),
                        defaultValueNode);
                i18n = defaultValueParsing.i18n();
                if (defaultValueParsing.result().getReferences() != null) {
                    for (final String reference : defaultValueParsing.result().getReferences()) {
                        if (!rootBuilder.getListDataKeys().contains(reference)) {
                            rootBuilder.buildError(ConfigurationException.UNKNOWN_REFERENCE_NAME, Map.of(
                                    "referenceName", reference,
                                    "allDataNames", rootBuilder.getListDataKeys()),
                                NodeSchemaValidator.joinPath(
                                    componentPath,
                                    ConfigurationSchemaNode.OA_CONSTANT_COMPONENTS,
                                    constantComponentKey,
                                    ConfigurationSchemaNode.OA_DEFAULT_VALUE,
                                    ConfigurationSchemaNode.OA_REFERENCES
                                )
                            );
                        }
                    }
                }
            } else {
                defaultValueParsing = new Parsing<>(i18n, null);
            }
            final Set<Tag> oaTags = TagsBuilder.validateDomainTagNames(
                componentNodeValue,
                NodeSchemaValidator.joinPath(
                    componentPath,
                    ConfigurationSchemaNode.OA_CONSTANT_COMPONENTS,
                    constantComponentKey,
                    ConfigurationSchemaNode.OA_TAGS
                ),
                rootBuilder);
            final ComponentPresenceConstraint mandatory = RootBuilder.isMandatory(componentNodeValue);
            final JsonNode importHeaderNode = componentNodeValue.findPath(ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_TARGET);
            final ConstantImportHeader importHeader = getAndtestConstantImportHeader(importHeaderNode,
                key,
                headerLine,
                firstRowLine,
                NodeSchemaValidator.joinPath(componentPath,
                    ConfigurationSchemaNode.OA_CONSTANT_COMPONENTS,
                    constantComponentKey,
                    ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_TARGET
                )
            );
            final Parsing<String> exportHeaderParsing = rootBuilder.addExportHeaders(key, i18n, constantComponentEntry, ConfigurationSchemaNode.OA_CONSTANT_COMPONENTS);
            String exportHeaderName = null;
            if (exportHeaderParsing != null) {
                i18n = exportHeaderParsing.i18n();
                exportHeaderName = exportHeaderParsing.result();
            }
            componentDescriptionBuilder.put(
                constantComponentKey,
                new ConstantComponent(
                    ComponentDescription.ComponentDescriptionType.ConstantComponent,
                    constantComponentKey,
                    defaultValueParsing.result(),
                    oaTags,
                    required,
                    mandatory,
                    checkerDescriptionParsing.result(),
                    importHeader,
                    exportHeaderName == null ? constantComponentKey : exportHeaderName,
                    null
                ));
        }
        return i18n;
    }

    private ConstantImportHeader getAndtestConstantImportHeader(final JsonNode importHeaderNode, final String dataKey, final Integer headerLine, final Integer firstRowLine, final String path) {
        final JsonNode rowNomberNode = importHeaderNode.findPath(ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_ROW_NUMBER);
        if (rowNomberNode.isMissingNode() || rowNomberNode.isNull() || !rowNomberNode.isInt()) {
            rootBuilder.buildError(
                    ConfigurationException.MISSING_CONSTANT_IMPORT_HEADER_ROW_NUMBER,
                    path
            );
            return null;
        }
        final int constantRowNumber = Optional.of(rowNomberNode)
                .map(JsonNode::asInt)
                .orElse(-1);
        if (constantRowNumber < 1) {
            rootBuilder.buildError(
                    ConfigurationException.NEGATIVE_CONSTANT_IMPORT_HEADER_ROW_NUMBER,
                    NodeSchemaValidator.joinPath(path, ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_ROW_NUMBER)
            );
            return null;
        }
        if (constantRowNumber >= firstRowLine) {
            rootBuilder.buildError(
                    ConfigurationException.BAD_CONSTANT_IMPORT_HEADER_ROW_NUMBER,
                    Map.of(
                            "givenRowNumber", constantRowNumber,
                            "firstRowLine", firstRowLine
                    ),
                    NodeSchemaValidator.joinPath(path, ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_ROW_NUMBER)
            );
            return null;
        }
        final JsonNode importHeaderColumnNumber = importHeaderNode.findPath(ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER);
        final JsonNode importHeaderColumnName = importHeaderNode.findPath(ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_COLUMN_NAME);
        final boolean isMissingImportHeaderColumnNumber = importHeaderColumnNumber.isMissingNode() || importHeaderColumnNumber.isNull();
        if (constantRowNumber < headerLine) {
            return getFileColumnConstantHeader(path, importHeaderColumnNumber, constantRowNumber, isMissingImportHeaderColumnNumber);
        }
        final boolean isMissingImportHeaderColumnName = importHeaderColumnName.isMissingNode() || Strings.isNullOrEmpty(importHeaderColumnName.asText());
        if (isMissingImportHeaderColumnNumber && isMissingImportHeaderColumnName) {
            rootBuilder.buildError(
                    ConfigurationException.MISSING_CONSTANT_IMPORT_HEADER_COLUMN_OR_ROW_NUMBER,
                    Map.of(
                    ),
                    path
            );
            return null;
        }
        if (isMissingImportHeaderColumnName) {
            return getColumnConstantHeaderByColumnNumber(path, importHeaderColumnNumber, constantRowNumber);
        } else {
            return getColumnConstantHeaderByHeaderColumnName(path, importHeaderColumnName, constantRowNumber, dataKey);
        }
    }

    @Nullable
    private ColumnConstantHeaderByColumnNumber getColumnConstantHeaderByColumnNumber(final String path, final JsonNode importHeaderColumnNumber, final int constantRowNumber) {
        final int constantColumnNumber = Optional.ofNullable(importHeaderColumnNumber)
                .map(JsonNode::asInt)
                .filter(i -> i > 0)
                .orElse(-1);
        if (constantColumnNumber <= 0) {
            rootBuilder.buildError(
                    ConfigurationException.NEGATIVE_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER,
                    Map.of(
                    ),
                    NodeSchemaValidator.joinPath(path, ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER)
            );
            return null;
        }
        return new ColumnConstantHeaderByColumnNumber(
                ConstantImportHeader.ConstantImportHeaderType.ColumnConstantHeaderByColumnNumber,
                constantRowNumber,
                constantColumnNumber
        );
    }

    private FileColumnConstantHeader getFileColumnConstantHeader(final String path, final JsonNode importHeaderColumnNumber, final int constantRowNumber, final boolean isMissingImportHeaderColumnNumber) {
        if (isMissingImportHeaderColumnNumber) {
            rootBuilder.buildError(
                    ConfigurationException.MISSING_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER,
                    Map.of(
                    ),
                    path
            );
            return null;
        }
        final int constantColumnNumber = Optional.ofNullable(importHeaderColumnNumber)
                .map(JsonNode::asInt)
                .filter(i -> i > 0)
                .orElse(-1);
        if (constantColumnNumber <= 0) {
            rootBuilder.buildError(
                    ConfigurationException.NEGATIVE_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER,
                    Map.of(
                    ),
                    NodeSchemaValidator.joinPath(path, ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER)
            );
            return null;
        }
        return new FileColumnConstantHeader(
                ConstantImportHeader.ConstantImportHeaderType.FileConstantHeader,
                constantRowNumber,
                constantColumnNumber
        );
    }

    private static ConstantComponentFields extractConstantComponentFields(Map.Entry<String, JsonNode> entry) {
        final String constantComponentKey = entry.getKey();
        final JsonNode componentNodeValue = entry.getValue();
        final JsonNode defaultValueNode = componentNodeValue.findPath(ConfigurationSchemaNode.OA_DEFAULT_VALUE);
        final boolean required = componentNodeValue.findPath(ConfigurationSchemaNode.OA_REQUIRED).asBoolean(false);
        return new ConstantComponentFields(constantComponentKey, componentNodeValue, defaultValueNode, required);
    }

    private record ConstantComponentFields(String constantComponentKey, JsonNode componentNodeValue, JsonNode defaultValueNode, boolean required) {}
}