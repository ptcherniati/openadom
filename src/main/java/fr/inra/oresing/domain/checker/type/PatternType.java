package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.PatternValidationCheckResult;

import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public non-sealed class PatternType<K, V> extends AbstractMapType<K, V> implements FieldType<Map<K, V>> {
    final Supplier<PatternType> clone;

    public PatternType(final Map<K, V> map) {
        super(map);
        clone = () -> new PatternType(map);
    }

    @Override
    public Map<K, V> getValue() {
        return super.getValue();
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.JSONB;
    }

    @Override
    public CheckerValidationCheckResult check(final String value, final LineChecker lineChecker) {
        return null;
    }

  /*  @Override
    public ValidationCheckResult check(String value, LineChecker lineChecker) {
        throw new NotImplementedException("No check for map");
    }

    @Override
    public ValidationCheckResult check(String value, LineCheckerWarper lineCheckerWarper) {
        throw new NotImplementedException("No check for map");
    }*/

    @Override
    public FieldType<?> toJsonForDatabase() {
        return this;
    }

    @Override
    public FieldType copy() {
        final PatternType mapType = clone.get();
        return mapType;
    }

    @Override
    public void serialize(final JsonGenerator gen) {
        throw new IllegalArgumentException();
    }


    @Override
    public String toStringForComponentValue() {
        V v = getValue().get(Column.__VALUE__);
        return v == null ? "" : v.toString();
    }

    @Override
    public CheckerValidationCheckResult postTreatment(CheckerValidationCheckResult checkerValidationCheckResult) {
        return PatternValidationCheckResult.of(checkerValidationCheckResult, this);
    }

    public FieldType<?> getColumnValue() {
        return Optional.ofNullable(getValue())
                .map(map -> map.get(Column.__VALUE__))
                .map(FieldType.class::cast)
                .orElse(NullType.INSTANCE);
    }
}