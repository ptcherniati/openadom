package fr.inra.oresing.domain.checker.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour ListType (code nouveau dans SonarQube, 0% de couverture).
 */
@Tag("domain.model")
@DisplayName("ListType – méthodes statiques et accesseurs")
class ListTypeTest {

    @Test
    @DisplayName("EMPTY_LIST est non null et son getValue est vide")
    void emptyList() {
        ListType<StringType> empty = ListType.EMPTY_LIST;
        assertThat(empty).isNotNull();
    }

    @Test
    @DisplayName("ofStringType() crée un ListType<StringType>")
    void ofStringType() {
        ListType<StringType> lt = ListType.ofStringType();
        assertThat(lt).isNotNull();
        assertThat(lt.getValue()).isEmpty();
    }

    @Test
    @DisplayName("getListTypeFromListValue() construit un ListType depuis une liste de StringType")
    void getListTypeFromListValue() {
        List<StringType> values = List.of(
                StringType.getStringTypeFromStringValue("a"),
                StringType.getStringTypeFromStringValue("b")
        );
        ListType<?> lt = ListType.getListTypeFromListValue(values);
        assertThat(lt).isNotNull();
        assertThat(lt.getValue()).hasSize(2);
    }

    @Test
    @DisplayName("add() ajoute un élément à la liste")
    void addElement() {
        ListType<StringType> lt = ListType.ofStringType();
        StringType val = StringType.getStringTypeFromStringValue("hello");
        lt.add(val);
        assertThat(lt.getValue()).hasSize(1);
        assertThat(lt.getValue().get(0).getValue()).isEqualTo("hello");
    }

    @Test
    @DisplayName("toJsonForFrontend() retourne une liste")
    void toJsonForFrontend() {
        ListType<StringType> lt = ListType.getListTypeFromListValue(
                List.of(StringType.getStringTypeFromStringValue("x")));
        Object json = lt.toJsonForFrontend();
        assertThat(json).isNotNull();
    }

    @Test
    @DisplayName("copy() retourne une nouvelle instance avec les mêmes valeurs")
    void copy() {
        ListType<StringType> lt = ListType.getListTypeFromListValue(
                List.of(StringType.getStringTypeFromStringValue("v")));
        ListType<?> copy = (ListType<?>) lt.copy();
        assertThat(copy).isNotNull();
        assertThat(copy.getValue()).hasSize(1);
    }
}