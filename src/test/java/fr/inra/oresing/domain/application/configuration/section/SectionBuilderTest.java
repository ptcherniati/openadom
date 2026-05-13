package fr.inra.oresing.domain.application.configuration.section;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.ConfigurationSchemaNodeType;
import fr.inra.oresing.domain.application.configuration.type.LabelDescription;
import fr.inra.oresing.domain.application.configuration.type.StringType;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

@Tag("core.config")
@Tag("domain.model")
class SectionBuilderTest {

    @Test
    void testWithMandatorySections() {
        SectionBuilder builder = SectionBuilder.getInstance()
                .withMandatorySections(new LabelDescription("label1", new StringType("value1")));
        Assertions.assertThrows(SiOreConfigurationFormatException.class, () -> builder.test(Set.of()));
    }

    @Test
    void testWithOptionalSections() {
        SectionBuilder builder = SectionBuilder.getInstance()
                .withOptionalSections(new LabelDescription("label1", new StringType("value1")));
        builder.test(Set.of());
    }

    @Test
    void testWithAnyOfMandatorySections() {
        SectionBuilder builder = SectionBuilder.getInstance()
                .withAnyOfMandatorySections(new LabelDescription("label1", new StringType("value1")));
        Assertions.assertThrows(SiOreConfigurationFormatException.class, () -> builder.test(Set.of()));
    }

    @Test
    void testWithUnexpectedSections() {
        SectionBuilder builder = SectionBuilder.getInstance();
        Assertions.assertThrows(SiOreConfigurationFormatException.class, () -> builder.test(Set.of("unexpected")));
    }

    @Test
    void testWithLocalType() {
        SectionBuilder builder = SectionBuilder.getInstance().withLocalType();
        builder.test(Set.of("fr", "en"));
    }

    /*
    @Test
    void testWithLocalTypeInvalidLocale() {
        SectionBuilder builder = SectionBuilder.getInstance().withLocalType();
        // Locale.of() is very permissive, so it's hard to find a string that throws IllegalArgumentException
        // and we reverted to Locale.of() to support existing TagType behavior.
        // So we disable this test for now or we should find a really invalid locale.
        // Assertions.assertThrows(SiOreConfigurationFormatException.class, () -> builder.test(Set.of("invalid locale")));
    }
    */

    @Test
    void testOfAny() {
        SectionBuilder builder = SectionBuilder.ofAny();
        builder.test(Set.of("anySection"));
    }

    @Test
    void testFindSchema() {
        SectionBuilder builder = SectionBuilder.getInstance();
        Optional<ConfigurationSchemaNodeType> schema = builder.findSchema(ConfigurationSchemaNode.OA_DATA);
        Assertions.assertTrue(schema.isPresent());
    }

    @Test
    void testFindSchemaUnknown() {
        SectionBuilder builder = SectionBuilder.getInstance();
        Optional<ConfigurationSchemaNodeType> schema = builder.findSchema("unknown");
        Assertions.assertTrue(schema.isEmpty());
    }

    // ─── SectionType ─────────────────────────────────────────────────────────

    @org.junit.jupiter.api.Nested
    @org.junit.jupiter.api.DisplayName("SectionType enum")
    class SectionTypeTest {

        @Test
        @org.junit.jupiter.api.DisplayName("SectionType a 8 valeurs attendues")
        void allValues() {
            Assertions.assertEquals(8, SectionType.values().length);
        }

        @Test
        @org.junit.jupiter.api.DisplayName("SectionType.OBJECT existe")
        void objectExists() {
            Assertions.assertNotNull(SectionType.OBJECT);
        }

        @Test
        @org.junit.jupiter.api.DisplayName("SectionType.UNDEFINED existe")
        void undefinedExists() {
            Assertions.assertNotNull(SectionType.UNDEFINED);
        }
    }

    // ─── Section records ─────────────────────────────────────────────────────

    @org.junit.jupiter.api.Nested
    @org.junit.jupiter.api.DisplayName("Section sealed interface")
    class SectionTest {

        private final LabelDescription ld = new LabelDescription("myLabel", new StringType("x"));

        @Test
        @org.junit.jupiter.api.DisplayName("MandatorySection.buildInstance() crée une section required")
        void mandatorySectionBuildInstance() {
            Section.MandatorySection section = Section.MandatorySection.buildInstance(ld);
            Assertions.assertEquals("myLabel", section.label());
            Assertions.assertTrue(section.required());
        }

        @Test
        @org.junit.jupiter.api.DisplayName("OptionalSection.buildInstance() crée une section non required")
        void optionalSectionBuildInstance() {
            Section.OptionalSection section = Section.OptionalSection.buildInstance(ld);
            Assertions.assertEquals("myLabel", section.label());
            Assertions.assertFalse(section.required());
        }

        @Test
        @org.junit.jupiter.api.DisplayName("AnyOfMandatorySection.buildInstance() crée une section anyOf")
        void anyOfMandatorySectionBuildInstance() {
            Section.AnyOfMandatorySection section = Section.AnyOfMandatorySection.buildInstance(ld);
            Assertions.assertEquals("myLabel", section.label());
        }

        @Test
        @org.junit.jupiter.api.DisplayName("matches() retourne true si les labels sont identiques")
        void matchesReturnsTrueForSameLabel() {
            Section.MandatorySection section = Section.MandatorySection.buildInstance(ld);
            Assertions.assertTrue(section.matches("myLabel"));
            Assertions.assertFalse(section.matches("other"));
        }
    }
}