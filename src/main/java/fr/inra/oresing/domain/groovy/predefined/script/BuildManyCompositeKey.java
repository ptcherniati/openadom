package fr.inra.oresing.domain.groovy.predefined.script;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.Ltree;
import groovy.lang.Closure;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public record BuildManyCompositeKey() implements ScriptConstantProvider {
    @Override
    public void bindToContext(Map<String, Object> context) {
        Function<String, String> nullOrEmptyToNull = partialKey -> Strings.isNullOrEmpty(partialKey) ? Ltree.NULL_KEY : partialKey;

        Closure<String> buildManyCompositeKey = new Closure<String>(this) {
            public String doCall(List<String> labels) {
                Map<String, String> datum = (Map<String, String>) context.get("datum");

                // Récupérer les valeurs pour chaque label et les diviser en listes
                List<List<String>> valuesList = labels.stream()
                        .map(label -> datum.getOrDefault(label, ""))
                        .map(value -> Arrays.asList(value.split(",")))
                        .toList();

                // Trouver la taille maximale des listes de valeurs
                int maxSize = valuesList.stream()
                        .mapToInt(List::size)
                        .max()
                        .orElse(0);

                if (maxSize == 0) {
                    return "";
                }

                List<String> compositeKeys = new ArrayList<>();
                for (int i = 0; i < maxSize; i++) {
                    final int index = i;
                    // Récupérer la liste des valeurs pour la position i
                    List<String> valuesAtIndex = valuesList.stream()
                            .map(values -> index < values.size() ? values.get(index) : "")
                            .map(String::trim)
                            .toList();
                    String compositeKey = BuildCompositeKey.buildNaturelKeyFromLabels(valuesAtIndex);
                    compositeKeys.add(compositeKey);
                }

                // Joindre toutes les clés composites avec une virgule
                return String.join(",", compositeKeys);
            }
        };
        context.put("OA_buildManyCompositeKey", buildManyCompositeKey);
    }
}