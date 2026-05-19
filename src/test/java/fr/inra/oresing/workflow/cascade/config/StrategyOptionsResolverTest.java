package fr.inra.oresing.workflow.cascade.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link StrategyOptionsResolver}.
 *
 * <p>Utilise une {@link ConfigFieldRegistry} minimale (fake) et
 * une liste de règles contrôlées pour vérifier la résolution des
 * options autorisées/bloquées par field enum hot-editable.
 */
@Tag("core.config")
@DisplayName("StrategyOptionsResolver — résolution des options enum")
class StrategyOptionsResolverTest {

    /**
     * Faux registre avec un seul field enum hot "sinkStrategy"
     * dont les valeurs autorisées sont MERGE_FILE, DIRECT_COPY.
     */
    private static ConfigFieldRegistry fakeRegistry() {
        ImportProperties props = new ImportProperties();
        ConfigFieldRegistryTest.FakePoolReloader reloader =
                new ConfigFieldRegistryTest.FakePoolReloader();
        fr.inra.oresing.workflow.cascade.ImportRateLimiter importRl =
                new fr.inra.oresing.workflow.cascade.ImportRateLimiter(
                        3, null, null, null);
        fr.inra.oresing.workflow.cascade.ExtractionRateLimiter extractionRl =
                new fr.inra.oresing.workflow.cascade.ExtractionRateLimiter(
                        5, 0L, null, null, null);
        ConfigFieldRegistry reg = new ConfigFieldRegistry(props, new PublishProperties(), reloader, importRl, extractionRl,
                Optional.empty());
        reg.registerAll();
        return reg;
    }

    private ConfigFieldRegistry registry;

    @BeforeEach
    void setUp() {
        registry = fakeRegistry();
    }

    @Test
    @DisplayName("resolve() retourne au moins un field enum hot")
    void resolveReturnsEnumFields() {
        StrategyOptionsResolver resolver = new StrategyOptionsResolver(registry, List.of());
        Map<String, List<StrategyOptionsResolver.Option>> result =
                resolver.resolve(registry.snapshot());
        assertFalse(result.isEmpty(), "Should have at least one enum field");
        assertTrue(result.containsKey("sinkStrategy"),
                "sinkStrategy should be in the result");
    }

    @Test
    @DisplayName("sans règle BLOCKING, toutes les options sont allowed=true")
    void noBlockingRulesAllAllowed() {
        StrategyOptionsResolver resolver = new StrategyOptionsResolver(registry, List.of());
        Map<String, List<StrategyOptionsResolver.Option>> result =
                resolver.resolve(registry.snapshot());

        for (Map.Entry<String, List<StrategyOptionsResolver.Option>> entry : result.entrySet()) {
            for (StrategyOptionsResolver.Option opt : entry.getValue()) {
                assertTrue(opt.allowed(),
                        "Without blocking rules, all options should be allowed, but "
                                + entry.getKey() + "=" + opt.value() + " is blocked");
                assertNull(opt.blockReason(),
                        "blockReason should be null when allowed");
            }
        }
    }

    @Test
    @DisplayName("une règle BLOCKING qui s'applique rend l'option allowed=false")
    void blockingRuleBlocksOption() {
        // Règle BLOCKING : refuse toujours MERGE_FILE comme sinkStrategy
        ConsistencyRule blockMergeFile = new ConsistencyRule() {
            @Override public String code() { return "TEST_BLOCK_MERGE_FILE"; }
            @Override public Severity severity() { return Severity.BLOCKING; }
            @Override public Optional<String> check(EffectiveConfig c) {
                if ("MERGE_FILE".equals(c.stringValue("sinkStrategy"))) {
                    return Optional.of("MERGE_FILE interdit (test)");
                }
                return Optional.empty();
            }
        };

        StrategyOptionsResolver resolver = new StrategyOptionsResolver(
                registry, List.of(blockMergeFile));
        Map<String, List<StrategyOptionsResolver.Option>> result =
                resolver.resolve(registry.snapshot());

        List<StrategyOptionsResolver.Option> opts = result.get("sinkStrategy");
        assertNotNull(opts);

        StrategyOptionsResolver.Option mergeFileOpt = opts.stream()
                .filter(o -> "MERGE_FILE".equals(o.value()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("MERGE_FILE option not found"));

        assertFalse(mergeFileOpt.allowed(), "MERGE_FILE should be blocked");
        assertNotNull(mergeFileOpt.blockReason(), "blockReason should be set");
        assertTrue(mergeFileOpt.blockReason().contains("MERGE_FILE interdit"),
                "blockReason should contain the rule message");
    }

    @Test
    @DisplayName("une règle WARNING n'influence pas allowed")
    void warningRuleDoesNotBlock() {
        ConsistencyRule warnRule = new ConsistencyRule() {
            @Override public String code() { return "TEST_WARNING"; }
            @Override public Severity severity() { return Severity.WARNING; }
            @Override public Optional<String> check(EffectiveConfig c) {
                return Optional.of("warning toujours déclenché");
            }
        };

        StrategyOptionsResolver resolver = new StrategyOptionsResolver(
                registry, List.of(warnRule));
        Map<String, List<StrategyOptionsResolver.Option>> result =
                resolver.resolve(registry.snapshot());

        for (List<StrategyOptionsResolver.Option> opts : result.values()) {
            for (StrategyOptionsResolver.Option opt : opts) {
                assertTrue(opt.allowed(),
                        "WARNING rules should not block options");
            }
        }
    }

    @Test
    @DisplayName("Option record : value/allowed/blockReason accessibles")
    void optionRecord() {
        StrategyOptionsResolver.Option opt1 = new StrategyOptionsResolver.Option("MERGE_FILE", true, null);
        assertEquals("MERGE_FILE", opt1.value());
        assertTrue(opt1.allowed());
        assertNull(opt1.blockReason());

        StrategyOptionsResolver.Option opt2 = new StrategyOptionsResolver.Option("DIRECT_COPY", false, "raison");
        assertEquals("DIRECT_COPY", opt2.value());
        assertFalse(opt2.allowed());
        assertEquals("raison", opt2.blockReason());
    }

    @Test
    @DisplayName("resolve() avec effective config modifiée : la valeur hypothétique est utilisée")
    void resolveUsesHypotheticalConfig() {
        // Règle BLOCKING : refuse DIRECT_COPY comme sinkStrategy
        ConsistencyRule blockDirectCopy = new ConsistencyRule() {
            @Override public String code() { return "TEST_BLOCK_DIRECT_COPY"; }
            @Override public Severity severity() { return Severity.BLOCKING; }
            @Override public Optional<String> check(EffectiveConfig c) {
                if ("DIRECT_COPY".equals(c.stringValue("sinkStrategy"))) {
                    return Optional.of("DIRECT_COPY bloqué (test)");
                }
                return Optional.empty();
            }
        };

        StrategyOptionsResolver resolver = new StrategyOptionsResolver(
                registry, List.of(blockDirectCopy));
        // current effective = MERGE_FILE (défaut ImportProperties)
        Map<String, List<StrategyOptionsResolver.Option>> result =
                resolver.resolve(registry.snapshot());

        List<StrategyOptionsResolver.Option> opts = result.get("sinkStrategy");
        assertNotNull(opts);

        // MERGE_FILE ne devrait pas être bloqué
        StrategyOptionsResolver.Option mergeOpt = opts.stream()
                .filter(o -> "MERGE_FILE".equals(o.value()))
                .findFirst()
                .orElseThrow();
        assertTrue(mergeOpt.allowed());

        // DIRECT_COPY devrait être bloqué
        StrategyOptionsResolver.Option directOpt = opts.stream()
                .filter(o -> "DIRECT_COPY".equals(o.value()))
                .findFirst()
                .orElseThrow();
        assertFalse(directOpt.allowed());
        assertNotNull(directOpt.blockReason());
    }

    @Test
    @DisplayName("résultat ne contient pas de champs non-enum")
    void noNonEnumFields() {
        StrategyOptionsResolver resolver = new StrategyOptionsResolver(registry, List.of());
        Map<String, List<StrategyOptionsResolver.Option>> result =
                resolver.resolve(registry.snapshot());

        // Les champs int/bool/string ne doivent pas apparaître dans le résultat
        assertFalse(result.containsKey("chunkSizeLines"),
                "int field should not appear in resolve() output");
        assertFalse(result.containsKey("parallelism"),
                "int field should not appear in resolve() output");
    }
}
