package fr.inra.oresing.domain.application.configuration;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

public class NodeBuilder {

    public static NodeMainBuilder node() {
        return new NodeMainBuilder();
    }

    public static BuilderNodeBuilder builderNode() {
        return new BuilderNodeBuilder();
    }

    public static class NodeMainBuilder {
        private Integer level = 0;
        private String nodeName;
        private String componentKey;
        private String columnToLookUpForRecursive;
        private String parent;
        private SortedSet<Node> children = new TreeSet<>();
        private Set<String> depends = Set.of();
        private Integer order = 9999;
        private boolean isRecursive = false;

        public NodeMainBuilder level(Integer level) {
            this.level = level;
            return this;
        }

        public NodeMainBuilder nodeName(String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        public NodeMainBuilder componentKey(String componentKey) {
            this.componentKey = componentKey;
            return this;
        }

        public NodeMainBuilder columnToLookUpForRecursive(String columnToLookUpForRecursive) {
            this.columnToLookUpForRecursive = columnToLookUpForRecursive;
            return this;
        }

        public NodeMainBuilder parent(String parent) {
            this.parent = parent;
            return this;
        }

        public NodeMainBuilder children(SortedSet<Node> children) {
            this.children = children;
            return this;
        }

        public NodeMainBuilder depends(Set<String> depends) {
            this.depends = depends;
            return this;
        }

        public NodeMainBuilder order(Integer order) {
            this.order = order;
            return this;
        }

        public NodeMainBuilder isRecursive(boolean isRecursive) {
            this.isRecursive = isRecursive;
            return this;
        }

        public Node build() {
            return new Node(level, nodeName, componentKey, columnToLookUpForRecursive, parent, children, depends, order, isRecursive);
        }
    }

    public static class BuilderNodeBuilder {
        private int level = 0;
        private String nodeName;
        private String componentKey;
        private String columnToLookUpForRecursive;
        private BuilderNode parent;
        private Set<String> children = Set.of();
        private Set<String> depends = Set.of();
        private Integer order = 9999;
        private boolean isRecursive = false;

        public BuilderNodeBuilder level(int level) {
            this.level = level;
            return this;
        }

        public BuilderNodeBuilder nodeName(String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        public BuilderNodeBuilder componentKey(String componentKey) {
            this.componentKey = componentKey;
            return this;
        }

        public BuilderNodeBuilder columnToLookUpForRecursive(String columnToLookUpForRecursive) {
            this.columnToLookUpForRecursive = columnToLookUpForRecursive;
            return this;
        }

        public BuilderNodeBuilder parent(BuilderNode parent) {
            this.parent = parent;
            return this;
        }

        public BuilderNodeBuilder children(Set<String> children) {
            this.children = children;
            return this;
        }

        public BuilderNodeBuilder depends(Set<String> depends) {
            this.depends = depends;
            return this;
        }

        public BuilderNodeBuilder order(Integer order) {
            this.order = order;
            return this;
        }

        public BuilderNodeBuilder isRecursive(boolean isRecursive) {
            this.isRecursive = isRecursive;
            return this;
        }

        public BuilderNode build() {
            return new BuilderNode(level, nodeName, componentKey, columnToLookUpForRecursive, parent, children, depends, order, isRecursive);
        }
    }
}