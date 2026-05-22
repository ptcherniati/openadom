package fr.inra.oresing.rest.model.data.query;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.BasicComponent;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.checker.*;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static fr.inra.oresing.domain.application.configuration.checker.CheckerDescription.CheckerDescriptionType.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@DisplayName("ComponentFilters et ComponentType")
@Tag("domain.model")
class ComponentFiltersAndTypeTest {
    private static ComponentDescription componentDescWith(String componentKey, CheckerDescription checker) {
        return new BasicComponent(
                ComponentDescription.ComponentDescriptionType.BasicComponent,
                componentKey, null, Set.of(), "", "", List.of(), false,
                ComponentPresenceConstraint.OPTIONAL, checker, null
        );
    }
    private static StandardDataDescription dataDescWith(String componentKey, CheckerDescription checker) {
        ComponentDescription compDesc = componentDescWith(componentKey, checker);
        StandardDataDescription dataDesc = Mockito.mock(StandardDataDescription.class);
        when(dataDesc.componentDescriptions()).thenReturn(Map.of(componentKey, compDesc));
        return dataDesc;
    }
    private static ComponentFilters filterWith(String key, List<String> filters) {
        return new ComponentFilters(key, filters, null, false);
    }
    private static ComponentFilters intervalFilterWith(String key, List<IntervalValues> intervals) {
        return new ComponentFilters(key, null, intervals, false);
    }
    @Nested @DisplayName("Erreurs de validation")
    class BuildErrorsTest {
        @Test @DisplayName("null componentFilter → BadDownloadDatasetQuery")
        void nullThrows() {
            assertThatThrownBy(() -> ComponentFilters.build((ComponentFilters)null, Mockito.mock(StandardDataDescription.class)))
                    .isInstanceOf(BadDownloadDatasetQuery.class);
        }
        @Test @DisplayName("componentKey vide → BadDownloadDatasetQuery")
        void emptyKeyThrows() {
            assertThatThrownBy(() -> ComponentFilters.build(new ComponentFilters("", null, null, false), Mockito.mock(StandardDataDescription.class)))
                    .isInstanceOf(BadDownloadDatasetQuery.class);
        }
        @Test @DisplayName("aucun filtre ni intervalle → BadDownloadDatasetQuery")
        void noFilterNoIntervalThrows() {
            assertThatThrownBy(() -> ComponentFilters.build(filterWith("k", null), dataDescWith("k", new IntegerChecker(IntegerChecker, Multiplicity.ONE, false, null, null))))
                    .isInstanceOf(BadDownloadDatasetQuery.class);
        }
    }
    @Nested @DisplayName("Filtres par valeur")
    class BuildFiltersTest {
        @Test @DisplayName("IntegerChecker → ComponentFiltersByNumeric")
        void integer() {
            assertThat(ComponentFilters.build(filterWith("c", List.of("1")), dataDescWith("c", new IntegerChecker(IntegerChecker, Multiplicity.ONE, false, null, null))))
                    .isInstanceOf(ComponentFiltersByNumeric.class);
        }
        @Test @DisplayName("FloatChecker → ComponentFiltersByNumeric")
        void floating() {
            assertThat(ComponentFilters.build(filterWith("c", List.of("1.5")), dataDescWith("c", new FloatChecker(FloatChecker, Multiplicity.ONE, false, null, null))))
                    .isInstanceOf(ComponentFiltersByNumeric.class);
        }
        @Test @DisplayName("ReferenceChecker → ComponentFiltersByReference")
        void reference() {
            assertThat(ComponentFilters.build(filterWith("c", List.of("r")), dataDescWith("c", new ReferenceChecker(ReferenceChecker, null, Multiplicity.ONE, false, "ref", false, false))))
                    .isInstanceOf(ComponentFiltersByReference.class);
        }
        @Test @DisplayName("NO_CHECKER + isRegExp=false → PlainText")
        void plainText() {
            assertThat(ComponentFilters.build(new ComponentFilters("c", List.of("t"), null, false), dataDescWith("c", CheckerDescription.NO_CHECKER)))
                    .isInstanceOf(ComponentFiltersForWordByPlainText.class);
        }
        @Test @DisplayName("NO_CHECKER + isRegExp=true → Regexp")
        void regexp() {
            assertThat(ComponentFilters.build(new ComponentFilters("c", List.of("t"), null, true), dataDescWith("c", CheckerDescription.NO_CHECKER)))
                    .isInstanceOf(ComponentFiltersForWordByRegexp.class);
        }
        @Test @DisplayName("BooleanChecker + intervalValues → ComponentFiltersByBoolean")
        void boolInterval() {
            assertThat(ComponentFilters.build(intervalFilterWith("c", List.of(new IntervalValues("true", null))), dataDescWith("c", new BooleanChecker(BooleanChecker, Multiplicity.ONE, false, false))))
                    .isInstanceOf(ComponentFiltersByBoolean.class);
        }
        @Test @DisplayName("FloatChecker + intervalValues → IntervalByNumeric")
        void floatInterval() {
            assertThat(ComponentFilters.build(intervalFilterWith("c", List.of(new IntervalValues("1", "5"))), dataDescWith("c", new FloatChecker(FloatChecker, Multiplicity.ONE, false, null, null))))
                    .isInstanceOf(ComponentFiltersForIntervalByNumeric.class);
        }
        @Test @DisplayName("IntegerChecker + intervalValues → IntervalByNumeric")
        void intInterval() {
            assertThat(ComponentFilters.build(intervalFilterWith("c", List.of(new IntervalValues("1", "5"))), dataDescWith("c", new IntegerChecker(IntegerChecker, Multiplicity.ONE, false, null, null))))
                    .isInstanceOf(ComponentFiltersForIntervalByNumeric.class);
        }
    }
    @Nested @DisplayName("Set de ComponentFilters")
    class BuildSetTest {
        @Test @DisplayName("Set vide → NoComponentFilters")
        void emptySet() {
            var r = ComponentFilters.build(Set.of(), null);
            assertThat(r).hasSize(1);
            assertThat(r.iterator().next()).isInstanceOf(NoComponentFilters.class);
        }
        @Test @DisplayName("Set null → NoComponentFilters")
        void nullSet() {
            var r = ComponentFilters.build((Set<ComponentFilters>) null, null);
            assertThat(r).hasSize(1);
            assertThat(r.iterator().next()).isInstanceOf(NoComponentFilters.class);
        }
        @Test @DisplayName("Set avec un élément → build l'élément")
        void singleElement() {
            var r = ComponentFilters.build(Set.of(filterWith("c", List.of("v"))), dataDescWith("c", new IntegerChecker(IntegerChecker, Multiplicity.ONE, false, null, null)));
            assertThat(r).hasSize(1).first().isInstanceOf(ComponentFiltersByNumeric.class);
        }
    }
    @Nested @DisplayName("DateChecker + intervalValues")
    class DateIntervalTest {
        @Test @DisplayName("yyyy-MM-dd → IntervalByDate")
        void date() {
            assertThat(ComponentFilters.build(intervalFilterWith("c", List.of(new IntervalValues("2022-01-01", "2022-12-31"))), dataDescWith("c", new DateChecker(DateChecker, Multiplicity.ONE, false, "yyyy-MM-dd", null, null, null))))
                    .isInstanceOf(ComponentFiltersForIntervalByDate.class);
        }
        @Test @DisplayName("HH:mm:ss → IntervalByTime")
        void time() {
            assertThat(ComponentFilters.build(intervalFilterWith("c", List.of(new IntervalValues("08:00:00", "18:00:00"))), dataDescWith("c", new DateChecker(DateChecker, Multiplicity.ONE, false, "HH:mm:ss", null, null, null))))
                    .isInstanceOf(ComponentFiltersForIntervalByTime.class);
        }
        @Test @DisplayName("yyyy-MM-dd HH:mm:ss → IntervalByDateTime")
        void datetime() {
            assertThat(ComponentFilters.build(intervalFilterWith("c", List.of(new IntervalValues("2022-01-01 00:00:00", "2022-12-31 23:59:59"))), dataDescWith("c", new DateChecker(DateChecker, Multiplicity.ONE, false, "yyyy-MM-dd HH:mm:ss", null, null, null))))
                    .isInstanceOf(ComponentFiltersForIntervalByDateTime.class);
        }
    }
    @Nested @DisplayName("DateChecker + filtres valeurs")
    class DateFilterValueTest {
        @Test @DisplayName("yyyy-MM-dd → FiltersByDate")
        void date() {
            assertThat(ComponentFilters.build(filterWith("c", List.of("2022-06-15")), dataDescWith("c", new DateChecker(DateChecker, Multiplicity.ONE, false, "yyyy-MM-dd", null, null, null))))
                    .isInstanceOf(ComponentFiltersByDate.class);
        }
        @Test @DisplayName("HH:mm:ss → FiltersByTime")
        void time() {
            assertThat(ComponentFilters.build(filterWith("c", List.of("12:00:00")), dataDescWith("c", new DateChecker(DateChecker, Multiplicity.ONE, false, "HH:mm:ss", null, null, null))))
                    .isInstanceOf(ComponentFiltersByTime.class);
        }
        @Test @DisplayName("yyyy-MM-dd HH:mm:ss → FiltersByDateTime")
        void datetime() {
            assertThat(ComponentFilters.build(filterWith("c", List.of("2022-06-15 12:00:00")), dataDescWith("c", new DateChecker(DateChecker, Multiplicity.ONE, false, "yyyy-MM-dd HH:mm:ss", null, null, null))))
                    .isInstanceOf(ComponentFiltersByDateTime.class);
        }
    }
    @Nested @DisplayName("ComponentType.getComponentType()")
    class ComponentTypeGetTest {
        private ComponentDescription cdWith(CheckerDescription checker) { return componentDescWith("k", checker); }
        @Test @DisplayName("null → ComponentTextType")
        void nullDesc() { assertThat(ComponentType.getComponentType(null)).isInstanceOf(fr.inra.oresing.domain.data.read.query.ComponentTextType.class); }
        @Test @DisplayName("checker null → ComponentTextType")
        void nullChecker() { assertThat(ComponentType.getComponentType(cdWith(null))).isInstanceOf(fr.inra.oresing.domain.data.read.query.ComponentTextType.class); }
        @Test @DisplayName("ReferenceChecker → ComponentReferenceType")
        void refChecker() { assertThat(ComponentType.getComponentType(cdWith(new ReferenceChecker(ReferenceChecker, null, Multiplicity.ONE, false, "r", false, false)))).isInstanceOf(fr.inra.oresing.domain.data.read.query.ComponentReferenceType.class); }
        @Test @DisplayName("IntegerChecker → ComponentNumericType")
        void intChecker() { assertThat(ComponentType.getComponentType(cdWith(new IntegerChecker(IntegerChecker, Multiplicity.ONE, false, null, null)))).isInstanceOf(fr.inra.oresing.domain.data.read.query.ComponentNumericType.class); }
        @Test @DisplayName("FloatChecker → ComponentNumericType")
        void floatChecker() { assertThat(ComponentType.getComponentType(cdWith(new FloatChecker(FloatChecker, Multiplicity.ONE, false, null, null)))).isInstanceOf(fr.inra.oresing.domain.data.read.query.ComponentNumericType.class); }
        @Test @DisplayName("BooleanChecker → ComponentBooleanType")
        void boolChecker() { assertThat(ComponentType.getComponentType(cdWith(new BooleanChecker(BooleanChecker, Multiplicity.ONE, false, false)))).isInstanceOf(fr.inra.oresing.domain.data.read.query.ComponentBooleanType.class); }
        @Test @DisplayName("DateChecker(yyyy-MM-dd) → ComponentDateType date")
        void dateCheckerDate() {
            var r = ComponentType.getComponentType(cdWith(new DateChecker(DateChecker, Multiplicity.ONE, false, "yyyy-MM-dd", null, null, null)));
            assertThat(r).isInstanceOf(fr.inra.oresing.domain.data.read.query.ComponentDateType.class);
            assertThat(((fr.inra.oresing.domain.data.read.query.ComponentDateType) r).fieldType()).isEqualTo(fr.inra.oresing.domain.data.read.query.DownloadDatasetQueryAdvancedSearch.FieldType.date);
        }
        @Test @DisplayName("DateChecker(HH:mm:ss) → ComponentDateType time")
        void dateCheckerTime() {
            var r = ComponentType.getComponentType(cdWith(new DateChecker(DateChecker, Multiplicity.ONE, false, "HH:mm:ss", null, null, null)));
            assertThat(r).isInstanceOf(fr.inra.oresing.domain.data.read.query.ComponentDateType.class);
            assertThat(((fr.inra.oresing.domain.data.read.query.ComponentDateType) r).fieldType()).isEqualTo(fr.inra.oresing.domain.data.read.query.DownloadDatasetQueryAdvancedSearch.FieldType.time);
        }
        @Test @DisplayName("DateChecker(yyyy-MM-dd HH:mm:ss) → ComponentDateType datetime")
        void dateCheckerDateTime() {
            var r = ComponentType.getComponentType(cdWith(new DateChecker(DateChecker, Multiplicity.ONE, false, "yyyy-MM-dd HH:mm:ss", null, null, null)));
            assertThat(r).isInstanceOf(fr.inra.oresing.domain.data.read.query.ComponentDateType.class);
            assertThat(((fr.inra.oresing.domain.data.read.query.ComponentDateType) r).fieldType()).isEqualTo(fr.inra.oresing.domain.data.read.query.DownloadDatasetQueryAdvancedSearch.FieldType.datetime);
        }
        @Test @DisplayName("StringChecker → ComponentTextType")
        void strChecker() { assertThat(ComponentType.getComponentType(cdWith(CheckerDescription.NO_CHECKER))).isInstanceOf(fr.inra.oresing.domain.data.read.query.ComponentTextType.class); }
        @Test @DisplayName("ComponentTextType equals")
        void textTypeEquals() { assertThat(new ComponentType.ComponentTextType()).isEqualTo(new ComponentType.ComponentTextType()); }
        @Test @DisplayName("ComponentReferenceType equals")
        void refTypeEquals() { assertThat(new ComponentType.ComponentReferenceType()).isEqualTo(new ComponentType.ComponentReferenceType()); }
        @Test @DisplayName("ComponentBooleanType equals")
        void boolTypeEquals() { assertThat(new ComponentType.ComponentBooleanType()).isEqualTo(new ComponentType.ComponentBooleanType()); }
        @Test @DisplayName("ComponentNumericType equals")
        void numTypeEquals() { assertThat(new ComponentType.ComponentNumericType()).isEqualTo(new ComponentType.ComponentNumericType()); }
        @Test @DisplayName("ComponentDateType format et fieldType")
        void dateTypeFields() {
            var d = new ComponentType.ComponentDateType("yyyy", fr.inra.oresing.domain.data.read.query.DownloadDatasetQueryAdvancedSearch.FieldType.date);
            assertThat(d.format()).isEqualTo("yyyy");
            assertThat(d.fieldType()).isEqualTo(fr.inra.oresing.domain.data.read.query.DownloadDatasetQueryAdvancedSearch.FieldType.date);
        }
    }
}