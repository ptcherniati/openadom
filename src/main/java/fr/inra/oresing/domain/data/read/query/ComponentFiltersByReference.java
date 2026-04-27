package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_COMPONENT_KEY_FOR_SEARCH;
import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_FILTER;

public record ComponentFiltersByReference(
        String componentKey,
        List<String> filters,
        Multiplicity multiplicity
) implements ComponentFilterSimpleSearch {
    public ComponentFiltersByReference {
        if (componentKey == null) {
            throw new BadDownloadDatasetQuery(MISSING_COMPONENT_KEY_FOR_SEARCH);
        }
        // null accepté ( convention "(vide)" §5.7 - filtre référence
        // peut sélectionner les lignes sans valeur pour la colonne ) ,
        // chaine vide rejetée.
        if (CollectionUtils.isEmpty(filters)
                || filters.stream().anyMatch(s -> s != null && s.isEmpty())) {
            throw new BadDownloadDatasetQuery(MISSING_FILTER);
        }
    }
}
