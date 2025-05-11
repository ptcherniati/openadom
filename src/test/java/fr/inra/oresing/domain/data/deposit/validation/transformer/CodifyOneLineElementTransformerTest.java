package fr.inra.oresing.domain.data.deposit.validation.transformer;

import fr.inra.oresing.domain.checker.CheckerTarget;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.SomethingThatCanProvideEvaluationContext;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class CodifyOneLineElementTransformerTest {

    private final CheckerTarget target = mock(CheckerTarget.class);
    private final SomethingThatCanProvideEvaluationContext context = mock(SomethingThatCanProvideEvaluationContext.class);
    private final CodifyOneLineElementTransformer transformer = new CodifyOneLineElementTransformer(target);

    @Test
    void transform_shouldReturnNull_whenValueIsNull() {
        FieldType result = transformer.transform(context, null);
        assertThat(result).isNull();
    }

    @Test
    void transform_shouldReturnSame_whenValueIsEmptyStringType() {
        FieldType empty = StringType.getStringTypeFromStringValue("");
        FieldType result = transformer.transform(context, empty);
        assertThat(result).isSameAs(empty);
    }

    @Test
    void transform_shouldCodifyStringType() {
        StringType input = StringType.getStringTypeFromStringValue("A B/C");
        FieldType result = transformer.transform(context, input);
        assertThat(result)
                .isInstanceOf(StringType.class)
                .extracting(Object::toString)
                .isEqualTo("a_bSOLIDUSc"); // Supposant que Ltree.escapeToLabel remplace espaces et slash par '_'
    }

    @Test
    void transform_shouldCodifyListType() {
        FieldType input = ListType.getListTypeFromListValue(List.of(
                StringType.getStringTypeFromStringValue("a b"),
                StringType.getStringTypeFromStringValue("c/d")
        ));
        // Simuler le .toString() de ListType pour donner "a b,c/d"
        FieldType result = transformer.transform(context, input);
        assertThat(result)
                .isInstanceOf(ListType.class);
        List<String> listType = ((ListType) result)
                .getValue().stream()
                .map(Object::toString)
                .toList();
        assertThat(listType)
                .containsExactly(
                        "a_b",
                        "cSOLIDUSd"
                );
    }
}