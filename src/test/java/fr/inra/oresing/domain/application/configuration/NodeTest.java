package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

@Tag("core.config")
class NodeTest {

    @Test
    void testNodeBuilder() {
        SortedSet<Node> children = new TreeSet<>();
        children.add(NodeBuilder.node().nodeName("child1").build());
        Set<String> depends = Set.of("dep1");

        Node node = NodeBuilder.node()
                .level(1)
                .nodeName("node1")
                .componentKey("key1")
                .columnToLookUpForRecursive("col1")
                .parent("parent1")
                .children(children)
                .depends(depends)
                .order(1)
                .isRecursive(true)
                .build();

        Assertions.assertEquals(1, node.level());
        Assertions.assertEquals("node1", node.nodeName());
        Assertions.assertEquals("key1", node.componentKey());
        Assertions.assertEquals("col1", node.columnToLookUpForRecursive());
        Assertions.assertEquals("parent1", node.parent());
        Assertions.assertEquals(children, node.children());
        Assertions.assertEquals(depends, node.depends());
        Assertions.assertEquals(1, node.order());
        Assertions.assertTrue(node.isRecursive());
    }

    @Test
    void testBuilderNodeBuilder() {
        BuilderNode parent = NodeBuilder.builderNode().nodeName("parent").build();
        Set<String> children = Set.of("child1");
        Set<String> depends = Set.of("dep1");

        BuilderNode node = NodeBuilder.builderNode()
                .level(1)
                .nodeName("node1")
                .componentKey("key1")
                .columnToLookUpForRecursive("col1")
                .parent(parent)
                .children(children)
                .depends(depends)
                .order(1)
                .isRecursive(true)
                .build();

        Assertions.assertEquals(1, node.level());
        Assertions.assertEquals("node1", node.nodeName());
        Assertions.assertEquals("key1", node.componentKey());
        Assertions.assertEquals("col1", node.columnToLookUpForRecursive());
        Assertions.assertEquals(parent, node.parent());
        Assertions.assertEquals(children, node.children());
        Assertions.assertEquals(depends, node.depends());
        Assertions.assertEquals(1, node.order());
        Assertions.assertTrue(node.isRecursive());
    }
}