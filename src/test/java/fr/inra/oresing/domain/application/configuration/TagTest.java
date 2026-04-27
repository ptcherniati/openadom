package fr.inra.oresing.domain.application.configuration;

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
    void testFilterTagBuilder() {
        Tag.FilterTag tag = TagBuilder.filterTag();
        Assertions.assertEquals(Tag.TagDefinitions.FILTER_TAG, tag.tagDefinition());
    }

    @Test
    void testFilterTagFromBuildTag() {
        // Le parsing YAML => Java passe par Tag.buildTag(String) ; vérifie
        // que la chaîne "__FILTER__" produit bien un FilterTag.
        Tag tag = Tag.buildTag(Tag.FilterTag.FILTER_PATTERN);
        Assertions.assertInstanceOf(Tag.FilterTag.class, tag);
        Assertions.assertEquals(Tag.TagDefinitions.FILTER_TAG, tag.tagDefinition());
    }

    @Test
    void testDomainTagBuilder() {
        Tag.DomainTag tag = TagBuilder.domainTag("mytag");
        Assertions.assertEquals(Tag.TagDefinitions.DOMAIN_TAG, tag.tagDefinition());
        Assertions.assertEquals("mytag", tag.tagName());
    }
}