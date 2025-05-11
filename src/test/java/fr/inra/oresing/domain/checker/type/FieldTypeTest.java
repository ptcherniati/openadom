package fr.inra.oresing.domain.checker.type;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.CheckerValidationCheckResult;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import org.junit.jupiter.api.*;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.when;

class FieldTypeTest {
    @Mock
    LineChecker.OneChecker lineChecker;
    DataColumn target = new DataColumn("column");

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(lineChecker.target()).thenReturn(target);
        when(lineChecker.fieldTypeForOne()).thenReturn(new StringType("t..i"));
    }

    @TestFactory
    @DisplayName("Tests des combinaisons de FieldTypes et de values")
    Stream<DynamicNode> permissionTests() {
        return getfieldTypesTestCases().stream()
                .map(testCase -> dynamicContainer(
                        String.format("%s - %s", testCase.fieldType().getClass().getSimpleName(), testCase.name()),
                        testCase.values().stream()
                                .map(token -> dynamicContainer(
                                        String.format("%s", token.value()),
                                        Stream.of(
                                                dynamicTest(String.format("get %s", testCase.name()), () -> testGetValueFieldTypeWithValue(testCase, token)),
                                                dynamicTest(String.format("check %s", testCase.name()), () -> testCheckFieldTypeWithValue(testCase, token)),
                                                dynamicTest(String.format("SqlType is %s", testCase.sqlType.getSql()), () -> testGetSqlType(testCase))
                                        )
                                ))
                ));
    }

    private void testGetSqlType(FieldTypeCases<?> testCase) {
        if (testCase.fieldType() instanceof MapType<?, ?>) {
            return;
        }
        Assertions.assertEquals(testCase.sqlType, testCase.fieldType.getSqlType());
    }


    private void testCheckFieldTypeWithValue(FieldTypeCases testCase, ResponseOrException token) {
        final CheckerValidationCheckResult check = testCase.fieldType().check(token.value(), lineChecker);
        switch (token) {
            case ExceptionResponse exceptionResponse -> {
                Assertions.assertTrue(check.isError());
                Assertions.assertFalse(check.isSuccess());
                final Object value = testCase.fieldType().getValue();
                testValue(testCase, exceptionResponse, value);
                Assertions.assertEquals(exceptionResponse.params(), check.messageParams());
                Assertions.assertEquals(exceptionResponse.message(), check.message());
            }
            case ValidResponse validResponse -> {
                if (testCase.fieldType() instanceof MapType<?, ?> || testCase.fieldType() instanceof PatternType) {
                    Assertions.assertTrue(check == null);
                    return;
                }
                Assertions.assertTrue(check.isSuccess());
                Assertions.assertFalse(check.isError());
            }
        }

    }

    private <T extends Object> void testGetValueFieldTypeWithValue(FieldTypeCases testCase, ResponseOrException<T> token) {
        final CheckerValidationCheckResult check = testCase.fieldType().check(token.value(), lineChecker);
        switch (token) {
            case ExceptionResponse exceptionResponse -> {
                Assertions.assertTrue(check.isError());
                Assertions.assertFalse(check.isSuccess());
                final Object value = testCase.fieldType().getValue();
                testValue(testCase, exceptionResponse, value);
            }
            case ValidResponse validResponse -> {
                if (testCase.fieldType() instanceof MapType<?, ?> || testCase.fieldType() instanceof PatternType) {                    Assertions.assertTrue(check == null);
                    return;
                }
                Assertions.assertTrue(check.isSuccess());
                Assertions.assertFalse(check.isError());
                final Object value = testCase.fieldType().getValue();
                Assertions.assertEquals(validResponse.response(), value);
            }
        }
    }

    private static void testValue(FieldTypeCases testCase, ExceptionResponse response, Object value) {
        switch (testCase.fieldType()) {
            case DateType dateType when response.message().equals("badIntervalDateWithComponent") ->
                    Assertions.assertTrue(value instanceof TemporalAccessor);
            case DateType dateType -> Assertions.assertTrue(value == null);
            case StringType stringType -> Assertions.assertTrue(value == null || value.equals(""));
            case BooleanType booleanType -> Assertions.assertTrue(true);
            default -> Assertions.assertTrue(true);
        }
    }

    record FieldTypeCases<T>(String name, FieldType fieldType, SqlPrimitiveType sqlType,
                             List<ResponseOrException<T>> values) {
    }

    record ValidResponse<T>(String value, T response) implements ResponseOrException {
    }

    record ExceptionResponse(String value, Map<String, Object> params, String message) implements ResponseOrException {
    }

    sealed interface ResponseOrException<T> permits ValidResponse, ExceptionResponse {
        String value();

        static <U> ResponseOrException<U> of(String value, U response) {
            return new ValidResponse<>(value, response);
        }

        static <E extends Exception> ResponseOrException<Class<E>> of(String value, Map<String, Object> params, String message) {
            return new ExceptionResponse(value, params, message);
        }
    }

    private List<FieldTypeCases<? extends Object>> getfieldTypesTestCases() {
        final StringType titi = new StringType("t..i");
        titi.value = "titi";
        String refType = "reftype";
        UUID UUID1 = UUID.randomUUID();
        UUID UUID2 = UUID.randomUUID();
        ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues = ImmutableMap.of(
                new DataValue.LineIdentityColumnName(Ltree.fromSql("parent__enfant"), Ltree.fromSql("parent.parent__enfant")),
                ImmutableSet.of(UUID1),
                new DataValue.LineIdentityColumnName(Ltree.fromSql("parent"), Ltree.fromSql("parent")),
                ImmutableSet.of(UUID2)
        );
        return List.of(
                new FieldTypeCases("stringType .*",
                        new StringType(".*"),
                        SqlPrimitiveType.TEXT,
                        List.of(
                                ResponseOrException.of("toto", "toto"),
                                ResponseOrException.of("titi", "titi")
                        )
                ),
                new FieldTypeCases("stringType t..i",
                        new StringType("t..i"),
                        SqlPrimitiveType.TEXT,
                        List.of(
                                ResponseOrException.of("toto", Map.of("component", "column", "pattern", "t..i", "value", "toto"), "patternNotMatchedWithComponent"),
                                ResponseOrException.of("titi", "titi")
                        )
                ),
                new FieldTypeCases("date dd/MM/yyyy",
                        new DateType("dd/MM/yyyy", null, null),
                        SqlPrimitiveType.TEXT,
                        List.of(
                                ResponseOrException.of("1987-12-24", Map.of("component", "column", "pattern", "dd/MM/yyyy", "value", "1987-12-24"), "invalidDateWithComponent"),
                                ResponseOrException.of("23/05/2004", LocalDate.parse("23/05/2004", DateTimeFormatter.ofPattern("dd/MM/yyyy")).atStartOfDay())
                        )
                ),
                new FieldTypeCases("date yyyy-MM-dd with interval",
                        new DateType("yyyy-MM-dd", null, LocalDate.parse("1987-01-01", DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay(), LocalDate.parse("1987-12-31", DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay()),
                        SqlPrimitiveType.TEXT,
                        List.of(
                                ResponseOrException.of("1986-12-24", Map.of("value", "1986-12-24", "component", "column", "type", "LOWER_THAN_MIN", "bound", LocalDate.parse("1987-01-01", DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay()), "badIntervalDateWithComponent"),
                                ResponseOrException.of("1988-12-24", Map.of("value", "1988-12-24", "component", "column", "type", "HIGHER_THAN_MAX", "bound", LocalDate.parse("1987-12-31", DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay()), "badIntervalDateWithComponent"),
                                ResponseOrException.of("1987-12-24", LocalDate.parse("1987-12-24", DateTimeFormatter.ofPattern("yyyy-MM-dd")).atStartOfDay())
                        )
                ),
                new FieldTypeCases("integer",
                        new IntegerType(null, null),
                        SqlPrimitiveType.INTEGER,
                        List.of(
                                ResponseOrException.of("14", 14),
                                ResponseOrException.of("toto", Map.of("component", "column", "value", "toto"), "invalidIntegerWithComponent"),
                                ResponseOrException.of("14.3", Map.of("component", "column", "value", "14.3"), "invalidIntegerWithComponent")
                        )
                ),
                new FieldTypeCases("integer with interval [12,14]",
                        new IntegerType(12, 14),
                        SqlPrimitiveType.INTEGER,
                        List.of(
                                ResponseOrException.of("11", Map.of("component", "column", "value", "11", "type", "LOWER_THAN_MIN", "bound", 12), "badIntervalIntegerWithComponent"),
                                ResponseOrException.of("15", Map.of("component", "column", "value", "15", "type", "HIGHER_THAN_MAX", "bound", 14), "badIntervalIntegerWithComponent")
                        )
                ),
                new FieldTypeCases("float",
                        new FloatType(null, null),
                        SqlPrimitiveType.NUMERIC,
                        List.of(
                                ResponseOrException.of("14", 14F),
                                ResponseOrException.of("14.3", 14.3F),
                                ResponseOrException.of("toto", Map.of("component", "column", "value", "toto"), "invalidFloatWithComponent")
                        )
                ),
                new FieldTypeCases("float with interval [12.3F,14.2F]",
                        new FloatType(12.3F, 14.2F),
                        SqlPrimitiveType.NUMERIC,
                        List.of(
                                ResponseOrException.of("11.2", Map.of("component", "column", "value", "11.2", "type", "LOWER_THAN_MIN", "bound", 12.3F), "badIntervalFloatWithComponent"),
                                ResponseOrException.of("15.7", Map.of("component", "column", "value", "15.7", "type", "HIGHER_THAN_MAX", "bound", 14.2F), "badIntervalFloatWithComponent")
                        )
                ),
                new FieldTypeCases("boolean",
                        new BooleanType(false),
                        SqlPrimitiveType.BOOLEAN,
                        List.of(
                                ResponseOrException.of("true", true),
                                ResponseOrException.of("TRUE", true),
                                ResponseOrException.of("TrUe", true),
                                ResponseOrException.of("false", false),
                                ResponseOrException.of("yes", false),
                                ResponseOrException.of("no", false),
                                ResponseOrException.of("1", false),
                                ResponseOrException.of("0", false),
                                ResponseOrException.of("toto", false)
                        )
                ),
                new FieldTypeCases("reference type",
                        new ReferenceType(target, refType, referenceValues, null, null),
                        SqlPrimitiveType.LTREE,
                        List.of(
                                ResponseOrException.of("parent", Ltree.fromSql("parent")),
                                ResponseOrException.of("parent__enfant", Ltree.fromSql("parent__enfant")),
                                ResponseOrException.of("autre", Map.of(
                                        "component", "column",
                                        "referenceValues", Set.of("parent", "parent__enfant"),
                                        "refType", refType,
                                        "value", "autre"
                                ), "invalidReferenceWithComponent")
                        )
                ),
                new FieldTypeCases("liste_en type of String",
                        new ListType(new StringType("t..i")),
                        SqlPrimitiveType.TEXT,
                        List.of(
                                ResponseOrException.of("titi", List.of(titi))
                        )
                ),
                new FieldTypeCases("Map type of String",
                        new MapType(Map.of("key", new StringType("t..i"))),
                        SqlPrimitiveType.TEXT,
                        List.of(
                                ResponseOrException.of("{'titi': 'titi'}", null)
                        )
                ),
                new FieldTypeCases("null type",
                        NullType.INSTANCE,
                        SqlPrimitiveType.TEXT,
                        List.of(
                                ResponseOrException.of("rien", NullType.Null.NULL)
                        )
                ),
                new FieldTypeCases("null type",
                        new PatternType(null),
                        SqlPrimitiveType.JSONB,
                        List.of(
                                ResponseOrException.of("rien", null)
                        )
                )
        );
    }
}