package fr.inra.oresing.persistence.normalized;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record SelectBuilder(List<String> select) {
    public String buildSelects() {
        return select().stream()
                .filter(Objects::nonNull)
                .collect(Collectors.joining(",\n\t\t"));
    }
}