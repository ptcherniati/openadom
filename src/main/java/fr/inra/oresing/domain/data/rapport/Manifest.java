package fr.inra.oresing.domain.data.rapport;

import fr.inra.oresing.persistence.data.read.bundle.FileContent;

import java.util.*;
import java.util.stream.Collectors;

public record Manifest(
        Map<String, List<FileContent>> referenceTypeFiles,
        Map<String, List<String>> referenceTypeDeps
) {
    public Manifest() {
        this(new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    public void add(String reference, FileContent fileContent) {
        referenceTypeFiles
                .computeIfAbsent(reference, k -> new ArrayList<>())
                .add(fileContent);

        List<String> deps = fileContent.refsLinked();
        referenceTypeDeps
                .computeIfAbsent(reference, k -> new ArrayList<>())
                .addAll(deps);
    }

    public Map<String, List<String>> orderedReferenceTypes() {
        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String node : referenceTypeDeps.keySet()) {
            visit(node, sorted, visited, visiting);
        }
        return sorted.stream()
                .filter(referenceTypeFiles()::containsKey)
                .collect(
                LinkedHashMap::new,
                (map, referenceType) -> map.put(
                        referenceType,
                        referenceTypeFiles().get(referenceType).stream()
                                .map(FileContent::fileName)
                                .collect(Collectors.toCollection(LinkedList::new))
                ),
                Map::putAll
        );
    }

    private void visit(String node, List<String> sorted, Set<String> visited, Set<String> visiting) {
        if (visited.contains(node)) return;
        if (visiting.contains(node)) throw new RuntimeException("Cycle detected");
        visiting.add(node);
        for (String dep : referenceTypeDeps.getOrDefault(node, Collections.emptyList())) {
            visit(dep, sorted, visited, visiting);
        }
        visiting.remove(node);
        visited.add(node);
        sorted.add(node);
    }
}