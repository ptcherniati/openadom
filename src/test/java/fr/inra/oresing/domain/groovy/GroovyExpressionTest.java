package fr.inra.oresing.domain.groovy;

import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.groovy.exception.GroovyException;
import fr.inra.oresing.domain.groovy.predefined.script.ScriptConstantProvider;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.script.ScriptException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@Tag("domain.model")
class GroovyExpressionTest {

    private Map<String, Object> context;

    @BeforeEach
    void setUp() {
        context = new HashMap<>();
        context.put("x", 5);
        context.put("y", 3);
        ScriptConstantProvider.addAllToContext(context);

        // Ajout de données de test au contexte
        context.put("datum", Map.of(
                "agroecosystem", "Agroécosystème 1",
                "site", "Site Expérimental 1",
                "plot", "Parcelle 2"
        ));

        // Construction des objets GroovyDecorator
        List<GroovyDecorator> agroecosystemList = List.of(
                new ReferenceBuilder("agroecosysteme_1", Map.of("agr_key", "agroecosysteme_1", "agr_name", "Agroécosystème 1")),
                new ReferenceBuilder("agroecosysteme_2", Map.of("agr_key", "agroecosysteme_2", "agr_name", "Agroécosystème 2"))
        );

        List<GroovyDecorator> siteList = List.of(
                new ReferenceBuilder("agroecosysteme_1__site_experimental_1", Map.of("sit_key", "site_experimental_1", "sit_name", "Site Expérimental 1", "sit_agroecosystem_key", "agroecosysteme_1")),
                new ReferenceBuilder("agroecosysteme_2__site_experimental_2", Map.of("sit_key", "site_experimental_2", "sit_name", "Site Expérimental 2", "sit_agroecosystem_key", "agroecosysteme_1"))
        );

        List<GroovyDecorator> parcelleList = List.of(
                new ReferenceBuilder("agroecosysteme_1__site_experimental_1__parcelle_1", Map.of("par_key", "parcelle_1", "par_name", "Parcelle 1", "par_site_key", "site_experimental_1", "par_agroecosystem_key", "agroecosysteme_1")),
                new ReferenceBuilder("agroecosysteme_1__site_experimental_1__parcelle_2", Map.of("par_key", "parcelle_2", "par_name", "Parcelle 2", "par_site_key", "site_experimental_1", "par_agroecosystem_key", "agroecosysteme_1"))
        );

        context.put("references", Map.of(
                "tr_agroecosystem_agr", agroecosystemList,
                "tr_site_sit", siteList,
                "tr_parcelle_par", parcelleList
        ));
    }

    @Test
    void testForExpressionAndEvaluate() {
        GroovyExpression expression = GroovyExpression.forExpression("x + y");
        Object result = expression.evaluate(context);
        assertEquals(8, result);
    }

    @Test
    void testForExpressionCaching() {
        GroovyExpression expr1 = GroovyExpression.forExpression("x * y");
        GroovyExpression expr2 = GroovyExpression.forExpression("x * y");
        assertSame(expr1, expr2, "Expressions should be cached");
    }

    @Test
    void testEvaluateWithInvalidExpression() {
        SiOreIllegalArgumentException exception = assertThrows(
                SiOreIllegalArgumentException.class,
                () -> GroovyExpression.forExpression("x +/ y")
        );

        assertEquals("badGroovyExpressionChecker", exception.getMessage());
        assertTrue(exception.getParams().containsKey("expression"));
        assertTrue(exception.getParams().containsKey("message"));
    }

    @Test
    void testValidateExpressionValid() {
        Optional<GroovyExpression.CompilationError> error = GroovyExpression.validateExpression("x + y");
        assertTrue(error.isEmpty());
    }

    @Test
    void testValidateExpressionInvalid() {
        Optional<GroovyExpression.CompilationError> error = GroovyExpression.validateExpression("x +/ y");
        assertTrue(error.isPresent());
        assertEquals(-1, error.get().lineNumber());
        assertEquals(-1, error.get().columnNumber());
        assertNotNull(error.get().message());
        assertTrue(error.get().message().contains("Unexpected input")); //
    }

    @Test
    void testGetErrorWithScriptException() {
        ScriptException e = new ScriptException("Test error", "Test.groovy", 1, 5);
        SiOreIllegalArgumentException exception = GroovyExpression.getError("x + y", e);

        assertEquals("badGroovyExpressionChecker", exception.getMessage());
        assertEquals("x + y", exception.getParams().get("expression"));
        assertEquals(1, exception.getParams().get("lineNumber"));
        assertEquals(5, exception.getParams().get("columnNumber"));
    }

    @Test
    void testGetErrorWithScriptExceptionAndContext() {
        ScriptException e = new ScriptException("Test error", "Test.groovy", 1, 5);
        SiOreIllegalArgumentException exception = GroovyExpression.getError("x + y", e, context);

        assertEquals("badGroovyExpressionChecker", exception.getMessage());
        assertEquals("x + y", exception.getParams().get("expression"));
        assertEquals(1, exception.getParams().get("lineNumber"));
        assertEquals(5, exception.getParams().get("columnNumber"));
        assertNotNull(exception.getParams().get("context"));
    }

    @Test
    void testToString() {
        GroovyExpression expression = GroovyExpression.forExpression("x + y");
        String toString = expression.toString();
        assertTrue(toString.contains("expression"));
        assertTrue(toString.contains("x + y"));
    }

    @Test
    void testNaturalKeyBuilder() {
        String expression = """
                    OA_naturalKeyBuilder
                        .forDatumField("agroecosystem").onDataName("tr_agroecosystem_agr").forKey("agr_key").withException("MISSING_AGROECOSYSTEM")
                        .forDatumField("site").onDataName("tr_site_sit").forKey("sit_key").withException("MISSING_SITE")
                        .forDatumField("plot").onDataName("tr_parcelle_par").forKey("par_key").withException("MISSING_PLOT")
                        .naturalKey()
                """;

        GroovyExpression groovyExpression = GroovyExpression.forExpression(expression);
        Object result = groovyExpression.evaluate(context);

        assertEquals("agroecosysteme_1__site_experimental_1__parcelle_2", result);
    }

    @Test
    void testEscapeLabel() {
        String expression = "OA_escapeLabel('Test Label')";
        GroovyExpression groovyExpression = GroovyExpression.forExpression(expression);
        Object result = groovyExpression.evaluate(context);

        assertEquals("test_label", result);
    }

    @Test
    void testBuildCompositeKey() {
        context = Map.of("datum",
                Map.of(
                        "agroecosystem", "Agroécosysteme 1",
                        "site", "Site expérimental 1",
                        "plot", "parcelle 2"
                )
        );
        String expression = "OA_buildCompositeKey(['agroecosystem', 'site', 'plot'])";
        GroovyExpression groovyExpression = GroovyExpression.forExpression(expression);
        Object result = groovyExpression.evaluate(context);

        assertEquals("agroecosysteme_1__site_experimental_1__parcelle_2", result);
    }

    @Test
    void testBuildCompositeKeyWithNullValue() {
        context = Map.of("datum",
                Map.of(
                        "agroecosystem", "Agroécosysteme 1",
                        "site", "",
                        "plot", "parcelle 2"
                )
        );
        String expression = "OA_buildCompositeKey(['agroecosystem', 'site', 'plot'])";
        GroovyExpression groovyExpression = GroovyExpression.forExpression(expression);
        Object result = groovyExpression.evaluate(context);

        assertEquals("agroecosysteme_1__NULL_KEY__parcelle_2", result);
    }

    @Test
    void testBuildCompositeKeyWithAllValueNull() {
        context = Map.of("datum",
                Map.of(
                        "agroecosystem", "",
                        "site", "",
                        "plot", ""
                )
        );
        String expression = "OA_buildCompositeKey(['agroecosystem', 'site', 'plot'])";
        GroovyExpression groovyExpression = GroovyExpression.forExpression(expression);
        Object result = groovyExpression.evaluate(context);

        assertEquals("", result);
    }

    @Test
    void testBuildManyCompositeKey() {
        context = Map.of("datum",
                Map.of(
                        "agroecosystem", "Agroécosysteme 1, Agroécosysteme 2",
                        "site", "Site expérimental 1, Site expérimental 1",
                        "plot", "parcelle 1, parcelle 2"
                )
        );
        String expression = "OA_buildManyCompositeKey(['agroecosystem', 'site', 'plot'])";
        GroovyExpression groovyExpression = GroovyExpression.forExpression(expression);
        Object result = groovyExpression.evaluate(context);

        assertEquals("agroecosysteme_1__site_experimental_1__parcelle_1,agroecosysteme_2__site_experimental_1__parcelle_2", result);
    }

    @Test
    void testBuildManyCompositeKeyWithNullValue() {
        context = Map.of("datum",
                Map.of(
                        "agroecosystem", "Agroécosysteme 1,",
                        "site", "Site expérimental 1, Site expérimental 1",
                        "plot", ",parcelle 1"
                )
        );
        String expression = "OA_buildManyCompositeKey(['agroecosystem', 'site', 'plot'])";
        GroovyExpression groovyExpression = GroovyExpression.forExpression(expression);
        Object result = groovyExpression.evaluate(context);

        assertEquals("agroecosysteme_1__site_experimental_1__NULL_KEY,NULL_KEY__site_experimental_1__parcelle_1", result);
    }

    @Test
    void testBuildManyCompositeKeyWithAllNullValue() {
        context = Map.of("datum",
                Map.of(
                        "agroecosystem", ",",
                        "site", "",
                        "plot", ""
                )
        );
        String expression = "OA_buildManyCompositeKey(['agroecosystem', 'site', 'plot'])";
        GroovyExpression groovyExpression = GroovyExpression.forExpression(expression);
        Object result = groovyExpression.evaluate(context);

        assertEquals("", result);
    }

    @Test
    void testBuildException() {
        String expression = """
                    throw OA_buildException(
                          "MISSING_SITE", \s
                          Map.of(
                            "site",  datum.site,\s
                            "knownSites", references.tr_site_sit.refValues.collect{it.sit_key}
                          )
                      )
                """;

        GroovyExpression groovyExpression = GroovyExpression.forExpression(expression);
        try {
            Object result = groovyExpression.evaluate(context);
            fail();
        } catch (GroovyException groovyException) {
            Map<String, Object> params = groovyException.getParams();
            String message = groovyException.getMessage();
            assertEquals("MISSING_SITE", message);
            assertEquals("Site Expérimental 1", params.get("site"));
            Assertions.assertThatCollection((List) params.get("knownSites"))
                    .hasSameElementsAs(List.of("site_experimental_1", "site_experimental_2"));
        }
    }

    @Test
    void testNaturalKeyBuilderWithInvalidData() {
        context.put("datum", Map.of(
                "agroecosystem", "Agroécosystème Inconnu",
                "site", "Site Expérimental 1",
                "plot", "Parcelle 2"
        ));

        String expression = """
                    OA_naturalKeyBuilder
                            .forDatumField("agroecosystem")
                            .onDataName("tr_agroecosystem_agr")
                            .forKey("agr_key")
                            .withException("MISSING_AGROECOSYSTEM")
                            .naturalKey()
                """;

        GroovyExpression groovyExpression = GroovyExpression.forExpression(expression);
        try {
            Object result = groovyExpression.evaluate(context);
        } catch (GroovyException groovyException) {
            assertEquals("MISSING_AGROECOSYSTEM", groovyException.getMessage());
            Map<String, Object> params = groovyException.getParams();
            assertEquals("agroecosystem", params.get("value"));
            Assertions.assertThatCollection((List) params.get("knownValues"))
                    .hasSameElementsAs(List.of("agroecosysteme_1", "agroecosysteme_2"));
        }
    }

    // ─── R-P2-4 : cache des résultats Groovy ─────────────────────────────────

    /**
     * R-P2-4 — Quand le contexte contient "currentRow" (cas normal des
     * validations ligne par ligne), le cache NE DOIT PAS être utilisé.
     * Les deux appels doivent retourner le bon résultat sans erreur.
     */
    @Test
    @Tag("PERF")
    void evaluate_contextAvecCurrentRow_cacheBypasse() {
        Map<String, Object> ctx = new HashMap<>(context);
        ctx.put("currentRow", List.of("Paris", "75001"));
        ctx.put("currentRowNumber", 1);

        GroovyExpression expr = GroovyExpression.forExpression("x + y");
        // Deux appels avec currentRow différent : doivent retourner le bon résultat
        // sans que le cache produise un résultat périmé
        ctx.put("x", 5);
        ctx.put("y", 3);
        Object r1 = expr.evaluate(ctx);
        ctx = new HashMap<>(ctx);
        ctx.put("currentRow", List.of("Lyon", "69000"));
        ctx.put("x", 10);
        ctx.put("y", 2);
        Object r2 = expr.evaluate(ctx);

        assertEquals(8,  r1, "Premier appel x=5+y=3");
        assertEquals(12, r2, "Second appel x=10+y=2 (cache ne doit pas masquer le nouveau résultat)");
    }

    /**
     * R-P2-4 — Quand le contexte est purement statique (pas de "currentRow"),
     * le cache DOIT fonctionner : deux appels avec le même contexte doivent
     * retourner le même résultat sans ré-évaluation (comportement observable
     * indirectement : pas de différence de résultat attendu).
     */
    @Test
    @Tag("PERF")
    void evaluate_contextStatiqueSansCurrent_cacheActif() {
        Map<String, Object> staticCtx = Map.of("x", 5, "y", 3);
        GroovyExpression expr = GroovyExpression.forExpression("x * y");
        Object r1 = expr.evaluate(staticCtx);
        Object r2 = expr.evaluate(staticCtx); // doit venir du cache
        assertEquals(15, r1);
        assertEquals(r1, r2, "Le cache doit retourner le même résultat pour un contexte statique identique");
    }

    /**
     * R-P2-4 — Les expressions contenant "currentRowNumber" ne sont pas
     * cacheables (isCacheable retourne false).
     */
    @Test
    @Tag("PERF")
    void evaluate_expressionAvecCurrentRowNumber_cacheBypasse() {
        Map<String, Object> ctx = new HashMap<>();
        ctx.put("currentRowNumber", 1);
        ctx.put("x", 5);

        GroovyExpression expr = GroovyExpression.forExpression("x + currentRowNumber");
        Object r1 = expr.evaluate(ctx);

        ctx = new HashMap<>(ctx);
        ctx.put("currentRowNumber", 10);
        Object r2 = expr.evaluate(ctx);

        assertEquals(6,  r1, "x=5 + currentRowNumber=1");
        assertEquals(15, r2, "x=5 + currentRowNumber=10 (pas de faux hit de cache)");
    }


    public record ReferenceBuilder(String naturalKey, Map<String, Object> refValues) implements GroovyDecorator {

        @Override
        public String getHierarchicalKey() {
            return "";
        }

        @Override
        public String getNaturalKey() {
            return naturalKey();
        }

        @Override
        public Map<String, Object> getRefValues() {
            return refValues();
        }
    }

}