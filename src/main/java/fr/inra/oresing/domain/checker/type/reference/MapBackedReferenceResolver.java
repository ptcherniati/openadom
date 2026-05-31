package fr.inra.oresing.domain.checker.type.reference;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataValue;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * {@link ReferenceResolver} historique : <b>toutes</b> les références du
 * référentiel sont chargées en mémoire ( map immuable de base ), avec un overlay
 * incrémental pour le mode récursif ordonné.
 *
 * <p>Encapsule l'état autrefois porté directement par {@code ReferenceType} :
 * map de base, overlay {@link #incrementalReferenceValues}, index O(1)
 * {@link #naturalKeyIndexRef} et ensemble des caractères spéciaux. Une seule
 * instance est <b>partagée</b> entre un {@code ReferenceType} et ses copies par
 * worker, de sorte que l'index n'est jamais reconstruit à la copie et que les
 * lectures concurrentes voient le même état.
 *
 * <h2>Concurrence ( identique au comportement antérieur )</h2>
 * <ul>
 *   <li>lectures ( {@link #findKey}, {@link #resolveUuids} ) : sans verrou,
 *       sur structures thread-safe ;</li>
 *   <li>{@link #register} : mono-thread ( mode récursif ordonné, 1 worker ) ;</li>
 *   <li>{@link #replaceBase} : hors hot path, réassigne atomiquement base +
 *       index + caractères spéciaux.</li>
 * </ul>
 */
public final class MapBackedReferenceResolver implements ReferenceResolver {

    private static final Pattern SINGLE_UPPERCASE = Pattern.compile("[A-Z]");

    /** Valeurs de base ( chargées une fois ). Réassignée par {@link #replaceBase}. */
    private volatile ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> base;

    /**
     * Overlay des références découvertes pendant l'import récursif ordonné.
     * {@link ConcurrentHashMap} pour la lecture cross-worker ; alimenté par
     * {@link #register} ( mono-thread ).
     */
    private final Map<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> incrementalReferenceValues =
            new ConcurrentHashMap<>();

    /** Index O(1) naturalKey → clé d'identité. Réassigné atomiquement à {@link #replaceBase}. */
    private final AtomicReference<Map<Ltree, DataValue.LineIdentityColumnName>> naturalKeyIndexRef =
            new AtomicReference<>(new ConcurrentHashMap<>());

    /** Codes de caractères spéciaux présents dans les clés naturelles connues. */
    private volatile Set<String> knownSpecialCharacters = ConcurrentHashMap.newKeySet();

    public MapBackedReferenceResolver(
            ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> base) {
        replaceBase(base);
    }

    @Override
    public DataValue.LineIdentityColumnName findKey(Ltree naturalKey) {
        return naturalKeyIndexRef.get().get(naturalKey);
    }

    @Override
    public ImmutableSet<UUID> resolveUuids(DataValue.LineIdentityColumnName key) {
        ImmutableSet<UUID> baseUuids = base.get(key);
        return baseUuids != null ? baseUuids : incrementalReferenceValues.get(key);
    }

    @Override
    public void register(DataValue.LineIdentityColumnName key, ImmutableSet<UUID> uuids) {
        if (base.containsKey(key) || incrementalReferenceValues.containsKey(key)) {
            return;
        }
        incrementalReferenceValues.put(key, uuids);
        naturalKeyIndexRef.get().put(key.naturalKey(), key);
        addKnownSpecialCharacters(key.naturalKey());
    }

    @Override
    public void replaceBase(ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues) {
        this.base = referenceValues;
        rebuildNaturalKeyIndex(referenceValues);
        rebuildKnownSpecialCharacters(referenceValues);
    }

    @Override
    public ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> baseValues() {
        return base;
    }

    @Override
    public Set<String> knownSpecialCharacters() {
        return knownSpecialCharacters;
    }

    // ----- internals ( repris à l'identique de ReferenceType ) -----

    private void rebuildNaturalKeyIndex(
            ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues) {
        // ConcurrentHashMap : register() peut y insérer pendant que findKey() lit.
        Map<Ltree, DataValue.LineIdentityColumnName> index =
                new ConcurrentHashMap<>(Math.max(16, referenceValues.size() * 2));
        for (DataValue.LineIdentityColumnName key : referenceValues.keySet()) {
            index.put(key.naturalKey(), key);
        }
        this.naturalKeyIndexRef.set(index);
    }

    private void rebuildKnownSpecialCharacters(
            ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues) {
        Set<String> rebuilt = ConcurrentHashMap.newKeySet();
        referenceValues.keySet().stream()
                .map(DataValue.LineIdentityColumnName::naturalKey)
                .map(Ltree::getSql)
                .filter(naturalKey -> SINGLE_UPPERCASE.matcher(naturalKey).matches())
                .map(MapBackedReferenceResolver::specialCharactersOf)
                .forEach(rebuilt::addAll);
        this.knownSpecialCharacters = rebuilt;
    }

    private void addKnownSpecialCharacters(Ltree naturalKey) {
        String nk = naturalKey.getSql();
        if (SINGLE_UPPERCASE.matcher(nk).matches()) {
            knownSpecialCharacters.addAll(specialCharactersOf(nk));
        }
    }

    private static Set<String> specialCharactersOf(String naturalKey) {
        return Ltree.KNOWN_SYMBOL_CODES.stream()
                .filter(naturalKey::contains)
                .collect(Collectors.toCollection(HashSet::new));
    }
}
