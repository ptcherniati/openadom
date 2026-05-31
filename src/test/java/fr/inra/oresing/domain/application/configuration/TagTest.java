package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("core.config")
class TagTest {

    @Test
    void testHiddenTagBuilder() {
        Tag.HiddenTag tag = TagBuilder.hiddenTag();
        Assertions.assertEquals(Tag.TagDefinitions.HIDDEN_TAG, tag.tagDefinition());
    }

    @Test
    void testDataTagBuilder() {
        Tag.DataTag tag = TagBuilder.dataTag();
        Assertions.assertEquals(Tag.TagDefinitions.DATA_TAG, tag.tagDefinition());
    }

    @Test
    void testReferenceTagBuilder() {
        Tag.ReferenceTag tag = TagBuilder.referenceTag();
        Assertions.assertEquals(Tag.TagDefinitions.REFFERENCE_TAG, tag.tagDefinition());
    }

    @Test
    void testNoTagBuilder() {
        Tag.NoTag tag = TagBuilder.noTag();
        Assertions.assertEquals(Tag.TagDefinitions.NO_TAG, tag.tagDefinition());
    }

    @Test
    void testOrderTagBuilder() {
        Tag.OrderTag tag = TagBuilder.orderTag(5);
        Assertions.assertEquals(Tag.TagDefinitions.ORDER_TAG, tag.tagDefinition());
        Assertions.assertEquals(5, tag.tagOrder());
    }

    @Test
    void testFilterTextTagBuilder() {
        Tag.FilterTextTag tag = TagBuilder.filterTextTag();
        Assertions.assertEquals(Tag.TagDefinitions.FILTER_TEXT_TAG, tag.tagDefinition());
    }

    @Test
    void testFilterListTagBuilder() {
        Tag.FilterListTag tag = TagBuilder.filterListTag();
        Assertions.assertEquals(Tag.TagDefinitions.FILTER_LIST_TAG, tag.tagDefinition());
    }

    @Test
    void testFilterTextTagFromBuildTag() {
        // Le parsing YAML => Java passe par Tag.buildTag(String) ; vérifie
        // que la chaîne "__FILTER_TEXT__" produit bien un FilterTextTag.
        Tag tag = Tag.buildTag(Tag.FilterTextTag.FILTER_TEXT_PATTERN);
        Assertions.assertInstanceOf(Tag.FilterTextTag.class, tag);
        Assertions.assertEquals(Tag.TagDefinitions.FILTER_TEXT_TAG, tag.tagDefinition());
    }

    @Test
    void testFilterListTagFromBuildTag() {
        Tag tag = Tag.buildTag(Tag.FilterListTag.FILTER_LIST_PATTERN);
        Assertions.assertInstanceOf(Tag.FilterListTag.class, tag);
        Assertions.assertEquals(Tag.TagDefinitions.FILTER_LIST_TAG, tag.tagDefinition());
    }

    @Test
    void testFilterTagSealedHierarchy() {
        // Les deux records concrets doivent être des FilterTag : permet aux
        // callers de tester "est-ce un filtre" sans énumérer chaque sous-type.
        Assertions.assertInstanceOf(Tag.FilterTag.class, TagBuilder.filterTextTag());
        Assertions.assertInstanceOf(Tag.FilterTag.class, TagBuilder.filterListTag());
    }

    @Test
    void testDomainTagBuilder() {
        Tag.DomainTag tag = TagBuilder.domainTag("mytag");
        Assertions.assertEquals(Tag.TagDefinitions.DOMAIN_TAG, tag.tagDefinition());
        Assertions.assertEquals("mytag", tag.tagName());
    }

    @Test
    void testOrderStrictTagBuilder() {
        Tag.OrderStrictTag tag = TagBuilder.orderStrictTag();
        Assertions.assertEquals(Tag.TagDefinitions.ORDER_STRICT_TAG, tag.tagDefinition());
    }

    @Test
    void testOrderStrictTagFromBuildTag() {
        // Le parsing YAML => Java passe par Tag.buildTag(String) ; vérifie
        // que la chaîne "__ORDER_STRICT__" produit bien un OrderStrictTag.
        Tag tag = Tag.buildTag(Tag.OrderStrictTag.ORDER_STRICT_PATTERN);
        Assertions.assertInstanceOf(Tag.OrderStrictTag.class, tag);
        Assertions.assertEquals(Tag.TagDefinitions.ORDER_STRICT_TAG, tag.tagDefinition());
    }

    @Test
    void testOrderStrictTagIsDefinedTag() {
        Tag.OrderStrictTag tag = Tag.OrderStrictTag.instance();
        Assertions.assertInstanceOf(Tag.DefinedTag.class, tag);
    }

    @Test
    void testOrderStrictTagPatternConstant() {
        Assertions.assertEquals("__ORDER_STRICT__", Tag.OrderStrictTag.ORDER_STRICT_PATTERN);
    }

    @Test
    void testStepTagBuilder() {
        Tag.StepTag tag = TagBuilder.stepTag(3);
        Assertions.assertEquals(Tag.TagDefinitions.STEP_TAG, tag.tagDefinition());
        Assertions.assertEquals(3, tag.stepLevel());
    }

    @Test
    void testStepTagFromBuildTag() {
        // Le parsing YAML => Java passe par Tag.buildTag(String) ; vérifie que la
        // chaîne "__STEP_2__" produit bien un StepTag de niveau 2.
        Tag tag = Tag.buildTag("__STEP_2__");
        Assertions.assertInstanceOf(Tag.StepTag.class, tag);
        Tag.StepTag stepTag = (Tag.StepTag) tag;
        Assertions.assertEquals(Tag.TagDefinitions.STEP_TAG, stepTag.tagDefinition());
        Assertions.assertEquals(2, stepTag.stepLevel());
    }

    @Test
    void testStepTagIsDefinedTag() {
        Tag.StepTag tag = TagBuilder.stepTag(1);
        Assertions.assertInstanceOf(Tag.DefinedTag.class, tag);
    }

    @Test
    void testStepTagPatternConstant() {
        Assertions.assertEquals("__STEP_(\\d+)__", Tag.StepTag.STEP_TAG_PATTERN.pattern());
    }

    @Test
    void testStepTagWithoutParameterIsBusinessTag() {
        // Le motif sans paramètre (__STEP__) n'est pas capté par StepTag :
        // il reste un BusinessTag libre.
        Tag tag = Tag.buildTag("__STEP__");
        Assertions.assertInstanceOf(Tag.BusinessTag.class, tag);
        Assertions.assertFalse(tag instanceof Tag.StepTag);
    }

    @Test
    void testBusinessTagWithoutParameterFromBuildTag() {
        Tag tag = Tag.buildTag("__STEP__");
        Assertions.assertInstanceOf(Tag.BusinessTag.class, tag);
        Tag.BusinessTag businessTag = (Tag.BusinessTag) tag;
        Assertions.assertEquals(Tag.TagDefinitions.BUSINESS_TAG, businessTag.tagDefinition());
        Assertions.assertEquals("STEP", businessTag.tagPrefix());
        Assertions.assertNull(businessTag.tagParameter());
    }

    @Test
    void testBusinessTagWithParameterFromBuildTag() {
        Tag tag = Tag.buildTag("__GROUP_100__");
        Assertions.assertInstanceOf(Tag.BusinessTag.class, tag);
        Tag.BusinessTag businessTag = (Tag.BusinessTag) tag;
        Assertions.assertEquals("GROUP", businessTag.tagPrefix());
        Assertions.assertEquals(100, businessTag.tagParameter());
    }

    @Test
    void testZoneBusinessTagWithParameterFromBuildTag() {
        Tag tag = Tag.buildTag("__ZONE_3__");
        Assertions.assertInstanceOf(Tag.BusinessTag.class, tag);
        Tag.BusinessTag businessTag = (Tag.BusinessTag) tag;
        Assertions.assertEquals("ZONE", businessTag.tagPrefix());
        Assertions.assertEquals(3, businessTag.tagParameter());
    }

    @Test
    void testReservedTagsWinBeforeBusinessTag() {
        Assertions.assertInstanceOf(Tag.OrderTag.class, Tag.buildTag("__ORDER_5__"));
        Assertions.assertInstanceOf(Tag.FilterTextTag.class, Tag.buildTag("__FILTER_TEXT__"));
        // __STEP_xxx__ est un tag système réservé : il l'emporte sur le BusinessTag
        // générique de même forme (__PREFIX_123__).
        Assertions.assertInstanceOf(Tag.StepTag.class, Tag.buildTag("__STEP_2__"));
    }

    @Test
    void testInvalidBusinessTagPatternsAreRejected() {
        Assertions.assertThrows(SiOreConfigurationFormatException.class, () -> Tag.buildTag("__foo__"));
        Assertions.assertThrows(SiOreConfigurationFormatException.class, () -> Tag.buildTag("__1STEP__"));
    }

    @Test
    void testBusinessTagIsNotDefinedTag() {
        Tag.BusinessTag tag = TagBuilder.businessTag("STEP", null);
        Assertions.assertFalse(Tag.DefinedTag.class.isInstance(tag));
    }
}