package fr.inra.oresing.domain.data.deposit.context.column;

import com.google.common.collect.ImmutableList;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.checker.Multiplicity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

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
            ImmutableList<String> headers = ImmutableList.of("a", "b", "c");
            ContextHeader h = new ContextHeader(1, "b", headers);
            assertThat(h.columnIndex()).isEqualTo(1);
            assertThat(h.columnHeader()).isEqualTo("b");
            assertThat(h.headersForRow()).isEqualTo(headers);
        }

        @Test
        @DisplayName("constructeur secondaire calcule columnIndex via indexOf")
        void secondaryConstructorComputesIndex() {
            ImmutableList<String> headers = ImmutableList.of("x", "y", "z");
            ContextHeader h = new ContextHeader("y", headers);
            assertThat(h.columnIndex()).isEqualTo(1);
            assertThat(h.columnHeader()).isEqualTo("y");
            assertThat(h.headersForRow()).isEqualTo(headers);
        }

        @Test
        @DisplayName("constructeur secondaire : premier élément -> index 0")
        void secondaryConstructorFirstElement() {
            ImmutableList<String> headers = ImmutableList.of("alpha", "beta");
            ContextHeader h = new ContextHeader("alpha", headers);
            assertThat(h.columnIndex()).isEqualTo(0);
        }

        @Test
        @DisplayName("constructeur secondaire : dernier élément -> bon index")
        void secondaryConstructorLastElement() {
            ImmutableList<String> headers = ImmutableList.of("a", "b", "c", "d");
            ContextHeader h = new ContextHeader("d", headers);
            assertThat(h.columnIndex()).isEqualTo(3);
        }

        @Test
        @DisplayName("constructeur secondaire : colonne absente -> index -1")
        void secondaryConstructorAbsentColumn() {
            ImmutableList<String> headers = ImmutableList.of("a", "b");
            ContextHeader h = new ContextHeader("missing", headers);
            assertThat(h.columnIndex()).isEqualTo(-1);
        }

        @Test
        @DisplayName("record equality")
        void equality() {
            ImmutableList<String> h = ImmutableList.of("a", "b");
            ContextHeader x = new ContextHeader("a", h);
            ContextHeader y = new ContextHeader("a", h);
            assertThat(x).isEqualTo(y);
            assertThat(x.hashCode()).isEqualTo(y.hashCode());
        }

        @Test
        @DisplayName("toString() ne lève pas d'exception")
        void toStringDoesNotThrow() {
            ImmutableList<String> h = ImmutableList.of("col");
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
            assertThat(a.hashCode()).isEqualTo(b.hashCode());
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
}
