package fr.inra.oresing.domain.checker.type;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.DefaultCheckerValidationCheckResult;
import fr.inra.oresing.persistence.SqlPrimitiveType;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.regex.Pattern;

public non-sealed class StringType implements FieldType<String> {
    final Supplier<StringType> clone;
    private final Predicate<String> predicate;
    private final String pattern;
    String value = "";

    public StringType(final String pattern) {
        super();
        this.pattern = pattern;
        predicate = Optional.ofNullable(pattern).filter(s -> !s.isBlank()).map(StringType::compile).map(Pattern::asMatchPredicate).orElse(null);
        clone = () -> new StringType(pattern);
    }

    public static StringType getStringTypeFromStringValue(final String value) {
        final StringType stringType = new StringType(null);
        stringType.value = value == null ? "" : value.trim();
        return stringType;
    }

    private static Pattern compile(final String patternString) {
        return Pattern.compile(patternString, Pattern.MULTILINE);
    }

    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;
        StringType that = (StringType) o;
        return Objects.equals(pattern, that.pattern) && Objects.equals(value, that.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pattern, value);
    }

    @Override
    public String getValue() {
        return value;
    }

    @Override
    public SqlPrimitiveType getSqlType() {
        return SqlPrimitiveType.TEXT;
    }

    @Override
    public CheckerValidationCheckResult check(final String value, final LineChecker lineChecker) {
        final CheckerValidationCheckResult validationCheckResult;
        final DataColumn target = lineChecker.target();
        if (predicate == null) {
            this.value = value;
            validationCheckResult = DefaultCheckerValidationCheckResult.success(target, this);
        } else {
            if (predicate.test(value)) {
                this.value = value;
                validationCheckResult = DefaultCheckerValidationCheckResult.success(target, this);
            } else {
                validationCheckResult = DefaultCheckerValidationCheckResult.error(target.getInternationalizedKey("patternNotMatched"), ImmutableMap.of("component", target.column(), "pattern", pattern, "value", value), target);
            }
        }
        return validationCheckResult;
    }

    @Override
    public FieldType<?> toJsonForDatabase() {
        return this;
    }

    @Override
    public FieldType copy() {
        final StringType stringType = clone.get();
        stringType.value = value;
        return stringType;
    }

    @Override
    public String toString() {
        return value;
    }
    @Override
    public void serializeAddArray(final ArrayNode arrayNode) {
        arrayNode.add(value);

    }

    @Override
    public Object toJsonForFrontend() {
        return value;
    }
    @Override
    public void serialize(final JsonGenerator gen) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        // Échapper les caractères de contrôle avant écriture
        String escapedValue = escapeJsonString(value);
        gen.writeString(escapedValue);
    }

    @Override
    public void serialize(final JsonGenerator gen, final String key) throws IOException {
        if (value == null) {
            gen.writeNullField(key);
            return;
        }
        String escapedValue = escapeJsonString(value);
        gen.writeStringField(key, escapedValue);
    }

    @Override
    public void serialize(final ObjectNode node, final ObjectMapper mapper, final String key) {
        if (value == null) {
            node.putNull(key);
            return;
        }
        String escapedValue = escapeJsonString(value);
        node.put(key, escapedValue);
    }

    /**
     * Échappe les caractères de contrôle dans une chaîne JSON
     */
    private String escapeJsonString(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }

        // Échapper les caractères de contrôle
        return str.replace("\\", "\\\\")   // \ → \\
                .replace("\"", "\\\"")   // " → \"
                .replace("\n", "\\n")    // newline → \n
                .replace("\r", "\\r")    // carriage return → \r
                .replace("\t", "\\t")    // tab → \t
                .replace("\b", "\\b")    // backspace → \b
                .replace("\f", "\\f");   // form feed → \f
    }

}