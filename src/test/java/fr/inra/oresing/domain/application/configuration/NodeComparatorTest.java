package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests de caractérisation et de correction de {@link Node#compareTo(Node)}.
 *
 * <p>Invariants vérifiés :
 * <ol>
 *   <li>Antisymétrie : {@code a.compareTo(b) == -b.compareTo(a)} (signe opposé)</li>
 *   <li>Transitivité : si {@code a < b} et {@code b < c} alors {@code a < c}</li>
 *   <li>Tri par dépendance : un nœud dépendant apparaît après ses dépendances</li>
 *   <li>Tri par {@code __order__} : à niveau de dépendance égal, l'ordre explicite prime</li>
 *   <li>Tri alphabétique : à ordre égal, tri par nom</li>
 * </ol>
 */
@Tag("core.config")
@Tag("domain.model")
class NodeComparatorTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static Node nodeNoDep(String name) {
        return NodeBuilder.node().nodeName(name).depends(Set.of()).build();
    }

    private static Node nodeNoDep(String name, int order) {
        return NodeBuilder.node().nodeName(name).order(order).depends(Set.of()).build();
    }

    private static Node nodeWithDep(String name, String... depNames) {
        return NodeBuilder.node().nodeName(name).depends(Set.of(depNames)).build();
    }

    private static Node nodeWithDepAndOrder(String name, int order, String... depNames) {
        return NodeBuilder.node().nodeName(name).order(order).depends(Set.of(depNames)).build();
    }

    // -----------------------------------------------------------------------
    // 1. Antisymétrie
    // -----------------------------------------------------------------------

    @Test
    void compareTo_antisymmetry_noDependencies() {
        Node a = nodeNoDep("alpha");
        Node b = nodeNoDep("beta");

        int ab = a.compareTo(b);
        int ba = b.compareTo(a);
        assertNotEquals(0, ab, "alpha et beta sont différents");
        assertEquals(-Integer.signum(ab), Integer.signum(ba),
                "compareTo doit être antisymétrique : signum(a.compareTo(b)) == -signum(b.compareTo(a))");
    }

    @Test
    void compareTo_antisymmetry_withDependency() {
        Node a = nodeNoDep("a");
        Node bDependsOnA = nodeWithDep("b", "a");

        int ab = a.compareTo(bDependsOnA);
        int ba = bDependsOnA.compareTo(a);

        assertTrue(ab < 0, "a devrait venir avant b (a < b)");
        assertTrue(ba > 0, "b dépend de a donc b > a (b vient après a)");
        assertEquals(-Integer.signum(ab), Integer.signum(ba),
                "antisymétrie : signum(a.compareTo(b)) == -signum(b.compareTo(a))");
    }

    @Test
    void compareTo_consistency_sameNode_returnsZero() {
        Node a = nodeNoDep("alpha");
        assertEquals(0, a.compareTo(a), "Un nœud comparé à lui-même doit retourner 0");
    }

    // -----------------------------------------------------------------------
    // 2. Ordre par dépendance
    // -----------------------------------------------------------------------

    @Test
    void compareTo_dependencyOrder_dependentComesAfterDependency() {
        Node independent = nodeNoDep("referentiel_a");
        Node dependent   = nodeWithDep("referentiel_b", "referentiel_a");

        assertTrue(dependent.compareTo(independent) > 0,
                "Un nœud dépendant doit venir après son dépendance");
        assertTrue(independent.compareTo(dependent) < 0,
                "Le nœud racine doit venir avant le nœud dépendant");
    }

    @Test
    void compareTo_dependencyOrder_inTreeSet() {
        Node a = nodeNoDep("a");
        Node bDependsOnA = nodeWithDep("b", "a");
        Node cDependsOnB = nodeWithDep("c", "a", "b");

        SortedSet<Node> sorted = new TreeSet<>();
        sorted.add(cDependsOnB);
        sorted.add(a);
        sorted.add(bDependsOnA);

        Object[] array = sorted.toArray();
        assertEquals("a", ((Node) array[0]).nodeName(), "a doit être en premier (pas de dép)");
        assertTrue(((Node) array[1]).nodeName().equals("b") || ((Node) array[1]).nodeName().equals("c"));
        assertTrue(((Node) array[2]).nodeName().equals("b") || ((Node) array[2]).nodeName().equals("c"));
    }

    // -----------------------------------------------------------------------
    // 3. Tri par __order__
    // -----------------------------------------------------------------------

    @Test
    void compareTo_orderTag_lowerOrderComesFirst() {
        Node order1 = nodeNoDep("zzz_late", 1);
        Node order2 = nodeNoDep("aaa_early", 2);

        assertTrue(order1.compareTo(order2) < 0,
                "Le nœud avec order=1 doit venir avant order=2 malgré le nom alphabétiquement plus tard");
    }

    @Test
    void compareTo_orderTag_sameOrderFallsBackToName() {
        Node nodeA = nodeNoDep("alpha", 5);
        Node nodeB = nodeNoDep("beta", 5);

        assertTrue(nodeA.compareTo(nodeB) < 0,
                "À order égal, tri alphabétique : alpha < beta");
        assertTrue(nodeB.compareTo(nodeA) > 0,
                "À order égal, tri alphabétique : beta > alpha");
    }

    @Test
    void compareTo_noOrderTag_defaultIs9999_alphabeticFallback() {
        Node nodeA = nodeNoDep("alpha"); // order=9999 (défaut dans NodeBuilder)
        Node nodeB = nodeNoDep("beta");  // order=9999

        assertTrue(nodeA.compareTo(nodeB) < 0, "Sans order, tri alphabétique : alpha < beta");
    }

    // -----------------------------------------------------------------------
    // 4. Transitivité
    // -----------------------------------------------------------------------

    @Test
    void compareTo_transitivity() {
        Node a = nodeNoDep("a", 1);
        Node b = nodeNoDep("b", 2);
        Node c = nodeNoDep("c", 3);

        assertTrue(a.compareTo(b) < 0, "a < b");
        assertTrue(b.compareTo(c) < 0, "b < c");
        assertTrue(a.compareTo(c) < 0, "transitivité : a < c");
    }

    // -----------------------------------------------------------------------
    // 5. Nœud null
    // -----------------------------------------------------------------------

    @Test
    void compareTo_null_returnsPositive() {
        Node a = nodeNoDep("alpha");
        assertTrue(a.compareTo(null) > 0, "N'importe quel nœud est supérieur à null");
    }

    // -----------------------------------------------------------------------
    // 6. TreeSet stable avec dépendances mixtes
    // -----------------------------------------------------------------------

    @Test
    void treeSet_stableWithMixedOrderAndDependencies() {
        Node typeDeSites = nodeNoDep("type_de_sites",  1);
        Node projet       = nodeNoDep("projet",         2);
        Node sites        = nodeWithDepAndOrder("sites", 3, "type_de_sites");
        Node pem          = nodeWithDepAndOrder("pem",   4, "sites", "projet");

        SortedSet<Node> sorted = new TreeSet<>();
        sorted.add(pem);
        sorted.add(sites);
        sorted.add(projet);
        sorted.add(typeDeSites);

        Object[] arr = sorted.toArray();
        String first  = ((Node) arr[0]).nodeName();
        String second = ((Node) arr[1]).nodeName();
        String third  = ((Node) arr[2]).nodeName();
        String fourth = ((Node) arr[3]).nodeName();

        assertEquals("pem", fourth, "pem doit être le dernier (le plus de dépendances)");
        int idxSites = List.of(first, second, third, fourth).indexOf("sites");
        int idxType  = List.of(first, second, third, fourth).indexOf("type_de_sites");
        assertTrue(idxType < idxSites,
                "type_de_sites doit apparaître avant sites (sites en dépend)");
    }
}