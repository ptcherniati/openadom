package fr.inra.oresing.domain.data.read.query;

import java.util.Locale;

public record OutPut(Locale locale, Long offset, Long limit) {
    public OutPut(final Locale locale, final Long offset, final Long limit) {
        this.locale = locale == null ? Locale.FRANCE : locale;
        this.offset = offset == null ? 0 : offset;
        this.limit = limit;
    }
}
