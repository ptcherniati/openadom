package fr.inra.oresing.domain.data.read;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.data.Datum;

import java.util.function.Function;

public record ComputedValuesContext(ImmutableMap<String, Function<Datum, FieldType>> defaultValueFns,
                                    ImmutableSet<String> replaceEnabled) {
}
