package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.Application;

import java.util.Set;

public record DownloadDatasetQueryNoFilter(
        boolean hasPatternDefinition,
        Application application,
        String dataName,
        OutPut outPut,
        Set<String> componentSelects,
        Set<ComponentOrderBy> componentOrderBy
) implements DownloadDatasetQuery {
}
