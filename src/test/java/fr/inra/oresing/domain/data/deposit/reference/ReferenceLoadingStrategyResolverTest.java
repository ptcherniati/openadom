package fr.inra.oresing.domain.data.deposit.reference;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataValue;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Vérifie que {@link ReferenceLoadingStrategyResolver} reproduit l'arbre de
 * décision historique ( iso ), et que chaque stratégie emprunte le bon chemin
 * de chargement. Fake {@link ReferenceIdLoader }, sans base ni Spring.
 */
class ReferenceLoadingStrategyResolverTest {

    private static final class RecordingLoader implements ReferenceIdLoader {
        boolean loadAllCalled = false;
        boolean loadByNaturalKeysCalled = false;
        final ImmutableMap<DataValue.LineIdentityColumnName, UUID> data;

        RecordingLoader(ImmutableMap<DataValue.LineIdentityColumnName, UUID> data) { this.data = data; }

        @Override public ImmutableMap<DataValue.LineIdentityColumnName, UUID> loadAll(String refType) {
            loadAllCalled = true; return data;
        }
        @Override public ImmutableMap<DataValue.LineIdentityColumnName, UUID> loadByNaturalKeys(String refType, Set<String> keys) {
            loadByNaturalKeysCalled = true; return data;
        }
    }

    private static DataValue.LineIdentityColumnName key(String nk) {
        Ltree k = Ltree.fromSql(nk);
        return new DataValue.LineIdentityColumnName(k, k, null);
    }

    private final ReferenceLoadingStrategyResolver resolver = new ReferenceLoadingStrategyResolver();

    @Test
    void non_recursive_selects_no_preload_and_loads_nothing() {
        ReferenceLoadingStrategy strategy = resolver.resolve(ReferenceLoadContext.nonRecursive());
        assertInstanceOf(NoPreloadReferenceLoadingStrategy.class, strategy);

        RecordingLoader loader = new RecordingLoader(ImmutableMap.of(key("a"), UUID.randomUUID()));
        assertTrue(strategy.loadIdsPerKey(loader, "t_ref", ReferenceLoadContext.nonRecursive()).isEmpty());
        assertFalse(loader.loadAllCalled);
        assertFalse(loader.loadByNaturalKeysCalled);
    }

    @Test
    void recursive_with_hint_selects_bounded_and_loads_by_natural_keys() {
        ReferenceLoadContext ctx = ReferenceLoadContext.recursive(Set.of("a", "b"));
        ReferenceLoadingStrategy strategy = resolver.resolve(ctx);
        assertInstanceOf(PrescanBoundedReferenceLoadingStrategy.class, strategy);

        RecordingLoader loader = new RecordingLoader(ImmutableMap.of(key("a"), UUID.randomUUID()));
        strategy.loadIdsPerKey(loader, "t_ref", ctx);
        assertTrue(loader.loadByNaturalKeysCalled);
        assertFalse(loader.loadAllCalled);
    }

    @Test
    void recursive_without_hint_selects_eager_and_full_loads() {
        ReferenceLoadContext ctx = ReferenceLoadContext.recursive(null);
        ReferenceLoadingStrategy strategy = resolver.resolve(ctx);
        assertInstanceOf(EagerReferenceLoadingStrategy.class, strategy);

        RecordingLoader loader = new RecordingLoader(ImmutableMap.of(key("a"), UUID.randomUUID()));
        strategy.loadIdsPerKey(loader, "t_ref", ctx);
        assertTrue(loader.loadAllCalled);
        assertFalse(loader.loadByNaturalKeysCalled);
    }

    @Test
    void bounded_falls_back_to_full_load_when_no_hint() {
        RecordingLoader loader = new RecordingLoader(ImmutableMap.of(key("a"), UUID.randomUUID()));
        new PrescanBoundedReferenceLoadingStrategy()
                .loadIdsPerKey(loader, "t_ref", ReferenceLoadContext.recursive(null));
        assertTrue(loader.loadAllCalled);
        assertFalse(loader.loadByNaturalKeysCalled);
    }
}
