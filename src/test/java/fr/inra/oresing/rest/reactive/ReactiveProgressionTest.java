package fr.inra.oresing.rest.reactive;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Tests unitaires purs de {@link ReactiveProgression} — aucun contexte Spring.
 * Couvre : DefaultCounter, ProgressionMessagesLabel, CreateApplicationProgression,
 * ChangeApplicationProgression, GetApplicationProgression.
 */
@Tag("domain.model")
@DisplayName("ReactiveProgression — records et ProgressionMessagesLabel")
class ReactiveProgressionTest {

    // ─────────────────────────────────────────────────────────────────────────
    //  DefaultCounter
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DefaultCounter")
    class DefaultCounterTest {

        @Test
        @DisplayName("progress() retourne la valeur fournie")
        void progressReturnsValue() {
            ReactiveProgression.DefaultCounter counter = new ReactiveProgression.DefaultCounter(0.5);
            assertThat(counter.progress()).isEqualTo(0.5);
        }

        @Test
        @DisplayName("progress = 0 est valide")
        void zeroIsValid() {
            ReactiveProgression.DefaultCounter counter = new ReactiveProgression.DefaultCounter(0);
            assertThat(counter.progress()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("progress = 100 est valide")
        void hundredIsValid() {
            ReactiveProgression.DefaultCounter counter = new ReactiveProgression.DefaultCounter(100);
            assertThat(counter.progress()).isEqualTo(100.0);
        }

        @Test
        @DisplayName("progress < 0 lève IllegalArgumentException")
        void negativeThrows() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ReactiveProgression.DefaultCounter(-0.1))
                    .withMessageContaining("progress is between 0 and 100");
        }

        @Test
        @DisplayName("progress > 100 lève IllegalArgumentException")
        void greaterThan100Throws() {
            assertThatIllegalArgumentException()
                    .isThrownBy(() -> new ReactiveProgression.DefaultCounter(100.1));
        }

        @Test
        @DisplayName("constante PROGRESSION_FOR_READING_CONFIGURATION dans CreateApplicationProgression")
        void createProgressionConstant() {
            ReactiveProgression.DefaultCounter constant = ReactiveProgression.CreateApplicationProgression.PROGRESSION_FOR_READING_CONFIGURATION;
            assertThat(constant.progress()).isNotNegative().isLessThanOrEqualTo(100);
        }

        @Test
        @DisplayName("constante PROGRESSION_FOR_READING_CONFIGURATION dans ChangeApplicationProgression")
        void changeProgressionConstant() {
            ReactiveProgression.DefaultCounter constant = ReactiveProgression.ChangeApplicationProgression.PROGRESSION_FOR_READING_CONFIGURATION;
            assertThat(constant.progress()).isNotNegative().isLessThanOrEqualTo(100);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  CreateApplicationProgressionMessagesLabel
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("CreateApplicationProgressionMessagesLabel")
    class CreateApplicationProgressionMessagesLabelTest {

        @Test
        @DisplayName("constructeur sans argument utilise un label par défaut")
        void defaultLabel() {
            ReactiveProgression.CreateApplicationProgressionMessagesLabel label =
                    new ReactiveProgression.CreateApplicationProgressionMessagesLabel();
            assertThat(label.label()).isNotBlank();
        }

        @Test
        @DisplayName("constructeur avec argument stocke le label")
        void customLabel() {
            ReactiveProgression.CreateApplicationProgressionMessagesLabel label =
                    new ReactiveProgression.CreateApplicationProgressionMessagesLabel("my.label");
            assertThat(label.label()).isEqualTo("my.label");
        }

        @Test
        @DisplayName("withSubLabel() compose le label avec un point")
        void withSubLabel() {
            ReactiveProgression.CreateApplicationProgressionMessagesLabel label =
                    new ReactiveProgression.CreateApplicationProgressionMessagesLabel("parent");
            ReactiveProgression.CreateApplicationProgressionMessagesLabel composed = label.withSubLabel("child");
            assertThat(composed.label()).isEqualTo("parent.child");
        }

        @Test
        @DisplayName("up() supprime tous les segments après le premier (regex \\.[^\\.]*)")
        void up() {
            // regex "\\.[^\\.]*" removes ALL ".something" segments
            ReactiveProgression.CreateApplicationProgressionMessagesLabel label =
                    new ReactiveProgression.CreateApplicationProgressionMessagesLabel("a.b.c");
            ReactiveProgression.CreateApplicationProgressionMessagesLabel parent = label.up();
            assertThat(parent.label()).isEqualTo("a");
        }

        @Test
        @DisplayName("newProgressionMessageLabel retourne une instance avec le label fourni")
        void newLabel() {
            ReactiveProgression.CreateApplicationProgressionMessagesLabel label =
                    new ReactiveProgression.CreateApplicationProgressionMessagesLabel();
            ReactiveProgression.CreateApplicationProgressionMessagesLabel newLabel = label.newProgressionMessageLabel("x.y");
            assertThat(newLabel.label()).isEqualTo("x.y");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ChangeApplicationProgressionMessagesLabel
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ChangeApplicationProgressionMessagesLabel")
    class ChangeApplicationProgressionMessagesLabelTest {

        @Test
        @DisplayName("constructeur sans argument utilise un label par défaut")
        void defaultLabel() {
            ReactiveProgression.ChangeApplicationProgressionMessagesLabel label =
                    new ReactiveProgression.ChangeApplicationProgressionMessagesLabel();
            assertThat(label.label()).isNotBlank();
        }

        @Test
        @DisplayName("withSubLabel() compose le label")
        void withSubLabel() {
            ReactiveProgression.ChangeApplicationProgressionMessagesLabel label =
                    new ReactiveProgression.ChangeApplicationProgressionMessagesLabel("root");
            ReactiveProgression.ChangeApplicationProgressionMessagesLabel composed = label.withSubLabel("leaf");
            assertThat(composed.label()).isEqualTo("root.leaf");
        }

        @Test
        @DisplayName("up() supprime tous les segments après le premier (regex \\.[^\\.]*)")
        void up() {
            ReactiveProgression.ChangeApplicationProgressionMessagesLabel label =
                    new ReactiveProgression.ChangeApplicationProgressionMessagesLabel("x.y.z");
            ReactiveProgression.ChangeApplicationProgressionMessagesLabel parent = label.up();
            assertThat(parent.label()).isEqualTo("x");
        }

        @Test
        @DisplayName("newProgressionMessageLabel retourne instance avec label")
        void newLabel() {
            ReactiveProgression.ChangeApplicationProgressionMessagesLabel label =
                    new ReactiveProgression.ChangeApplicationProgressionMessagesLabel();
            ReactiveProgression.ChangeApplicationProgressionMessagesLabel newLabel = label.newProgressionMessageLabel("change.label");
            assertThat(newLabel.label()).isEqualTo("change.label");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  GetApplicationProgressionMessagesLabel
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("GetApplicationProgressionMessagesLabel")
    class GetApplicationProgressionMessagesLabelTest {

        @Test
        @DisplayName("constructeur sans argument utilise un label par défaut")
        void defaultLabel() {
            ReactiveProgression.GetApplicationProgressionMessagesLabel label =
                    new ReactiveProgression.GetApplicationProgressionMessagesLabel();
            assertThat(label.label()).isNotBlank();
        }

        @Test
        @DisplayName("withSubLabel() compose le label")
        void withSubLabel() {
            ReactiveProgression.GetApplicationProgressionMessagesLabel label =
                    new ReactiveProgression.GetApplicationProgressionMessagesLabel("get");
            // Note: GetApplicationProgressionMessagesLabel.newProgressionMessageLabel() returns
            // a CreateApplicationProgressionMessagesLabel (current implementation)
            ReactiveProgression.ProgressionMessagesLabel composed = label.withSubLabel("item");
            assertThat(composed.label()).isEqualTo("get.item");
        }

        @Test
        @DisplayName("up() supprime tous les segments après le premier (regex \\.[^\\.]*)")
        void up() {
            ReactiveProgression.GetApplicationProgressionMessagesLabel label =
                    new ReactiveProgression.GetApplicationProgressionMessagesLabel("a.b.c");
            // Note: up() internally calls newProgressionMessageLabel which returns CreateApplicationProgressionMessagesLabel
            ReactiveProgression.ProgressionMessagesLabel parent = label.up();
            assertThat(parent.label()).isEqualTo("a");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ProgressionMessagesLabel interface default methods
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("ProgressionMessagesLabel.COMPOSITION_LABEL")
    class CompositionLabelTest {

        @Test
        @DisplayName("COMPOSITION_LABEL est '%s.%s'")
        void compositionLabelConstant() {
            assertThat(ReactiveProgression.ProgressionMessagesLabel.COMPOSITION_LABEL)
                    .isEqualTo("%s.%s");
        }

        @Test
        @DisplayName("withSubLabel sur label simple → parent.child")
        void withSubLabelSimple() {
            ReactiveProgression.CreateApplicationProgressionMessagesLabel l =
                    new ReactiveProgression.CreateApplicationProgressionMessagesLabel("parent");
            assertThat(l.withSubLabel("child").label()).isEqualTo("parent.child");
        }

        @Test
        @DisplayName("up() sur label à un seul niveau laisse le label inchangé (aucun point)")
        void upSingleLevel() {
            ReactiveProgression.CreateApplicationProgressionMessagesLabel l =
                    new ReactiveProgression.CreateApplicationProgressionMessagesLabel("one");
            // replaceAll("\\.[^\\.]*", "") on "one" gives "one" (no dots to remove)
            assertThat(l.up().label()).isEqualTo("one");
        }
    }
}
