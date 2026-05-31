package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.reference.MapBackedReferenceResolver;
import fr.inra.oresing.domain.checker.type.reference.ReferenceResolver;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.ReferenceValidationCheckResult;

import lombok.Getter;
import org.apache.commons.collections4.MapUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public non-sealed class ReferenceType implements FieldType<Ltree> {

    final Supplier<ReferenceType> clone;
    @Getter
    private final String refType;
    public Set<UUID> uuid;
    protected CheckerTarget target;
    protected LineChecker.Transformer transformer;
    Ltree value;
    DataValue.LineIdentityColumnName lineIdentityColumnName;

    /**
     * Résolution des références ( valeurs de base + overlay incrémental + index
     * O(1) naturalKey + caractères spéciaux ) déléguée à cette stratégie
     * ( {@link ReferenceResolver} ). PARTAGÉE entre l'instance d'origine et ses
     * copies par worker ( cf {@link #copy()} ) : les lectures concurrentes voient
     * le même état et l'index n'est jamais reconstruit à la copie.
     */
    private final ReferenceResolver resolver;

    // ─── R-P2-2 : cache lazy partagé entre l'original et toutes ses copies ────
    // ConcurrentHashMap → thread-safe pour les workers Cascade parallèles.
    // seenOnce : valeurs vues exactement une fois (pas encore en cache).
    // precomputedResults : valeurs vues ≥ 2 fois → résultat mis en cache.
    // Les deux maps sont partagées entre l'original et toutes ses copies
    // (passées par référence dans le constructeur de copie).
    private final Set<Ltree> seenOnce;
    private final Map<Ltree, DataValue.LineIdentityColumnName> precomputedResults;

    /** Plafond du cache. Configurable via cascade.import.reference-cache-max-entries. */
    private final AtomicInteger maxCacheEntriesRef = new AtomicInteger(5_000);

    /**
     * Constructeur principal ( instance originale ) : crée ses propres caches
     * mémo et un {@link MapBackedReferenceResolver} eager à partir des valeurs
     * fournies. Délègue au constructeur canonique ( DRY ).
     */
    public ReferenceType(final CheckerTarget target, final String refType,
                         final ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues,
                         final LineChecker.Transformer transformer,
                         DataValue.LineIdentityColumnName lineIdentityColumnName) {
        this(target, refType, transformer, lineIdentityColumnName,
                ConcurrentHashMap.newKeySet(),
                new ConcurrentHashMap<>(),
                new MapBackedReferenceResolver(referenceValues));
    }

    /**
     * Constructeur canonique ( utilisé par {@link #copy()} et {@code clone} ).
     * Les caches mémo {@code seenOnce} / {@code precomputedResults} ET le
     * {@link ReferenceResolver} sont PARTAGÉS avec l'instance parente → les
     * résultats calculés par un worker bénéficient à tous les autres, et l'index
     * O(N) du resolver n'est jamais reconstruit lors d'un {@code copy()}.
     */
    ReferenceType(final CheckerTarget target, final String refType,
                  final LineChecker.Transformer transformer,
                  DataValue.LineIdentityColumnName lineIdentityColumnName,
                  Set<Ltree> sharedSeenOnce,
                  Map<Ltree, DataValue.LineIdentityColumnName> sharedPrecomputedResults,
                  ReferenceResolver sharedResolver) {
        super();
        this.target = target;
        this.refType = refType;
        this.transformer = transformer;
        this.lineIdentityColumnName = lineIdentityColumnName;
        this.seenOnce = sharedSeenOnce;
        this.precomputedResults = sharedPrecomputedResults;
        this.resolver = sharedResolver;
        clone = () -> new ReferenceType(target, refType, transformer,
                this.lineIdentityColumnName, this.seenOnce, this.precomputedResults, this.resolver);
    }

    /** Configure le plafond du cache. Appelé depuis DataImporter après importProperties. */
    public void setMaxCacheEntries(int max) {
        this.maxCacheEntriesRef.set(max);
    }

    @JsonIgnore
    public Set<UUID> getUuid() {
        return uuid;
    }

    public final CheckerTarget target() {
        return target;
    }

    public void setReferenceValues(ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues) {
        // Invalider les caches : les valeurs de référence ont changé (ex. import récursif
        // → addKnownIdToReferenceValues ajoute des UUID parents en cours de traitement).
        seenOnce.clear();
        precomputedResults.clear();
        resolver.replaceBase(referenceValues);
    }

    /** Vue des valeurs de base ( hors overlay incrémental ), lue par CsvReader
     *  pour la fusion pré-chargé + incréments à la construction des checkers. */
    public ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> getReferenceValues() {
        return resolver.baseValues();
    }

    /**
     * Ajout incremental O(1) d'UNE valeur de reference ( import recursif ORDONNE :
     * une seule entree nouvelle par ligne ) , sans reconstruire toute la map /
     * l'index / les special chars ni vider les caches comme {@link #setReferenceValues} .
     * Iso-resultat : equivalent a setReferenceValues( ancien + cette entree ) pour
     * la resolution ( meme cle visible , meme UUID , meme special chars ) .
     *
     * <p><b>Mono-thread uniquement</b> : a appeler exclusivement depuis le mode
     * recursif ordonne ( 1 worker ) . L'overlay est un ConcurrentHashMap mais
     * l'index naturalKey ( HashMap ) et le set special chars ne sont pas
     * thread-safe en ecriture ; le mode parallele garde le chemin
     * {@link #setReferenceValues} ( cf WithRecursion.addKnownIdToReferenceValues ) .
     */
    public void addReferenceValue(DataValue.LineIdentityColumnName key, ImmutableSet<UUID> uuids) {
        resolver.register(key, uuids);
    }

    /** Délègue au {@link ReferenceResolver} : base d'abord, puis overlay incrémental. */
    private ImmutableSet<UUID> resolveUuids(DataValue.LineIdentityColumnName key) {
        return resolver.resolveUuids(key);
    }

    @Override
    public Ltree getValue() {
        return value;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.LTREE;
    }

    @Override
    public CheckerValidationCheckResult check(final String rawValue, final LineChecker lineChecker) {
        final String localRawValue = Ltree.escapeToLabel(rawValue, resolver.knownSpecialCharacters());
        final CheckerTarget target = lineChecker.target();
        value = Ltree.fromSql(localRawValue);

        // ── R-P2-2 : vérifier le cache des résultats pré-calculés (O(1)) ────────
        DataValue.LineIdentityColumnName cachedKey = precomputedResults.get(value);
        if (cachedKey != null) {
            value = cachedKey.naturalKey();
            uuid = resolveUuids(cachedKey);
            lineIdentityColumnName = cachedKey;
            return ReferenceValidationCheckResult.success(target, localRawValue,
                    Set.of(value), uuid, this);
        }

        // ── R-P2-1 : lookup O(1) via l'index naturalKey ─────────────────────────
        DataValue.LineIdentityColumnName foundKey = resolver.findKey(value);
        if (foundKey != null) {
            value = foundKey.naturalKey();
            uuid = resolveUuids(foundKey);
            lineIdentityColumnName = foundKey;

            // ── R-P2-2 : mettre en cache après la 2e occurrence ─────────────────
            if (seenOnce.contains(value)) {
                // Valeur vue ≥ 2 fois → promouvoir dans precomputedResults
                if (precomputedResults.size() < maxCacheEntriesRef.get()) {
                    precomputedResults.put(value, foundKey);
                }
            } else {
                seenOnce.add(value);
            }

            return ReferenceValidationCheckResult.success(target, localRawValue,
                    Set.of(value), resolveUuids(foundKey), this);
        }

        // Valeur non trouvée → erreur
        return ReferenceValidationCheckResult.error(target, localRawValue,
                target.getInternationalizedKey("invalidReference"),
                ImmutableMap.of(
                        "component", ((DataColumn) target).column(),
                        "referenceValues", Optional.ofNullable(resolver.baseValues())
                                .filter(MapUtils::isNotEmpty)
                                .map(Map::keySet)
                                .orElseGet(HashSet::new)
                                .stream()
                                .map(DataValue.LineIdentityColumnName::naturalKey)
                                .map(Ltree::getSql)
                                .collect(Collectors.toSet()),
                        "refType", refType,
                        "value", rawValue),
                this);
    }

    @Override
    public FieldType<?> toJsonForDatabase() {
        return this;
    }

    @Override
    public FieldType copy() {
        // R-P2-2 + TRANSFORM iter2 #1 : copie avec partage des caches mémo
        // ( seenOnce + precomputedResults ) ET du ReferenceResolver → les workers
        // Cascade parallèles partagent les caches, et l'index O(N) du resolver
        // n'est jamais reconstruit (auparavant 357 samples / 18% CPU sur le hot
        // path toJsonForFrontend → ReferenceType.<init>).
        final ReferenceType referenceType = new ReferenceType(
                this.target,
                this.refType,
                this.transformer,
                this.lineIdentityColumnName,
                this.seenOnce,           // partagé
                this.precomputedResults, // partagé
                this.resolver            // partagé : base + overlay + index + special chars
        );
        referenceType.value = value;
        referenceType.maxCacheEntriesRef.set(this.maxCacheEntriesRef.get());
        return referenceType;

    }

    @Override
    public void serialize(final JsonGenerator gen) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        gen.writeString(Optional.of(value).map(Ltree::getSql).orElse(""));
    }

    @Override
    public String toString() {
        return Optional.ofNullable(value).map(Ltree::toString).orElse(null);
    }

    @Override
    public void serialize(final JsonGenerator gen, final String key) throws IOException {
        gen.writeObjectField(key, value.getSql());
    }

    @Override
    public DataColumnValue transform(final LineChecker lineChecker,
                                     final DataColumnValue referenceColumnRawValue,
                                     final DataColumn referenceColumn,
                                     final Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo) {
        return Optional.ofNullable(value)
                .map(ltree -> {
                    refsLinkedTo
                            .computeIfAbsent(
                                    refType, k -> new HashMap<>())
                            .computeIfAbsent(referenceColumn.column(), k -> new HashMap<>())
                            .computeIfAbsent(lineIdentityColumnName.hierarchicalKey().getSql(), k -> new LinkedLines(getUuid()));
                    return switch (referenceColumnRawValue) {
                        case DataColumnSingleValue ignored -> new DataColumnSingleValue(this);
                        case DataColumnMultipleValue dataColumnMultipleValue -> dataColumnMultipleValue;
                        default -> null;
                    };
                })
                .orElse(referenceColumnRawValue);
    }


    @Override
    public void serialize(final ObjectNode node, final ObjectMapper mapper, final String key) {
        node.put(key, value.toString());
    }

    @Override
    public void serializeAddArray(final ArrayNode arrayNode) {
        arrayNode.add(value.toString());

    }

    @Override
    public Object toJsonForFrontend() {
        return value;
    }

    public Ltree getHierarchicalKey() {
        return Optional.ofNullable(lineIdentityColumnName)
                .map(DataValue.LineIdentityColumnName::hierarchicalKey)
                .orElse(value);
    }
}