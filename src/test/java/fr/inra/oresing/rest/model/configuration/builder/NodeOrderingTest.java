package fr.inra.oresing.rest.model.configuration.builder;

import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests du tri IHM garanti : les référentiels doivent apparaître dans
 * {@link Configuration#orderedNodes()} dans un ordre tel que les dépendances
 * précèdent toujours leurs dépendants.
 *
 * <p>Ces tests reproduisent les scénarios réels de l'IHM où l'utilisateur doit
 * déposer les référentiels dans un ordre précis.</p>
 */
@Tag("core.config")
@Tag("domain.model")
class NodeOrderingTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static ReferenceChecker refChecker(String componentKey, String refType) {
        return new ReferenceChecker(CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                componentKey, Multiplicity.ONE, false, refType, false, false);
    }

    private static ReferenceChecker refCheckerParent(String componentKey, String refType) {
        return new ReferenceChecker(CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                componentKey, Multiplicity.ONE, false, refType, false, true);
    }

    private static Configuration buildConf(
            Map<String, List<ReferenceChecker>> checkers,
            Map<String, Integer> orders) {

        List<ValidationParams> errors = new ArrayList<>();
        Map<String, BuilderNode> nodes = new TreeMap<>();
        for (String name : orders.keySet()) {
            nodes.put(name, new BuilderNode(0, name, null, null, null,
                    new TreeSet<>(), new TreeSet<>(), orders.get(name), false));
        }
        SortedSet<Node> hierarchicalNodes = HierarchicalDependancesBuilder
                .buildHierchicalDependances(errors::add, checkers, orders, nodes, Set.of());
        return new Configuration(new Version("1.0.0"), Set.of(), null, null,
                Map.of(), null, Map.of(), hierarchicalNodes, List.of());
    }

    // -----------------------------------------------------------------------
    // 1. Sans dépendance : tri alphabétique (order égal)
    // -----------------------------------------------------------------------

    @Test
    void ordering_noDependencies_alphabeticalOrder() {
        Map<String, Integer> orders = new LinkedHashMap<>();
        orders.put("c_ref", 9999);
        orders.put("a_ref", 9999);
        orders.put("b_ref", 9999);

        Configuration conf = buildConf(Map.of(), orders);
        List<String> ordered = conf.orderedNodes().stream().map(Node::nodeName).toList();

        assertEquals("a_ref", ordered.get(0), "Ordre alphabétique : a en premier");
        assertEquals("b_ref", ordered.get(1), "Ordre alphabétique : b en second");
        assertEquals("c_ref", ordered.get(2), "Ordre alphabétique : c en troisième");
    }

    // -----------------------------------------------------------------------
    // 2. Avec __order__ explicite : l'order prime sur l'alphabet
    // -----------------------------------------------------------------------

    @Test
    void ordering_explicitOrder_overridesAlphabetical() {
        Map<String, Integer> orders = new LinkedHashMap<>();
        orders.put("z_last_alpha", 1);   // order=1 mais nom tardif alphabétiquement
        orders.put("a_first_alpha", 2);  // order=2 mais nom précoce alphabétiquement

        Configuration conf = buildConf(Map.of(), orders);
        List<String> ordered = conf.orderedNodes().stream().map(Node::nodeName).toList();

        assertEquals("z_last_alpha", ordered.get(0),
                "order=1 doit primer sur l'ordre alphabétique");
        assertEquals("a_first_alpha", ordered.get(1),
                "order=2 vient après order=1 même si alphabétiquement a < z");
    }

    // -----------------------------------------------------------------------
    // 3. Dépendance simple : le référentiel dépendé précède le dépendant
    // -----------------------------------------------------------------------

    @Test
    void ordering_dependency_dependencyBeforeDependent() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "commune", List.of(refChecker("type_col", "type_commune")));
        Map<String, Integer> orders = Map.of("type_commune", 1, "commune", 2);

        Configuration conf = buildConf(checkers, orders);
        List<String> ordered = conf.orderedNodes().stream().map(Node::nodeName).toList();

        int idxType    = ordered.indexOf("type_commune");
        int idxCommune = ordered.indexOf("commune");
        assertTrue(idxType >= 0 && idxCommune >= 0, "Les deux nœuds doivent être présents");
        assertTrue(idxType < idxCommune,
                "type_commune (dépendance) doit précéder commune (dépendant)");
    }

    // -----------------------------------------------------------------------
    // 4. Hiérarchie parent-enfant (isParent=true)
    // -----------------------------------------------------------------------

    @Test
    void ordering_parentRelationship_parentBeforeChild() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "site", List.of(refCheckerParent("type_site_col", "type_de_sites")));
        Map<String, Integer> orders = Map.of("type_de_sites", 1, "site", 2);

        Configuration conf = buildConf(checkers, orders);
        List<String> ordered = conf.orderedNodes().stream().map(Node::nodeName).toList();

        int idxParent = ordered.indexOf("type_de_sites");
        int idxChild  = ordered.indexOf("site");
        assertTrue(idxParent < idxChild,
                "type_de_sites (parent) doit précéder site (enfant)");
    }

    // -----------------------------------------------------------------------
    // 5. Chaîne de dépendances : A→B→C (ordre A, B, C)
    // -----------------------------------------------------------------------

    @Test
    void ordering_chain_A_B_C() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "b", List.of(refChecker("col_a", "a")),
                "c", List.of(refChecker("col_b", "b")));
        Map<String, Integer> orders = Map.of("a", 1, "b", 2, "c", 3);

        Configuration conf = buildConf(checkers, orders);
        List<String> ordered = conf.orderedNodes().stream().map(Node::nodeName).toList();

        assertTrue(ordered.indexOf("a") < ordered.indexOf("b"), "a < b");
        assertTrue(ordered.indexOf("b") < ordered.indexOf("c"), "b < c");
    }

    // -----------------------------------------------------------------------
    // 6. Nœud racine sans dépendances parmi d'autres → placé en premier
    // -----------------------------------------------------------------------

    @Test
    void ordering_isolatedRootNode_comesBeforeDependentNodes() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "mesure_journaliere", List.of(refChecker("table_col", "table_pivot")));
        Map<String, Integer> orders = Map.of("table_pivot", 1, "mesure_journaliere", 2);

        Configuration conf = buildConf(checkers, orders);
        List<String> ordered = conf.orderedNodes().stream().map(Node::nodeName).toList();

        int idxRoot      = ordered.indexOf("table_pivot");
        int idxDependent = ordered.indexOf("mesure_journaliere");
        assertTrue(idxRoot < idxDependent,
                "table_pivot (indépendant) doit précéder mesure_journaliere (dépendant)");
    }

    // -----------------------------------------------------------------------
    // 7. findNode retrouve le bon nœud dans l'arbre
    // -----------------------------------------------------------------------

    @Test
    void findNode_inHierarchy_traversesChildrenCorrectly() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "sites", List.of(refCheckerParent("type_col", "type_de_sites")),
                "pem",   List.of(refCheckerParent("site_col", "sites")));
        Map<String, Integer> orders = Map.of("type_de_sites", 1, "sites", 2, "pem", 3);

        Configuration conf = buildConf(checkers, orders);

        Optional<HierarchicalNode> found = conf.findCompositeReferencesUsing("pem");
        assertTrue(found.isPresent(), "findCompositeReferencesUsing('pem') doit trouver le nœud");
        Optional<HierarchicalNode> rootFound = conf.findCompositeReferencesUsing("type_de_sites");
        assertTrue(rootFound.isPresent(), "findCompositeReferencesUsing('type_de_sites') doit trouver le nœud racine");
    }

    // -----------------------------------------------------------------------
    // 8. Scénario OLA complet (plusieurs niveaux de hiérarchie)
    // -----------------------------------------------------------------------

    @Test
    void ordering_olaLikeHierarchy_correctDepositOrder() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "type_sites", List.of(refChecker("theme_col", "themes")),
                "sites",      List.of(refChecker("type_site_col", "type_sites")));
        Map<String, Integer> orders = Map.of("themes", 1, "type_sites", 2, "sites", 3);

        Configuration conf = buildConf(checkers, orders);
        List<String> ordered = conf.orderedNodes().stream().map(Node::nodeName).toList();

        int idxThemes    = ordered.indexOf("themes");
        int idxTypeSites = ordered.indexOf("type_sites");
        int idxSites     = ordered.indexOf("sites");

        assertTrue(idxThemes < idxTypeSites,    "themes < type_sites");
        assertTrue(idxTypeSites < idxSites, "type_sites < sites");
    }
}