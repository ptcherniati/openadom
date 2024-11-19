package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;

import java.util.List;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_COMPONENT_KEY_FOR_SEARCH;

public record ComponentFiltersForIntervalByDate(
        String componentKey,
        List<IntervalValuesDate> intervalsValues,
        Multiplicity multiplicity
) implements ComponentFiltersForIntervalByTemporal {
    public ComponentFiltersForIntervalByDate {
        if (componentKey == null) {
            throw new BadDownloadDatasetQuery(MISSING_COMPONENT_KEY_FOR_SEARCH);
        }
    }
}
