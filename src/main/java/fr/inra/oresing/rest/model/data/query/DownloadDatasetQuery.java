package fr.inra.oresing.rest.model.data.query;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.read.query.*;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

@Getter
@Setter
public class DownloadDatasetQuery {
    Application application;
    String dataName;
    OutPut outPut;
    Long offset;
    Long limit;
    Set<String> rowIds;
    Set<Ltree> naturalKeys;
   
    Set<String> componentSelects;
   
    Set<ComponentFilters> componentFilters;
   
    Set<ComponentOrderBy> componentOrderBy;

    Set<AuthorizationDescription> authorizationDescriptions;
    boolean horizontalDisplay;

    public DownloadDatasetQuery() {
        super();
    }

    public DownloadDatasetQuery(final Long offset, final Long limit, final Set<String> componentSelects, final Set<ComponentFilters> componentFilters, final Set<ComponentOrderBy> componentOrderBy) {
        super();
        this.offset = offset;
        this.limit = limit;
        this.componentSelects = componentSelects;
        this.componentFilters = componentFilters;
        this.componentOrderBy = componentOrderBy;
        application = null;
        dataName = null;
    }

    public DownloadDatasetQuery(final Application application, final Long offset, final String dataType, final Set<String> componentSelects, final Set<ComponentFilters> componentFilters, final Set<ComponentOrderBy> componentOrderBy, final Set<AuthorizationDescription> authorizationDescriptions, final Long limit, final Set<String> rowIds) {
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

    public DownloadDatasetQuery(final Application application, final String dataType) {
        super();
        this.application = application;
        this.dataName = dataType;    }

    public static fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery build(
            final DownloadDatasetQuery downloadDatasetQuery) {
        if (CollectionUtils.isNotEmpty(downloadDatasetQuery.naturalKeys)) {
            return new DownloadDatasetQueryByNaturalKey(
                    downloadDatasetQuery.getApplication(),
                    downloadDatasetQuery.dataName,
                    new OutPut(
                            Optional.ofNullable(downloadDatasetQuery.getOutPut())
                                    .map(OutPut::locale)
                                    .orElse(Locale.FRENCH),
                            downloadDatasetQuery.getOffset(),
                            downloadDatasetQuery.getLimit()
                    ),
                    downloadDatasetQuery.componentSelects,

                    Optional.ofNullable(downloadDatasetQuery.componentOrderBy)
                            .map(componentOrderBy -> componentOrderBy.stream()
                                    .map(componentOrderBy1 -> ComponentOrderBy.build(
                                            componentOrderBy1,
                                            Objects.requireNonNull(downloadDatasetQuery.getApplication().findData(downloadDatasetQuery.getDataName()).orElse(null))
                                    ))
                                    .collect(Collectors.toSet())
                            ).orElse(null),
                    downloadDatasetQuery.naturalKeys,
                    downloadDatasetQuery.isHorizontalDisplay()
            );
        }
        if (CollectionUtils.isNotEmpty(downloadDatasetQuery.getRowIds())) {
            return new DownloadDatasetQueryByRowId(
                    downloadDatasetQuery.getApplication(),
                    downloadDatasetQuery.dataName,
                    new OutPut(
                            Optional.ofNullable(downloadDatasetQuery.getOutPut())
                                    .map(OutPut::locale)
                                    .orElse(Locale.FRENCH),
                            downloadDatasetQuery.getOffset(),
                            downloadDatasetQuery.getLimit()
                    ),
                    downloadDatasetQuery.componentSelects,

                    Optional.ofNullable(downloadDatasetQuery.componentOrderBy)
                            .map(componentOrderBy -> componentOrderBy.stream()
                                    .map(componentOrderBy1 -> ComponentOrderBy.build(
                                            componentOrderBy1,
                                            Objects.requireNonNull(downloadDatasetQuery.getApplication().findData(downloadDatasetQuery.getDataName()).orElse(null))
                                    ))
                                    .collect(Collectors.toSet())
                            ).orElse(null),
                    downloadDatasetQuery.rowIds.stream()
                            .filter(Objects::nonNull)
                            .map(UUID::fromString)
                            .map(DataRowIds::new)
                            .collect(Collectors.toSet()),
                    downloadDatasetQuery.isHorizontalDisplay()
            );
        }
        if (CollectionUtils.isNotEmpty(downloadDatasetQuery.componentFilters)) {
            return new DownloadDatasetQueryAdvancedSearch(
                    downloadDatasetQuery.getApplication(),
                    downloadDatasetQuery.dataName,
                    new OutPut(
                            Optional.ofNullable(downloadDatasetQuery.getOutPut())
                                    .map(OutPut::locale)
                                    .orElse(Locale.FRENCH),
                            downloadDatasetQuery.getOffset(),
                            downloadDatasetQuery.getLimit()
                    ),
                    downloadDatasetQuery.componentSelects,
                    ComponentFilters.build(
                            downloadDatasetQuery.componentFilters,
                            downloadDatasetQuery.getApplication().findData(downloadDatasetQuery.getDataName()).orElse(null)),

                    Optional.ofNullable(downloadDatasetQuery.componentOrderBy)
                            .map(componentOrderBy -> componentOrderBy.stream()
                                    .map(componentOrderBy1 -> ComponentOrderBy.build(
                                            componentOrderBy1,
                                            Objects.requireNonNull(downloadDatasetQuery.getApplication().findData(downloadDatasetQuery.getDataName()).orElse(null))
                                    ))
                                    .collect(Collectors.toSet())
                            ).orElse(null),
                    downloadDatasetQuery.isHorizontalDisplay()
            );
        }
        return new DownloadDatasetQueryNoFilter(
                downloadDatasetQuery.getApplication(),
                downloadDatasetQuery.dataName,
                new OutPut(
                        Optional.ofNullable(downloadDatasetQuery.getOutPut())
                                .map(OutPut::locale)
                                .orElse(Locale.FRENCH),
                        downloadDatasetQuery.getOffset(),
                        downloadDatasetQuery.getLimit()
                ),
                downloadDatasetQuery.componentSelects,
                Optional.ofNullable(downloadDatasetQuery.componentOrderBy)
                        .map(componentOrderBy -> componentOrderBy.stream()
                                .map(componentOrderBy1 -> ComponentOrderBy.build(
                                        componentOrderBy1,
                                        Objects.requireNonNull(downloadDatasetQuery.getApplication().findData(downloadDatasetQuery.getDataName()).orElse(null))
                                ))
                                .collect(Collectors.toSet())
                        ).orElse(null),
                downloadDatasetQuery.isHorizontalDisplay()
        );

    }

    public long patternDefinitionCount() {
        return application.patternDefinitionCount(dataName);
    }


}