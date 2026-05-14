package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@org.junit.jupiter.api.Tag("core.config")
@org.junit.jupiter.api.Tag("domain.model")
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
}