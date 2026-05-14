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
 * Tests unitaires de {@link ReferenceGraphBuilder}.
 *
 * <p>Couvre tous les types de relation : {@link ReferenceGraphBuilder.RelationType#DEPENDS},
 * {@link ReferenceGraphBuilder.RelationType#RECURSIVE},
 * {@link ReferenceGraphBuilder.RelationType#PARENT_CHILD}.</p>
 */
@Tag("core.config")
@Tag("domain.model")
class ReferenceGraphBuilderTest {

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private static ReferenceChecker refChecker(String componentKey, String refType) {
        return new ReferenceChecker(CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                componentKey, Multiplicity.ONE, false, refType, false, false);
    }

    private static ReferenceChecker refCheckerRecursive(String componentKey, String refType) {
        return new ReferenceChecker(CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                componentKey, Multiplicity.ONE, false, refType, true, false);
    }

    private static ReferenceChecker refCheckerParent(String componentKey, String refType) {
        return new ReferenceChecker(CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                componentKey, Multiplicity.ONE, false, refType, false, true);
    }

    // -----------------------------------------------------------------------
    // 1. detectRelations — dépendance simple
    // -----------------------------------------------------------------------

    @Test
    void detectRelations_simpleReference_producesDepends() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "b", List.of(refChecker("col_a", "a")));

        List<ReferenceGraphBuilder.ReferenceRelation> relations =
                ReferenceGraphBuilder.detectRelations(checkers);

        assertEquals(1, relations.size());
        assertEquals(ReferenceGraphBuilder.RelationType.DEPENDS, relations.get(0).relationType());
        assertEquals("b", relations.get(0).sourceDataName());
        assertEquals("a", relations.get(0).targetRefType());
    }

    // -----------------------------------------------------------------------
    // 2. detectRelations — nœud récursif (référence avec isRecursive=true)
    // -----------------------------------------------------------------------

    @Test
    void detectRelations_recursiveReference_producesRecursive() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "site", List.of(refCheckerRecursive("parent_col", "site")));

        List<ReferenceGraphBuilder.ReferenceRelation> relations =
                ReferenceGraphBuilder.detectRelations(checkers);

        assertEquals(1, relations.size());
        assertEquals(ReferenceGraphBuilder.RelationType.RECURSIVE, relations.get(0).relationType());
    }

    // -----------------------------------------------------------------------
    // 3. detectRelations — nœud auto-référencé implicitement
    // -----------------------------------------------------------------------

    @Test
    void detectRelations_selfReference_producesRecursive() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "zone", List.of(refChecker("parent_zone", "zone")));

        List<ReferenceGraphBuilder.ReferenceRelation> relations =
                ReferenceGraphBuilder.detectRelations(checkers);

        assertEquals(1, relations.size());
        assertEquals(ReferenceGraphBuilder.RelationType.RECURSIVE, relations.get(0).relationType(),
                "Un référentiel qui se référence lui-même doit être RECURSIVE");
    }

    // -----------------------------------------------------------------------
    // 4. detectRelations — parentalité (isParent=true)
    // -----------------------------------------------------------------------

    @Test
    void detectRelations_parentChecker_producesParentChild() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "site", List.of(refCheckerParent("type_col", "type_de_sites")));

        List<ReferenceGraphBuilder.ReferenceRelation> relations =
                ReferenceGraphBuilder.detectRelations(checkers);

        assertEquals(1, relations.size());
        assertEquals(ReferenceGraphBuilder.RelationType.PARENT_CHILD, relations.get(0).relationType());
        assertEquals("site",          relations.get(0).sourceDataName());
        assertEquals("type_de_sites", relations.get(0).targetRefType());
    }

    // -----------------------------------------------------------------------
    // 5. detectRelations — plusieurs checkers par nœud
    // -----------------------------------------------------------------------

    @Test
    void detectRelations_multipleCheckersPerNode_allDetected() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "pem", List.of(
                        refChecker("site_col",   "sites"),
                        refChecker("projet_col", "projets")));

        List<ReferenceGraphBuilder.ReferenceRelation> relations =
                ReferenceGraphBuilder.detectRelations(checkers);

        assertEquals(2, relations.size(), "Deux checkers → deux relations");
        assertTrue(relations.stream().allMatch(r -> r.relationType() == ReferenceGraphBuilder.RelationType.DEPENDS));
        Set<String> targets = new HashSet<>();
        relations.forEach(r -> targets.add(r.targetRefType()));
        assertTrue(targets.contains("sites"),   "La relation sites doit être détectée");
        assertTrue(targets.contains("projets"), "La relation projets doit être détectée");
    }

    // -----------------------------------------------------------------------
    // 6. buildNodes — les dépendances sont correctement annotées
    // -----------------------------------------------------------------------

    @Test
    void buildNodes_dependency_childKnowsParent() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "commune", List.of(refChecker("dep_col", "departement")));
        Map<String, Integer> orders = Map.of("departement", 1, "commune", 2);

        Map<String, BuilderNode> nodes = ReferenceGraphBuilder.buildNodes(checkers, orders);

        BuilderNode commune = nodes.get("commune");
        assertNotNull(commune, "'commune' doit être présent dans les nœuds");
        assertTrue(commune.depends().contains("departement"),
                "'commune' doit avoir 'departement' dans ses dépendances");
    }

    // -----------------------------------------------------------------------
    // 7. buildNodes — nœud récursif marqué isRecursive
    // -----------------------------------------------------------------------

    @Test
    void buildNodes_recursive_markedAsRecursive() {
        Map<String, List<ReferenceChecker>> checkers = Map.of(
                "site", List.of(refCheckerRecursive("parent_site", "site")));
        Map<String, Integer> orders = Map.of("site", 1);

        Map<String, BuilderNode> nodes = ReferenceGraphBuilder.buildNodes(checkers, orders);

        BuilderNode site = nodes.get("site");
        assertNotNull(site);
        assertTrue(site.isRecursive(), "Le nœud récursif doit être marqué isRecursive=true");
    }

    // -----------------------------------------------------------------------
    // 8. buildNodes — résultat immuable
    // -----------------------------------------------------------------------

    @Test
    void buildNodes_result_isImmutable() {
        Map<String, BuilderNode> nodes = ReferenceGraphBuilder.buildNodes(Map.of(), Map.of("a", 1));
        assertThrows(UnsupportedOperationException.class,
                () -> nodes.put("intrus", null),
                "buildNodes() doit retourner une map immuable");
    }
}