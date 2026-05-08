package fr.inra.oresing.workflow.cascade.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link ConfigField} et son builder.
 *
 * <p>Couvre les chemins non encore atteints par {@link ConfigFieldRegistryTest} :
 * cast() (int/bool/string/raw), enumField() validation,
 * builder.hot(), builder.validator(), builder.notBlank(),
 * Mutation.oldString/newString.
 */
@Tag("core.config")
@DisplayName("ConfigField — builder et cast")
class ConfigFieldTest {

    // ─── cast() via apply() sans parser ─────────────────────────────────────

    @Nested
    @DisplayName("cast() via apply() sans parser explicite")
    class CastViaNullParser {

        @Test
        @DisplayName("apply(null) retourne Mutation.changed=false quand oldValue=null")
        void applyNullRawValue() {
            AtomicInteger holder = new AtomicInteger(42);
            ConfigField<Integer> field = ConfigField.intField("myInt")
                    .getter(holder::get)
                    .setter(holder::set)
                    .build();

            // apply() avec parser Integer::parseInt défini par intField()
            ConfigField.Mutation<Integer> m = field.apply(100);
            assertTrue(m.changed());
        }

        @Test
        @DisplayName("cast : INT depuis Number")
        void castIntFromNumber() {
            AtomicInteger holder = new AtomicInteger(0);
            ConfigField<Integer> field = ConfigField.<Integer>intField("myInt")
                    .getter(holder::get)
                    .setter(holder::set)
                    .build();
            // Le parser de intField parse Integer::parseInt donc on passe un String
            field.apply("77");
            assertEquals(77, holder.get());
        }

        @Test
        @DisplayName("cast : BOOL depuis Boolean (sans parser)")
        void castBoolFromBoolean() {
            AtomicInteger calls = new AtomicInteger(0);
            // Créer un field bool SANS parser pour déclencher cast()
            ConfigField<Boolean> field = new ConfigField.Builder<Boolean>()
                    .name("myBool")
                    .type(ConfigField.Type.BOOL)
                    .getter(() -> false)
                    .setter(v -> calls.incrementAndGet())
                    .build(); // pas de parser → cast() sera utilisé

            ConfigField.Mutation<Boolean> m = field.apply(true);
            assertTrue(m.changed());
            assertEquals(1, calls.get());
        }

        @Test
        @DisplayName("cast : STRING depuis String (sans parser)")
        void castStringFromString() {
            String[] holder = {"old"};
            ConfigField<String> field = new ConfigField.Builder<String>()
                    .name("myStr")
                    .type(ConfigField.Type.STRING)
                    .getter(() -> holder[0])
                    .setter(v -> holder[0] = v)
                    .build();

            ConfigField.Mutation<String> m = field.apply("new");
            assertTrue(m.changed());
            assertEquals("new", holder[0]);
        }
    }

    // ─── builder.hot() ──────────────────────────────────────────────────────

    @Test
    @DisplayName("builder.hot() positionne hot=true et restartRequired=false")
    void builderHot() {
        // readOnly positionne hot=false, puis hot() doit remettre hot=true
        AtomicInteger h = new AtomicInteger(1);
        ConfigField<Integer> field = ConfigField.intField("f")
                .getter(h::get)
                .setter(h::set)
                .readOnly("must restart")  // hot=false, restartRequired=true
                .hot()                     // hot=true, restartRequired=false
                .build();

        assertTrue(field.hot());
        assertFalse(field.restartRequired());
    }

    // ─── builder.validator() ────────────────────────────────────────────────

    @Test
    @DisplayName("validator personnalisé lancé lors d'un apply()")
    void customValidator() {
        AtomicInteger h = new AtomicInteger(5);
        ConfigField<Integer> field = ConfigField.intField("positiveInt")
                .getter(h::get)
                .setter(h::set)
                .validator((name, val) -> {
                    if (val <= 0) throw new IllegalArgumentException(name + " must be > 0");
                })
                .build();

        assertThrows(IllegalArgumentException.class, () -> field.apply(-1));
        assertThrows(IllegalArgumentException.class, () -> field.apply(0));
        assertDoesNotThrow(() -> field.apply(10));
    }

    // ─── builder.notBlank() ──────────────────────────────────────────────────

    @Test
    @DisplayName("notBlank() bloque une valeur vide")
    void notBlankEmpty() {
        String[] h = {"hello"};
        ConfigField<String> field = ConfigField.stringField("name")
                .getter(() -> h[0])
                .setter(v -> h[0] = v)
                .notBlank()
                .build();

        assertThrows(IllegalArgumentException.class, () -> field.apply(""));
        assertThrows(IllegalArgumentException.class, () -> field.apply("   "));
    }

    @Test
    @DisplayName("notBlank() laisse passer une valeur non-vide")
    void notBlankNonEmpty() {
        String[] h = {"old"};
        ConfigField<String> field = ConfigField.stringField("name")
                .getter(() -> h[0])
                .setter(v -> h[0] = v)
                .notBlank()
                .build();

        assertDoesNotThrow(() -> field.apply("newValue"));
        assertEquals("newValue", h[0]);
    }

    // ─── enumField() validation ──────────────────────────────────────────────

    @Test
    @DisplayName("enumField() parser lève IllegalArgumentException pour valeur invalide")
    void enumFieldInvalidValue() {
        String[] h = {"MERGE_FILE"};
        ConfigField<String> field = ConfigField.enumField("sink", TestSink.class)
                .getter(() -> h[0])
                .setter(v -> h[0] = v)
                .build();

        assertThrows(IllegalArgumentException.class, () -> field.apply("UNKNOWN_VALUE"));
    }

    @Test
    @DisplayName("enumField() parser accepte valeur valide")
    void enumFieldValidValue() {
        String[] h = {"MERGE_FILE"};
        ConfigField<String> field = ConfigField.enumField("sink", TestSink.class)
                .getter(() -> h[0])
                .setter(v -> h[0] = v)
                .build();

        assertDoesNotThrow(() -> field.apply("DIRECT_COPY"));
        assertEquals("DIRECT_COPY", h[0]);
    }

    @Test
    @DisplayName("enumField() allowedValues contient toutes les constantes de l'enum")
    void enumFieldAllowedValues() {
        String[] h = {"MERGE_FILE"};
        ConfigField<String> field = ConfigField.enumField("sink", TestSink.class)
                .getter(() -> h[0])
                .setter(v -> h[0] = v)
                .build();

        List<String> allowed = field.allowedValues();
        assertNotNull(allowed);
        assertEquals(2, allowed.size());
        assertTrue(allowed.contains("MERGE_FILE"));
        assertTrue(allowed.contains("DIRECT_COPY"));
    }

    // ─── apply() — read-only ──────────────────────────────────────────────────

    @Test
    @DisplayName("apply() sur field read-only lève UnsupportedOperationException")
    void applyReadOnly() {
        ConfigField<Integer> field = ConfigField.intField("ro")
                .getter(() -> 42)
                .readOnly("needs restart")
                .build();

        assertTrue(field.isReadOnly());
        assertThrows(UnsupportedOperationException.class, () -> field.apply(99));
    }

    // ─── Mutation record ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("Mutation record")
    class MutationRecord {

        @Test
        @DisplayName("oldString() et newString() sur des valeurs non-null")
        void stringsNonNull() {
            ConfigField.Mutation<Integer> m = new ConfigField.Mutation<>("f", 1, 2, true);
            assertEquals("1", m.oldString());
            assertEquals("2", m.newString());
        }

        @Test
        @DisplayName("oldString() et newString() sur des valeurs null retournent null")
        void stringsNull() {
            ConfigField.Mutation<Integer> m = new ConfigField.Mutation<>("f", null, null, false);
            assertNull(m.oldString());
            assertNull(m.newString());
        }

        @Test
        @DisplayName("changed=false quand les valeurs sont identiques")
        void unchanged() {
            AtomicInteger h = new AtomicInteger(100);
            ConfigField<Integer> field = ConfigField.intField("x")
                    .getter(h::get)
                    .setter(h::set)
                    .build();
            ConfigField.Mutation<Integer> m = field.apply(100); // même valeur
            assertFalse(m.changed());
            assertEquals("100", m.oldString());
            assertEquals("100", m.newString());
        }
    }

    // ─── builder.build() — validations ───────────────────────────────────────

    @Test
    @DisplayName("build() sans name lève IllegalStateException")
    void buildNoName() {
        assertThrows(IllegalStateException.class,
                () -> new ConfigField.Builder<Integer>()
                        .type(ConfigField.Type.INT)
                        .getter(() -> 1)
                        .build());
    }

    @Test
    @DisplayName("build() sans getter lève IllegalStateException")
    void buildNoGetter() {
        assertThrows(IllegalStateException.class,
                () -> ConfigField.intField("f").build());
    }

    // ─── accesseurs divers ────────────────────────────────────────────────────

    @Test
    @DisplayName("currentValue() délègue au getter")
    void currentValue() {
        AtomicInteger h = new AtomicInteger(55);
        ConfigField<Integer> field = ConfigField.intField("v")
                .getter(h::get)
                .setter(h::set)
                .build();
        assertEquals(55, field.currentValue());
        h.set(77);
        assertEquals(77, field.currentValue());
    }

    // ─── enum helper ─────────────────────────────────────────────────────────

    private enum TestSink { MERGE_FILE, DIRECT_COPY }
}
