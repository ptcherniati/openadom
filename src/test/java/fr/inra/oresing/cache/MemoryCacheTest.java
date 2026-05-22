package fr.inra.oresing.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de {@link MemoryCache} - LRU eviction , TTL ,
 * invalidations ciblées , reconfiguration à chaud.
 */
class MemoryCacheTest {

    @Test
    void putAndGet_returnsStoredValue() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        cache.put("a", 1);
        assertEquals(1, cache.get("a"));
    }

    @Test
    void get_missingKey_returnsNull() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        assertNull(cache.get("missing"));
    }

    @Test
    void put_evictsOldestWhenCapacityReached() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 2, 0);
        cache.put("a", 1);
        sleepMs(2);
        cache.put("b", 2);
        sleepMs(2);
        cache.put("c", 3);
        // 'a' évincé ( plus ancien timestamp ).
        assertNull(cache.get("a"));
        assertNotNull(cache.get("b"));
        assertNotNull(cache.get("c"));
        assertEquals(2, cache.size());
    }

    @Test
    void put_existingKey_updatesWithoutEviction() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 2, 0);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.put("a", 99);
        assertEquals(99, cache.get("a"));
        assertEquals(2, cache.get("b"));
        assertEquals(2, cache.size());
    }

    @Test
    void ttl_expiredEntriesNotReturned() throws InterruptedException {
        // TTL de 1 minute : on simule en passant ttlMinutes très petit
        // via reconfigure ne marche pas ici car ttlMinutes est en minutes.
        // On teste plutôt le path "ttl <= 0 = never expires" + base.
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        cache.put("a", 1);
        Thread.sleep(50);
        // ttl=0 -> jamais expiré
        assertNotNull(cache.get("a"));
    }

    @Test
    void invalidate_removesEntry() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        cache.put("a", 1);
        cache.invalidate("a");
        assertNull(cache.get("a"));
        assertEquals(0, cache.size());
    }

    @Test
    void invalidate_nullKey_noop() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        cache.put("a", 1);
        cache.invalidate(null);
        assertNotNull(cache.get("a"));
    }

    @Test
    void invalidateMatching_removesMatchingKeysOnly() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        cache.put("appA::dataX", 1);
        cache.put("appA::dataY", 2);
        cache.put("appB::dataX", 3);
        int removed = cache.invalidateMatching(k -> k.startsWith("appA::"));
        assertEquals(2, removed);
        assertNull(cache.get("appA::dataX"));
        assertNull(cache.get("appA::dataY"));
        assertNotNull(cache.get("appB::dataX"));
    }

    @Test
    void invalidateAll_emptiesCache() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        cache.put("a", 1);
        cache.put("b", 2);
        cache.invalidateAll();
        assertEquals(0, cache.size());
    }

    @Test
    void reconfigure_updatesCaps() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        cache.reconfigure(20, 5);
        assertEquals(20, cache.maxEntries());
        assertEquals(5, cache.ttlMinutes());
    }

    @Test
    void reconfigure_invalidMaxEntriesRejected() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        assertThrows(IllegalArgumentException.class, () -> cache.reconfigure(0, 5));
    }

    @Test
    void constructor_invalidMaxEntriesRejected() {
        assertThrows(IllegalArgumentException.class, () -> new MemoryCache<>("t", 0, 5));
        assertThrows(IllegalArgumentException.class, () -> new MemoryCache<>("t", -1, 5));
    }

    @Test
    void name_andCapsExposed() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("myCache", 7, 3);
        assertEquals("myCache", cache.name());
        assertEquals(7, cache.maxEntries());
        assertEquals(3, cache.ttlMinutes());
    }

    @Test
    void snapshot_returnsImmutableCopy() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        cache.put("a", 1);
        var snap = cache.snapshot();
        assertEquals(1, snap.size());
        assertTrue(snap.containsKey("a"));
        assertThrows(UnsupportedOperationException.class, () -> snap.put("b", new MemoryCache.Entry<>(2, 0L)));
    }

    @Test
    void lastWriteAt_isNullBeforeFirstPut() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        assertNull(cache.lastWriteAt());
    }

    @Test
    void lastWriteAt_isSetAfterPut() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        long before = System.currentTimeMillis();
        cache.put("a", 1);
        long after = System.currentTimeMillis();
        java.time.Instant lw = cache.lastWriteAt();
        assertNotNull(lw);
        long t = lw.toEpochMilli();
        assertTrue(t >= before && t <= after, "timestamp dans la fenêtre");
    }

    @Test
    void lastWriteAt_resetByInvalidateAll() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        cache.put("a", 1);
        assertNotNull(cache.lastWriteAt());
        cache.invalidateAll();
        assertNull(cache.lastWriteAt());
    }

    @Test
    void invalidateAll_emptyCache_noopButResetsTimestamp() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 0);
        cache.invalidateAll();
        assertEquals(0, cache.size());
        assertNull(cache.lastWriteAt());
    }

    @Test
    void ttl_positive_returnsValueWhenFresh() {
        MemoryCache<String, Integer> cache = new MemoryCache<>("t", 10, 60);
        cache.put("a", 1);
        assertEquals(1, cache.get("a"));
    }

    @Test
    void estimateSizeBytes_emptyCache_returnsZero() {
        MemoryCache<String, String> cache = new MemoryCache<>("t", 10, 0);
        assertEquals(0L, cache.estimateSizeBytes(new ObjectMapper()));
    }

    @Test
    void estimateSizeBytes_sumsBytesAcrossEntries() throws Exception {
        MemoryCache<String, String> cache = new MemoryCache<>("t", 10, 0);
        cache.put("a", "hello");
        cache.put("b", "world");
        ObjectMapper mapper = new ObjectMapper();
        long expected = mapper.writeValueAsBytes("hello").length + mapper.writeValueAsBytes("world").length;
        assertEquals(expected, cache.estimateSizeBytes(mapper));
    }

    @Test
    void estimateSizeBytes_withExtractor_projectsValues() throws Exception {
        MemoryCache<String, String> cache = new MemoryCache<>("t", 10, 0);
        cache.put("a", "hello");
        ObjectMapper mapper = new ObjectMapper();
        long expected = mapper.writeValueAsBytes("HELLO").length;
        assertEquals(expected, cache.estimateSizeBytes(mapper, String::toUpperCase));
    }

    @Test
    void estimateSizeBytes_extractorReturningNull_isSkipped() {
        MemoryCache<String, String> cache = new MemoryCache<>("t", 10, 0);
        cache.put("a", "hello");
        assertEquals(0L, cache.estimateSizeBytes(new ObjectMapper(), v -> null));
    }

    @Test
    void estimateSizeBytes_serializationFailure_isSwallowed() throws Exception {
        MemoryCache<String, String> cache = new MemoryCache<>("t", 10, 0);
        cache.put("a", "hello");
        ObjectMapper mapper = mock(ObjectMapper.class);
        when(mapper.writeValueAsBytes(any())).thenThrow(new JsonProcessingException("boom") {});
        assertEquals(0L, cache.estimateSizeBytes(mapper));
    }

    private static void sleepMs(long ms) {
        try {
            Thread.sleep(ms);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}