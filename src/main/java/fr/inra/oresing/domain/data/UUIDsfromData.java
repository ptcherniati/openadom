package fr.inra.oresing.domain.data;


import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public record UUIDsfromData(Set<UUID> uuidsfromData) {

    public UUIDsfromData() {
        this(new HashSet<>());
    }

    public void addRefsLinkedTo(RefsLinked refsLinked) {
        uuidsfromData()
                .add(refsLinked.id());
    }
}