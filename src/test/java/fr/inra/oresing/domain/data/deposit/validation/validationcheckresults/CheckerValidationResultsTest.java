package fr.inra.oresing.domain.data.deposit.validation.validationcheckresults;

import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.checker.type.BooleanType;
import fr.inra.oresing.domain.checker.type.DateType;
import fr.inra.oresing.domain.checker.type.FloatType;
import fr.inra.oresing.domain.checker.type.IntegerType;
import fr.inra.oresing.domain.checker.type.NullType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.DataColumn;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Tag("domain.model")
class CheckerValidationResultsTest {

    private static final DataColumn TARGET = new DataColumn("col");

    // ── BooleanValidationCheckResult ──────────────────────────────────────────

    @Test
    void booleanSuccess_levelIsSuccessAndValueNonNull() {
        BooleanType booleanType = BooleanType.of(true);
        BooleanValidationCheckResult result = BooleanValidationCheckResult.success(TARGET, booleanType);

        assertEquals(ValidationLevel.SUCCESS, result.level());
        assertNotNull(result.value());
        assertTrue(result.isSuccess());
        assertFalse(result.isError());
    }

    // ── IntegerValidationCheckResult ──────────────────────────────────────────

    @Test
    void integerSuccess_levelIsSuccess() {
        IntegerType integerType = IntegerType.of(42);
        IntegerValidationCheckResult result = IntegerValidationCheckResult.success(TARGET, integerType);

        assertEquals(ValidationLevel.SUCCESS, result.level());
        assertNotNull(result.value());
        assertTrue(result.isSuccess());
        assertFalse(result.isError());
    }

    @Test
    void integerError_levelIsErrorAndIsErrorTrue() {
        IntegerValidationCheckResult result = IntegerValidationCheckResult.error(
                TARGET, "invalidInteger", ImmutableMap.of("component", "col", "value", "abc")
        );

        assertEquals(ValidationLevel.ERROR, result.level());
        assertTrue(result.isError());
        assertFalse(result.isSuccess());
        assertNull(result.value());
    }

    // ── FloatValidationCheckResult ────────────────────────────────────────────

    @Test
    void floatSuccess_levelIsSuccess() {
        FloatType floatType = FloatType.of(3.14f);
        FloatValidationCheckResult result = FloatValidationCheckResult.success(TARGET, floatType);

        assertEquals(ValidationLevel.SUCCESS, result.level());
        assertNotNull(result.value());
        assertTrue(result.isSuccess());
        assertFalse(result.isError());
    }

    @Test
    void floatError_levelIsError() {
        FloatValidationCheckResult result = FloatValidationCheckResult.error(
                TARGET, "invalidFloat", ImmutableMap.of("component", "col", "value", "xyz")
        );

        assertEquals(ValidationLevel.ERROR, result.level());
        assertTrue(result.isError());
        assertFalse(result.isSuccess());
        assertNull(result.value());
    }

    // ── StringValidationCheckResult ───────────────────────────────────────────

    @Test
    void stringSuccess_levelIsSuccess() {
        StringType stringType = StringType.getStringTypeFromStringValue("hello");
        StringValidationCheckResult result = StringValidationCheckResult.success(TARGET, stringType);

        assertEquals(ValidationLevel.SUCCESS, result.level());
        assertNotNull(result.value());
        assertTrue(result.isSuccess());
        assertFalse(result.isError());
    }

    @Test
    void stringError_isErrorTrue() {
        StringValidationCheckResult result = StringValidationCheckResult.error(
                TARGET, "patternNotMatched", ImmutableMap.of("component", "col", "pattern", "\\d+", "value", "abc")
        );

        assertTrue(result.isError());
        assertFalse(result.isSuccess());
        assertNull(result.value());
    }

    // ── DefaultCheckerValidationCheckResult ───────────────────────────────────

    @Test
    void defaultSuccess_isSuccessTrue() {
        StringType stringType = StringType.getStringTypeFromStringValue("test");
        DefaultCheckerValidationCheckResult result = DefaultCheckerValidationCheckResult.success(TARGET, stringType);

        assertTrue(result.isSuccess());
        assertFalse(result.isError());
        assertEquals(ValidationLevel.SUCCESS, result.level());
    }

    @Test
    void defaultWarn_isWarnTrue() {
        DefaultCheckerValidationCheckResult result = DefaultCheckerValidationCheckResult.warn(
                "someWarning", ImmutableMap.of("key", "val"), TARGET, NullType.INSTANCE
        );

        assertEquals(ValidationLevel.WARN, result.level());
        assertFalse(result.isSuccess());
        assertFalse(result.isError());
    }

    @Test
    void defaultError_isErrorTrue() {
        DefaultCheckerValidationCheckResult result = DefaultCheckerValidationCheckResult.error(
                "someError", ImmutableMap.of("key", "val"), TARGET
        );

        assertTrue(result.isError());
        assertFalse(result.isSuccess());
        assertEquals(ValidationLevel.ERROR, result.level());
    }

    @Test
    void defaultCopyConstructor_preservesAllFields() {
        StringType stringType = StringType.getStringTypeFromStringValue("copy");
        DefaultCheckerValidationCheckResult original = DefaultCheckerValidationCheckResult.success(TARGET, stringType);
        DefaultCheckerValidationCheckResult copy = new DefaultCheckerValidationCheckResult(original);

        assertEquals(original.level(), copy.level());
        assertEquals(original.message(), copy.message());
        assertEquals(original.messageParams(), copy.messageParams());
        assertEquals(original.target(), copy.target());
        assertEquals(original.value(), copy.value());
    }

    // ── Interface CheckerValidationCheckResult default methods ────────────────

    @Test
    void interfaceMethods_isSuccessIsErrorIsWarn_viaPolymorphism() {
        CheckerValidationCheckResult<?> success = IntegerValidationCheckResult.success(TARGET, IntegerType.of(1));
        CheckerValidationCheckResult<?> error = IntegerValidationCheckResult.error(TARGET, "err", ImmutableMap.of());
        CheckerValidationCheckResult<?> warn = DefaultCheckerValidationCheckResult.warn(
                "w", ImmutableMap.of(), TARGET, NullType.INSTANCE
        );

        assertTrue(success.isSuccess());
        assertFalse(success.isError());

        assertTrue(error.isError());
        assertFalse(error.isSuccess());

        assertFalse(warn.isSuccess());
        assertFalse(warn.isError());
        assertEquals(ValidationLevel.WARN, warn.level());
    }

    // ── DateValidationCheckResult ──────────────────────────────────────────

    @Nested
    class DateValidationCheckResultTest {

        @Test
        void success_setsSuccessLevel_andConvertsTemporalAccessors() {
            DateType dt = DateType.of("date:2024-06-15T00:00:00:yyyy-MM-dd'T'HH:mm:ss");
            TemporalAccessor ta = LocalDate.of(2024, 6, 15);
            DateValidationCheckResult result = DateValidationCheckResult.success(TARGET, List.of(ta), dt);
            assertEquals(ValidationLevel.SUCCESS, result.level());
            assertNull(result.message());
            assertNotNull(result.localDateTime());
            assertFalse(result.localDateTime().isEmpty());
            assertEquals(LocalDateTime.of(2024, 6, 15, 0, 0), result.localDateTime().first());
        }

        @Test
        void error_setsErrorLevel_andPreservesMessage() {
            DateType dt = DateType.of("date:2024-01-01T00:00:00:yyyy-MM-dd'T'HH:mm:ss");
            DateValidationCheckResult result = DateValidationCheckResult.error(
                    TARGET, "badDate", ImmutableMap.of("key", "val"), dt);
            assertEquals(ValidationLevel.ERROR, result.level());
            assertEquals("badDate", result.message());
            assertNull(result.date());
            assertNull(result.localDateTime());
        }

        @Test
        void success_withNullLocalTime_defaultsToMidnight() {
            // TemporalAccessor with only a date (no time part) → localTime defaults to LocalTime.MIN
            TemporalAccessor dateOnly = LocalDate.of(2024, 3, 10);
            DateType dt = DateType.of("date:2024-03-10T00:00:00:yyyy-MM-dd'T'HH:mm:ss");
            DateValidationCheckResult result = DateValidationCheckResult.success(TARGET, List.of(dateOnly), dt);
            assertEquals(LocalDateTime.of(2024, 3, 10, 0, 0), result.localDateTime().first());
        }
    }
}
