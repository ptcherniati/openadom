package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.ReferenceValidationCheckResult;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.function.Supplier;

import java.io.IOException;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public non-sealed class ReferenceType extends AbstractType<Ltree> {

    @JsonIgnore
    public Set<UUID> getUuid() {
        return uuid;
    }

    private Set<String> knownSpecialCharacters = new HashSet<>();
    @Getter
    private final String refType;
    public final CheckerTarget target(){
        return target;
    };

    public void setReferenceValues(ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues) {
        this.referenceValues = referenceValues;
        buildKnownSpecialCharacters(referenceValues);
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

    @Getter
    ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues;
    Ltree value;
    DataValue.LineIdentityColumnName lineIdentityColumnName;

    final Supplier<ReferenceType> clone;

    public ReferenceType(final CheckerTarget target, final String refType, final ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues, final LineChecker.Transformer transformer, DataValue.LineIdentityColumnName lineIdentityColumnName) {
        super();
        this.target = target;
        this.refType = refType;
        this.referenceValues = referenceValues;
        this.transformer = transformer;
        this.lineIdentityColumnName = lineIdentityColumnName;
        clone = () -> new ReferenceType(target, refType, referenceValues, transformer, this.lineIdentityColumnName);
    }

    @Override
    public Ltree getValue() {
        return value;
    }

    public Set<UUID> uuid;

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.LTREE;
    }

    @Override
    public CheckerValidationCheckResult check(final String rawValue, final LineChecker lineChecker) {
        final String localRawValue = Ltree.escapeToLabel(rawValue, knownSpecialCharacters);
        final CheckerTarget target = lineChecker.target();

        value = Ltree.fromSql(localRawValue);
        Predicate<DataValue.LineIdentityColumnName> matchesValue = v -> v.naturalKey().equals(value);
        Optional<DataValue.LineIdentityColumnName> optionalKey = referenceValues.keySet().stream()
                .filter(matchesValue)
                .findFirst();
        if (optionalKey.isPresent()) {
            value = optionalKey.get().naturalKey();
            uuid = referenceValues.get(optionalKey.get());
            lineIdentityColumnName = optionalKey.get();
            return ReferenceValidationCheckResult.success(target,
                    localRawValue,
                    Set.of(value),
                    referenceValues.get(optionalKey.get()),
                    this);
        }
        return ReferenceValidationCheckResult.error(target, localRawValue, target.getInternationalizedKey("invalidReference"), ImmutableMap.of(
                        "target", target.toHumanReadableString(),
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
    public FieldType toJsonForDatabase() {
        return this;
    }

    @Override
    public FieldType copy() {
        final ReferenceType referenceType = clone.get();
        referenceType.value = value;
        referenceType.uuid = uuid;
        referenceType.lineIdentityColumnName = lineIdentityColumnName;
        referenceType.setReferenceValues(ImmutableMap.copyOf(getReferenceValues()));
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
                                     final Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo) {
        return Optional.ofNullable(value)
                .map(ltree -> {
                    refsLinkedTo
                            .computeIfAbsent(
                                    refType, k -> new HashMap<>())
                            .put(referenceColumn.column(),
                                    new RefsLinkedToValue(
                                            getUuid(),
                                            lineIdentityColumnName.hierarchicalKey()
                                    ));
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
