package fr.inra.oresing.persistence.normalized;

import java.util.stream.Collectors;

public record BuildIndexes(java.util.List<String> indexes) {

    public String buildIndexes() {
        return indexes().stream()
                .collect(Collectors.joining("\n"));
    }
}