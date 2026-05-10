package fr.inra.oresing.rest;

import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.usecases.application.GetApplicationUseCase;
import fr.inra.oresing.rest.usecases.data.ReadEntryUseCase;
import fr.inra.oresing.rest.usecases.data.SendZipLinkByMailUseCase;
import fr.inra.oresing.rest.usecases.data.WriteUploadBundleUseCase;
import fr.inra.oresing.rest.usecases.security.authentication.GetCurrentUserUseCase;
import fr.inra.oresing.rest.usecases.storage.binaryfile.CreateDataUseCase;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.LocaleResolver;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests unitaires de la logique interne de {@link BundleResources}.
 *
 * <p>Ces tests ne démarrent <em>pas</em> de contexte Spring ni de conteneur Docker.
 * Ils couvrent principalement l'algorithme de tri topologique ({@code processBatchTopological})
 * et la robustesse des cas limites.
 */
@Tag("integration.bundle")
@DisplayName("Tests unitaires — BundleResources")
@ExtendWith(MockitoExtension.class)
@Slf4j
class BundleResourcesUnitTest {

    // ── Mocks pour le constructeur ───────────────────────────────────────────

    @Mock GetApplicationUseCase    getApplicationUseCase;
    @Mock GetCurrentUserUseCase    getCurrentUserUseCase;
    @Mock WriteUploadBundleUseCase writeUploadBundleUseCase;
    @Mock SendZipLinkByMailUseCase sendZipLinkByMailUseCase;
    @Mock ReadEntryUseCase         readEntryUseCase;
    @Mock CreateDataUseCase        createDataUseCase;
    @Mock DataService              dataService;
    @Mock LocaleResolver           localeResolver;
    @Mock fr.inra.oresing.persistence.JsonRowMapper<Object> jsonRowMapper;

    private ExecutorService normalExecutor;
    private ExecutorService heavyExecutor;
    private BundleResources bundleResources;

    @BeforeEach
    void setUp() {
        normalExecutor = Executors.newFixedThreadPool(2);
        heavyExecutor  = Executors.newFixedThreadPool(4);
        bundleResources = new BundleResources(
                getApplicationUseCase, getCurrentUserUseCase,
                writeUploadBundleUseCase, sendZipLinkByMailUseCase,
                readEntryUseCase, createDataUseCase,
                dataService, localeResolver, jsonRowMapper,
                normalExecutor, heavyExecutor);
    }

    @AfterEach
    void tearDown() {
        normalExecutor.shutdownNow();
        heavyExecutor.shutdownNow();
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Crée une fonction {@code processReference} qui :
     * <ul>
     *   <li>Enregistre le nom dans {@code order}</li>
     *   <li>Ajoute le nom dans {@code processed} pour que la vague suivante soit correctement calculée</li>
     * </ul>
     */
    private Function<String, Mono<Void>> recordingFn(List<String> order, Set<String> processed) {
        return name -> Mono.fromRunnable(() -> {
            order.add(name);
            processed.add(name);
        });
    }

    // ── Tests ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Manifest vide → Mono.empty() sans erreur")
    void processBatchTopological_emptyManifest_completesImmediately() {
        Set<String> processed = ConcurrentHashMap.newKeySet();
        Set<String> remaining = ConcurrentHashMap.newKeySet(); // vide

        bundleResources
                .processBatchTopological(processed, remaining, name -> Mono.empty(), Map.of(), 10)
                .block(); // ne doit pas lever d'exception

        assertThat(processed).isEmpty();
    }

    @Test
    @DisplayName("Aucune dépendance : tous les éléments sont traités en une seule vague")
    void processBatchTopological_noDependencies_processesAll() {
        Set<String> processed = ConcurrentHashMap.newKeySet();
        Set<String> remaining = ConcurrentHashMap.newKeySet();
        remaining.addAll(Set.of("a", "b", "c"));

        List<String> order = Collections.synchronizedList(new ArrayList<>());

        bundleResources
                .processBatchTopological(processed, remaining, recordingFn(order, processed), Map.of(), 10)
                .block();

        assertThat(order).containsExactlyInAnyOrder("a", "b", "c");
        assertThat(remaining).isEmpty();
    }

    @Test
    @DisplayName("Chaîne linéaire a → b → c : ordre topologique respecté")
    void processBatchTopological_linearChain_preservesTopologicalOrder() {
        Set<String> processed = ConcurrentHashMap.newKeySet();
        Set<String> remaining = ConcurrentHashMap.newKeySet();
        remaining.addAll(Set.of("a", "b", "c"));

        // b dépend de a, c dépend de b
        Map<String, List<String>> deps = Map.of(
                "b", List.of("a"),
                "c", List.of("b")
        );

        List<String> order = Collections.synchronizedList(new ArrayList<>());

        bundleResources
                .processBatchTopological(processed, remaining, recordingFn(order, processed), deps, 10)
                .block();

        assertThat(order).containsExactlyInAnyOrder("a", "b", "c");
        // a doit précéder b, b doit précéder c
        assertThat(order.indexOf("a")).isLessThan(order.indexOf("b"));
        assertThat(order.indexOf("b")).isLessThan(order.indexOf("c"));
    }

    @Test
    @DisplayName("DAG en losange a → (b,c) → d : a et d sont aux extrêmes")
    void processBatchTopological_diamondDag_processesCorrectly() {
        Set<String> processed = ConcurrentHashMap.newKeySet();
        Set<String> remaining = ConcurrentHashMap.newKeySet();
        remaining.addAll(Set.of("a", "b", "c", "d"));

        // d dépend de b et c ; b et c dépendent de a
        Map<String, List<String>> deps = Map.of(
                "b", List.of("a"),
                "c", List.of("a"),
                "d", List.of("b", "c")
        );

        List<String> order = Collections.synchronizedList(new ArrayList<>());

        bundleResources
                .processBatchTopological(processed, remaining, recordingFn(order, processed), deps, 10)
                .block();

        assertThat(order).containsExactlyInAnyOrder("a", "b", "c", "d");
        // a doit être le premier, d le dernier
        assertThat(order.indexOf("a")).isZero();
        assertThat(order.indexOf("d")).isEqualTo(3);
        // b et c se trouvent entre a et d (dans n'importe quel ordre entre eux)
        assertThat(order.indexOf("b")).isBetween(1, 2);
        assertThat(order.indexOf("c")).isBetween(1, 2);
    }

    @Test
    @DisplayName("Self-loop : une dépendance vers soi-même est ignorée")
    void processBatchTopological_selfLoop_isIgnoredAndProcessed() {
        Set<String> processed = ConcurrentHashMap.newKeySet();
        Set<String> remaining = ConcurrentHashMap.newKeySet();
        remaining.add("a");

        List<String> order = Collections.synchronizedList(new ArrayList<>());

        // a dépend de lui-même → doit être filtré dans l'algorithme
        Map<String, List<String>> deps = Map.of("a", List.of("a"));

        bundleResources
                .processBatchTopological(processed, remaining, recordingFn(order, processed), deps, 10)
                .block();

        assertThat(order).containsExactly("a");
    }

    @Test
    @DisplayName("Cycle de dépendances : RuntimeException levée")
    void processBatchTopological_cycle_throwsRuntimeException() {
        Set<String> processed = ConcurrentHashMap.newKeySet();
        Set<String> remaining = ConcurrentHashMap.newKeySet();
        remaining.addAll(Set.of("a", "b"));

        // a dépend de b et b dépend de a → cycle
        Map<String, List<String>> deps = Map.of(
                "a", List.of("b"),
                "b", List.of("a")
        );

        assertThrows(RuntimeException.class, () ->
                bundleResources
                        .processBatchTopological(processed, remaining, name -> Mono.empty(), deps, 10)
                        .block()
        );
    }

    @Test
    @DisplayName("Concurrence limitée : MAX_DB_CONCURRENCY respecté (1 = traitement séquentiel)")
    void processBatchTopological_maxConcurrencyOne_processesSequentially() {
        Set<String> processed = ConcurrentHashMap.newKeySet();
        Set<String> remaining = ConcurrentHashMap.newKeySet();
        remaining.addAll(Set.of("a", "b", "c"));

        AtomicInteger maxConcurrentSeen = new AtomicInteger(0);
        AtomicInteger currentlyRunning  = new AtomicInteger(0);

        Function<String, Mono<Void>> concurrencyCheckFn = name -> Mono.fromRunnable(() -> {
            int now = currentlyRunning.incrementAndGet();
            maxConcurrentSeen.accumulateAndGet(now, Math::max);
            processed.add(name);
            currentlyRunning.decrementAndGet();
        });

        bundleResources
                .processBatchTopological(processed, remaining, concurrencyCheckFn, Map.of(), 1)
                .block();

        assertThat(processed).containsExactlyInAnyOrder("a", "b", "c");
        // Avec concurrence = 1, on ne devrait jamais avoir plus d'1 tâche simultanée
        assertThat(maxConcurrentSeen.get()).isLessThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Dépendance partielle : les éléments sans dépendances traités en premier")
    void processBatchTopological_mixedDeps_independentItemsFirstWave() {
        Set<String> processed  = ConcurrentHashMap.newKeySet();
        Set<String> remaining  = ConcurrentHashMap.newKeySet();
        // "x" n'a pas de dépendance; "y" dépend de "x"; "z" n'a pas de dépendance
        remaining.addAll(Set.of("x", "y", "z"));

        Map<String, List<String>> deps = Map.of("y", List.of("x"));

        List<String> order = Collections.synchronizedList(new ArrayList<>());

        bundleResources
                .processBatchTopological(processed, remaining, recordingFn(order, processed), deps, 10)
                .block();

        assertThat(order).containsExactlyInAnyOrder("x", "y", "z");
        // y doit venir après x
        assertThat(order.indexOf("x")).isLessThan(order.indexOf("y"));
    }
}