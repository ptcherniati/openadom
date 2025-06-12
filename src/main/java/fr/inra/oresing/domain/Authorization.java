package fr.inra.oresing.domain;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class Authorization {
    LocalDateTimeRange timeScope = LocalDateTimeRange.always();
    private Map<String, List<Ltree>> requiredAuthorizations;

    public Authorization(final Map<String, List<Ltree>> requiredAuthorizations, final LocalDateTimeRange timeScope) {
        super();
        this.requiredAuthorizations = requiredAuthorizations;
        this.timeScope = timeScope;
    }

    public Authorization() {
        super();
    }

}