package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("core.config")
@Tag("domain.model")
class DependsTest {

    @Test
    void testDependsParentBuilder() {
        DependsParent depends = DependsBuilder.dependsParent()
                .references("refs")
                .component("comp")
                .build();

        Assertions.assertEquals(Depends.DependsType.DependsParent, depends.type());
        Assertions.assertEquals("refs", depends.references());
        Assertions.assertEquals("comp", depends.component());
    }

    @Test
    void testDependsRecursiveBuilder() {
        DependsRecursive depends = DependsBuilder.dependsRecursive()
                .references("refs")
                .component("comp")
                .build();

        Assertions.assertEquals(Depends.DependsType.DependsRecursive, depends.type());
        Assertions.assertEquals("refs", depends.references());
        Assertions.assertEquals("comp", depends.component());
    }

    @Test
    void testDependsReferencesBuilder() {
        DependsReferences depends = DependsBuilder.dependsReferences()
                .references("refs")
                .component("comp")
                .build();

        Assertions.assertEquals(Depends.DependsType.DependsReferences, depends.type());
        Assertions.assertEquals("refs", depends.references());
        Assertions.assertEquals("comp", depends.component());
    }
}