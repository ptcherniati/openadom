package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.additionalfiles.OperationAdditionalFileType;
import fr.inra.oresing.domain.checker.type.SqlPrimitiveType;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.persistence.SqlSchemaForApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires des types légers du package persistence.
 * Aucun contexte Spring.
 */
@DisplayName("Persistence lightweight types")
@Tag("core.config")
@Tag("domain.model")
class PersistenceLightweightTypesTest {

    // ---------------------------------------------------------
    // SqlPrimitiveType
    // ---------------------------------------------------------

    @Nested
    @DisplayName("SqlPrimitiveType enum")
    class SqlPrimitiveTypeTest {

        @Test
        void allValues() {
            assertThat(SqlPrimitiveType.values()).containsExactlyInAnyOrder(
                    SqlPrimitiveType.UUID, SqlPrimitiveType.LTREE, SqlPrimitiveType.TEXT,
                    SqlPrimitiveType.INTEGER, SqlPrimitiveType.NUMERIC,
                    SqlPrimitiveType.COMPOSITE_DATE, SqlPrimitiveType.BOOLEAN,
                    SqlPrimitiveType.JSONB);
        }

        @Test
        void getSqlReturnsName() {
            for (SqlPrimitiveType type : SqlPrimitiveType.values()) {
                assertThat(type.getSql()).isEqualTo(type.name());
            }
        }

        @Test
        void isEmptyStringValidValueOnlyForTextAndLtree() {
            assertThat(SqlPrimitiveType.TEXT.isEmptyStringValidValue()).isTrue();
            assertThat(SqlPrimitiveType.LTREE.isEmptyStringValidValue()).isTrue();
            assertThat(SqlPrimitiveType.UUID.isEmptyStringValidValue()).isFalse();
            assertThat(SqlPrimitiveType.INTEGER.isEmptyStringValidValue()).isFalse();
            assertThat(SqlPrimitiveType.BOOLEAN.isEmptyStringValidValue()).isFalse();
            assertThat(SqlPrimitiveType.JSONB.isEmptyStringValidValue()).isFalse();
        }
    }

    // ---------------------------------------------------------
    // OperationReferenceType
    // ---------------------------------------------------------

    @Nested
    @DisplayName("OperationReferenceType enum")
    class OperationReferenceTypeTest {

        @Test
        void allValues() {
            assertThat(OperationReferenceType.values())
                    .containsExactlyInAnyOrder(
                            OperationReferenceType.admin,
                            OperationReferenceType.manage);
        }

        @Test
        void authorizationColumnsDescriptionNotNull() {
            for (OperationReferenceType t : OperationReferenceType.values()) {
                assertThat(t.getAuthorizationColumnsDescription())
                        .as("authorizationColumnsDescription de %s", t.name())
                        .isNotNull();
            }
        }
    }

    // ---------------------------------------------------------
    // OperationAdditionalFileType
    // ---------------------------------------------------------

    @Nested
    @DisplayName("OperationAdditionalFileType enum")
    class OperationAdditionalFileTypeTest {

        @Test
        void allValues() {
            assertThat(OperationAdditionalFileType.values())
                    .containsExactlyInAnyOrder(
                            OperationAdditionalFileType.admin,
                            OperationAdditionalFileType.delete,
                            OperationAdditionalFileType.depot,
                            OperationAdditionalFileType.extraction);
        }

        @Test
        void authorizationColumnsDescriptionNotNull() {
            for (OperationAdditionalFileType t : OperationAdditionalFileType.values()) {
                assertThat(t.getAuthorizationColumnsDescription())
                        .as("authorizationColumnsDescription de %s", t.name())
                        .isNotNull();
            }
        }
    }

    // ---------------------------------------------------------
    // FilterList
    // ---------------------------------------------------------

    @Nested
    @DisplayName("FilterList record")
    class FilterListTest {

        @Test
        void recordAccessors() {
            FilterList fl = new FilterList("myList", List.of());
            assertThat(fl.listName()).isEqualTo("myList");
            assertThat(fl.refsLinkeds()).isEmpty();
        }

        @Test
        void nullRefsLinkeds() {
            FilterList fl = new FilterList("x", null);
            assertThat(fl.listName()).isEqualTo("x");
            assertThat(fl.refsLinkeds()).isNull();
        }
    }

    // ---------------------------------------------------------
    // Uniqueness
    // ---------------------------------------------------------

    @Nested
    @DisplayName("Uniqueness (HashMap extension)")
    class UniquenessTest {

        @Test
        void defaultConstructorCreatesEmptyMap() {
            Uniqueness u = new Uniqueness();
            assertThat(u).isEmpty();
        }

        @Test
        void canStoreAndRetrieveEntries() {
            Uniqueness u = new Uniqueness();
            u.put("key1", List.of("a", "b"));
            assertThat(u.get("key1")).containsExactly("a", "b");
        }
    }

    // ---------------------------------------------------------
    // DataRepository.Order
    // ---------------------------------------------------------

    @Nested
    @DisplayName("DataRepository.Order enum")
    class DataRepositoryOrderTest {

        @Test
        void allValues() {
            assertThat(DataRepository.Order.values())
                    .containsExactlyInAnyOrder(
                            DataRepository.Order.ASC,
                            DataRepository.Order.DESC);
        }

        @Test
        void valueOf() {
            assertThat(DataRepository.Order.valueOf("ASC"))
                    .isEqualTo(DataRepository.Order.ASC);
        }
    }

    // ---------------------------------------------------------
    // OreSiSqlSchema
    // ---------------------------------------------------------

    @Nested
    @DisplayName("OreSiSqlSchema enum")
    class OreSiSqlSchemaTest {

        @Test
        void singleValueMain() {
            assertThat(OreSiSqlSchema.values()).containsExactly(OreSiSqlSchema.MAIN);
        }

        @Test
        void getNameReturnsPublic() {
            assertThat(OreSiSqlSchema.MAIN.getName()).isEqualTo("public");
        }

        @Test
        void applicationFactoryMethod() {
            assertThat(OreSiSqlSchema.application()).isNotNull();
            assertThat(OreSiSqlSchema.application().name()).isEqualTo("application");
        }

        @Test
        void binaryFileFactoryMethod() {
            assertThat(OreSiSqlSchema.binaryFile(OreSiSqlSchema.MAIN)).isNotNull();
            assertThat(OreSiSqlSchema.binaryFile(OreSiSqlSchema.MAIN).name()).isEqualTo("binaryfile");
        }

        @Test
        void referenceValueFactoryMethod() {
            assertThat(OreSiSqlSchema.referencevalue(OreSiSqlSchema.MAIN)).isNotNull();
            assertThat(OreSiSqlSchema.referencevalue(OreSiSqlSchema.MAIN).name()).isEqualTo("referencevalue");
        }

        @Test
        void authorizationFactoryMethod() {
            assertThat(OreSiSqlSchema.authorization(OreSiSqlSchema.MAIN)).isNotNull();
            assertThat(OreSiSqlSchema.authorization(OreSiSqlSchema.MAIN).name()).isEqualTo("oresiauthorization");
        }

        @Test
        void oreSiUserFactoryMethod() {
            assertThat(OreSiSqlSchema.oreSiUser()).isNotNull();
            assertThat(OreSiSqlSchema.oreSiUser().name()).isEqualTo("oreSiUser");
        }

        @Test
        void getSqlIdentifierReturnsName() {
            // "public" ne contient pas d'espaces ni de tirets → pas d'échappement
            assertThat(OreSiSqlSchema.MAIN.getSqlIdentifier()).isEqualTo("public");
        }
    }

    // ---------------------------------------------------------
    // Schemas (constantes)
    // ---------------------------------------------------------

    @Nested
    @DisplayName("Schemas constants")
    class SchemasConstantsTest {

        @Test
        @DisplayName("BUSINESS est 'public'")
        void businessIsPublic() {
            assertThat(Schemas.BUSINESS).isEqualTo("public");
        }

        @Test
        @DisplayName("AUDIT est 'oa_audit'")
        void auditIsOaAudit() {
            assertThat(Schemas.AUDIT).isEqualTo("oa_audit");
        }

        @Test
        @DisplayName("STAGING est 'oa_staging'")
        void stagingIsOaStaging() {
            assertThat(Schemas.STAGING).isEqualTo("oa_staging");
        }
    }

    // ---------------------------------------------------------
    // SqlSchema interface static factories
    // ---------------------------------------------------------

    @Nested
    @DisplayName("SqlSchema static factories")
    class SqlSchemaStaticFactoriesTest {

        @Test
        @DisplayName("mainSchema() retourne OreSiSqlSchema.MAIN")
        void mainSchema() {
            assertThat(SqlSchema.mainSchema()).isEqualTo(OreSiSqlSchema.MAIN);
        }
    }

    // ---------------------------------------------------------
    // SqlSchemaForApplication static methods
    // ---------------------------------------------------------

    @Nested
    @DisplayName("SqlSchemaForApplication — constantes et méthodes statiques")
    class SqlSchemaForApplicationTest {

        @Test
        @DisplayName("PUBLIC_UUID est un UUID valide non nul")
        void publicUuidIsValid() {
            assertThat(SqlSchemaForApplication.PUBLIC_UUID).isNotNull();
        }

        @Test
        @DisplayName("publicRoleId() retourne la représentation String de PUBLIC_UUID")
        void publicRoleId() {
            assertThat(SqlSchemaForApplication.publicRoleId())
                    .isEqualTo(SqlSchemaForApplication.PUBLIC_UUID.toString());
        }
    }
}