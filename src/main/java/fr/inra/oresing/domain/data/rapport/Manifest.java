package fr.inra.oresing.domain.data.rapport;

import fr.inra.oresing.domain.data.deposit.bundle.BundleFileContent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record Manifest(
        Map<String, List<BundleFileContent>> referenceTypeFiles,
        Map<String, List<BundleFileContent>> referenceFilesInErrors,
        Map<String, List<String>> referenceTypeDeps
) {
    public Manifest() {
        this(new ConcurrentHashMap<>(), new ConcurrentHashMap<>(), new ConcurrentHashMap<>());
    }

    public synchronized void add(String reference, BundleFileContent fileContent) {
        referenceTypeFiles
                .computeIfAbsent(reference, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(fileContent);

        List<String> deps = fileContent.refsLinked().stream()
                .filter(Predicate.not(reference::equals))
                .toList();
        referenceTypeDeps
                .computeIfAbsent(reference, k -> Collections.synchronizedList(new ArrayList<>()))
                .addAll(deps);
    }

    public synchronized void addError(String reference, BundleFileContent fileContent) {
        referenceFilesInErrors
                .computeIfAbsent(reference, k -> Collections.synchronizedList(new ArrayList<>()))
                .add(fileContent);
    }

    public synchronized Map<String, List<String>> orderedReferenceTypes() {
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
                                        .map(BundleFileContent::refsLinked)
                                        .flatMap(Collection::stream)
                                        .filter(Predicate.not(referenceType::equals))
                                        .collect(Collectors.toCollection(LinkedList::new))
                        ),
                        Map::putAll
                );
    }

    private void visit(String node, List<String> sorted, Set<String> visited, Set<String> visiting) {
        if (visited.contains(node)) return;
        if (visiting.contains(node)) {
            throw new RuntimeException("Cycle detected");
        }
        visiting.add(node);

        // FILTRE les auto-références lors du parcours
        referenceTypeDeps.getOrDefault(node, Collections.emptyList()).stream()
                .filter(dep -> !dep.equals(node)) // EXCLURE l'auto-référence
                .forEach(dep -> visit(dep, sorted, visited, visiting));

        visiting.remove(node);
        visited.add(node);
        sorted.add(node);
    }
}