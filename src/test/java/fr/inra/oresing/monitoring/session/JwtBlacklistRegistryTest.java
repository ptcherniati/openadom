package fr.inra.oresing.monitoring.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link JwtBlacklistRegistry} — sans Spring.
 *
 * <p>Couvre : add(), contains(), list(), remove(), purgeExpired(),
 * clear(), size(), hash(), eviction LRU, idempotence.
 */
@Tag("domain.model")
@DisplayName("JwtBlacklistRegistry — tests unitaires")
class JwtBlacklistRegistryTest {

    private JwtBlacklistRegistry registry;

    private static final UUID USER_A = UUID.randomUUID();
    private static final UUID SESSION_A = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        registry = new JwtBlacklistRegistry();
    }

    private Instant future() {
        return Instant.now().plusSeconds(3600); // 1 h dans le futur
    }

    private Instant past() {
        return Instant.now().minusSeconds(1); // déjà expiré
    }

    // ─── add() + contains() ──────────────────────────────────────────────────

    @Test
    @DisplayName("add() puis contains() retourne true")
    void addAndContains() {
        registry.add("hash1", USER_A, "alice", SESSION_A, Instant.now(), future());
        assertTrue(registry.contains("hash1"));
    }

    @Test
    @DisplayName("contains() retourne false pour hash inconnu")
    void containsUnknown() {
        assertFalse(registry.contains("unknown-hash"));
    }

    @Test
    @DisplayName("contains(null) retourne false")
    void containsNull() {
        assertFalse(registry.contains(null));
    }

    @Test
    @DisplayName("add() avec hash null ou blank est ignoré")
    void addNullOrBlankIgnored() {
        registry.add(null, USER_A, "alice", SESSION_A, Instant.now(), future());
        registry.add("  ", USER_A, "alice", SESSION_A, Instant.now(), future());
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("add() est idempotent : un double-add ne duplique pas")
    void addIdempotent() {
        registry.add("hash1", USER_A, "alice", SESSION_A, Instant.now(), future());
        registry.add("hash1", USER_A, "alice", SESSION_A, Instant.now(), future());
        assertEquals(1, registry.size());
    }

    // ─── expiration automatique ───────────────────────────────────────────────

    @Test
    @DisplayName("contains() retourne false pour une entrée expirée")
    void expiredEntryNotContained() {
        registry.add("hash-expired", USER_A, "alice", SESSION_A, Instant.now(), past());
        assertFalse(registry.contains("hash-expired"));
    }

    @Test
    @DisplayName("purgeExpired() supprime les entrées expirées")
    void purgeExpiredRemovesExpired() {
        registry.add("hash-live", USER_A, "alice", SESSION_A, Instant.now(), future());
        registry.add("hash-dead", USER_A, "alice", SESSION_A, Instant.now(), past());

        int removed = registry.purgeExpired();

        assertEquals(1, removed);
        assertEquals(1, registry.size());
        assertTrue(registry.contains("hash-live"));
    }

    @Test
    @DisplayName("entrée sans expiration (null expiresAt) n'est jamais purgée")
    void nullExpiresAtNeverPurged() {
        registry.add("hash-permanent", USER_A, "alice", SESSION_A, Instant.now(), null);
        registry.purgeExpired();
        assertTrue(registry.contains("hash-permanent"));
    }

    // ─── list() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("list() retourne toutes les entrées non-expirées, triées DESC")
    void listReturnsSortedDesc() {
        Instant t1 = Instant.now().minusMillis(100);
        Instant t2 = Instant.now();
        registry.add("hash-old", USER_A, "alice", SESSION_A, t1, future());
        registry.add("hash-new", USER_A, "alice", SESSION_A, t2, future());

        List<JwtBlacklistRegistry.Entry> list = registry.list();
        assertEquals(2, list.size());
        // "hash-new" (blacklistedAt=t2) doit être en premier (DESC)
        assertTrue(list.get(0).blacklistedAt().compareTo(list.get(1).blacklistedAt()) >= 0);
    }

    @Test
    @DisplayName("list() exclut les entrées expirées")
    void listExcludesExpired() {
        registry.add("hash-live", USER_A, "alice", SESSION_A, Instant.now(), future());
        registry.add("hash-dead", USER_A, "alice", SESSION_A, Instant.now(), past());

        List<JwtBlacklistRegistry.Entry> list = registry.list();
        assertEquals(1, list.size());
        assertEquals("hash-live", list.get(0).tokenHash());
    }

    // ─── remove() ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("remove() supprime une entrée existante et retourne true")
    void removeExisting() {
        registry.add("hash1", USER_A, "alice", SESSION_A, Instant.now(), future());
        boolean removed = registry.remove("hash1");
        assertTrue(removed);
        assertFalse(registry.contains("hash1"));
    }

    @Test
    @DisplayName("remove() sur hash inexistant retourne false")
    void removeNonExisting() {
        assertFalse(registry.remove("not-there"));
    }

    // ─── clear() ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("clear() vide complètement la registry")
    void clearAll() {
        registry.add("h1", USER_A, "alice", SESSION_A, Instant.now(), future());
        registry.add("h2", USER_A, "alice", SESSION_A, Instant.now(), future());
        int cleared = registry.clear();
        assertEquals(2, cleared);
        assertEquals(0, registry.size());
    }

    @Test
    @DisplayName("clear() sur registry vide retourne 0")
    void clearEmpty() {
        assertEquals(0, registry.clear());
    }

    // ─── LRU eviction ────────────────────────────────────────────────────────

    @Test
    @DisplayName("eviction LRU quand MAX_ENTRIES est atteint")
    void lruEviction() {
        // Remplir jusqu'à MAX_ENTRIES
        for (int i = 0; i < JwtBlacklistRegistry.MAX_ENTRIES; i++) {
            registry.add("hash-" + i, USER_A, "alice", SESSION_A,
                    Instant.now(), future());
        }
        assertEquals(JwtBlacklistRegistry.MAX_ENTRIES, registry.size());

        // Ajouter une entrée de plus : la première (hash-0) doit être évictée
        registry.add("hash-overflow", USER_A, "alice", SESSION_A,
                Instant.now(), future());

        // Taille reste plafonnée
        assertEquals(JwtBlacklistRegistry.MAX_ENTRIES, registry.size());
        // La nouvelle entrée est présente
        assertTrue(registry.contains("hash-overflow"));
        // La plus ancienne est évictée
        assertFalse(registry.contains("hash-0"),
                "La plus ancienne entrée devrait être évictée");
    }

    // ─── hash() ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("hash() retourne un hash SHA-256 hex 64 chars")
    void hashLength() {
        String h = JwtBlacklistRegistry.hash("my-jwt-token");
        assertNotNull(h);
        assertEquals(64, h.length(), "SHA-256 hex = 64 chars");
    }

    @Test
    @DisplayName("hash() est déterministe")
    void hashDeterministic() {
        String jwt = "header.payload.signature";
        assertEquals(JwtBlacklistRegistry.hash(jwt), JwtBlacklistRegistry.hash(jwt));
    }

    @Test
    @DisplayName("hash() de deux valeurs différentes donne des hashs différents")
    void hashDifferent() {
        assertNotEquals(JwtBlacklistRegistry.hash("jwt-a"), JwtBlacklistRegistry.hash("jwt-b"));
    }

    @Test
    @DisplayName("hash(null) retourne null")
    void hashNull() {
        assertNull(JwtBlacklistRegistry.hash(null));
    }

    // ─── Entry record ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Entry record")
    class EntryRecord {

        @Test
        @DisplayName("hashPrefix() retourne les 12 premiers chars")
        void hashPrefix() {
            JwtBlacklistRegistry.Entry e = new JwtBlacklistRegistry.Entry(
                    "abcdefghijklmnopqrstuvwxyz", USER_A, "alice",
                    SESSION_A, Instant.now(), future());
            assertEquals("abcdefghijkl", e.hashPrefix());
        }

        @Test
        @DisplayName("hashPrefix() sur hash court ne déborde pas")
        void hashPrefixShort() {
            JwtBlacklistRegistry.Entry e = new JwtBlacklistRegistry.Entry(
                    "short", USER_A, "alice", SESSION_A, Instant.now(), future());
            assertEquals("short", e.hashPrefix());
        }

        @Test
        @DisplayName("hashPrefix() sur null retourne chaîne vide")
        void hashPrefixNull() {
            JwtBlacklistRegistry.Entry e = new JwtBlacklistRegistry.Entry(
                    null, USER_A, "alice", SESSION_A, Instant.now(), future());
            assertEquals("", e.hashPrefix());
        }

        @Test
        @DisplayName("accesseurs du record fonctionnent")
        void recordAccessors() {
            Instant blacklisted = Instant.now();
            Instant expires = future();
            JwtBlacklistRegistry.Entry e = new JwtBlacklistRegistry.Entry(
                    "myhash", USER_A, "bob", SESSION_A, blacklisted, expires);
            assertEquals("myhash", e.tokenHash());
            assertEquals(USER_A, e.userId());
            assertEquals("bob", e.userLogin());
            assertEquals(SESSION_A, e.sessionId());
            assertEquals(blacklisted, e.blacklistedAt());
            assertEquals(expires, e.expiresAt());
        }
    }
}
