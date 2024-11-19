package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.Application;

import java.util.Locale;
import java.util.Set;

public record DownloadDatasetQueryOnlyMetadata(
        boolean hasPatternDefinition,
        Application application,
        String dataName,
        Locale locale
) implements DownloadDatasetQuery {


    public static DownloadDatasetQuery of(DownloadDatasetQuery downloadDatasetQuery) {
        return new DownloadDatasetQueryOnlyMetadata(
                downloadDatasetQuery.hasPatternDefinition(),
                downloadDatasetQuery.application(),
                downloadDatasetQuery.dataName(),
                downloadDatasetQuery.outPut().locale());
    }

    @Override
    public Set<ComponentOrderBy> componentOrderBy() {
        return Set.of();
    }

    @Override
    public Set<String> componentSelects() {
        return Set.of();
    }

    @Override
    public OutPut outPut() {
        return new OutPut(locale(), 0L, 0L);
    }
}
