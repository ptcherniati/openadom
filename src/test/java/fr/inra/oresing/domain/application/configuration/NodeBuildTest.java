package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de caractérisation pour {@link Node#buildNode}.
 *
 * <p>Vérifie notamment :
 * <ul>
 *   <li>Idempotence : deux appels successifs avec les mêmes entrées produisent
 *       des résultats structurellement équivalents</li>
 *   <li>Non-contamination : les {@link BuilderNode} sources ne sont pas mutés</li>
 *   <li>Arbre parent-enfant : l'arbre construit reflète correctement la hiérarchie</li>
 * </ul>
 */
@Tag("core.config")
@Tag("domain.model")
class NodeBuildTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    /**
     * Snapshots le contenu structurel relevant d'un SortedSet de nodes
     * pour pouvoir le comparer plus tard sans être sensible à l'identité des objets.
     */
    private static String snapshot(SortedSet<Node> nodes) {
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) {
            sb.append(n.nodeName())
              .append(":level=").append(n.level())
              .append(":recursive=").append(n.isRecursive())
              .append(":children=[");
            n.children().forEach(c -> sb.append(c.nodeName()).append(","));
            sb.append("]:depends=[");
            new TreeSet<>(n.depends()).forEach(d -> sb.append(d).append(","));
            sb.append("]; ");
        }
        return sb.toString();
    }

    private static BuilderNode builderNode(String name, int order) {
        return new BuilderNode(0, name, null, null, null,
                new TreeSet<>(), new TreeSet<>(), order, false);
    }

    private static BuilderNode builderNodeWithDep(String name, int order, String... deps) {
        return new BuilderNode(0, name, null, null, null,
                new TreeSet<>(), new TreeSet<>(Arrays.asList(deps)), order, false);
    }

    // -----------------------------------------------------------------------
    // 1. Idempotence : deux appels identiques → résultats équivalents
    // -----------------------------------------------------------------------

    @Test
    void buildNode_idempotent_sameInputProducesSameOutput() {
        List<BuilderNode> inputs = List.of(
                builderNode("a", 1),
                builderNodeWithDep("b", 2, "a"),
                builderNodeWithDep("c", 3, "b")
        );

        SortedSet<Node> result1 = Node.buildNode(inputs);
        SortedSet<Node> result2 = Node.buildNode(inputs);

        assertEquals(snapshot(result1), snapshot(result2),
                "Deux appels successifs à buildNode doivent produire des résultats structurellement équivalents");
    }

    // -----------------------------------------------------------------------
    // 2. Non-contamination : les BuilderNode sources ne doivent pas être mutés
    // -----------------------------------------------------------------------

    @Test
    void buildNode_doesNotMutateInputBuilderNodes() {
        BuilderNode nodeA = builderNode("a", 1);
        BuilderNode nodeB = builderNodeWithDep("b", 2, "a");

        Set<String> aDepsBeore = new HashSet<>(nodeA.depends());
        Set<String> bDepsBefore = new HashSet<>(nodeB.depends());

        Node.buildNode(List.of(nodeA, nodeB));

        assertEquals(aDepsBeore, nodeA.depends(),
                "Les depends() du BuilderNode nodeA ne doivent pas être mutés par buildNode");
        assertEquals(bDepsBefore, nodeB.depends(),
                "Les depends() du BuilderNode nodeB ne doivent pas être mutés par buildNode");
    }

    // -----------------------------------------------------------------------
    // 3. Construction de l'arbre : structure parent-enfant correcte
    // -----------------------------------------------------------------------

    @Test
    void buildNode_parentChildHierarchy_isCorrectlyBound() {
        List<BuilderNode> inputs = List.of(
                builderNode("root", 1),
                builderNodeWithDep("child1", 2, "root"),
                builderNodeWithDep("child2", 3, "root")
        );

        SortedSet<Node> result = Node.buildNode(inputs);

        Configuration conf = new Configuration(
                new Version("1.0.0"), Set.of(), null, null,
                Map.of(), null, Map.of(), result, List.of());
        List<String> ordered = conf.orderedNodes().stream().map(Node::nodeName).toList();

        assertTrue(ordered.contains("root"), "'root' doit être dans l'arbre");
        assertTrue(ordered.contains("child1"), "'child1' doit être dans l'arbre");
        assertTrue(ordered.contains("child2"), "'child2' doit être dans l'arbre");
        assertTrue(ordered.indexOf("root") < ordered.indexOf("child1"),
                "'root' doit précéder 'child1'");
        assertTrue(ordered.indexOf("root") < ordered.indexOf("child2"),
                "'root' doit précéder 'child2'");
    }

    // -----------------------------------------------------------------------
    // 4. Résultat non-mutable depuis l'extérieur (Node.children est isolé)
    // -----------------------------------------------------------------------

    @Test
    void buildNode_childrenOfNodeAreIsolatedAcrossMultipleCalls() {
        List<BuilderNode> inputs = List.of(
                builderNode("a", 1),
                builderNodeWithDep("b", 2, "a")
        );

        SortedSet<Node> result1 = Node.buildNode(inputs);
        SortedSet<Node> result2 = Node.buildNode(inputs);

        Node aFrom1 = result1.stream()
                .flatMap(n -> n.children().stream())
                .filter(n -> n.nodeName().equals("b"))
                .findFirst()
                .orElse(result1.stream().filter(n -> n.nodeName().equals("a")).findFirst().orElseThrow());

        String snapshotBefore = snapshot(result2);

        try {
            aFrom1.children().add(NodeBuilder.node().nodeName("__intrus__").build());
        } catch (UnsupportedOperationException e) {
            // Si les children sont immuables : parfait, le test passe aussi
        }

        String snapshotAfter = snapshot(result2);
        assertEquals(snapshotBefore, snapshotAfter,
                "La mutation des children de result1 ne doit pas affecter result2");
    }

    // -----------------------------------------------------------------------
    // 5. Nœud récursif
    // -----------------------------------------------------------------------

    @Test
    void buildNode_recursiveNode_isCorrectlyMarked() {
        BuilderNode recursiveSite = new BuilderNode(0, "site", "parent_col", "parent_col",
                null, new TreeSet<>(), new TreeSet<>(), 1, true);

        SortedSet<Node> result = Node.buildNode(List.of(recursiveSite));

        assertEquals(1, result.size());
        assertTrue(result.first().isRecursive(), "Le nœud doit être marqué isRecursive=true");
    }

    // -----------------------------------------------------------------------
    // 6. Hiérarchie 3 niveaux — pas de doublon de racine (régression)
    // -----------------------------------------------------------------------

    /**
     * Vérifie que {@code buildNode} ne produit pas de racine en double lorsque la hiérarchie
     * comporte 3 niveaux ou plus et que le nœud intermédiaire reçoit ses enfants en plusieurs
     * passes de la boucle interne.
     *
     * <pre>
     *   root
     *   └── intermediate
     *       ├── leaf1
     *       └── leaf2
     *           └── leaf3
     * </pre>
     */
    @Test
    void buildNode_threeLevel_rootAppearsExactlyOnce() {
        BuilderNode root         = builderNode("root", 1);
        BuilderNode intermediate = new BuilderNode(1, "intermediate", null, null, root,
                new TreeSet<>(), new TreeSet<>(Set.of("root")), 2, false);
        BuilderNode leaf1        = new BuilderNode(2, "leaf1", null, null, intermediate,
                new TreeSet<>(), new TreeSet<>(Set.of("root", "intermediate")), 3, false);
        BuilderNode leaf2        = new BuilderNode(2, "leaf2", null, null, intermediate,
                new TreeSet<>(), new TreeSet<>(Set.of("root", "intermediate")), 4, false);
        BuilderNode leaf3        = new BuilderNode(3, "leaf3", null, null, leaf2,
                new TreeSet<>(), new TreeSet<>(Set.of("root", "intermediate", "leaf2")), 5, false);

        SortedSet<Node> result = Node.buildNode(
                List.of(root, intermediate, leaf1, leaf2, leaf3));

        long rootCount = result.stream()
                .filter(n -> "root".equals(n.nodeName()))
                .count();
        assertEquals(1, rootCount,
                "Le nœud 'root' ne doit apparaître qu'une seule fois dans l'ensemble résultat");

        Node rootNode = result.stream()
                .filter(n -> "root".equals(n.nodeName()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, rootNode.children().size(),
                "'root' doit avoir exactement 1 enfant direct ('intermediate')");
        assertEquals("intermediate", rootNode.children().first().nodeName());

        Node intermediateNode = rootNode.children().first();
        assertEquals(2, intermediateNode.children().size(),
                "'intermediate' doit avoir exactement 2 enfants (leaf1 et leaf2)");

        Node leaf2Node = intermediateNode.children().stream()
                .filter(n -> "leaf2".equals(n.nodeName()))
                .findFirst()
                .orElseThrow();
        assertEquals(1, leaf2Node.children().size(),
                "'leaf2' doit avoir exactement 1 enfant (leaf3)");
        assertEquals("leaf3", leaf2Node.children().first().nodeName());
    }

    // -----------------------------------------------------------------------
    // 7. Parent introuvable → IllegalArgumentException
    // -----------------------------------------------------------------------

    /**
     * Vérifie que {@code buildNode} lève une {@link IllegalArgumentException} quand un nœud
     * référence via son champ {@code BuilderNode.parent} un nœud qui n'existe ni dans
     * {@code buildedByName} ni dans {@code remainingBuilders}.
     */
    @Test
    void buildNode_unknownParentField_throwsIllegalArgumentException() {
        BuilderNode ghost  = new BuilderNode(0, "ghost", null, null, null,
                new TreeSet<>(), new TreeSet<>(), 1, false);
        BuilderNode orphan = new BuilderNode(1, "orphan", null, null, ghost,
                new TreeSet<>(), new TreeSet<>(Set.of("ghost")), 2, false);

        // On ne passe que 'orphan', pas 'ghost' → le parent est introuvable
        assertThrows(IllegalArgumentException.class,
                () -> Node.buildNode(List.of(orphan)),
                "Un nœud dont le parent (champ BuilderNode.parent) est introuvable " +
                        "doit lever IllegalArgumentException");
    }
}