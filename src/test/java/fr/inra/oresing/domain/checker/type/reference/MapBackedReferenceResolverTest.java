package fr.inra.oresing.domain.checker.type.reference;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataValue;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrat de {@link MapBackedReferenceResolver}, testé en isolation ( sans DB
 * ni Spring ) : c'est l'intérêt de l'abstraction {@link ReferenceResolver}.
 */
class MapBackedReferenceResolverTest {

    private static DataValue.LineIdentityColumnName key(String naturalKey) {
        Ltree nk = Ltree.fromSql(naturalKey);
        return new DataValue.LineIdentityColumnName(nk, nk, null);
    }

    @Test
    void resolves_a_known_base_key() {
        DataValue.LineIdentityColumnName k = key("alpha");
        UUID id = UUID.randomUUID();
        MapBackedReferenceResolver resolver =
                new MapBackedReferenceResolver(ImmutableMap.of(k, ImmutableSet.of(id)));

        assertEquals(k, resolver.findKey(Ltree.fromSql("alpha")));
        assertEquals(ImmutableSet.of(id), resolver.resolveUuids(k));
    }

    @Test
    void returns_null_for_unknown_key() {
        MapBackedReferenceResolver resolver =
                new MapBackedReferenceResolver(ImmutableMap.of());
        assertNull(resolver.findKey(Ltree.fromSql("ghost")));
        assertNull(resolver.resolveUuids(key("ghost")));
    }

    @Test
    void register_adds_an_incremental_key_resolvable_afterwards() {
        MapBackedReferenceResolver resolver =
                new MapBackedReferenceResolver(ImmutableMap.of());
        DataValue.LineIdentityColumnName k = key("beta");
        UUID id = UUID.randomUUID();

        resolver.register(k, ImmutableSet.of(id));

        assertEquals(k, resolver.findKey(Ltree.fromSql("beta")));
        assertEquals(ImmutableSet.of(id), resolver.resolveUuids(k));
        // overlay : pas dans la base
        assertTrue(resolver.baseValues().isEmpty());
    }

    @Test
    void register_is_noop_when_key_already_present() {
        DataValue.LineIdentityColumnName k = key("gamma");
        UUID base = UUID.randomUUID();
        MapBackedReferenceResolver resolver =
                new MapBackedReferenceResolver(ImmutableMap.of(k, ImmutableSet.of(base)));

        resolver.register(k, ImmutableSet.of(UUID.randomUUID())); // doit être ignoré

        assertEquals(ImmutableSet.of(base), resolver.resolveUuids(k));
    }

    @Test
    void replaceBase_swaps_base_and_rebuilds_index_but_keeps_overlay() {
        MapBackedReferenceResolver resolver =
                new MapBackedReferenceResolver(ImmutableMap.of(key("old"), ImmutableSet.of(UUID.randomUUID())));
        DataValue.LineIdentityColumnName incremental = key("inc");
        UUID incId = UUID.randomUUID();
        resolver.register(incremental, ImmutableSet.of(incId));

        DataValue.LineIdentityColumnName fresh = key("fresh");
        UUID freshId = UUID.randomUUID();
        resolver.replaceBase(ImmutableMap.of(fresh, ImmutableSet.of(freshId)));

        // nouvelle base visible, ancienne base partie
        assertEquals(fresh, resolver.findKey(Ltree.fromSql("fresh")));
        assertNull(resolver.findKey(Ltree.fromSql("old")));
        // overlay incrémental conservé ( sémantique historique de setReferenceValues )
        assertEquals(ImmutableSet.of(incId), resolver.resolveUuids(incremental));
    }

    @Test
    void baseValues_returns_the_provided_base() {
        ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> base =
                ImmutableMap.of(key("delta"), ImmutableSet.of(UUID.randomUUID()));
        MapBackedReferenceResolver resolver = new MapBackedReferenceResolver(base);
        assertSame(base, resolver.baseValues());
    }
}
