package fr.inra.oresing.domain.data.deposit.prescan;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.repository.data.DataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests pour {@link LazyParentLoader} . Verifie le cache positif +
 * negatif , le batch coalescing , les statistiques , et le respect
 * de l'invariant iso-resultat sur les naturalkeys reellement
 * demandees ( sous-ensemble strict de ce qu'aurait retourne un full
 * preload ) .
 */
class LazyParentLoaderTest {

    private DataRepository    repo;
    private LazyParentLoader  loader;

    @BeforeEach
    void setUp() {
        repo   = mock(DataRepository.class);
        loader = new LazyParentLoader(repo, "site");
    }

    @Test
    void request_returns_true_when_cache_hit() {
        Ltree nk = Ltree.fromSql("foo");
        UUID id = UUID.randomUUID();
        loader.putKnown(nk, id);
        assertThat(loader.request(nk)).isTrue();
        assertThat(loader.getCachedId(nk)).isEqualTo(id);
        verify(repo, never()).getDataIdPerKeysByNaturalKeys(any(), any());
    }

    @Test
    void request_returns_false_on_cache_miss_and_adds_to_pending() {
        Ltree nk = Ltree.fromSql("foo");
        assertThat(loader.request(nk)).isFalse();
        // pending = { foo } ; flush triggers query
        UUID id = UUID.randomUUID();
        when(repo.getDataIdPerKeysByNaturalKeys(eq("site"), any()))
                .thenReturn(ImmutableMap.of(
                        new DataValue.LineIdentityColumnName(nk, Ltree.fromSql("foo"), null), id));
        loader.flush();
        assertThat(loader.getCachedId(nk)).isEqualTo(id);
    }

    @Test
    void flush_groups_pending_into_single_query() {
        Ltree a = Ltree.fromSql("a");
        Ltree b = Ltree.fromSql("b");
        Ltree c = Ltree.fromSql("c");
        loader.request(a);
        loader.request(b);
        loader.request(c);

        when(repo.getDataIdPerKeysByNaturalKeys(eq("site"), any()))
                .thenReturn(ImmutableMap.of(
                        new DataValue.LineIdentityColumnName(a, Ltree.fromSql("a"), null), UUID.randomUUID(),
                        new DataValue.LineIdentityColumnName(b, Ltree.fromSql("b"), null), UUID.randomUUID()
                ));
        loader.flush();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Set<String>> cap = ArgumentCaptor.forClass(Set.class);
        verify(repo, times(1)).getDataIdPerKeysByNaturalKeys(eq("site"), cap.capture());
        assertThat(cap.getValue()).containsExactlyInAnyOrder("a", "b", "c");
    }

    @Test
    void negative_cache_prevents_requery_of_missing_keys() {
        Ltree missing = Ltree.fromSql("ghost");
        loader.request(missing);
        when(repo.getDataIdPerKeysByNaturalKeys(eq("site"), any()))
                .thenReturn(ImmutableMap.of());
        loader.flush();

        // Second request : should NOT add to pending ( negative cache )
        assertThat(loader.request(missing)).isTrue();   // counts as hit ( found in cache )
        // getCachedId returns null because it's negative-cached
        assertThat(loader.getCachedId(missing)).isNull();

        // Flush should be no-op ( pending empty )
        loader.flush();
        // Only ONE query was made
        verify(repo, times(1)).getDataIdPerKeysByNaturalKeys(eq("site"), any());
    }

    @Test
    void cache_hits_short_circuit_after_first_load() {
        Ltree nk = Ltree.fromSql("city");
        UUID id = UUID.randomUUID();
        loader.request(nk);
        when(repo.getDataIdPerKeysByNaturalKeys(eq("site"), any()))
                .thenReturn(ImmutableMap.of(
                        new DataValue.LineIdentityColumnName(nk, Ltree.fromSql("city"), null), id));
        loader.flush();

        // 100 subsequent requests should all be hits , no extra queries
        for (int i = 0; i < 100; i++) {
            assertThat(loader.request(nk)).isTrue();
        }
        loader.flush();
        verify(repo, times(1)).getDataIdPerKeysByNaturalKeys(eq("site"), any());
        assertThat(loader.stats().totalCacheHits()).isGreaterThanOrEqualTo(100);
    }

    @Test
    void flush_is_no_op_when_pending_is_empty() {
        loader.flush();
        verify(repo, never()).getDataIdPerKeysByNaturalKeys(any(), any());
    }

    @Test
    void preload_loads_a_known_set_in_one_query() {
        Set<String> known = new HashSet<>();
        known.add("foo");
        known.add("bar");
        Ltree foo = Ltree.fromSql("foo");
        Ltree bar = Ltree.fromSql("bar");
        UUID fooId = UUID.randomUUID();

        when(repo.getDataIdPerKeysByNaturalKeys(eq("site"), eq(known)))
                .thenReturn(ImmutableMap.of(
                        new DataValue.LineIdentityColumnName(foo, Ltree.fromSql("foo"), null), fooId
                ));
        loader.preload(known);

        assertThat(loader.getCachedId(foo)).isEqualTo(fooId);
        assertThat(loader.getCachedId(bar)).isNull(); // negative-cached
        // re-request bar should hit cache , not query
        assertThat(loader.request(bar)).isTrue();
        loader.flush();
        verify(repo, times(1)).getDataIdPerKeysByNaturalKeys(any(), any());
    }

    @Test
    void putKnown_inserts_directly_without_query() {
        Ltree nk = Ltree.fromSql("baz");
        UUID id = UUID.randomUUID();
        loader.putKnown(nk, id);
        assertThat(loader.request(nk)).isTrue();
        assertThat(loader.getCachedId(nk)).isEqualTo(id);
        verify(repo, never()).getDataIdPerKeysByNaturalKeys(any(), any());
    }

    @Test
    void null_inputs_are_ignored_safely() {
        loader.request(null);
        loader.putKnown(null, UUID.randomUUID());
        loader.putKnown(Ltree.fromSql("x"), null);
        assertThat(loader.getCachedId(null)).isNull();
        loader.flush();
        verify(repo, never()).getDataIdPerKeysByNaturalKeys(any(), any());
    }

    @Test
    void preload_empty_or_null_set_is_no_op() {
        loader.preload(null);
        loader.preload(new HashSet<>());
        verify(repo, never()).getDataIdPerKeysByNaturalKeys(any(), any());
    }

    @Test
    void stats_track_requests_hits_queries_and_rows() {
        Ltree nk = Ltree.fromSql("foo");
        UUID id = UUID.randomUUID();
        loader.request(nk);   // miss + pending
        when(repo.getDataIdPerKeysByNaturalKeys(eq("site"), any()))
                .thenReturn(ImmutableMap.of(
                        new DataValue.LineIdentityColumnName(nk, Ltree.fromSql("foo"), null), id));
        loader.flush();
        loader.request(nk);   // hit
        loader.request(nk);   // hit

        LazyParentLoader.Stats s = loader.stats();
        assertThat(s.totalRequests()).isEqualTo(3);
        assertThat(s.totalCacheHits()).isEqualTo(2);
        assertThat(s.totalQueries()).isEqualTo(1);
        assertThat(s.totalRowsLoaded()).isEqualTo(1);
        assertThat(s.cacheSize()).isGreaterThanOrEqualTo(1);
        assertThat(s.pendingSize()).isZero();
        assertThat(s.hitRate()).isCloseTo(2.0 / 3.0, org.assertj.core.data.Offset.offset(0.001));
    }
}
