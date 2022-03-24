package fr.inra.oresing.model;

import com.google.common.collect.Maps;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.rest.ReferenceImporterContext;
import lombok.Value;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Value
public class ReferenceColumnIndexedValue implements ReferenceColumnValue<Map<String, String>, Map<String, String>> {

    Map<Ltree, String> values;

    @Override
    public Collection<String> getValuesToCheck() {
        return values.values();
    }

    @Override
    public ReferenceColumnIndexedValue transform(Function<String, String> transformation) {
        Map<Ltree, String> transformedValues = Maps.transformValues(values, transformation::apply);
        return new ReferenceColumnIndexedValue(transformedValues);
    }

    @Override
    public String toValueString(ReferenceImporterContext referenceImporterContext, String referencedColumn, String locale) {
        return values.entrySet().stream()
                .map(ltreeStringEntry -> String.format("\"%s\"\"=%s\"", referenceImporterContext.getDisplayByReferenceAndNaturalKey( referencedColumn,ltreeStringEntry.getKey().toString(), locale), ltreeStringEntry.getValue()))
                .collect(Collectors.joining(",","[","]"));
    }

    @Override
    public Map<String, String> toJsonForFrontend() {
        return toStringStringMap();
    }

    @Override
    public Map<String, String> toJsonForDatabase() {
        return toStringStringMap();
    }

    private Map<String, String> toStringStringMap() {
        Map<String, String> jsonForDatabase = values.entrySet().stream()
                .collect(Collectors.toMap(entry -> entry.getKey().getSql(), Map.Entry::getValue));
        return jsonForDatabase;
    }
}