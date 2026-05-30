package fr.inra.oresing.domain.checker.type;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.ReferenceValidationCheckResult;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.UUID;

@Tag("domain.checker")
class ReferenceTypeTest {
    LineChecker.Transformer transformer;
    ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues;
    LineChecker checker;
    ReferenceValidationCheckResult checkGood;
    ReferenceValidationCheckResult checkGoodValueNotLabel;
    ReferenceValidationCheckResult checkBad;
    private DataColumn dataColumn;
    private String goodValue;
    private UUID uuid1;

    @BeforeEach
    public void before() {
        dataColumn = new DataColumn("laColonne");
        goodValue = "leman";
        String badValue = "annecy";
        String goodValueNotLabel = "LéMan";
        uuid1 = UUID.randomUUID();
        referenceValues = new ImmutableMap.Builder()
                .put(new DataValue.LineIdentityColumnName(Ltree.fromSql(goodValue), Ltree.fromSql(goodValue), ""), ImmutableSet.of(uuid1))
                .build();
        checker = Mockito.mock(LineChecker.OneChecker.class);

        Mockito.when(checker.target()).thenReturn(dataColumn);
        checkGood = (ReferenceValidationCheckResult) buildReference().check(goodValue, checker);
        checkGoodValueNotLabel = (ReferenceValidationCheckResult) buildReference().check(goodValueNotLabel, checker);
        checkBad = (ReferenceValidationCheckResult) buildReference().check(badValue, checker);
    }

    ReferenceType buildReference() {
        return new ReferenceType(
                dataColumn,
                "",
                referenceValues,
                transformer,
                null);
    }


    @Test
    @Tag("SUITE")
    void check() {
        Assertions.assertTrue(checkGood.isSuccess());
        Assertions.assertEquals(goodValue, checkGood.value().getValue().toString());
        Assertions.assertEquals(goodValue, checkGood.matchedReferenceHierarchicalKey().stream().map(Ltree::toString).findFirst().orElse("null"));
        Assertions.assertEquals(uuid1, checkGood.matchedReferenceId().stream().findFirst().orElse(null));
        Assertions.assertTrue(checkGoodValueNotLabel.isSuccess());
        Assertions.assertEquals(goodValue, checkGoodValueNotLabel.value().getValue().toString());
        Assertions.assertEquals(goodValue, checkGoodValueNotLabel.matchedReferenceHierarchicalKey().stream().map(Ltree::toString).findFirst().orElse("null"));
        Assertions.assertEquals(uuid1, checkGoodValueNotLabel.matchedReferenceId().stream().findFirst().orElse(null));
        Assertions.assertFalse(checkBad.isSuccess());
    }

    // ─── R-P2-1 : index O(1) ─────────────────────────────────────────────────

    /**
     * R-P2-1 — La même valeur trouvée deux fois doit retourner le même résultat
     * (couverture de la branche naturalKeyIndex O(1) vs ancien stream O(N)).
     */
    @Test
    @Tag("PERF")
    void checkReturnsConsistentResultOnRepeat() {
        ReferenceType rt = buildReference();
        var r1 = (ReferenceValidationCheckResult) rt.check(goodValue, checker);
        var r2 = (ReferenceValidationCheckResult) rt.check(goodValue, checker);
        Assertions.assertTrue(r1.isSuccess());
        Assertions.assertTrue(r2.isSuccess());
        Assertions.assertEquals(
                r1.matchedReferenceHierarchicalKey(),
                r2.matchedReferenceHierarchicalKey(),
                "Le naturalKeyIndex doit retourner le même résultat à chaque appel");
        Assertions.assertEquals(
                r1.matchedReferenceId(),
                r2.matchedReferenceId());
    }

    // ─── R-P2-2 : promotion seenOnce → precomputedResults ───────────────────

    /**
     * R-P2-2 — Après deux appels avec la même valeur, le résultat doit être
     * promu dans precomputedResults (via seenOnce → promote).
     * Le troisième appel doit bénéficier du cache.
     */
    @Test
    @Tag("PERF")
    void checkPromotesValueToCacheAfterSecondOccurrence() {
        ReferenceType rt = buildReference();
        // 1er appel : entre dans seenOnce
        var r1 = (ReferenceValidationCheckResult) rt.check(goodValue, checker);
        // 2e appel : promu dans precomputedResults
        var r2 = (ReferenceValidationCheckResult) rt.check(goodValue, checker);
        // 3e appel : doit venir du cache (même résultat)
        var r3 = (ReferenceValidationCheckResult) rt.check(goodValue, checker);

        Assertions.assertTrue(r1.isSuccess());
        Assertions.assertTrue(r2.isSuccess());
        Assertions.assertTrue(r3.isSuccess());
        // Tous trois doivent pointer vers le même UUID
        Assertions.assertEquals(r1.matchedReferenceId(), r3.matchedReferenceId());
    }

    /**
     * R-P2-2 — Le plafond maxCacheEntries=0 doit empêcher toute entrée dans
     * precomputedResults, mais les vérifications restent correctes via naturalKeyIndex.
     */
    @Test
    @Tag("PERF")
    void checkRespectsMaxCacheEntriesZero() {
        ReferenceType rt = buildReference();
        rt.setMaxCacheEntries(0);
        // Appels répétés : ne doit pas crasher et doit retourner le bon résultat
        var r1 = (ReferenceValidationCheckResult) rt.check(goodValue, checker);
        var r2 = (ReferenceValidationCheckResult) rt.check(goodValue, checker);
        var r3 = (ReferenceValidationCheckResult) rt.check(goodValue, checker);
        Assertions.assertTrue(r1.isSuccess());
        Assertions.assertTrue(r2.isSuccess());
        Assertions.assertTrue(r3.isSuccess());
    }

    // ─── R-P2-2 : partage du cache entre copies ──────────────────────────────

    /**
     * R-P2-2 — une copie Cascade partage les structures seenOnce et
     * precomputedResults avec l'instance d'origine.
     * Un hit sur la copie doit être visible depuis l'original et vice-versa.
     */
    @Test
    @Tag("PERF")
    void copySharesCacheWithOriginal() {
        ReferenceType original = buildReference();
        // 1er appel sur l'original : entre dans seenOnce
        original.check(goodValue, checker);

        // Créer une copie (simule un worker Cascade)
        ReferenceType copy = (ReferenceType) original.copy();
        // 2e appel sur la COPIE : doit promouvoir la valeur dans precomputedResults
        var rCopy = (ReferenceValidationCheckResult) copy.check(goodValue, checker);
        Assertions.assertTrue(rCopy.isSuccess());

        // 3e appel sur l'ORIGINAL : doit bénéficier du cache promu par la copie
        var rOriginal = (ReferenceValidationCheckResult) original.check(goodValue, checker);
        Assertions.assertTrue(rOriginal.isSuccess());
        Assertions.assertEquals(rCopy.matchedReferenceId(), rOriginal.matchedReferenceId());
    }

    /**
     * Iso-resultat : l'ajout INCREMENTAL ( addReferenceValue , mode recursif
     * ordonne ) doit donner exactement les memes resultats de check() que le
     * rebuild complet historique ( setReferenceValues avec la map entiere ) ,
     * pour la valeur de base , la valeur ajoutee , et une valeur absente .
     */
    @Test
    @Tag("PERF")
    void addReferenceValueIsoWithFullSetReferenceValues() {
        UUID uuidA = UUID.randomUUID();
        String valA = "geneve";
        DataValue.LineIdentityColumnName keyA = new DataValue.LineIdentityColumnName(
                Ltree.fromSql(valA), Ltree.fromSql(valA), "");

        // Chemin historique : rebuild complet ( base + nouvelle entree ) .
        ReferenceType full = buildReference();
        full.setReferenceValues(new ImmutableMap.Builder<DataValue.LineIdentityColumnName, ImmutableSet<UUID>>()
                .putAll(referenceValues)
                .put(keyA, ImmutableSet.of(uuidA))
                .build());

        // Nouveau chemin : ajout incremental O(1) de la seule entree nouvelle .
        ReferenceType incr = buildReference();
        incr.addReferenceValue(keyA, ImmutableSet.of(uuidA));

        for (String v : java.util.List.of(goodValue, valA, "annecy")) {
            var rf = (ReferenceValidationCheckResult) full.check(v, checker);
            var ri = (ReferenceValidationCheckResult) incr.check(v, checker);
            Assertions.assertEquals(rf.isSuccess(), ri.isSuccess(), "success diverge pour " + v);
            Assertions.assertEquals(rf.matchedReferenceId(), ri.matchedReferenceId(), "uuid diverge pour " + v);
            Assertions.assertEquals(rf.matchedReferenceHierarchicalKey(), ri.matchedReferenceHierarchicalKey(), "hk diverge pour " + v);
        }
    }

    /**
     * R-P2-2 — setReferenceValues() doit invalider seenOnce et precomputedResults.
     */
    @Test
    @Tag("PERF")
    void setReferenceValuesClearsCaches() {
        ReferenceType rt = buildReference();
        // Remplir le cache
        rt.check(goodValue, checker);
        rt.check(goodValue, checker); // → promote dans precomputedResults

        UUID uuid2 = UUID.randomUUID();
        String newValue = "geneve";
        ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> newRefs =
                ImmutableMap.of(
                        new DataValue.LineIdentityColumnName(Ltree.fromSql(newValue), Ltree.fromSql(newValue), ""),
                        ImmutableSet.of(uuid2));

        rt.setReferenceValues(newRefs);

        // L'ancienne valeur ne doit plus être trouvée (plus dans l'index ni dans le cache)
        var rOld = (ReferenceValidationCheckResult) rt.check(goodValue, checker);
        Assertions.assertFalse(rOld.isSuccess(), "L'ancienne valeur ne doit plus être valide après setReferenceValues");

        // La nouvelle valeur doit être trouvée
        var rNew = (ReferenceValidationCheckResult) rt.check(newValue, checker);
        Assertions.assertTrue(rNew.isSuccess());
        Assertions.assertEquals(uuid2, rNew.matchedReferenceId().stream().findFirst().orElse(null));
    }
}