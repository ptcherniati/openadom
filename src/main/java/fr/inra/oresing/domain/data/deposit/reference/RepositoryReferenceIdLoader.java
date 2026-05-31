package fr.inra.oresing.domain.data.deposit.reference;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.repository.data.DataRepository;

import java.util.Set;
import java.util.UUID;

/**
 * Adaptateur ( pattern Adapter ) exposant un {@link DataRepository} ( domaine )
 * comme {@link ReferenceIdLoader }, le port étroit dont dépendent les
 * {@link ReferenceLoadingStrategy}. Garde {@code DataRepository} focalisé ( SRP )
 * et découple les stratégies de la persistance ( DIP ).
 */
public final class RepositoryReferenceIdLoader implements ReferenceIdLoader {

    private final DataRepository repository;

    public RepositoryReferenceIdLoader(DataRepository repository) {
        this.repository = repository;
    }

    @Override
    public ImmutableMap<DataValue.LineIdentityColumnName, UUID> loadAll(String refType) {
        return repository.getDataIdPerKeys(refType);
    }

    @Override
    public ImmutableMap<DataValue.LineIdentityColumnName, UUID> loadByNaturalKeys(String refType, Set<String> naturalKeys) {
        return repository.getDataIdPerKeysByNaturalKeys(refType, naturalKeys);
    }
}
