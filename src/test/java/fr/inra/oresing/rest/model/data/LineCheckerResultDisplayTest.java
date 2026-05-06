package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.StringChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour LineCheckerResultDisplay.
 */
@Tag("domain.model")
@DisplayName("LineCheckerResultDisplay – constructeurs")
class LineCheckerResultDisplayTest {

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("constructeur de copie délègue toutes les propriétés depuis LineCheckerResult")
    void copyConstructor() {
        DataColumn column = new DataColumn("myColumn");
        StringType stringValue = StringType.getStringTypeFromStringValue("item");
        ListType<StringType> listValue = new ListType<>(stringValue);
        StringType fieldTypeForOne = StringType.getStringTypeFromStringValue("single");
        CheckerDescription checkerDesc = new StringChecker(
                CheckerDescription.CheckerDescriptionType.StringChecker, Multiplicity.ONE, false, ".*");
        DataValue dataValue = new DataValue();
        dataValue.setPatternColumnName("test");

        LineCheckerResult source = new LineCheckerResult() {
            @Override public ListType value() { return listValue; }
            @Override public FieldType<?> fieldTypeForOne() { return fieldTypeForOne; }
            @Override public DataColumn target() { return column; }
            @Override public String transformer() { return "myTransformer"; }
            @Override public CheckerDescription checkerDescription() { return checkerDesc; }
        };

        LineCheckerResultDisplay<?, ?> display = new LineCheckerResultDisplay<>(source, dataValue);

        assertThat(display.target()).isEqualTo(column);
        assertThat(display.transformer()).isEqualTo("myTransformer");
        assertThat(display.checkerDescription()).isEqualTo(checkerDesc);
        assertThat(display.dataValue()).isEqualTo(dataValue);
        assertThat(display.value()).isEqualTo(listValue);
        assertThat(display.fieldTypeForOne()).isEqualTo(fieldTypeForOne);
    }

    @Test
    @DisplayName("constructeur complet assigne tous les champs")
    void fullConstructor() {
        DataColumn column = new DataColumn("col");
        DataValue dataValue = new DataValue();

        LineCheckerResultDisplay<?, ?> display = new LineCheckerResultDisplay<>(
                null, null, column, "transformer42", null, dataValue);

        assertThat(display.target()).isEqualTo(column);
        assertThat(display.transformer()).isEqualTo("transformer42");
        assertThat(display.dataValue()).isEqualTo(dataValue);
        assertThat(display.value()).isNull();
        assertThat(display.fieldTypeForOne()).isNull();
        assertThat(display.checkerDescription()).isNull();
    }
}