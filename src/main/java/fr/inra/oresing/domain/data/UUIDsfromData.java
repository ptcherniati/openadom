package fr.inra.oresing.domain.data;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UUIDsfromData(Set<UUID> uuidsfromData) {

    public UUIDsfromData() {
        this(new HashSet<>());
    }

    public void addRefsLinkedTo(Map.Entry<String, Map<String, RefsLinkedToValue>> refsLinkedToEntry) {
        Set<UUID> uuids = refsLinkedToEntry.getValue().values().stream()
                .map(RefsLinkedToValue::uuids)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        uuidsfromData()
                .addAll(uuids);
    }
}