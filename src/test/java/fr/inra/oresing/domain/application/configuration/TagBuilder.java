package fr.inra.oresing.domain.application.configuration;

public class TagBuilder {

    public static Tag.HiddenTag hiddenTag() {
        return Tag.HiddenTag.instance();
    }

    public static Tag.DataTag dataTag() {
        return Tag.DataTag.instance();
    }

    public static Tag.ReferenceTag referenceTag() {
        return Tag.ReferenceTag.instance();
    }

    public static Tag.NoTag noTag() {
        return Tag.NoTag.instance();
    }

    public static Tag.OrderTag orderTag(int order) {
        return new Tag.OrderTag(order);
    }

    public static Tag.FilterTextTag filterTextTag() {
        return Tag.FilterTextTag.instance();
    }

    public static Tag.FilterListTag filterListTag() {
        return Tag.FilterListTag.instance();
    }

    public static Tag.DomainTag domainTag(String tagName) {
        return new Tag.DomainTag(tagName);
    }
}