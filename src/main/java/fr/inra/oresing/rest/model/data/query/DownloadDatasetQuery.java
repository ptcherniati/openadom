package fr.inra.oresing.rest.model.data.query;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.PatternComponent;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.data.read.query.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;

import javax.annotation.Nullable;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Getter
@Setter
public class DownloadDatasetQuery {
    Application application;
    String dataName;
    String locale;
    Long offset;
    Long limit;
    Set<String> rowIds;
    Set<Ltree> naturalKeys;
    @Nullable
    Set<String> componentSelects;
    @Nullable
    Set<ComponentFilters> componentFilters;
    @Nullable
    Set<ComponentOrderBy> componentOrderBy;

    Set<AuthorizationDescription> authorizationDescriptions;

    public DownloadDatasetQuery() {
        super();
    }

    public DownloadDatasetQuery(final Long offset, final Long limit, @Nullable final Set<String> componentSelects, @Nullable final Set<ComponentFilters> componentFilters, @Nullable final Set<ComponentOrderBy> componentOrderBy) {
        super();
        this.offset = offset;
        this.limit = limit;
        this.componentSelects = componentSelects;
        this.componentFilters = componentFilters;
        this.componentOrderBy = componentOrderBy;
        application = null;
        dataName = null;
    }

    public DownloadDatasetQuery(final Application application, final Long offset, final String dataType, @Nullable final Set<String> componentSelects, @Nullable final Set<ComponentFilters> componentFilters, @Nullable final Set<ComponentOrderBy> componentOrderBy, final Set<AuthorizationDescription> authorizationDescriptions, final Long limit, final Set<String> rowIds) {
        super();
        this.dataName = dataType;
        this.offset = offset;
        this.limit = limit;
        this.rowIds = rowIds;
        this.componentSelects = componentSelects;
        this.componentFilters = componentFilters;
        this.componentOrderBy = componentOrderBy;
        this.authorizationDescriptions = authorizationDescriptions;
        this.application = application;

    }

    public boolean hasPatternDefinition() {
        return application.hasPatternDefinition(dataName);
    }

    ;

    public DownloadDatasetQuery(final Application application, final String dataType) {
        super();
        this.application = application;
        this.dataName = dataType;
    }

    public static fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery build(
            final DownloadDatasetQuery downloadDatasetQuery) {
        if (CollectionUtils.isNotEmpty(downloadDatasetQuery.naturalKeys)) {
            return new DownloadDatasetQueryByNaturalKey(
                    downloadDatasetQuery.hasPatternDefinition(),
                    downloadDatasetQuery.getApplication(),
                    downloadDatasetQuery.dataName,
                    new OutPut(
                            Optional.ofNullable(downloadDatasetQuery.getLocale())
                                    .map(Locale::of)
                                    .orElse(Locale.FRENCH),
                            downloadDatasetQuery.getOffset(),
                            downloadDatasetQuery.getLimit()
                    ),
                    downloadDatasetQuery.componentSelects,

                    Optional.ofNullable(downloadDatasetQuery.componentOrderBy)
                            .map(componentOrderBy -> componentOrderBy.stream()
                                    .map(componentOrderBy1 -> ComponentOrderBy.build(
                                            componentOrderBy1,
                                            downloadDatasetQuery.getApplication().findData(downloadDatasetQuery.getDataName()).orElse(null)
                                    ))
                                    .collect(Collectors.toSet())
                            ).orElse(null),
                    downloadDatasetQuery.naturalKeys
            );
        }
        if (CollectionUtils.isNotEmpty(downloadDatasetQuery.rowIds)) {
            return new DownloadDatasetQueryByRowId(
                    downloadDatasetQuery.hasPatternDefinition(),
                    downloadDatasetQuery.getApplication(),
                    downloadDatasetQuery.dataName,
                    new OutPut(
                            Optional.ofNullable(downloadDatasetQuery.getLocale())
                                    .map(Locale::of)
                                    .orElse(Locale.FRENCH),
                            downloadDatasetQuery.getOffset(),
                            downloadDatasetQuery.getLimit()
                    ),
                    downloadDatasetQuery.componentSelects,

                    Optional.ofNullable(downloadDatasetQuery.componentOrderBy)
                            .map(componentOrderBy -> componentOrderBy.stream()
                                    .map(componentOrderBy1 -> ComponentOrderBy.build(
                                            componentOrderBy1,
                                            downloadDatasetQuery.getApplication().findData(downloadDatasetQuery.getDataName()).orElse(null)
                                    ))
                                    .collect(Collectors.toSet())
                            ).orElse(null),
                    downloadDatasetQuery.rowIds.stream()
                            .map(UUID::fromString)
                            .map(DataRowIds::new)
                            .collect(Collectors.toSet())
            );
        }
        if (CollectionUtils.isNotEmpty(downloadDatasetQuery.componentFilters)) {
            return new DownloadDatasetQueryAdvancedSearch(
                    downloadDatasetQuery.hasPatternDefinition(),
                    downloadDatasetQuery.getApplication(),
                    downloadDatasetQuery.dataName,
                    new OutPut(
                            Optional.ofNullable(downloadDatasetQuery.getLocale())
                                    .map(Locale::of)
                                    .orElse(Locale.FRENCH),
                            downloadDatasetQuery.getOffset(),
                            downloadDatasetQuery.getLimit()
                    ),
                    downloadDatasetQuery.componentSelects,
                    ComponentFilters.build(
                            downloadDatasetQuery.componentFilters,
                            downloadDatasetQuery.authorizationDescriptions,
                            downloadDatasetQuery.getApplication().findData(downloadDatasetQuery.getDataName()).orElse(null)),

                    Optional.ofNullable(downloadDatasetQuery.componentOrderBy)
                            .map(componentOrderBy -> componentOrderBy.stream()
                                    .map(componentOrderBy1 -> ComponentOrderBy.build(
                                            componentOrderBy1,
                                            downloadDatasetQuery.getApplication().findData(downloadDatasetQuery.getDataName()).orElse(null)
                                    ))
                                    .collect(Collectors.toSet())
                            ).orElse(null)
            );
        }
        return new DownloadDatasetQueryNoFilter(
                downloadDatasetQuery.hasPatternDefinition(),
                downloadDatasetQuery.getApplication(),
                downloadDatasetQuery.dataName,
                new OutPut(
                        Optional.ofNullable(downloadDatasetQuery.getLocale())
                                .map(Locale::of)
                                .orElse(Locale.FRENCH),
                        downloadDatasetQuery.getOffset(),
                        downloadDatasetQuery.getLimit()
                ),
                downloadDatasetQuery.componentSelects,
                Optional.ofNullable(downloadDatasetQuery.componentOrderBy)
                        .map(componentOrderBy -> componentOrderBy.stream()
                                .map(componentOrderBy1 -> ComponentOrderBy.build(
                                        componentOrderBy1,
                                        downloadDatasetQuery.getApplication().findData(downloadDatasetQuery.getDataName()).orElse(null)
                                ))
                                .collect(Collectors.toSet())
                        ).orElse(null)
        );

    }


}