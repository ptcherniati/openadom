package fr.inra.oresing.domain.application.configuration.type;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.section.SectionBuilder;

import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

public record I18nType(SectionBuilder sectionBuilder,
                       Map<String, String> children,
                       boolean required,
                       boolean nullable) implements ApplicationType<Map<String, String>> {
    private I18nType(final Map<String, String> children, final RootType.CHECKING checking) {
        this(SECTION_BUILDER(),
                children,
                false,
                false);
    }

    public I18nType(final Map<String, String> children) {
        this(SECTION_BUILDER()
                        .test(children.keySet()),
                children,
                false,
                false);
    }

    public static SectionBuilder SECTION_BUILDER() {
        return SectionBuilder.getInstance()
                .withAnyOfMandatorySections(
                        Locale.availableLocales()
                                .map(Locale::getLanguage)
                                .filter(Predicate.not(String::isEmpty))
                                .sorted((a, b) -> {
                                    if (a.equals(b)) {
                                        return 0;
                                    }
                                    if (a.equals(Locale.FRENCH.getLanguage())) {
                                        return -1;
                                    }
                                    if (b.equals(Locale.FRENCH.getLanguage())) {
                                        return 1;
                                    }
                                    if (a.equals(Locale.ENGLISH.getLanguage())) {
                                        return -1;
                                    }
                                    if (b.equals(Locale.ENGLISH.getLanguage())) {
                                        return 1;
                                    }
                                    return a.compareTo(b);
                                })
                                .distinct()
                                .map(locale -> new LabelDescription(
                                        locale,
                                        StringType.EMPTY_INSTANCE()
                                ))
                                .toArray(LabelDescription[]::new)

                );
    }

    public static I18nType EMPTY_INSTANCE() {
        return new I18nType(Map.of(), RootType.CHECKING.NO_CHECK);
    }

    @Override
    public String buildExample(final int level) {
        final StringBuilder builder = getBuilder();
        for (final Map.Entry<String, String> entry : children.entrySet()) {
            final String label = entry.getKey();
            final String value = entry.getValue();
            builder.append("%1$s%2$s: %3$s\n".formatted(Strings.repeat("  ", level), label, value));
        }
        return builder.toString();
    }
}