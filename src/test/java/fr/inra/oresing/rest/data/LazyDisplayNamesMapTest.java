package fr.inra.oresing.rest.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link LazyDisplayNamesMap} . Verifies the lazy-load
 * contract : valid keys trigger the loader on the first call and the
 * result is memoised ; invalid keys are rejected without DB hit ;
 * iteration operations fail fast .
 *
 * @author R.YAHIAOUI
 */
class LazyDisplayNamesMapTest {

    @Test
    @DisplayName("get( valid key ) loads from loader on first call , caches afterwards")
    void getValidKeyLoadsLazilyAndCaches() {
        AtomicInteger loaderCalls = new AtomicInteger(0);
        Function<String, Map<String, Map<String, String>>> loader = ref -> {
            loaderCalls.incrementAndGet();
            Map<String, Map<String, String>> result = new HashMap<>();
            result.put("nk1", Map.of("fr", "ref-" + ref + "-nk1-fr"));
            return result;
        };
        LazyDisplayNamesMap map = new LazyDisplayNamesMap(Set.of("ref_a", "ref_b"), loader);

        // No load on construction
        assertThat(loaderCalls.get()).isZero();
        assertThat(map.loadedCount()).isZero();

        // First get triggers load
        Map<String, Map<String, String>> a1 = map.get("ref_a");
        assertThat(loaderCalls.get()).isEqualTo(1);
        assertThat(a1).containsKey("nk1");
        assertThat(map.loadedCount()).isEqualTo(1);

        // Second get on same key : cache hit , no new load
        Map<String, Map<String, String>> a2 = map.get("ref_a");
        assertThat(loaderCalls.get()).isEqualTo(1);
        assertThat(a2).isSameAs(a1);

        // get on different valid key : new load
        map.get("ref_b");
        assertThat(loaderCalls.get()).isEqualTo(2);
        assertThat(map.loadedCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("get( invalid key ) returns null without invoking loader")
    void getInvalidKeyDoesNotInvokeLoader() {
        AtomicInteger loaderCalls = new AtomicInteger(0);
        Function<String, Map<String, Map<String, String>>> loader = ref -> {
            loaderCalls.incrementAndGet();
            return new HashMap<>();
        };
        LazyDisplayNamesMap map = new LazyDisplayNamesMap(Set.of("ref_a"), loader);

        assertThat(map.get("not_a_valid_ref")).isNull();
        assertThat(map.get("ref_b")).isNull();
        assertThat(loaderCalls.get()).isZero();
    }

    @Test
    @DisplayName("getOrDefault returns default when key invalid , loaded value otherwise")
    void getOrDefaultRoutesThroughGet() {
        Function<String, Map<String, Map<String, String>>> loader = ref ->
                Map.of("nk", Map.of("fr", "label-" + ref));
        LazyDisplayNamesMap map = new LazyDisplayNamesMap(Set.of("ref_a"), loader);

        Map<String, Map<String, String>> defaultMap = new HashMap<>();
        assertThat(map.getOrDefault("ref_a", defaultMap)).containsKey("nk");
        assertThat(map.getOrDefault("ref_x", defaultMap)).isSameAs(defaultMap);
    }

    @Test
    @DisplayName("loader returning null is normalised to empty map ( computeIfAbsent contract )")
    void loaderReturningNullIsNormalisedToEmptyMap() {
        Function<String, Map<String, Map<String, String>>> loader = ref -> null;
        LazyDisplayNamesMap map = new LazyDisplayNamesMap(Set.of("ref_a"), loader);

        Map<String, Map<String, String>> result = map.get("ref_a");
        assertThat(result).isNotNull().isEmpty();
        // Second get : still empty , no new loader invocation needed
        assertThat(map.get("ref_a")).isSameAs(result);
    }

    @Test
    @DisplayName("containsKey is cheap : checks validKeys only , no loader call")
    void containsKeyDoesNotInvokeLoader() {
        AtomicInteger loaderCalls = new AtomicInteger(0);
        Function<String, Map<String, Map<String, String>>> loader = ref -> {
            loaderCalls.incrementAndGet();
            return new HashMap<>();
        };
        LazyDisplayNamesMap map = new LazyDisplayNamesMap(Set.of("ref_a", "ref_b"), loader);

        assertThat(map.containsKey("ref_a")).isTrue();
        assertThat(map.containsKey("ref_b")).isTrue();
        assertThat(map.containsKey("ref_x")).isFalse();
        assertThat(map.containsKey(42)).isFalse(); // not a String
        assertThat(loaderCalls.get()).isZero();
    }

    @Test
    @DisplayName("isEmpty reflects validKeys , not cache state")
    void isEmptyChecksValidKeys() {
        LazyDisplayNamesMap empty = new LazyDisplayNamesMap(Set.of(), ref -> new HashMap<>());
        LazyDisplayNamesMap nonEmpty = new LazyDisplayNamesMap(Set.of("ref_a"), ref -> new HashMap<>());
        assertThat(empty.isEmpty()).isTrue();
        assertThat(nonEmpty.isEmpty()).isFalse();
    }

    @Test
    @DisplayName("entrySet throws : iteration would defeat the lazy contract")
    void entrySetThrows() {
        LazyDisplayNamesMap map = new LazyDisplayNamesMap(Set.of("ref_a"), ref -> new HashMap<>());
        assertThatThrownBy(map::entrySet)
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("iteration-hostile");
    }

    @Test
    @DisplayName("constructor rejects null validKeys / loader")
    void constructorRejectsNullArgs() {
        assertThatThrownBy(() -> new LazyDisplayNamesMap(null, ref -> new HashMap<>()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LazyDisplayNamesMap(Set.of("a"), null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
