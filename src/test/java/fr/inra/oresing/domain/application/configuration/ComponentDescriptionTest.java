package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescriptionBuilder;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

@org.junit.jupiter.api.Tag("core.config")
@org.junit.jupiter.api.Tag("domain.model")
@org.junit.jupiter.api.Tag("domain.model")
class ComponentDescriptionTest {

    @Test
    void testBasicComponentBuilder() {
        CheckerDescription checker = CheckerDescriptionBuilder.stringChecker().build();
        Set<Tag> tags = Set.of(TagBuilder.dataTag());

        BasicComponent component = ComponentDescriptionBuilder.basicComponent()
                .componentKey("myComponent")
                .tags(tags)
                .checker(checker)
                .mandatory(ComponentPresenceConstraint.MANDATORY)
                .build();

        Assertions.assertEquals("myComponent", component.componentKey());
        Assertions.assertEquals(tags, component.tags());
        Assertions.assertEquals(checker, component.checker());
        Assertions.assertEquals(ComponentPresenceConstraint.MANDATORY, component.mandatory());
    }

    @Test
    void testComputedComponentBuilder() {
        CheckerDescription checker = CheckerDescriptionBuilder.stringChecker().build();
        ComputationChecker computationChecker = CheckerDescriptionBuilder.computationChecker().build();
        Set<Tag> tags = Set.of(TagBuilder.dataTag());

        ComputedComponent component = ComponentDescriptionBuilder.computedComponent()
                .componentKey("myComponent")
                .tags(tags)
                .checker(checker)
                .computationChecker(computationChecker)
                .mandatory(ComponentPresenceConstraint.MANDATORY)
                .build();

        Assertions.assertEquals("myComponent", component.componentKey());
        Assertions.assertEquals(tags, component.tags());
        Assertions.assertEquals(checker, component.checker());
        Assertions.assertEquals(computationChecker, component.computationChecker());
        Assertions.assertEquals(ComponentPresenceConstraint.MANDATORY, component.mandatory());
    }

    @Test
    void testConstantComponentBuilder() {
        CheckerDescription checker = CheckerDescriptionBuilder.stringChecker().build();
        ConstantImport constantImport = ConstantImportBuilder.submissionConstantHeader().build();
        Set<Tag> tags = Set.of(TagBuilder.dataTag());

        ConstantComponent component = ComponentDescriptionBuilder.constantComponent()
                .componentKey("myComponent")
                .tags(tags)
                .checker(checker)
                .constantImportHeader(constantImport)
                .mandatory(ComponentPresenceConstraint.MANDATORY)
                .build();

        Assertions.assertEquals("myComponent", component.componentKey());
        Assertions.assertEquals(tags, component.tags());
        Assertions.assertEquals(checker, component.checker());
        Assertions.assertEquals(constantImport, component.constantImportHeader());
        Assertions.assertEquals(ComponentPresenceConstraint.MANDATORY, component.mandatory());
    }

    @Test
    void testDynamicComponentBuilder() {
        CheckerDescription checker = CheckerDescriptionBuilder.stringChecker().build();
        Set<Tag> tags = Set.of(TagBuilder.dataTag());

        DynamicComponent component = ComponentDescriptionBuilder.dynamicComponent()
                .componentKey("myComponent")
                .tags(tags)
                .checker(checker)
                .prefix("prefix")
                .reference("ref")
                .referenceColumnToLookForHeader("header")
                .mandatory(ComponentPresenceConstraint.MANDATORY)
                .build();

        Assertions.assertEquals("myComponent", component.componentKey());
        Assertions.assertEquals(tags, component.tags());
        Assertions.assertEquals(checker, component.checker());
        Assertions.assertEquals("prefix", component.prefix());
        Assertions.assertEquals("ref", component.reference());
        Assertions.assertEquals("header", component.referenceColumnToLookForHeader());
        Assertions.assertEquals(ComponentPresenceConstraint.MANDATORY, component.mandatory());
    }

    @Test
    void testFilteredDescriptionComponentBuilder() {
        Set<Tag> tags = Set.of(TagBuilder.dataTag());

        FilteredDescriptionComponent component = ComponentDescriptionBuilder.filteredDescriptionComponent()
                .componentKey("myComponent")
                .tags(tags)
                .submissionAuthorizationScope("scope")
                .build();

        Assertions.assertEquals("myComponent", component.componentKey());
        Assertions.assertEquals(tags, component.tags());
        Assertions.assertEquals("scope", component.submissionAuthorizationScope());
    }

    @Test
    void testPatternComponentBuilder() {
        CheckerDescription checker = CheckerDescriptionBuilder.stringChecker().build();
        Set<Tag> tags = Set.of(TagBuilder.dataTag());
        Map<String, PatternComponentQualifiers> qualifiers = Map.of("q1", ComponentDescriptionBuilder.patternComponentQualifiers().build());
        Map<String, PatternComponentAdjacents> adjacents = Map.of("a1", ComponentDescriptionBuilder.patternComponentAdjacents().build());

        PatternComponent component = ComponentDescriptionBuilder.patternComponent()
                .componentKey("myComponent")
                .tags(tags)
                .checker(checker)
                .patternForComponents("pattern")
                .patternComponentQualifiers(qualifiers)
                .patternComponentAdjacents(adjacents)
                .mandatory(ComponentPresenceConstraint.MANDATORY)
                .build();

        Assertions.assertEquals("myComponent", component.componentKey());
        Assertions.assertEquals(tags, component.tags());
        Assertions.assertEquals(checker, component.checker());
        Assertions.assertEquals("pattern", component.patternForComponents());
        Assertions.assertEquals(qualifiers, component.patternComponentQualifiers());
        Assertions.assertEquals(adjacents, component.patternComponentAdjacents());
        Assertions.assertEquals(ComponentPresenceConstraint.MANDATORY, component.mandatory());
    }

    @Test
    void testPatternComponentQualifiersBuilder() {
        CheckerDescription checker = CheckerDescriptionBuilder.stringChecker().build();
        Set<Tag> tags = Set.of(TagBuilder.dataTag());

        PatternComponentQualifiers component = ComponentDescriptionBuilder.patternComponentQualifiers()
                .componentKey("myComponent")
                .tags(tags)
                .checker(checker)
                .patternNumber(1)
                .build();

        Assertions.assertEquals("myComponent", component.componentKey());
        Assertions.assertEquals(tags, component.tags());
        Assertions.assertEquals(checker, component.checker());
        Assertions.assertEquals(1, component.patternNumber());
    }

    @Test
    void testPatternComponentAdjacentsBuilder() {
        CheckerDescription checker = CheckerDescriptionBuilder.stringChecker().build();
        Set<Tag> tags = Set.of(TagBuilder.dataTag());

        PatternComponentAdjacents component = ComponentDescriptionBuilder.patternComponentAdjacents()
                .componentKey("myComponent")
                .tags(tags)
                .checker(checker)
                .importHeaderPattern("pattern")
                .mandatory(ComponentPresenceConstraint.MANDATORY)
                .build();

        Assertions.assertEquals("myComponent", component.componentKey());
        Assertions.assertEquals(tags, component.tags());
        Assertions.assertEquals(checker, component.checker());
        Assertions.assertEquals("pattern", component.importHeaderPattern());
        Assertions.assertEquals(ComponentPresenceConstraint.MANDATORY, component.mandatory());
    }

    @Test
    void testReferenceScopeComponentBuilder() {
        ReferenceScopeComponent component = ComponentDescriptionBuilder.referenceScopeComponent()
                .componentKey("myComponent")
                .authorizationScopeName("scopeName")
                .references("refs")
                .component("comp")
                .exportHeaderName("header")
                .submissionAuthorizationScope("subScope")
                .build();

        Assertions.assertEquals("myComponent", component.componentKey());
        Assertions.assertEquals("scopeName", component.authorizationScopeName());
        Assertions.assertEquals("refs", component.references());
        Assertions.assertEquals("comp", component.component());
        Assertions.assertEquals("header", component.exportHeaderName());
        Assertions.assertEquals("subScope", component.submissionAuthorizationScope());
    }

    @Test
    void isFilterableAsText_trueWhenFilterTextTagPresentOnStringChecker() {
        BasicComponent component = ComponentDescriptionBuilder.basicComponent()
                .componentKey("c")
                .tags(Set.of(TagBuilder.filterTextTag()))
                .checker(CheckerDescriptionBuilder.stringChecker().build())
                .build();

        Assertions.assertTrue(component.isFilterableAsText());
        Assertions.assertFalse(component.isFilterableAsList());
        Assertions.assertTrue(component.isFilterable());
    }

    @Test
    void isFilterableAsList_trueWhenFilterListTagPresentOnStringChecker() {
        BasicComponent component = ComponentDescriptionBuilder.basicComponent()
                .componentKey("c")
                .tags(Set.of(TagBuilder.filterListTag()))
                .checker(CheckerDescriptionBuilder.stringChecker().build())
                .build();

        Assertions.assertFalse(component.isFilterableAsText());
        Assertions.assertTrue(component.isFilterableAsList());
        Assertions.assertTrue(component.isFilterable());
    }

    @Test
    void isFilterable_falseWhenNoFilterTag() {
        // Couverture des cas mixtes : tags non vide mais sans tag de filtre.
        BasicComponent component = ComponentDescriptionBuilder.basicComponent()
                .componentKey("c")
                .tags(Set.of(TagBuilder.dataTag(), TagBuilder.orderTag(3)))
                .checker(CheckerDescriptionBuilder.stringChecker().build())
                .build();

        Assertions.assertFalse(component.isFilterable());
        Assertions.assertFalse(component.isFilterableAsText());
        Assertions.assertFalse(component.isFilterableAsList());
    }

    @Test
    void filterTagSuppressed_byHiddenTag() {
        // Règle d'exclusion §7 : la colonne masquée annule tout filtre opt-in.
        BasicComponent component = ComponentDescriptionBuilder.basicComponent()
                .componentKey("c")
                .tags(Set.of(TagBuilder.filterTextTag(), TagBuilder.hiddenTag()))
                .checker(CheckerDescriptionBuilder.stringChecker().build())
                .build();

        Assertions.assertTrue(component.hasFilterTagSuppressed());
        Assertions.assertFalse(component.isFilterableAsText());
        Assertions.assertFalse(component.isFilterable());
    }

    @Test
    void filterTagSuppressed_byNativeFilterChecker_integer() {
        // Règle d'exclusion §7 : un filtre natif ( intervalle numérique ) prime.
        BasicComponent component = ComponentDescriptionBuilder.basicComponent()
                .componentKey("c")
                .tags(Set.of(TagBuilder.filterListTag()))
                .checker(CheckerDescriptionBuilder.integerChecker().build())
                .build();

        Assertions.assertTrue(component.hasFilterTagSuppressed());
        Assertions.assertFalse(component.isFilterableAsList());
        Assertions.assertFalse(component.isFilterable());
    }

    @Test
    void filterTagSuppressed_byNativeFilterChecker_reference() {
        // Cas typique de l'image #271 : __FILTER_TEXT__ posé par erreur sur une
        // colonne référence. La dropdown référence native prime , le tag est ignoré.
        BasicComponent component = ComponentDescriptionBuilder.basicComponent()
                .componentKey("c")
                .tags(Set.of(TagBuilder.filterTextTag()))
                .checker(CheckerDescriptionBuilder.referenceChecker().refType("tr_treatment").build())
                .build();

        Assertions.assertTrue(component.hasNativeFilter());
        Assertions.assertFalse(component.isFilterableAsText());
        Assertions.assertFalse(component.isFilterable());
    }

    @Test
    void hasNativeFilter_falseForOpenTextCheckers() {
        // Les checkers texte ouverts ( String , Computation , Groovy ) n'ont pas
        // de filtre natif et acceptent les deux tags opt-in.
        Assertions.assertFalse(ComponentDescriptionBuilder.basicComponent()
                .componentKey("c")
                .checker(CheckerDescriptionBuilder.stringChecker().build())
                .build()
                .hasNativeFilter());
    }
}