package fr.inra.oresing.domain.application.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour l'interface scellée Tag et ses implémentations.
 * Complète la couverture SonarQube de Tag.java (82.6% → cible 80%+).
 */
@org.junit.jupiter.api.Tag("core.config")
@DisplayName("Tag – buildTag, buildTags et sous-types")
class TagBuildTest {

    @Test
    @DisplayName("buildTag(__HIDDEN__) retourne HiddenTag")
    void buildHiddenTag() {
        Tag tag = Tag.buildTag("__HIDDEN__");
        assertThat(tag).isInstanceOf(Tag.HiddenTag.class);
    }

    @Test
    @DisplayName("buildTag(__DATA__) retourne DataTag")
    void buildDataTag() {
        Tag tag = Tag.buildTag("__DATA__");
        assertThat(tag).isInstanceOf(Tag.DataTag.class);
    }

    @Test
    @DisplayName("buildTag(__REFERENCE__) retourne ReferenceTag")
    void buildReferenceTag() {
        Tag tag = Tag.buildTag("__REFERENCE__");
        assertThat(tag).isInstanceOf(Tag.ReferenceTag.class);
    }

    @Test
    @DisplayName("buildTag(__FILTER_TEXT__) retourne FilterTextTag")
    void buildFilterTextTag() {
        Tag tag = Tag.buildTag("__FILTER_TEXT__");
        assertThat(tag).isInstanceOf(Tag.FilterTextTag.class);
    }

    @Test
    @DisplayName("buildTag(__FILTER_LIST__) retourne FilterListTag")
    void buildFilterListTag() {
        Tag tag = Tag.buildTag("__FILTER_LIST__");
        assertThat(tag).isInstanceOf(Tag.FilterListTag.class);
    }

    @Test
    @DisplayName("buildTag(__ORDER_STRICT__) retourne OrderStrictTag")
    void buildOrderStrictTag() {
        Tag tag = Tag.buildTag("__ORDER_STRICT__");
        assertThat(tag).isInstanceOf(Tag.OrderStrictTag.class);
    }

    @Test
    @DisplayName("buildTag(__ORDER_3__) retourne OrderTag avec ordre=3")
    void buildOrderTag() {
        Tag tag = Tag.buildTag("__ORDER_3__");
        assertThat(tag).isInstanceOf(Tag.OrderTag.class);
        assertThat(((Tag.OrderTag) tag).tagOrder()).isEqualTo(3);
    }

    @Test
    @DisplayName("buildTag(no-tag) retourne NoTag")
    void buildNoTag() {
        Tag tag = Tag.buildTag("no-tag");
        assertThat(tag).isInstanceOf(Tag.NoTag.class);
    }

    @Test
    @DisplayName("buildTag(mon_domain_tag) retourne DomainTag")
    void buildDomainTag() {
        Tag tag = Tag.buildTag("mon_domaine_tag");
        assertThat(tag).isInstanceOf(Tag.DomainTag.class);
        assertThat(tag.tagName()).isEqualTo("mon_domaine_tag");
    }

    private static Validation noopValidation() {
        return new Validation(_ -> {}, "test", Map.of());
    }

    @Test
    @DisplayName("buildTags(Set.of()) retourne NoTag")
    void buildTagsEmpty() {
        LinkedHashSet<Tag> tags = Tag.buildTags(Set.of(), noopValidation());
        assertThat(tags).hasSize(1);
        assertThat(tags.iterator().next()).isInstanceOf(Tag.NoTag.class);
    }

    @Test
    @DisplayName("buildTags avec plusieurs tags valides")
    void buildTagsMultiple() {
        LinkedHashSet<Tag> tags = Tag.buildTags(Set.of("__DATA__", "__HIDDEN__"), noopValidation());
        assertThat(tags).hasSize(2);
        assertThat(tags.stream().anyMatch(Tag.DataTag.class::isInstance)).isTrue();
        assertThat(tags.stream().anyMatch(Tag.HiddenTag.class::isInstance)).isTrue();
    }

    @Test
    @DisplayName("FilterTextTag.instance() retourne la constante")
    void filterTextTagInstance() {
        Tag.FilterTextTag t = Tag.FilterTextTag.instance();
        assertThat(t.tagDefinition()).isEqualTo(Tag.TagDefinitions.FILTER_TEXT_TAG);
    }

    @Test
    @DisplayName("FilterListTag.instance() retourne la constante")
    void filterListTagInstance() {
        Tag.FilterListTag t = Tag.FilterListTag.instance();
        assertThat(t.tagDefinition()).isEqualTo(Tag.TagDefinitions.FILTER_LIST_TAG);
    }

    @Test
    @DisplayName("OrderStrictTag.instance() retourne la constante")
    void orderStrictTagInstance() {
        Tag.OrderStrictTag t = Tag.OrderStrictTag.instance();
        assertThat(t.tagDefinition()).isEqualTo(Tag.TagDefinitions.ORDER_STRICT_TAG);
    }
}