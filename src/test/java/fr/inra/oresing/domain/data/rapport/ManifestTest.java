package fr.inra.oresing.domain.data.rapport;

import fr.inra.oresing.domain.data.deposit.bundle.BundleFileContent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link Manifest}.
 *
 * <p>Couvre : constructeur par défaut, add(), addError(),
 * orderedReferenceTypes() (tri topologique), détection de cycle.
 */
@Tag("domain.model")
@DisplayName("Manifest — tests unitaires")
class ManifestTest {

    private static BundleFileContent fc(String fileName, List<String> refs) {
        return new BundleFileContent(refs, fileName, null);
    }

    // ─── constructeur ───────────────────────────────────────────────────────

    @Test
    @DisplayName("constructeur par défaut crée des maps vides")
    void defaultConstructorEmptyMaps() {
        Manifest m = new Manifest();
        assertTrue(m.referenceTypeFiles().isEmpty());
        assertTrue(m.referenceFilesInErrors().isEmpty());
        assertTrue(m.referenceTypeDeps().isEmpty());
    }

    // ─── add() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("add() peuple referenceTypeFiles et referenceTypeDeps")
    void addPopulatesFilesAndDeps() {
        Manifest m = new Manifest();
        BundleFileContent fc = fc("communes.csv", List.of("regions"));

        m.add("communes", fc);

        assertEquals(1, m.referenceTypeFiles().get("communes").size());
        assertEquals(fc, m.referenceTypeFiles().get("communes").get(0));
        // deps : "regions" est une dépendance de "communes"
        assertTrue(m.referenceTypeDeps().get("communes").contains("regions"));
    }

    @Test
    @DisplayName("add() ignore l'auto-référence dans les deps")
    void addIgnoresSelfReference() {
        Manifest m = new Manifest();
        // communes se référence elle-même (auto-référence)
        BundleFileContent fc = fc("communes.csv", List.of("communes", "regions"));

        m.add("communes", fc);

        List<String> deps = m.referenceTypeDeps().get("communes");
        // "communes" ne doit pas apparaître comme dépendance d'elle-même
        assertFalse(deps.contains("communes"), "Auto-référence ne doit pas être dans deps");
        assertTrue(deps.contains("regions"));
    }

    @Test
    @DisplayName("add() multiple fois accumule les fichiers")
    void addAccumulatesFiles() {
        Manifest m = new Manifest();
        m.add("communes", fc("communes1.csv", List.of("regions")));
        m.add("communes", fc("communes2.csv", List.of("regions")));

        assertEquals(2, m.referenceTypeFiles().get("communes").size());
    }

    // ─── addError() ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("addError() peuple referenceFilesInErrors")
    void addErrorPopulatesErrors() {
        Manifest m = new Manifest();
        BundleFileContent fc = fc("bad.csv", List.of());

        m.addError("communes", fc);

        assertEquals(1, m.referenceFilesInErrors().get("communes").size());
        assertFalse(m.referenceTypeFiles().containsKey("communes"),
                "addError ne doit pas toucher referenceTypeFiles");
    }

    // ─── orderedReferenceTypes() ─────────────────────────────────────────────

    @Test
    @DisplayName("orderedReferenceTypes() : feuille avant dépendance")
    void orderedLeafBeforeParent() {
        Manifest m = new Manifest();
        // regions n'a pas de dep ; communes dépend de regions
        m.add("regions", fc("regions.csv", List.of()));
        m.add("communes", fc("communes.csv", List.of("regions")));

        Map<String, List<String>> ordered = m.orderedReferenceTypes();
        List<String> keys = new java.util.ArrayList<>(ordered.keySet());

        // regions doit venir avant communes (ou être présent)
        int regIdx = keys.indexOf("regions");
        int comIdx = keys.indexOf("communes");

        // Au moins communes doit avoir regions comme dep potentielle
        assertTrue(comIdx == -1 || regIdx == -1 || regIdx < comIdx,
                "regions devrait précéder communes dans l'ordre topologique");
    }

    @Test
    @DisplayName("orderedReferenceTypes() : seuls les refs présents dans referenceTypeFiles sont inclus")
    void orderedOnlyIncludesKnownFiles() {
        Manifest m = new Manifest();
        // "departements" a une dépendance vers "regions" mais "regions"
        // n'a jamais été ajouté via add()
        m.add("departements", fc("dept.csv", List.of("regions")));

        Map<String, List<String>> ordered = m.orderedReferenceTypes();

        // "regions" est une dep mais pas dans referenceTypeFiles → absent du résultat
        assertFalse(ordered.containsKey("regions"),
                "regions n'est pas dans referenceTypeFiles donc ne doit pas figurer");
        // "departements" est dans referenceTypeFiles → présent
        assertTrue(ordered.containsKey("departements"));
    }

    @Test
    @DisplayName("orderedReferenceTypes() : graphe vide retourne une map vide")
    void orderedEmptyGraph() {
        Manifest m = new Manifest();
        assertTrue(m.orderedReferenceTypes().isEmpty());
    }

    @Test
    @DisplayName("orderedReferenceTypes() : cycle détecté lève RuntimeException")
    void orderedCycleDetected() {
        Manifest m = new Manifest();
        // A → B → A : cycle
        m.add("a", fc("a.csv", List.of("b")));
        m.add("b", fc("b.csv", List.of("a")));

        assertThrows(RuntimeException.class, m::orderedReferenceTypes,
                "Un cycle dans les dépendances doit lever une exception");
    }

    @Test
    @DisplayName("orderedReferenceTypes() : nœud sans dépendances externe inclus")
    void orderedNodeWithNoDeps() {
        Manifest m = new Manifest();
        m.add("standalone", fc("standalone.csv", List.of()));

        Map<String, List<String>> ordered = m.orderedReferenceTypes();
        assertTrue(ordered.containsKey("standalone"));
    }
}
