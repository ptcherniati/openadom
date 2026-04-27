package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import org.apache.commons.collections4.CollectionUtils;

import java.util.List;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_COMPONENT_KEY_FOR_SEARCH;
import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_FORMAT_FOR_FILTER;

public record ComponentFiltersForWordByPlainText(
        String componentKey,
        List<String> filters,
        Multiplicity multiplicity
) implements ComponentFilterSimpleSearch {
    public ComponentFiltersForWordByPlainText {
        if (componentKey == null) {
            throw new BadDownloadDatasetQuery(MISSING_COMPONENT_KEY_FOR_SEARCH);
        }
        // Validation : la liste doit avoir au moins une entrée , et chaque
        // entrée non-null doit être non vide. On accepte explicitement
        // null comme convention "(vide)" - cf. §5.7 de FILTER_TEXT_LIST.md.
        if (CollectionUtils.isEmpty(filters)
                || filters.stream().anyMatch(s -> s != null && s.isEmpty())) {
            throw new BadDownloadDatasetQuery(MISSING_FORMAT_FOR_FILTER);
        }
    }
}
