package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;

import java.util.List;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_COMPONENT_KEY_FOR_SEARCH;
import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.MISSING_INTERVAL_VALUE;

public record ComponentFiltersForIntervalByDateTime(
        String componentKey,
        List<IntervalValuesDateTime> intervalsValues,
        Multiplicity multiplicity
) implements ComponentFiltersForIntervalByTemporal {
    public ComponentFiltersForIntervalByDateTime {
        if (componentKey == null) {
            throw new BadDownloadDatasetQuery(MISSING_COMPONENT_KEY_FOR_SEARCH);
        }
        if (intervalsValues == null) {
            throw new BadDownloadDatasetQuery(MISSING_INTERVAL_VALUE);
        }
    }
}
