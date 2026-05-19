package fr.inra.oresing.domain.application.configuration.type;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de CheckerEnum.
 * Aucun contexte Spring.
 */
@Tag("domain.model")
@DisplayName("CheckerEnum — noms et constante VALUES")
class CheckerEnumTest {

    @Nested
    @DisplayName("Valeurs de l'enum")
    class ValuesTest {

        @Test
        @DisplayName("toutes les 7 valeurs sont présentes")
        void allValues() {
            assertThat(CheckerEnum.values()).hasSize(7);
        }

        @Test
        @DisplayName("getName() retourne le nom technique (ex: OA_float)")
        void getName() {
            assertThat(CheckerEnum.OA_float.getName()).isEqualTo("OA_float");
            assertThat(CheckerEnum.OA_integer.getName()).isEqualTo("OA_integer");
            assertThat(CheckerEnum.OA_boolean.getName()).isEqualTo("OA_boolean");
            assertThat(CheckerEnum.OA_string.getName()).isEqualTo("OA_string");
            assertThat(CheckerEnum.OA_date.getName()).isEqualTo("OA_date");
            assertThat(CheckerEnum.OA_reference.getName()).isEqualTo("OA_reference");
            assertThat(CheckerEnum.OA_groovyExpression.getName()).isEqualTo("OA_groovyExpression");
        }

        @Test
        @DisplayName("toString() retourne le même résultat que getName()")
        void toStringEqualsGetName() {
            for (CheckerEnum c : CheckerEnum.values()) {
                assertThat(c).hasToString(c.getName());
            }
        }

        @Test
        @DisplayName("VALUES contient tous les noms techniques")
        void valuesConstantContainsAllNames() {
            assertThat(CheckerEnum.VALUES).hasSize(7);
            for (CheckerEnum c : CheckerEnum.values()) {
                assertThat(CheckerEnum.VALUES).contains(c.getName());
            }
        }

        @Test
        @DisplayName("VALUES contient OA_float")
        void valuesConstantContainsOaFloat() {
            assertThat(CheckerEnum.VALUES).contains("OA_float");
        }

        @Test
        @DisplayName("compareTo() suit l'ordre de déclaration")
        void compareTo() {
            assertThat(CheckerEnum.OA_reference).isLessThan(CheckerEnum.OA_float);
            assertThat(CheckerEnum.OA_float).isGreaterThan(CheckerEnum.OA_reference);
            assertThat(CheckerEnum.OA_float).isEqualByComparingTo(CheckerEnum.OA_float);
        }
    }
}