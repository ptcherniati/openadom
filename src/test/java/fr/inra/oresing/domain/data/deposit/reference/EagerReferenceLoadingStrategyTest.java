package fr.inra.oresing.domain.data.deposit.reference;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataValue;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Contrat de {@link EagerReferenceLoadingStrategy}, testé sur un fake
 * {@link ReferenceIdLoader} ( pas de base, pas de Spring, pas de Mockito ) :
 * c'est l'intérêt du port étroit.
 */
class EagerReferenceLoadingStrategyTest {

    /** Fake loader enregistrant l'appel effectué, pour vérifier la stratégie. */
    private static final class RecordingLoader implements ReferenceIdLoader {
        boolean loadAllCalled = false;
        boolean loadByNaturalKeysCalled = false;
        final ImmutableMap<DataValue.LineIdentityColumnName, UUID> all;

        RecordingLoader(ImmutableMap<DataValue.LineIdentityColumnName, UUID> all) {
            this.all = all;
        }

        @Override
        public ImmutableMap<DataValue.LineIdentityColumnName, UUID> loadAll(String refType) {
            loadAllCalled = true;
            return all;
        }

        @Override
        public ImmutableMap<DataValue.LineIdentityColumnName, UUID> loadByNaturalKeys(String refType, Set<String> naturalKeys) {
            loadByNaturalKeysCalled = true;
            return ImmutableMap.of();
        }
    }

    private static DataValue.LineIdentityColumnName key(String nk) {
        Ltree k = Ltree.fromSql(nk);
        return new DataValue.LineIdentityColumnName(k, k, null);
    }

    @Test
    void eager_full_loads_via_loadAll() {
        ImmutableMap<DataValue.LineIdentityColumnName, UUID> data =
                ImmutableMap.of(key("alpha"), UUID.randomUUID());
        RecordingLoader loader = new RecordingLoader(data);

        ImmutableMap<DataValue.LineIdentityColumnName, UUID> result =
                new EagerReferenceLoadingStrategy()
                        .loadIdsPerKey(loader, "t_ref", ReferenceLoadContext.nonRecursive());

        assertSame(data, result);
        assertTrue(loader.loadAllCalled);
    }

    @Test
    void eager_ignores_the_natural_keys_hint() {
        RecordingLoader loader = new RecordingLoader(ImmutableMap.of());

        new EagerReferenceLoadingStrategy()
                .loadIdsPerKey(loader, "t_ref", ReferenceLoadContext.recursive(Set.of("a", "b")));

        assertTrue(loader.loadAllCalled);
        // Eager ne doit JAMAIS emprunter le chemin borné, même avec un hint présent.
        assertEquals(false, loader.loadByNaturalKeysCalled);
    }
}
