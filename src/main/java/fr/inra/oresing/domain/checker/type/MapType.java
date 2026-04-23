package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.persistence.SqlPrimitiveType;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public non-sealed class MapType<K, V> extends AbstractMapType<K, V> implements FieldType<Map<K, V>> {
    final Supplier<MapType<K, V>> clone;

    public MapType(final Map<K, V> map) {
        super(map);
        clone = () -> new MapType<>(map);
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.JSONB;
    }

    @Override
    public CheckerValidationCheckResult check(final String value, final LineChecker lineChecker) {
        return null;
    }

    @Override
    public FieldType<?> toJsonForDatabase() {
        return this;
    }

    @Override
    public FieldType<Map<K, V>> copy() {
        Map<K, V> current = getValue();
        return new MapType<>(current != null ? new HashMap<>(current) : new HashMap<>());
    }

    @Override
    public void serialize(final JsonGenerator gen) {
        throw new IllegalArgumentException();
    }

}