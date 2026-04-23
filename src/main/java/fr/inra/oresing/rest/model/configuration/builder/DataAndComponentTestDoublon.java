package fr.inra.oresing.rest.model.configuration.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import org.flywaydb.core.internal.util.CollectionsUtils;

import java.util.*;

import static fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode.*;

public class DataAndComponentTestDoublon extends HashMap<String, Map<String, List<String>>> {
    public static final String[] COMPONENT_SECTIONS = {
            OA_BASIC_COMPONENTS,
            OA_CONSTANT_COMPONENTS,
            OA_DYNAMIC_COMPONENTS,
            OA_COMPUTED_COMPONENTS,
            OA_PATTERN_COMPONENTS
    };
    private final Map<String, Map<String, Map<String, List<String>>>> duplicatedInPattern = new HashMap<>();
    private final RootBuilder rootBuilder;
    private final Map<String, Map<String, List<String>>> headers = new HashMap<>();
    private boolean hasErrors;

    public DataAndComponentTestDoublon(final RootBuilder rootBuilder) {
        super();
        this.rootBuilder = rootBuilder;
    }

    private static void addImportHeader(final JsonNode dataNodes, final String path, final Map<String, List<String>> headerForData) {
        JsonNode currentNode = dataNodes;
        boolean first = true;
        String headerName;
        final String[] split = path.split(NodeSchemaValidator.PATH_SEPARATOR);
        for (int i = 0; i < split.length; i++) {
            final String label = split[i];
            if (first) {
                first = false;
                continue;
            }
            currentNode = currentNode.findPath(label);
            headerName = currentNode.findPath(OA_IMPORT_HEADER).findPath(OA_HEADER_NAME).asText(label);
            if (i == split.length - 1) {
                addHeaderPath(currentNode, path, headerName, headerForData);
            }
        }
    }



    private static void addHeaderPath(JsonNode node, String path, String headerName, Map<String, List<String>> headerForData) {
        final boolean noHeader = node.findPath(OA_IMPORT_HEADER).findPath(OA_HEADER_NAME).isMissingNode() || node.findPath(OA_IMPORT_HEADER).findPath(OA_HEADER_NAME).isNull();
        final String importHeaderPath = noHeader ? path : NodeSchemaValidator.joinPath(path, OA_IMPORT_HEADER, OA_HEADER_NAME);
        headerForData.computeIfAbsent(headerName, l -> new LinkedList<>())
                .add(importHeaderPath);
    }

    public void testUniqueComponentsForData(final JsonNode dataNodes) {
        Arrays.stream(COMPONENT_SECTIONS)
                .forEach(component -> testUniqueComponentsForData(dataNodes, component));
        final List<Entry<String, List<String>>> duplicatedComponentLabels = values().stream()
                .flatMap(map -> map.entrySet().stream())
                .filter(entry -> entry.getValue().size() > 1)
                .toList();
        if (!duplicatedComponentLabels.isEmpty()) {
            duplicatedComponentLabels
                    .forEach(entry -> rootBuilder.buildError(
                            ConfigurationException.DUPLICATED_COMPONENT_NAME,
                            Map.of(
                                    "componentName", entry.getKey(),
                                    "duplicatedPathes", entry.getValue()
                            ),
                            entry.getValue().getLast()
                    ));
            hasErrors = true;
            return;
        }
        for (Entry<String, Map<String, Map<String, List<String>>>> dataEntry : duplicatedInPattern.entrySet()) {
            String dataName = dataEntry.getKey();
            Map<String, Map<String, List<String>>> duplicatedByComponentKey = dataEntry.getValue();
            for (Entry<String, Map<String, List<String>>> componentEntry : duplicatedByComponentKey.entrySet()) {
                String componentName = componentEntry.getKey();
                for (Entry<String, List<String>> duplicatedByComponenComponentKeyEntry : componentEntry.getValue().entrySet()) {
                    String componentComponentKey = duplicatedByComponenComponentKeyEntry.getKey();
                    List<String> duplicatedPathes = duplicatedByComponenComponentKeyEntry.getValue();
                    if (CollectionsUtils.hasItems(duplicatedPathes) && duplicatedPathes.size() > 1) {
                        List<String> patternsOfComponentComponent = Arrays.stream(componentComponentKey.split(Column.COLUMN_IN_COLUMN_SEPARATOR)).toList();
                        rootBuilder.buildError(
                                ConfigurationException.DUPLICATED_COMPONENT_HEADER_IN_PATTERN_COMPONENT,
                                Map.of(
                                        "data", dataName,
                                        "patternComponent", componentName,
                                        "qualifierName", patternsOfComponentComponent.get(1),
                                        "duplicatedPathes", duplicatedPathes
                                ),
                                duplicatedPathes.getLast()
                        );
                    }
                }
            }
        }
    }

    private void testUniqueComponentsForData(final JsonNode dataNodes, final String componentType) {
        dataNodes.fieldNames().forEachRemaining(dataName -> dataNodes.get(dataName)
                .findPath(componentType)
                .fieldNames()
                .forEachRemaining(componentName -> {
                    final String path = NodeSchemaValidator.joinPath(OA_DATA, dataName, componentType, componentName);
                    addPathesForNode(dataNodes, dataName, componentName, path);
                    if (OA_PATTERN_COMPONENTS.equals(componentType)) {
                        for (final JsonNode componentComponentNode : dataNodes
                                .findPath(dataName)
                                .findPath(OA_PATTERN_COMPONENTS)
                                .findPath(componentName)
                                .findPath(OA_COMPONENT_QUALIFIERS)) {
                            componentComponentNode.fieldNames().forEachRemaining(qualifierName ->
                            {
                                final String componentComponentPath = NodeSchemaValidator.joinPath(OA_DATA, dataName, componentType, componentName, OA_COMPONENT_QUALIFIERS, qualifierName);

                                String qualifierKey = Column.COLUMN_IN_COLUMN_PATTERN.formatted(componentName, qualifierName);
                                addPathesForPatternNode(componentComponentNode, dataName, componentName, qualifierKey, componentComponentPath);
                            });
                        }
                        for (final JsonNode componentComponentNode : dataNodes
                                .findPath(dataName)
                                .findPath(OA_PATTERN_COMPONENTS)
                                .findPath(componentName)
                                .findPath(OA_COMPONENT_ADJACENTS)) {
                            componentComponentNode.fieldNames().forEachRemaining(adjacentName ->
                            {
                                final String componentComponentPath = NodeSchemaValidator.joinPath(OA_DATA, dataName, componentType, componentName, OA_COMPONENT_ADJACENTS, adjacentName);
                                String qualifierKey = Column.COLUMN_IN_COLUMN_PATTERN.formatted(componentName, adjacentName);
                                addPathesForPatternNode(componentComponentNode, dataName, componentName, qualifierKey, componentComponentPath);
                            });
                        }
                    }
                }));
    }

    private void addPathesForNode(final JsonNode dataNodes, final String dataName, final String componentName, final String path) {
        computeIfAbsent(dataName, l -> new HashMap<>())
                .computeIfAbsent(componentName, l -> new LinkedList<>())
                .add(path);
        addImportHeader(
                dataNodes,
                path,
                headers
                        .computeIfAbsent(dataName, l -> new HashMap<>())
        );
    }

    private void addPathesForPatternNode(final JsonNode dataNodes, final String dataName, final String componentName, final String qualifierOrAdjacentName, final String path) {
        duplicatedInPattern.computeIfAbsent(dataName, l -> new HashMap<>())
                .computeIfAbsent(componentName, l -> new HashMap<>())
                .computeIfAbsent(qualifierOrAdjacentName, l -> new LinkedList<>())
                .add(path);
        addHeaderPath(
                dataNodes.findPath(qualifierOrAdjacentName),
                path,
                qualifierOrAdjacentName,
                headers
                        .computeIfAbsent(dataName, l -> new HashMap<>())
        );
    }

    public List<String> listDataKeys() {
        return keySet().stream().toList();
    }

    public Map<String, List<String>> listReferencableComponentKeysByDataKey() {
        final ImmutableMap.Builder<String, List<String>> builder = new ImmutableMap.Builder<>();
        forEach((key, value) -> {
            final ImmutableList.Builder<String> components = new ImmutableList.Builder<>();
            for (final Entry<String, List<String>> componentEntry : value.entrySet()) {
                if (componentEntry.getValue().stream().anyMatch(path -> path.matches(".*(" + OA_BASIC_COMPONENTS + "|" + OA_CONSTANT_COMPONENTS + "|" + OA_PATTERN_COMPONENTS + "|" + OA_COMPUTED_COMPONENTS + ").*"))) {
                    components.add(componentEntry.getKey());
                }
            }
            builder
                    .put(key, components.build());
        });
        return builder.build();
    }

    public Map<String, List<String>> listComponentImportHeaderByDataKey() {
        final ImmutableMap.Builder<String, List<String>> builder = new ImmutableMap.Builder<>();
        for (final Entry<String, Map<String, List<String>>> entry : entrySet()) {
            builder.put(entry.getKey(), entry.getValue().keySet().stream().toList());
        }
        return builder.build();
    }

    public boolean hasErrors() {
        return hasErrors;
    }
}