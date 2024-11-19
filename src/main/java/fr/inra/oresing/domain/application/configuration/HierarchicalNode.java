package fr.inra.oresing.domain.application.configuration;

public record HierarchicalNode(Node node) {
    public boolean isRecursive() {
        return node().isRecursive();
    }
}
