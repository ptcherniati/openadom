package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.internationalization.Internationalization;

public record AuthorizationColumnsDescription(
        Internationalization internationalization,

        //@ApiModelProperty(notes = "This column is or not visible in the submissionScope panel", required = false)
        boolean display,
        //@ApiModelProperty(notes = "This column name or the id for internationalization", required = false)
        String title,
        boolean withPeriods,
        boolean withDataGroups,
        boolean forPublic,
        boolean forRequest) {
}
