package fr.inra.oresing.domain.data.deposit.context.column;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des classes du package domain.data.deposit.context.column.
 * Aucun contexte Spring.
 */
@Tag("domain.model")
@DisplayName("Column context — ContextHeader, AdjacentDescription")
class ColumnContextTest {

    // ─── ContextHeader ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ContextHeader")
    class ContextHeaderTest {

        @Test
        @DisplayName("constructeur canonique stocke les trois champs")
        void canonicalConstructor() {
            List<String> headers = List.of("a", "b", "c");
            ContextHeader h = new ContextHeader(1, "b", headers);
            assertThat(h.columnIndex()).isEqualTo(1);
            assertThat(h.columnHeader()).isEqualTo("b");
            assertThat(h.headersForRow()).isEqualTo(headers);
        }

        @Test
        @DisplayName("constructeur secondaire calcule columnIndex via indexOf")
        void secondaryConstructorComputesIndex() {
            List<String> headers = List.of("x", "y", "z");
            ContextHeader h = new ContextHeader("y", headers);
            assertThat(h.columnIndex()).isEqualTo(1);
            assertThat(h.columnHeader()).isEqualTo("y");
            assertThat(h.headersForRow()).isEqualTo(headers);
        }

        @Test
        @DisplayName("constructeur secondaire : premier élément -> index 0")
        void secondaryConstructorFirstElement() {
            List<String> headers = List.of("alpha", "beta");
            ContextHeader h = new ContextHeader("alpha", headers);
            assertThat(h.columnIndex()).isZero();
        }

        @Test
        @DisplayName("constructeur secondaire : dernier élément -> bon index")
        void secondaryConstructorLastElement() {
            List<String> headers = List.of("a", "b", "c", "d");
            ContextHeader h = new ContextHeader("d", headers);
            assertThat(h.columnIndex()).isEqualTo(3);
        }

        @Test
        @DisplayName("constructeur secondaire : colonne absente -> index -1")
        void secondaryConstructorAbsentColumn() {
            List<String> headers = List.of("a", "b");
            ContextHeader h = new ContextHeader("missing", headers);
            assertThat(h.columnIndex()).isEqualTo(-1);
        }

        @Test
        @DisplayName("record equality")
        void equality() {
            List<String> h = List.of("a", "b");
            ContextHeader x = new ContextHeader("a", h);
            ContextHeader y = new ContextHeader("a", h);
            assertThat(x).isEqualTo(y);
            assertThat(x).hasSameHashCodeAs(y);
        }

        @Test
        @DisplayName("toString() ne lève pas d'exception")
        void toStringDoesNotThrow() {
            List<String> h = List.of("col");
            ContextHeader ctx = new ContextHeader("col", h);
            assertThat(ctx.toString()).isNotNull();
        }
    }

    // ─── AdjacentDescription ─────────────────────────────────────────────────

    @Nested
    @DisplayName("AdjacentDescription")
    class AdjacentDescriptionTest {

        @Test
        @DisplayName("record stocke les quatre champs")
        void fieldsStoredCorrectly() {
            AdjacentDescription desc = new AdjacentDescription(
                    "myComponent",
                    "adjacentCol",
                    ComponentPresenceConstraint.MANDATORY,
                    Multiplicity.ONE);
            assertThat(desc.componentKey()).isEqualTo("myComponent");
            assertThat(desc.adjacentColumnName()).isEqualTo("adjacentCol");
            assertThat(desc.mandatoryForComponentComponent()).isEqualTo(ComponentPresenceConstraint.MANDATORY);
            assertThat(desc.multiplicityForComponentComponent()).isEqualTo(Multiplicity.ONE);
        }

        @Test
        @DisplayName("record equality")
        void equality() {
            AdjacentDescription a = new AdjacentDescription("k", "col",
                    ComponentPresenceConstraint.OPTIONAL, Multiplicity.MANY);
            AdjacentDescription b = new AdjacentDescription("k", "col",
                    ComponentPresenceConstraint.OPTIONAL, Multiplicity.MANY);
            assertThat(a).isEqualTo(b);
            assertThat(a).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("deux AdjacentDescription différentes ne sont pas égales")
        void inequality() {
            AdjacentDescription a = new AdjacentDescription("k1", "col",
                    ComponentPresenceConstraint.OPTIONAL, Multiplicity.ONE);
            AdjacentDescription b = new AdjacentDescription("k2", "col",
                    ComponentPresenceConstraint.OPTIONAL, Multiplicity.ONE);
            assertThat(a).isNotEqualTo(b);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ReferenceStaticColumnDescription
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ReferenceStaticColumnDescription")
    class ReferenceStaticColumnDescriptionTest {

        @Test
        @DisplayName("accesseurs du record retournent les valeurs fournies")
        void accessors() {
            ReferenceStaticColumnDescription desc = new ReferenceStaticColumnDescription(
                    ComponentPresenceConstraint.MANDATORY,
                    Set.of(),
                    CheckerDescription.NO_CHECKER,
                    "headerName"
            );
            assertThat(desc.presenceConstraint()).isEqualTo(ComponentPresenceConstraint.MANDATORY);
            assertThat(desc.tags()).isEmpty();
            assertThat(desc.checker()).isEqualTo(CheckerDescription.NO_CHECKER);
            assertThat(desc.headerName()).isEqualTo("headerName");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ReferenceStaticNotComputedColumnDescription
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ReferenceStaticNotComputedColumnDescription")
    class ReferenceStaticNotComputedColumnDescriptionTest {

        @Test
        @DisplayName("accesseurs du record retournent les valeurs fournies")
        void accessors() {
            ReferenceStaticNotComputedColumnDescription desc = new ReferenceStaticNotComputedColumnDescription(
                    ComponentPresenceConstraint.OPTIONAL,
                    Set.of(),
                    CheckerDescription.NO_CHECKER,
                    "col_header",
                    null
            );
            assertThat(desc.presenceConstraint()).isEqualTo(ComponentPresenceConstraint.OPTIONAL);
            assertThat(desc.headerName()).isEqualTo("col_header");
            assertThat(desc.defaultValue()).isNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ReferenceStaticComputedColumnDescription
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ReferenceStaticComputedColumnDescription")
    class ReferenceStaticComputedColumnDescriptionTest {

        @Test
        @DisplayName("accesseurs du record retournent les valeurs fournies")
        void accessors() {
            ReferenceStaticComputedColumnDescription desc = new ReferenceStaticComputedColumnDescription(
                    ComponentPresenceConstraint.OPTIONAL,
                    Set.of(),
                    CheckerDescription.NO_CHECKER,
                    "computed_col",
                    null
            );
            assertThat(desc.presenceConstraint()).isEqualTo(ComponentPresenceConstraint.OPTIONAL);
            assertThat(desc.headerName()).isEqualTo("computed_col");
            assertThat(desc.computation()).isNull();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ReferenceDynamicColumnDescription
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ReferenceDynamicColumnDescription")
    class ReferenceDynamicColumnDescriptionTest {

        @Test
        @DisplayName("accesseurs du record retournent les valeurs fournies")
        void accessors() {
            ReferenceDynamicColumnDescription desc = new ReferenceDynamicColumnDescription(
                    ComponentPresenceConstraint.OPTIONAL,
                    Set.of(),
                    Map.of("fr", "Dynamique"),
                    "prefix_",
                    "refType",
                    "lookupCol"
            );
            assertThat(desc.presenceConstraint()).isEqualTo(ComponentPresenceConstraint.OPTIONAL);
            assertThat(desc.internationalizationName()).containsEntry("fr", "Dynamique");
            assertThat(desc.headerPrefix()).isEqualTo("prefix_");
            assertThat(desc.reference()).isEqualTo("refType");
            assertThat(desc.referenceColumnToLookForHeader()).isEqualTo("lookupCol");
        }
    }
}
