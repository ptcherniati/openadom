package fr.inra.oresing.persistence.requestBuilder.data;

import fr.inra.oresing.domain.application.configuration.ComponentDescription;

import java.util.List;
import java.util.Map;

record SelectedComponent(
        String componentKey,
        boolean isSelected,
        boolean isReference,
        boolean isHidden) {
    static final String valuesPathToHide = """
                        #- '{%s}'::text[]
            """;
    static final String refsLinkedToPathToHide = """
                        #- '{%s}'::text[]
            """;

    public static List<BuildRemoveSqlSelectNotInValues> of(List<Map.Entry<String, ComponentDescription>> entries) {
        return entries
                .stream()
                .map(entry -> new BuildRemoveSqlSelectNotInValues(
                        valuesPathToHide.formatted(DataRequestBuilder.sanitize(entry.getKey())),
                        entry.getValue().isReference() ? refsLinkedToPathToHide.formatted(DataRequestBuilder.sanitize(entry.getKey())) : null

                ))
                .toList();
    }
}