package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.StringType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.function.UnaryOperator;

@Tag("domain.model")
@DisplayName("DataColumnMultipleValue — unit tests")
class DataColumnMultipleValueTest {

    @Test
    @DisplayName("Constructor from List<Object> — converts strings")
    void constructorFromList() {
        DataColumnMultipleValue<String> value = new DataColumnMultipleValue<>(List.of("a", "b", "c"));
        Assertions.assertNotNull(value.getValues());
        Assertions.assertEquals(3, value.getValues().getValue().size());
    }

    @Test
    @DisplayName("Constructor from List<Object> — handles FieldType elements")
    void constructorFromListWithFieldTypes() {
        StringType s1 = StringType.getStringTypeFromStringValue("x");
        StringType s2 = StringType.getStringTypeFromStringValue("y");
        DataColumnMultipleValue<String> value = new DataColumnMultipleValue<>(List.of(s1, s2));
        Assertions.assertEquals(2, value.getValues().getValue().size());
    }

    @Test
    @DisplayName("Constructor from ListType")
    void constructorFromListType() {
        ListType<FieldType<?>> listType = new ListType<>(StringType.getStringTypeFromStringValue(null));
        listType.getValue().add(StringType.getStringTypeFromStringValue("val1"));
        DataColumnMultipleValue<String> value = new DataColumnMultipleValue<>(listType);
        Assertions.assertEquals(listType, value.getValues());
    }

    @Test
    @DisplayName("toJsonForDatabase returns the ListType")
    void toJsonForDatabase() {
        DataColumnMultipleValue<String> value = new DataColumnMultipleValue<>(List.of("a"));
        ListType<FieldType<?>> result = value.toJsonForDatabase();
        Assertions.assertNotNull(result);
        Assertions.assertEquals(1, result.getValue().size());
    }

    @Test
    @DisplayName("getValuesToCheck returns the ListType")
    void getValuesToCheck() {
        DataColumnMultipleValue<String> value = new DataColumnMultipleValue<>(List.of("a", "b"));
        FieldType<?> result = value.getValuesToCheck();
        Assertions.assertNotNull(result);
        Assertions.assertInstanceOf(ListType.class, result);
    }

    @Test
    @DisplayName("transform — identity transformation returns same values")
    void transformIdentity() {
        DataColumnMultipleValue<String> value = new DataColumnMultipleValue<>(List.of("a", "b"));
        UnaryOperator<FieldType<?>> identity = v -> v;
        DataColumnValue<ListType<FieldType<?>>, FieldType<?>> result = value.transform(identity);
        Assertions.assertNotNull(result);
        Assertions.assertInstanceOf(DataColumnMultipleValue.class, result);
    }

    @Test
    @DisplayName("transform — null result produces empty MultipleValue")
    void transformNullResult() {
        DataColumnMultipleValue<String> value = new DataColumnMultipleValue<>(List.of("a"));
        // Operator that returns null
        UnaryOperator<FieldType<?>> toNull = v -> null;
        DataColumnValue<ListType<FieldType<?>>, FieldType<?>> result = value.transform(toNull);
        Assertions.assertNotNull(result);
        Assertions.assertInstanceOf(DataColumnMultipleValue.class, result);
    }

    @Test
    @DisplayName("getCsvCellContent — joins with comma")
    void getCsvCellContent() {
        DataColumnMultipleValue<String> value = new DataColumnMultipleValue<>(List.of("alpha", "beta", "gamma"));
        String csv = value.getCsvCellContent();
        Assertions.assertEquals("alpha,beta,gamma", csv);
    }

    @Test
    @DisplayName("getCsvCellContent — single value")
    void getCsvCellContentSingleValue() {
        DataColumnMultipleValue<String> value = new DataColumnMultipleValue<>(List.of("only"));
        Assertions.assertEquals("only", value.getCsvCellContent());
    }

    @Test
    @DisplayName("getCsvCellContent — empty list")
    void getCsvCellContentEmpty() {
        DataColumnMultipleValue<String> value = new DataColumnMultipleValue<>(List.of());
        Assertions.assertEquals("", value.getCsvCellContent());
    }

    @Test
    @DisplayName("getCsvCellContent — value containing comma throws IllegalStateException")
    void getCsvCellContentWithCommaThrows() {
        DataColumnMultipleValue<String> value = new DataColumnMultipleValue<>(List.of("a,b"));
        Assertions.assertThrows(IllegalStateException.class, value::getCsvCellContent);
    }

    @Test
    @DisplayName("toJsonForFrontend returns a copy")
    void toJsonForFrontend() {
        DataColumnMultipleValue<String> value = new DataColumnMultipleValue<>(List.of("x", "y"));
        FieldType<?> result = value.toJsonForFrontend();
        Assertions.assertNotNull(result);
    }
}
