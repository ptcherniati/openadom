package fr.inra.oresing.domain.application.configuration.type;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
@Tag("domain.model")
class StaticMapTypeTest {

    @Test
    void testAuthorizationScope() {
        StaticMapType type = StaticMapType.AUTHORIZATION_SCOPE();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.MapType.class, type.type());
    }

    @Test
    void testI18n() {
        StaticMapType type = StaticMapType.I18N();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.MapType.class, type.type());
    }

    @Test
    void testAuthorizationScopes() {
        StaticMapType type = StaticMapType.AUTHORIZATION_SCOPES();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.ArrayType.class, type.type());
    }

    @Test
    void testFileMatchPatternScopes() {
        StaticMapType type = StaticMapType.FILE_MATCH_PATTERN_SCOPES();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.ArrayType.class, type.type());
    }

    @Test
    void testReferenceScopes() {
        StaticMapType type = StaticMapType.REFERENCE_SCOPES();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.ArrayType.class, type.type());
    }

    @Test
    void testReferenceScopesForFile() {
        StaticMapType type = StaticMapType.REFERENCE_SCOPES_FOR_FILE();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.ArrayType.class, type.type());
    }

    @Test
    void testValidations() {
        StaticMapType type = StaticMapType.VALIDATIONS();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.MapType.class, type.type());
    }

    @Test
    void testFormats() {
        StaticMapType type = StaticMapType.FORMATS();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.MapType.class, type.type());
    }

    @Test
    void testAdditionalFiles() {
        StaticMapType type = StaticMapType.ADDITIONAL_FILES();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.MapType.class, type.type());
    }

    @Test
    void testBasicComponents() {
        StaticMapType type = StaticMapType.BASIC_COMPONENTS();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.MapType.class, type.type());
    }

    @Test
    void testComputedComponents() {
        StaticMapType type = StaticMapType.COMPUTED_COMPONENTS();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.MapType.class, type.type());
    }

    @Test
    void testConstantComponents() {
        StaticMapType type = StaticMapType.CONSTANT_COMPONENTS();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.MapType.class, type.type());
    }

    @Test
    void testDynamicComponents() {
        StaticMapType type = StaticMapType.DYNAMIC_COMPONENTS();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.MapType.class, type.type());
    }

    @Test
    void testPatternComponentsQualifiers() {
        StaticMapType type = StaticMapType.PATTERN_COMPONENTS_QUALIFIERS();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.ArrayType.class, type.type());
    }

    @Test
    void testPatternComponentsAdjacent() {
        StaticMapType type = StaticMapType.PATTERN_COMPONENTS_ADJACENT();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.ArrayType.class, type.type());
    }

    @Test
    void testPatternComponents() {
        StaticMapType type = StaticMapType.PATTERN_COMPONENTS();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.MapType.class, type.type());
    }

    @Test
    void testData() {
        StaticMapType type = StaticMapType.DATA();
        Assertions.assertNotNull(type);
        Assertions.assertInstanceOf(CollectionType.MapType.class, type.type());
    }
}