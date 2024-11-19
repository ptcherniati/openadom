package fr.inra.oresing.domain.data;

import java.util.Set;

public record RefsLinkedToValue(Set<java.util.UUID> uuids, fr.inra.oresing.domain.application.configuration.Ltree  hierarchicalKey) {
}
