package fr.inra.oresing.domain.groovy.predefined.script;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.type.DateType;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import groovy.lang.Closure;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record BuildCompositeKey() implements ScriptConstantProvider {
    public static final Function<String, String> nullOrEmptyToNull = partialKey -> Strings.isNullOrEmpty(partialKey) ? Ltree.NULL_KEY : partialKey;

    public static String buildNaturelKeyFromLabels(List<String> values) {
        if (values.stream().allMatch(Strings::isNullOrEmpty)) {
            return "";
        }
        return values.stream()
                .map(nullOrEmptyToNull)
                .map(label -> label.matches(DateType.PATTERN_DATE_REGEXP_FIND_DATE) ? DateType.sorteableDateToFormattedDate(label).replaceAll("/", "_") : label)
                .map(Ltree::escapeToLabel)
                .collect(Collectors.joining(DataImporterContext.getCompositeNaturalKeyComponentsSeparator()));
    }

    @Override
    public void bindToContext(Map<String, Object> context) {
        Closure<String> buildCompositeKey = new Closure<String>(this) {
            public String doCall(List<String> labels) {
                Map<String, String> datum = (Map<String, String>) context.get("datum");

                List<String> values = labels.stream()
                        .map(label -> datum.getOrDefault(label, ""))
                        .map(String::trim)
                        .toList();
                return buildNaturelKeyFromLabels(values);
            }
        };
        context.put("OA_buildCompositeKey", buildCompositeKey);
    }
}