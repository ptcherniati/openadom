package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.MapType;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record DataColumnIndexedValue(
        Map<Ltree, String> values) implements DataColumnValue<MapType<String, String>, Map<String, String>> {

    @Override
    public MapType getValuesToCheck() {
        return new MapType<Ltree, String>(values);
    }

    @Override
    public DataColumnIndexedValue transform(final Function<FieldType, FieldType> transformation) {
        final Map<Ltree, String> transformedValues = null;//Maps.transformValues(values, transformation::apply);
        return new DataColumnIndexedValue(null);
    }

    @Override
    public String toValueString(final DataImporterContext referenceImporterContext, final String referencedColumn, final String locale) {
        return values.entrySet().stream()
                .map(ltreeStringEntry -> String.format("\"%s\"\"=%s\"", referenceImporterContext.getDisplayNamesByReferenceAndNaturalKey(referencedColumn, ltreeStringEntry.getKey().toString(), locale), ltreeStringEntry.getValue()))
                .collect(Collectors.joining(",", "[", "]"));
    }

    @Override
    public Map<String, String> toJsonForFrontend() {
        return toStringStringMap();
    }

    @Override
    public MapType<String, String> toJsonForDatabase() {
        final Map<String, String> map = toStringStringMap();
        return new MapType<String, String>(map);
    }

    private Map<String, String> toStringStringMap() {
        final Map<String, String> jsonForDatabase = values.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getSql(), Map.Entry::getValue));
        return jsonForDatabase;
    }
}