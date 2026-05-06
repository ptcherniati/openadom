package fr.inra.oresing.rest.model.configuration.builder;

import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires purs (sans Spring, sans Docker) pour
 * {@link HierarchicalDependancesBuilder}.
 */
@Tag("core.config")
class HierarchicalDependancesBuilderTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static ReferenceChecker refChecker(String componentKey, String refType) {
        return new ReferenceChecker(
                CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                componentKey, Multiplicity.ONE, false, refType, false, false);
    }

    private static ReferenceChecker refCheckerRecursive(String componentKey, String refType) {
        return new ReferenceChecker(
                CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                componentKey, Multiplicity.ONE, false, refType, true, false);
    }

    private static ReferenceChecker refCheckerParent(String componentKey, String refType) {
        return new ReferenceChecker(
                CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                componentKey, Multiplicity.ONE, false, refType, false, true);
    }

    private static SortedSet<Node> build(
            Map<String, List<ReferenceChecker>> checkers,
            Map<String, Integer> orders) {

        List<ValidationParams> collectedErrors = new ArrayList<>();
        Map<String, BuilderNode> nodes = new TreeMap<>();
        for (String dataName : orders.keySet()) {
            nodes.put(dataName, new BuilderNode(0, dataName, null, null, null,
                    new TreeSet<>(), new TreeSet<>(), orders.get(dataName), false));
        }
        return HierarchicalDependancesBuilder.buildHierchicalDependances(
                collectedErrors::add, checkers, orders, nodes, Set.of());
    }

    /** Construit une Configuration minimale depuis les nœuds pour tester orderedNodes(). */
    private static Configuration confFromNodes(SortedSet<Node> nodes) {
        return new Configuration(
                new Version("1.0.0"), Set.of(), null, null,
                Map.of(), null, Map.of(), nodes, List.of());
    }

    // -----------------------------------------------------------------------
    // 1. Nœud isolé
    // -----------------------------------------------------------------------

    @Test
    void isolated_node_becomesRootWithNoChildren() {
        SortedSet<Node> result = build(Map.of(), Map.of("referentiel_a", 1));

        assertEquals(1, result.size());
        Node root = result.first();
        assertEquals("referentiel_a", root.nodeName());
        assertTrue(root.children().isEmpty());
        assertTrue(root.depends().isEmpty());
    }

    // -----------------------------------------------------------------------
    // 2. Dépendance simple A dépend de B
    // -----------------------------------------------------------------------

    @Test
    void simple_dependency_bComesBefore_a() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "a", List.of(refChecker("ref_col", "b")));
        Map<String, Integer> orders = Map.of("a", 2, "b", 1);

        SortedSet<Node> result = build(checkers, orders);
        List<String> ordered = confFromNodes(result).orderedNodes()
                .stream().map(Node::nodeName).toList();

        assertTrue(ordered.indexOf("b") < ordered.indexOf("a"),
                "b (dépendance de a) doit précéder a");
    }

    // -----------------------------------------------------------------------
    // 3. Récursivité A→A
    // -----------------------------------------------------------------------

    @Test
    void recursive_node_markedAsRecursive() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "site", List.of(refCheckerRecursive("parent_site", "site")));
        SortedSet<Node> result = build(checkers, Map.of("site", 1));

        assertEquals(1, result.size());
        assertTrue(result.first().isRecursive());
        assertEquals("site", result.first().nodeName());
    }

    // -----------------------------------------------------------------------
    // 4. Parentalité
    // -----------------------------------------------------------------------

    @Test
    void parent_relationship_parentComesBefore_child() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "site", List.of(refCheckerParent("type_site_col", "type_de_sites")));
        Map<String, Integer> orders = Map.of("site", 2, "type_de_sites", 1);

        SortedSet<Node> result = build(checkers, orders);
        List<String> ordered = confFromNodes(result).orderedNodes()
                .stream().map(Node::nodeName).toList();

        assertTrue(ordered.indexOf("type_de_sites") < ordered.indexOf("site"),
                "Le parent (type_de_sites) doit précéder l'enfant (site)");
    }

    // -----------------------------------------------------------------------
    // 5. Tri par __order__
    // -----------------------------------------------------------------------

    @Test
    void order_tag_guaranteesIHMDepositOrder() {
        Map<String, Integer> orders = new LinkedHashMap<>();
        orders.put("zzz_third", 3);
        orders.put("aaa_first", 1);
        orders.put("mmm_second", 2);

        SortedSet<Node> result = build(Map.of(), orders);
        List<String> ordered = confFromNodes(result).orderedNodes()
                .stream().map(Node::nodeName).toList();

        assertTrue(ordered.indexOf("aaa_first")  < ordered.indexOf("mmm_second"),
                "order=1 avant order=2");
        assertTrue(ordered.indexOf("mmm_second") < ordered.indexOf("zzz_third"),
                "order=2 avant order=3");
    }

    // -----------------------------------------------------------------------
    // 6. Cycle → exception
    // -----------------------------------------------------------------------

    @Test
    void cyclic_dependency_throwsBadApplicationConfigurationException() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "a", List.of(refChecker("col_b", "b")),
                "b", List.of(refChecker("col_a", "a")));

        assertThrows(BadApplicationConfigurationException.class,
                () -> build(checkers, Map.of("a", 1, "b", 2)));
    }

    // -----------------------------------------------------------------------
    // 7. Chaîne A→B→C
    // -----------------------------------------------------------------------

    @Test
    void chained_dependencies_correctOrder() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "b", List.of(refChecker("col_a", "a")),
                "c", List.of(refChecker("col_b", "b")));

        SortedSet<Node> result = build(checkers, Map.of("a", 1, "b", 2, "c", 3));
        List<String> ordered = confFromNodes(result).orderedNodes()
                .stream().map(Node::nodeName).toList();

        int idxA = ordered.indexOf("a");
        int idxB = ordered.indexOf("b");
        int idxC = ordered.indexOf("c");
        assertTrue(idxA < idxB, "a doit précéder b");
        assertTrue(idxB < idxC, "b doit précéder c");
    }
}