package fr.inra.oresing.domain.groovy.predefined.script;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des ScriptConstantProvider et de la méthode statique
 * {@link BuildCompositeKey#buildNaturelKeyFromLabels}.
 * Aucun contexte Spring.
 */
@Tag("domain.model")
@DisplayName("Groovy predefined script providers")
class ScriptProvidersTest {

    // ─── ScriptConstantProvider ───────────────────────────────────────────────

    @Nested
    @DisplayName("ScriptConstantProvider")
    class ScriptConstantProviderTest {

        @Test
        @DisplayName("PROVIDERS contient exactement 5 fournisseurs")
        void providersCount() {
            assertThat(ScriptConstantProvider.PROVIDERS).hasSize(5);
        }

        @Test
        @DisplayName("addAllToContext() lie tous les fournisseurs au contexte")
        void addAllToContextBindsAllProviders() {
            Map<String, Object> ctx = new java.util.HashMap<>();
            ScriptConstantProvider.addAllToContext(ctx);
            assertThat(ctx).containsKey("OA_buildCompositeKey");
            assertThat(ctx).containsKey("OA_buildException");
            assertThat(ctx).containsKey("OA_buildManyCompositeKey");
            assertThat(ctx).containsKey("OA_escapeLabel");
            assertThat(ctx).containsKey("OA_naturalKeyBuilder");
        }

        @Test
        @DisplayName("addAllToContext() sur un contexte déjà initialisé n'écrase pas les autres clés")
        void addAllToContextDoesNotRemoveExistingKeys() {
            Map<String, Object> ctx = new java.util.HashMap<>();
            ctx.put("existingKey", "existingValue");
            ScriptConstantProvider.addAllToContext(ctx);
            assertThat(ctx).containsKey("existingKey");
        }
    }

    // ─── BuildCompositeKey ────────────────────────────────────────────────────

    @Nested
    @DisplayName("BuildCompositeKey.buildNaturelKeyFromLabels")
    class BuildCompositeKeyTest {

        private static final String SEP = AsynchroneFileImporterContext.getCompositeNaturalKeyComponentsSeparator();

        @Test
        @DisplayName("toutes valeurs vides retourne chaîne vide")
        void allEmptyReturnsEmpty() {
            assertThat(BuildCompositeKey.buildNaturelKeyFromLabels(List.of("", "", "")))
                    .isEmpty();
        }

        @Test
        @DisplayName("liste vide retourne chaîne vide")
        void emptyListReturnsEmpty() {
            assertThat(BuildCompositeKey.buildNaturelKeyFromLabels(List.of())).isEmpty();
        }

        @Test
        @DisplayName("valeur non vide est échappée et retournée")
        void singleValue() {
            String result = BuildCompositeKey.buildNaturelKeyFromLabels(List.of("hello"));
            assertThat(result).isEqualTo(Ltree.escapeToLabel("hello"));
        }

        @Test
        @DisplayName("valeur vide au milieu remplacée par NULL_KEY")
        void emptyInMiddleReplacedByNullKey() {
            List<String> values = List.of("a", "", "b");
            String result = BuildCompositeKey.buildNaturelKeyFromLabels(values);
            assertThat(result).contains(Ltree.NULL_KEY);
            assertThat(result).contains(SEP);
        }

        @Test
        @DisplayName("plusieurs valeurs jointes par le séparateur")
        void multipleValuesJoined() {
            List<String> values = List.of("a", "b", "c");
            String result = BuildCompositeKey.buildNaturelKeyFromLabels(values);
            assertThat(result).contains(SEP);
            String[] parts = result.split(SEP);
            assertThat(parts).hasSize(3);
        }

        @Test
        @DisplayName("nullOrEmptyToNull remplace null ou vide par NULL_KEY")
        void nullOrEmptyToNull() {
            assertThat(BuildCompositeKey.nullOrEmptyToNull.apply("")).isEqualTo(Ltree.NULL_KEY);
            assertThat(BuildCompositeKey.nullOrEmptyToNull.apply(null)).isEqualTo(Ltree.NULL_KEY);
            assertThat(BuildCompositeKey.nullOrEmptyToNull.apply("x")).isEqualTo("x");
        }

        @Test
        @DisplayName("record equality : deux BuildCompositeKey sont toujours égaux")
        void equality() {
            assertThat(new BuildCompositeKey()).isEqualTo(new BuildCompositeKey());
        }
    }

    // ─── EscapeLabelProvider ─────────────────────────────────────────────────

    @Nested
    @DisplayName("EscapeLabelProvider")
    class EscapeLabelProviderTest {

        @Test
        @DisplayName("bindToContext() ajoute OA_escapeLabel dans le contexte")
        void bindsEscapeLabel() {
            Map<String, Object> ctx = new java.util.HashMap<>();
            new EscapeLabelProvider().bindToContext(ctx);
            assertThat(ctx).containsKey("OA_escapeLabel");
        }

        @Test
        @DisplayName("record equality")
        void equality() {
            assertThat(new EscapeLabelProvider()).isEqualTo(new EscapeLabelProvider());
        }
    }

    // ─── BuildExceptionProvider ───────────────────────────────────────────────

    @Nested
    @DisplayName("BuildExceptionProvider")
    class BuildExceptionProviderTest {

        @Test
        @DisplayName("bindToContext() ajoute OA_buildException dans le contexte")
        void bindsBuilException() {
            Map<String, Object> ctx = new java.util.HashMap<>();
            new BuildExceptionProvider().bindToContext(ctx);
            assertThat(ctx).containsKey("OA_buildException");
        }

        @Test
        @DisplayName("record equality")
        void equality() {
            assertThat(new BuildExceptionProvider()).isEqualTo(new BuildExceptionProvider());
        }
    }

    // ─── NaturalKeyProvider ───────────────────────────────────────────────────

    @Nested
    @DisplayName("NaturalKeyProvider")
    class NaturalKeyProviderTest {

        @Test
        @DisplayName("bindToContext() ajoute OA_naturalKeyBuilder dans le contexte")
        void bindsNaturalKeyBuilder() {
            Map<String, Object> ctx = new java.util.HashMap<>();
            ctx.put("datum", Map.of());
            new NaturalKeyProvider().bindToContext(ctx);
            assertThat(ctx).containsKey("OA_naturalKeyBuilder");
        }

        @Test
        @DisplayName("record equality")
        void equality() {
            assertThat(new NaturalKeyProvider()).isEqualTo(new NaturalKeyProvider());
        }
    }

    // ─── BuildManyCompositeKey ────────────────────────────────────────────────

    @Nested
    @DisplayName("BuildManyCompositeKey")
    class BuildManyCompositeKeyTest {

        @Test
        @DisplayName("bindToContext() ajoute OA_buildManyCompositeKey dans le contexte")
        void bindsKey() {
            Map<String, Object> ctx = new java.util.HashMap<>();
            new BuildManyCompositeKey().bindToContext(ctx);
            assertThat(ctx).containsKey("OA_buildManyCompositeKey");
        }

        @Test
        @DisplayName("record equality")
        void equality() {
            assertThat(new BuildManyCompositeKey()).isEqualTo(new BuildManyCompositeKey());
        }
    }
}
