package fr.inra.oresing.rest.model.data.query;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires purs des enums et records légers du package rest.model.data.query.
 * Aucun contexte Spring.
 */
@Tag("domain.model")
class DataQueryModelTest {

    // ------------------------------------------------------------------ //
    //  FieldType enum                                                      //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("FieldType enum")
    class FieldTypeTest {

        @Test
        void allValues() {
            assertThat(FieldType.values()).containsExactlyInAnyOrder(
                    FieldType.date, FieldType.time, FieldType.datetime,
                    FieldType.numeric, FieldType.bool, FieldType.reference);
        }

        @Test
        void valueOf() {
            assertThat(FieldType.valueOf("date")).isEqualTo(FieldType.date);
            assertThat(FieldType.valueOf("numeric")).isEqualTo(FieldType.numeric);
        }
    }

    // ------------------------------------------------------------------ //
    //  FieldTypeForInterval enum                                           //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("FieldTypeForInterval enum")
    class FieldTypeForIntervalTest {

        @Test
        void allValues() {
            assertThat(FieldTypeForInterval.values()).containsExactlyInAnyOrder(
                    FieldTypeForInterval.date, FieldTypeForInterval.time,
                    FieldTypeForInterval.datetime, FieldTypeForInterval.numeric);
        }
    }

    // ------------------------------------------------------------------ //
    //  IntervalValues                                                      //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("IntervalValues")
    class IntervalValuesTest {

        @Test
        void defaultConstructor() {
            IntervalValues iv = new IntervalValues();
            assertThat(iv.getFrom()).isNull();
            assertThat(iv.getTo()).isNull();
        }

        @Test
        void twoArgConstructor() {
            IntervalValues iv = new IntervalValues("2024-01-01", "2024-12-31");
            assertThat(iv.getFrom()).isEqualTo("2024-01-01");
            assertThat(iv.getTo()).isEqualTo("2024-12-31");
        }

        @Test
        void settersWork() {
            IntervalValues iv = new IntervalValues();
            iv.setFrom("a");
            iv.setTo("b");
            assertThat(iv.getFrom()).isEqualTo("a");
            assertThat(iv.getTo()).isEqualTo("b");
        }
    }

    // ------------------------------------------------------------------ //
    //  ComponentOrderBy                                                    //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("ComponentOrderBy")
    class ComponentOrderByTest {

        @Test
        void defaultConstructor() {
            ComponentOrderBy ob = new ComponentOrderBy();
            assertThat(ob.getComponentKey()).isNull();
        }

        @Test
        void twoArgConstructor() {
            ComponentOrderBy ob = new ComponentOrderBy(
                    "myKey", fr.inra.oresing.domain.repository.data.DataRepository.Order.ASC);
            assertThat(ob.getComponentKey()).isEqualTo("myKey");
            assertThat(ob.order).isEqualTo(fr.inra.oresing.domain.repository.data.DataRepository.Order.ASC);
        }
    }

    // ------------------------------------------------------------------ //
    //  AuthorizationDescription                                            //
    // ------------------------------------------------------------------ //

    @Nested
    @DisplayName("AuthorizationDescription")
    class AuthorizationDescriptionTest {

        @Test
        void constructorAndGetters() {
            IntervalValues ts = new IntervalValues("2020-01-01", "2020-12-31");
            AuthorizationDescription ad = new AuthorizationDescription(ts, java.util.Map.of());
            assertThat(ad.getTimeScope()).isSameAs(ts);
            assertThat(ad.getRequiredAuthorizations()).isEmpty();
        }

        @Test
        void setters() {
            AuthorizationDescription ad = new AuthorizationDescription(null, null);
            IntervalValues ts = new IntervalValues("a", "b");
            ad.setTimeScope(ts);
            assertThat(ad.getTimeScope()).isSameAs(ts);
        }
    }
}