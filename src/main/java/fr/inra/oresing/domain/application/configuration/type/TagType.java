package fr.inra.oresing.domain.application.configuration.type;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record TagType(
        SectionBuilder sectionBuilder,
        I18nType children,
        boolean required,
        boolean nullable
) implements ApplicationType<I18nType> {
    private TagType(final I18nType children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

    public TagType(final I18nType children) {
        this(SECTION_BUILDER()
                        .test(Optional.ofNullable(children)
                                .map(I18nType::children)
                                .map(Map::keySet)
                                .orElseGet(Set::of)
                        ),
                children,
                false,
                false);
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withLocalType();
    }

    public static TagType EMPTY_INSTANCE() {
        return new TagType(I18nType.EMPTY_INSTANCE(), RootType.CHECKING.NO_CHECK);
    }

    @Override
    public String buildExample(final int level) {
        final StringBuilder builder = getBuilder();
        for (final Map.Entry<String, String> entry : children().children().entrySet()) {
            final String label = entry.getKey();
            final String value = entry.getValue();
            builder.append("%1$s%2$s: %3$s\n".formatted(Strings.repeat("  ", level), label, value));
        }
        return builder.toString();
    }
}