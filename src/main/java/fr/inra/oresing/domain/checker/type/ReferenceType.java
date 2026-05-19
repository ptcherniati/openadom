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
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.ReferenceValidationCheckResult;

import lombok.Getter;
import org.apache.commons.collections4.MapUtils;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public non-sealed class ReferenceType implements FieldType<Ltree> {

    final Supplier<ReferenceType> clone;
    @Getter
    private final String refType;
    public Set<UUID> uuid;
    protected CheckerTarget target;
    protected LineChecker.Transformer transformer;
    @Getter
    ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues;
    Ltree value;
    DataValue.LineIdentityColumnName lineIdentityColumnName;
    private Set<String> knownSpecialCharacters = new HashSet<>();

    // ─── R-P2-1 : index O(1) naturalKey → LineIdentityColumnName ──────────────
    // Construit une seule fois dans le constructeur original.
    // TRANSFORM iter2 #1 : volatile + partagé via copy() pour eviter un rebuild
    // O(N) HashMap (jusqu'a 50k entrees) a chaque copy() per row sur le hot path
    // toJsonForFrontend. Profile async-profiler: 357 samples (18% CPU) sur
    // ReferenceType.<init> -> buildNaturalKeyIndex / HashMap.put.
    // volatile car reassigne lors de setReferenceValues, lu depuis workers
    // Cascade en parallele.
    private volatile Map<Ltree, DataValue.LineIdentityColumnName> naturalKeyIndex = new HashMap<>();

    // ─── R-P2-2 : cache lazy partagé entre l'original et toutes ses copies ────
    // ConcurrentHashMap → thread-safe pour les workers Cascade parallèles.
    // seenOnce : valeurs vues exactement une fois (pas encore en cache).
    // precomputedResults : valeurs vues ≥ 2 fois → résultat mis en cache.
    // Les deux maps sont partagées entre l'original et toutes ses copies
    // (passées par référence dans le constructeur de copie).
    private final Set<Ltree> seenOnce;
    private final Map<Ltree, DataValue.LineIdentityColumnName> precomputedResults;
    /** Plafond du cache. Configurable via cascade.import.reference-cache-max-entries. */
    private volatile int maxCacheEntries = 5_000;

    /** Constructeur principal (instance originale, crée ses propres caches). */
    public ReferenceType(final CheckerTarget target, final String refType,
                         final ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues,
                         final LineChecker.Transformer transformer,
                         DataValue.LineIdentityColumnName lineIdentityColumnName) {
        super();
        this.target = target;
        this.refType = refType;
        this.referenceValues = referenceValues;
        this.transformer = transformer;
        this.lineIdentityColumnName = lineIdentityColumnName;
        this.seenOnce = ConcurrentHashMap.newKeySet();
        this.precomputedResults = new ConcurrentHashMap<>();
        buildNaturalKeyIndex(referenceValues);
        // TRANSFORM iter2 #1 : partager l'index avec les copies ; pas de rebuild .
        clone = () -> new ReferenceType(target, refType, referenceValues, transformer,
                this.lineIdentityColumnName, this.seenOnce, this.precomputedResults,
                this.naturalKeyIndex);
    }

    /**
     * Constructeur de copie partagée (utilisé par copy() et clone).
     * Les caches {@code seenOnce}, {@code precomputedResults} et l'index
     * {@code naturalKeyIndex} sont PARTAGÉS avec l'instance parente → les
     * résultats calculés par un worker bénéficient à tous les autres workers
     * sans recalcul, et l'index O(N) n'est jamais reconstruit lors d'un copy().
     */
    ReferenceType(final CheckerTarget target, final String refType,
                  final ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues,
                  final LineChecker.Transformer transformer,
                  DataValue.LineIdentityColumnName lineIdentityColumnName,
                  Set<Ltree> sharedSeenOnce,
                  Map<Ltree, DataValue.LineIdentityColumnName> sharedPrecomputedResults,
                  Map<Ltree, DataValue.LineIdentityColumnName> sharedNaturalKeyIndex) {
        super();
        this.target = target;
        this.refType = refType;
        this.referenceValues = referenceValues;
        this.transformer = transformer;
        this.lineIdentityColumnName = lineIdentityColumnName;
        this.seenOnce = sharedSeenOnce;
        this.precomputedResults = sharedPrecomputedResults;
        // TRANSFORM iter2 #1 : pas de rebuild ; on partage l'index immuable .
        this.naturalKeyIndex = sharedNaturalKeyIndex;
        clone = () -> new ReferenceType(target, refType, referenceValues, transformer,
                this.lineIdentityColumnName, this.seenOnce, this.precomputedResults,
                this.naturalKeyIndex);
    }

    /** Configure le plafond du cache. Appelé depuis DataImporter après importProperties. */
    public void setMaxCacheEntries(int max) {
        this.maxCacheEntries = max;
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
        this.referenceValues = referenceValues;
        buildNaturalKeyIndex(referenceValues);
        buildKnownSpecialCharacters(referenceValues);
    }

    /**
     * R-P2-1 — Construit l'index O(1) naturalKey → LineIdentityColumnName.
     * Remplace le scan O(N) {@code referenceValues.keySet().stream().filter(...).findFirst()}
     * dans {@link #check}. Rebuild complet à chaque appel de setReferenceValues.
     */
    private void buildNaturalKeyIndex(ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues) {
        Map<Ltree, DataValue.LineIdentityColumnName> index = new HashMap<>(referenceValues.size() * 2);
        for (DataValue.LineIdentityColumnName key : referenceValues.keySet()) {
            index.put(key.naturalKey(), key);
        }
        this.naturalKeyIndex = index;
    }

    private void buildKnownSpecialCharacters(ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues) {
        this.knownSpecialCharacters = referenceValues.keySet().stream()
                .map(DataValue.LineIdentityColumnName::naturalKey)
                .map(Ltree::getSql)
                .filter(naturalKey -> naturalKey.matches("[A-Z]"))
                .map(this::getSpecialCharacters)
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
    }

    private Set<String> getSpecialCharacters(String naturalKey) {
        Predicate<String> containsSpecialCharacter = naturalKey::contains;
        return Ltree.KNOWN_SYMBOL_CODES
                .stream()
                .filter(containsSpecialCharacter)
                .collect(Collectors.toSet());
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
        final String localRawValue = Ltree.escapeToLabel(rawValue, knownSpecialCharacters);
        final CheckerTarget target = lineChecker.target();
        value = Ltree.fromSql(localRawValue);

        // ── R-P2-2 : vérifier le cache des résultats pré-calculés (O(1)) ────────
        DataValue.LineIdentityColumnName cachedKey = precomputedResults.get(value);
        if (cachedKey != null) {
            value = cachedKey.naturalKey();
            uuid = referenceValues.get(cachedKey);
            lineIdentityColumnName = cachedKey;
            return ReferenceValidationCheckResult.success(target, localRawValue,
                    Set.of(value), uuid, this);
        }

        // ── R-P2-1 : lookup O(1) via l'index naturalKey ─────────────────────────
        DataValue.LineIdentityColumnName foundKey = naturalKeyIndex.get(value);
        if (foundKey != null) {
            value = foundKey.naturalKey();
            uuid = referenceValues.get(foundKey);
            lineIdentityColumnName = foundKey;

            // ── R-P2-2 : mettre en cache après la 2e occurrence ─────────────────
            if (seenOnce.contains(value)) {
                // Valeur vue ≥ 2 fois → promouvoir dans precomputedResults
                if (precomputedResults.size() < maxCacheEntries) {
                    precomputedResults.put(value, foundKey);
                }
            } else {
                seenOnce.add(value);
            }

            return ReferenceValidationCheckResult.success(target, localRawValue,
                    Set.of(value), referenceValues.get(foundKey), this);
        }

        // Valeur non trouvée → erreur
        return ReferenceValidationCheckResult.error(target, localRawValue,
                target.getInternationalizedKey("invalidReference"),
                ImmutableMap.of(
                        "component", ((DataColumn) target).column(),
                        "referenceValues", Optional.ofNullable(referenceValues)
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
        // R-P2-2 + TRANSFORM iter2 #1 : copie avec partage des caches seenOnce
        // + precomputedResults + naturalKeyIndex → les workers Cascade
        // parallèles partagent les caches ET l'index O(N) n'est jamais
        // reconstruit (auparavant 357 samples / 18% CPU sur le hot path
        // toJsonForFrontend → ReferenceType.<init>).
        final ReferenceType referenceType = new ReferenceType(
                this.target,
                this.refType,
                this.referenceValues,
                this.transformer,
                this.lineIdentityColumnName,
                this.seenOnce,          // partagé
                this.precomputedResults, // partagé
                this.naturalKeyIndex     // partagé : pas de rebuild O(N)
        );
        referenceType.value = value;
        referenceType.maxCacheEntries = this.maxCacheEntries;
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