package fr.inra.oresing.domain.groovy;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.DataValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs de {@link GroovyContextHelper} — aucun contexte Spring.
 * Couvre la nested record {@link GroovyContextHelper.ReferenceValueDecorator}.
 */
@Tag("domain.model")
@DisplayName("GroovyContextHelper — ReferenceValueDecorator")
class GroovyContextHelperTest {

    // ─────────────────────────────────────────────────────────────────────────
    //  Helper factory
    // ─────────────────────────────────────────────────────────────────────────

    private static DataValue buildDataValue(String naturalKeySql, String hierarchicalKeySql) {
        DataValue dv = new DataValue();
        dv.setNaturalKey(Ltree.fromSql(naturalKeySql));
        dv.setHierarchicalKey(Ltree.fromSql(hierarchicalKeySql));
        DataDatum datum = new DataDatum();
        dv.setRefValues(datum);
        return dv;
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ReferenceValueDecorator
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ReferenceValueDecorator")
    class ReferenceValueDecoratorTest {

        @Test
        @DisplayName("decorated() retourne la DataValue fournie au constructeur")
        void decoratedAccessor() {
            DataValue dv = buildDataValue("nk", "hk");
            GroovyContextHelper.ReferenceValueDecorator decorator =
                    new GroovyContextHelper.ReferenceValueDecorator(dv);
            assertThat(decorator.decorated()).isSameAs(dv);
        }

        @Test
        @DisplayName("getHierarchicalKey() délègue à DataValue.getHierarchicalKey().getSql()")
        void getHierarchicalKey() {
            DataValue dv = buildDataValue("nk", "root.child");
            GroovyContextHelper.ReferenceValueDecorator decorator =
                    new GroovyContextHelper.ReferenceValueDecorator(dv);
            assertThat(decorator.getHierarchicalKey()).isEqualTo("root.child");
        }

        @Test
        @DisplayName("getNaturalKey() délègue à DataValue.getNaturalKey().getSql()")
        void getNaturalKey() {
            DataValue dv = buildDataValue("my_nk", "hk");
            GroovyContextHelper.ReferenceValueDecorator decorator =
                    new GroovyContextHelper.ReferenceValueDecorator(dv);
            assertThat(decorator.getNaturalKey()).isEqualTo("my_nk");
        }

        @Test
        @DisplayName("getRefValues() retourne la map exposée dans le contexte Groovy")
        void getRefValues() {
            DataValue dv = buildDataValue("nk2", "hk2");
            GroovyContextHelper.ReferenceValueDecorator decorator =
                    new GroovyContextHelper.ReferenceValueDecorator(dv);
            Map<String, Object> refValues = decorator.getRefValues();
            assertThat(refValues).isNotNull();
        }

        @Test
        @DisplayName("implémente GroovyDecorator")
        void implementsGroovyDecorator() {
            DataValue dv = buildDataValue("nk", "hk");
            GroovyContextHelper.ReferenceValueDecorator decorator =
                    new GroovyContextHelper.ReferenceValueDecorator(dv);
            assertThat(decorator).isInstanceOf(GroovyDecorator.class);
        }

        @Test
        @DisplayName("deux ReferenceValueDecorator wrappant la même DataValue sont égaux (record)")
        void recordEquality() {
            DataValue dv = buildDataValue("nk", "hk");
            GroovyContextHelper.ReferenceValueDecorator d1 =
                    new GroovyContextHelper.ReferenceValueDecorator(dv);
            GroovyContextHelper.ReferenceValueDecorator d2 =
                    new GroovyContextHelper.ReferenceValueDecorator(dv);
            assertThat(d1).isEqualTo(d2);
        }
    }
}
