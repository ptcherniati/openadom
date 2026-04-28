package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_COMPONENT_KEY_FOR_SEARCH;
import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_FORMAT_FOR_FILTER;

public record ComponentFiltersForWordByRegexp(
        String componentKey,
        List<String> filters,
        Multiplicity multiplicity
) implements ComponentFilterSimpleSearch {
    public ComponentFiltersForWordByRegexp {
        if (componentKey == null) {
            throw new BadDownloadDatasetQuery(MISSING_COMPONENT_KEY_FOR_SEARCH);
        }
        // Idem PlainText : null accepté ( convention "(vide)" §5.7 ) ,
        // chaine vide rejetée ( saisie utilisateur incomplète ).
        if (CollectionUtils.isEmpty(filters)
                || filters.stream().anyMatch(s -> s != null && s.isEmpty())) {
            throw new BadDownloadDatasetQuery(MISSING_FORMAT_FOR_FILTER);
        }
    }
}
