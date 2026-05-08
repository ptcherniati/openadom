package fr.inra.oresing.domain;

import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.application.ApplicationInformation;
import fr.inra.oresing.domain.application.normalized.SqlTypes;
import fr.inra.oresing.domain.checker.CheckerReturnType;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.TypeOfDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des enums et DTOs légers du package domain.
 * Aucun contexte Spring.
 */
@DisplayName("Domain enums / lightweight types")
@Tag("domain.model")
class DomainEnumsTest {

    // ---------------------------------------------------------
    // SqlTypes
    // ---------------------------------------------------------

    @Nested
    @DisplayName("SqlTypes enum")
    class SqlTypesTest {

        @Test
        void allValues() {
            assertThat(SqlTypes.values())
                    .containsExactlyInAnyOrder(
                            SqlTypes.TEXT, SqlTypes.INTEGER,
                            SqlTypes.FLOAT, SqlTypes.BOOLEAN);
        }

        @Test
        void valueOf() {
            assertThat(SqlTypes.valueOf("TEXT")).isEqualTo(SqlTypes.TEXT);
            assertThat(SqlTypes.valueOf("BOOLEAN")).isEqualTo(SqlTypes.BOOLEAN);
        }
    }

    // ---------------------------------------------------------
    // ApplicationInformation
    // ---------------------------------------------------------

    @Nested
    @DisplayName("ApplicationInformation enum")
    class ApplicationInformationTest {

        @Test
        void allValues() {
            assertThat(ApplicationInformation.values())
                    .containsExactlyInAnyOrder(
                            ApplicationInformation.ALL,
                            ApplicationInformation.DATA,
                            ApplicationInformation.CONFIGURATION,
                            ApplicationInformation.SYNTHESIS,
                            ApplicationInformation.ADDITIONALFILE,
                            ApplicationInformation.RIGHTSREQUEST,
                            ApplicationInformation.DATATYPE,
                            ApplicationInformation.REFERENCETYPE);
        }

        @Test
        void valueOf() {
            assertThat(ApplicationInformation.valueOf("ALL"))
                    .isEqualTo(ApplicationInformation.ALL);
        }
    }

    // ---------------------------------------------------------
    // CheckerReturnType
    // ---------------------------------------------------------

    @Nested
    @DisplayName("CheckerReturnType enum")
    class CheckerReturnTypeTest {

        @Test
        void allValues() {
            assertThat(CheckerReturnType.values())
                    .containsExactlyInAnyOrder(
                            CheckerReturnType.BOOLEAN,
                            CheckerReturnType.STRING,
                            CheckerReturnType.NUMBER,
                            CheckerReturnType.SET_OF_STRING,
                            CheckerReturnType.SET_OF_NUMBER);
        }

        @Test
        void nameMatchesToString() {
            assertThat(CheckerReturnType.BOOLEAN.toString()).isEqualTo("Boolean");
            assertThat(CheckerReturnType.STRING.toString()).isEqualTo("String");
            assertThat(CheckerReturnType.NUMBER.toString()).isEqualTo("Number");
            assertThat(CheckerReturnType.SET_OF_STRING.toString()).isEqualTo("Set<String>");
            assertThat(CheckerReturnType.SET_OF_NUMBER.toString()).isEqualTo("Set<Number>");
        }

        @Test
        void getNameMatchesToString() {
            for (CheckerReturnType t : CheckerReturnType.values()) {
                assertThat(t.getName()).isEqualTo(t.toString());
            }
        }
    }

    // ---------------------------------------------------------
    // Multiplicity
    // ---------------------------------------------------------

    @Nested
    @DisplayName("Multiplicity enum")
    class MultiplicityTest {

        @Test
        void allValues() {
            assertThat(Multiplicity.values())
                    .containsExactlyInAnyOrder(Multiplicity.ONE, Multiplicity.MANY);
        }

        @Test
        void valuesSetContainsOneAndMany() {
            assertThat(Multiplicity.VALUES)
                    .containsExactlyInAnyOrder("ONE", "MANY");
        }
    }

    // ---------------------------------------------------------
    // TypeOfDate
    // ---------------------------------------------------------

    @Nested
    @DisplayName("TypeOfDate enum")
    class TypeOfDateTest {

        @Test
        void allValues() {
            assertThat(TypeOfDate.values())
                    .containsExactlyInAnyOrder(
                            TypeOfDate.DATE, TypeOfDate.TIME, TypeOfDate.DATETIME);
        }

        @Test
        void valueOf() {
            assertThat(TypeOfDate.valueOf("DATE")).isEqualTo(TypeOfDate.DATE);
            assertThat(TypeOfDate.valueOf("DATETIME")).isEqualTo(TypeOfDate.DATETIME);
        }
    }

    // ---------------------------------------------------------
    // PolicyDescription
    // ---------------------------------------------------------

    @Nested
    @DisplayName("PolicyDescription")
    class PolicyDescriptionTest {

        @Test
        void settersAndGetters() {
            PolicyDescription pd = new PolicyDescription();
            pd.setPolicyname("my_policy");
            pd.setSchemaname("my_schema");
            pd.setTablename("my_table");
            assertThat(pd.getPolicyname()).isEqualTo("my_policy");
            assertThat(pd.getSchemaname()).isEqualTo("my_schema");
            assertThat(pd.getTablename()).isEqualTo("my_table");
        }

        @Test
        void toStringContainsFields() {
            PolicyDescription pd = new PolicyDescription();
            pd.setPolicyname("pol");
            assertThat(pd.toString()).contains("pol");
        }
    }

    // ---------------------------------------------------------
    // ValidationLevel
    // ---------------------------------------------------------

    @Nested
    @DisplayName("ValidationLevel")
    class ValidationLevelTest {

        @Test
        void successIsSuccessNotError() {
            assertThat(ValidationLevel.SUCCESS.isSuccess()).isTrue();
            assertThat(ValidationLevel.SUCCESS.isError()).isFalse();
        }

        @Test
        void errorIsErrorNotSuccess() {
            assertThat(ValidationLevel.ERROR.isError()).isTrue();
            assertThat(ValidationLevel.ERROR.isSuccess()).isFalse();
        }

        @Test
        void warnIsNeitherSuccessNorError() {
            assertThat(ValidationLevel.WARN.isSuccess()).isFalse();
            assertThat(ValidationLevel.WARN.isError()).isFalse();
        }

        @Test
        void allValues() {
            assertThat(ValidationLevel.values()).hasSize(3);
        }
    }

    // ---------------------------------------------------------
    // ComponentPresenceConstraint
    // ---------------------------------------------------------

    @Nested
    @DisplayName("ComponentPresenceConstraint")
    class ComponentPresenceConstraintTest {

        @Test
        void mandatoryIsMandatoryAndExpected() {
            assertThat(ComponentPresenceConstraint.MANDATORY.isMandatory()).isTrue();
            assertThat(ComponentPresenceConstraint.MANDATORY.isExpected()).isTrue();
        }

        @Test
        void optionalIsNotMandatoryButExpected() {
            assertThat(ComponentPresenceConstraint.OPTIONAL.isMandatory()).isFalse();
            assertThat(ComponentPresenceConstraint.OPTIONAL.isExpected()).isTrue();
        }

        @Test
        void absentIsNotExpectedAndNotMandatory() {
            assertThat(ComponentPresenceConstraint.ABSENT.isMandatory()).isFalse();
            assertThat(ComponentPresenceConstraint.ABSENT.isExpected()).isFalse();
        }

        @Test
        void valuesSetContainsAll() {
            assertThat(ComponentPresenceConstraint.VALUES).containsExactlyInAnyOrder("MANDATORY", "OPTIONAL", "ABSENT");
        }
    }
}