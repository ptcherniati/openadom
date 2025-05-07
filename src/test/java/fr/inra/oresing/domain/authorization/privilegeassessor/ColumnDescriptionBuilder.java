package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.internationalization.Internationalization;
import fr.inra.oresing.rest.model.authorization.GetGrantableResult;

import java.util.Locale;

public class ColumnDescriptionBuilder {
    public static final ColumnDescriptionBuilder builder() {
        return new ColumnDescriptionBuilder();
    }

    private boolean isDisplay = false;
    private String title = "title";
    private boolean withPeriod = false;
    private boolean withDataGroup = false;
    private boolean forPublic = false;
    private boolean forRequest = false;
    private Internationalization internationalisationName = new Internationalization();

    public GetGrantableResult.ColumnDescription build() {
        return new GetGrantableResult.ColumnDescription(
                isDisplay,
                title,
                withPeriod,
                withDataGroup,
                forPublic,
                forRequest,
                internationalisationName
        );
    }

    public ColumnDescriptionBuilder withIsDisplay(boolean isDisplay) {
        this.isDisplay = isDisplay;
        return this;
    }

    public ColumnDescriptionBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public ColumnDescriptionBuilder withPeriod(boolean withPeriod) {
        this.withPeriod = withPeriod;
        return this;
    }

    public ColumnDescriptionBuilder withDataGroup(boolean withDataGroup) {
        this.withDataGroup = withDataGroup;
        return this;
    }

    public ColumnDescriptionBuilder isRequest(boolean forRequest) {
        this.forRequest = forRequest;
        return this;
    }

    public ColumnDescriptionBuilder isPublic(boolean isPublic) {
        this.forPublic = isPublic;
        return this;
    }

    public ColumnDescriptionBuilder withInternationalisationName(Internationalization internationalisationName) {
        this.internationalisationName = internationalisationName;
        return this;
    }

    public ColumnDescriptionBuilder isDisplay(boolean isDisplay) {
        this.isDisplay = isDisplay;
        return this;
    }

    public ColumnDescriptionBuilder addLocale(Locale locale, String value) {
        this.internationalisationName.put(locale, value);
        return this;
    }
}