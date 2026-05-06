package fr.inra.oresing.persistence.requestbuilder.data;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.persistence.DataRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour les inner classes de SelectRequest et DeleteRequest.
 */
@Tag("core.config")
@DisplayName("SelectRequest / DeleteRequest inner classes – tests unitaires")
class SelectDeleteRequestInnerClassesTest {

    // =========================================================================
    //  SelectRequest.SelectRequestOffset
    // =========================================================================

    @Nested
    @DisplayName("SelectRequestOffset")
    class SelectRequestOffsetTest {

        @Test
        @DisplayName("build() retourne OFFSET N quand offset >= 0")
        void buildWithOffset() {
            OutPut outPut = new OutPut(Locale.FRANCE, 10L, 50L);
            SelectRequest.SelectRequestOffset offset = new SelectRequest.SelectRequestOffset(outPut);
            String result = offset.build();
            assertThat(result).contains("OFFSET");
            assertThat(result).contains("10");
        }

        @Test
        @DisplayName("build() retourne chaîne vide quand offset < 0")
        void buildWithNegativeOffset() {
            OutPut outPut = new OutPut(Locale.FRANCE, -1L, 50L);
            SelectRequest.SelectRequestOffset offset = new SelectRequest.SelectRequestOffset(outPut);
            assertThat(offset.build()).isEmpty();
        }
    }

    // =========================================================================
    //  SelectRequest.SelectRequestLimit
    // =========================================================================

    @Nested
    @DisplayName("SelectRequestLimit")
    class SelectRequestLimitTest {

        @Test
        @DisplayName("build() retourne LIMIT N quand limit >= 0")
        void buildWithLimit() {
            OutPut outPut = new OutPut(Locale.FRANCE, 0L, 100L);
            SelectRequest.SelectRequestLimit limit = new SelectRequest.SelectRequestLimit(outPut);
            String result = limit.build();
            assertThat(result).contains("LIMIT");
            assertThat(result).contains("100");
        }

        @Test
        @DisplayName("build() retourne chaîne vide quand limit est null")
        void buildWithNullLimit() {
            OutPut outPut = new OutPut(Locale.FRANCE, 0L, null);
            SelectRequest.SelectRequestLimit limit = new SelectRequest.SelectRequestLimit(outPut);
            assertThat(limit.build()).isEmpty();
        }

        @Test
        @DisplayName("build() retourne chaîne vide quand limit < 0")
        void buildWithNegativeLimit() {
            OutPut outPut = new OutPut(Locale.FRANCE, 0L, -1L);
            SelectRequest.SelectRequestLimit limit = new SelectRequest.SelectRequestLimit(outPut);
            assertThat(limit.build()).isEmpty();
        }
    }

    // =========================================================================
    //  SelectRequest.SelectRequestOrderBy
    // =========================================================================

    @Nested
    @DisplayName("SelectRequestOrderBy")
    class SelectRequestOrderByTest {

        @Test
        @DisplayName("build() retourne chaîne vide quand componentOrderBy est vide")
        void buildEmptyOrderBy() {
            SelectRequest.SelectRequestOrderBy orderBy = new SelectRequest.SelectRequestOrderBy(Set.of());
            assertThat(orderBy.build()).isEmpty();
        }

        @Test
        @DisplayName("build() retourne ORDER BY quand componentOrderBy est rempli (ComponentTextType)")
        void buildOrderByWithText() {
            ComponentOrderBy componentOrderBy = new ComponentOrderBy(
                    "myCol",
                    DataRepository.Order.ASC,
                    new ComponentTextType()
            );
            SelectRequest.SelectRequestOrderBy orderBy = new SelectRequest.SelectRequestOrderBy(Set.of(componentOrderBy));
            String result = orderBy.build();
            assertThat(result).contains("ORDER BY");
            assertThat(result).contains("myCol");
            assertThat(result).contains("TEXT");
        }

        @Test
        @DisplayName("build() retourne ORDER BY pour ComponentNumericType")
        void buildOrderByWithNumeric() {
            ComponentOrderBy componentOrderBy = new ComponentOrderBy(
                    "numCol",
                    DataRepository.Order.DESC,
                    new ComponentNumericType()
            );
            SelectRequest.SelectRequestOrderBy orderBy = new SelectRequest.SelectRequestOrderBy(Set.of(componentOrderBy));
            String result = orderBy.build();
            assertThat(result).contains("NUMERIC");
        }

        @Test
        @DisplayName("build() retourne ORDER BY pour ComponentDateType")
        void buildOrderByWithDate() {
            ComponentOrderBy componentOrderBy = new ComponentOrderBy(
                    "dateCol",
                    DataRepository.Order.ASC,
                    new ComponentDateType("yyyy-MM-dd", DownloadDatasetQueryAdvancedSearch.FieldType.date)
            );
            SelectRequest.SelectRequestOrderBy orderBy = new SelectRequest.SelectRequestOrderBy(Set.of(componentOrderBy));
            String result = orderBy.build();
            assertThat(result).contains("TIMESTAMP");
        }

        @Test
        @DisplayName("build() retourne ORDER BY pour ComponentReferenceType")
        void buildOrderByWithReference() {
            ComponentOrderBy componentOrderBy = new ComponentOrderBy(
                    "refCol",
                    DataRepository.Order.ASC,
                    new ComponentReferenceType()
            );
            SelectRequest.SelectRequestOrderBy orderBy = new SelectRequest.SelectRequestOrderBy(Set.of(componentOrderBy));
            String result = orderBy.build();
            assertThat(result).contains("LTREE");
        }

        @Test
        @DisplayName("build() retourne ORDER BY pour ComponentBooleanType")
        void buildOrderByWithBoolean() {
            ComponentOrderBy componentOrderBy = new ComponentOrderBy(
                    "boolCol",
                    DataRepository.Order.ASC,
                    new ComponentBooleanType()
            );
            SelectRequest.SelectRequestOrderBy orderBy = new SelectRequest.SelectRequestOrderBy(Set.of(componentOrderBy));
            String result = orderBy.build();
            assertThat(result).contains("BOOL");
        }
    }

    // =========================================================================
    //  OutPut
    // =========================================================================

    @Nested
    @DisplayName("OutPut")
    class OutPutTest {

        @Test
        @DisplayName("constructeur avec null locale utilise FRANCE")
        void constructorNullLocale() {
            OutPut outPut = new OutPut(null, 0L, 10L);
            assertThat(outPut.locale()).isEqualTo(Locale.FRANCE);
        }

        @Test
        @DisplayName("constructeur avec null offset utilise 0")
        void constructorNullOffset() {
            OutPut outPut = new OutPut(Locale.FRANCE, null, 10L);
            assertThat(outPut.offset()).isEqualTo(0L);
        }
    }

    // =========================================================================
    //  DeleteRequest inner classes
    // =========================================================================

    @Nested
    @DisplayName("DeleteRequest.SelectRequestOffset")
    class DeleteRequestOffsetTest {

        @Test
        @DisplayName("build() retourne OFFSET N quand offset >= 0")
        void buildWithOffset() {
            OutPut outPut = new OutPut(Locale.FRANCE, 5L, 20L);
            DeleteRequest.SelectRequestOffset offset = new DeleteRequest.SelectRequestOffset(outPut);
            String result = offset.build();
            assertThat(result).contains("OFFSET");
            assertThat(result).contains("5");
        }

        @Test
        @DisplayName("build() retourne chaîne vide quand offset < 0")
        void buildWithNegativeOffset() {
            OutPut outPut = new OutPut(Locale.FRANCE, -1L, 20L);
            DeleteRequest.SelectRequestOffset offset = new DeleteRequest.SelectRequestOffset(outPut);
            assertThat(offset.build()).isEmpty();
        }
    }

    @Nested
    @DisplayName("DeleteRequest.SelectRequestLimit")
    class DeleteRequestLimitTest {

        @Test
        @DisplayName("build() retourne OFFSET N quand limit >= 0")
        void buildWithLimit() {
            OutPut outPut = new OutPut(Locale.FRANCE, 0L, 50L);
            DeleteRequest.SelectRequestLimit limit = new DeleteRequest.SelectRequestLimit(outPut);
            String result = limit.build();
            assertThat(result).contains("OFFSET");
            assertThat(result).contains("50");
        }

        @Test
        @DisplayName("build() retourne chaîne vide quand limit est null")
        void buildWithNullLimit() {
            OutPut outPut = new OutPut(Locale.FRANCE, 0L, null);
            DeleteRequest.SelectRequestLimit limit = new DeleteRequest.SelectRequestLimit(outPut);
            assertThat(limit.build()).isEmpty();
        }
    }

    @Nested
    @DisplayName("DeleteRequest.SelectRequestOrderBy")
    class DeleteRequestOrderByTest {

        @Test
        @DisplayName("build() retourne chaîne vide quand componentOrderBy est vide")
        void buildEmpty() {
            DeleteRequest.SelectRequestOrderBy orderBy = new DeleteRequest.SelectRequestOrderBy(Set.of());
            assertThat(orderBy.build()).isEmpty();
        }

        @Test
        @DisplayName("build() retourne la colonne avec cast TEXT")
        void buildWithText() {
            ComponentOrderBy componentOrderBy = new ComponentOrderBy(
                    "colA", DataRepository.Order.ASC, new ComponentTextType());
            DeleteRequest.SelectRequestOrderBy orderBy = new DeleteRequest.SelectRequestOrderBy(Set.of(componentOrderBy));
            String result = orderBy.build();
            assertThat(result).contains("colA");
            assertThat(result).contains("TEXT");
        }
    }
}