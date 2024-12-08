package fr.inra.oresing.persistence.requestBuilder.data;

import fr.inra.oresing.domain.data.read.query.*;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

record SelectRequest(
        DownloadDatasetQuery downloadDatasetQuery,
        MapSqlParameterSource parameterSource,
        SelectRequestRequest selectRequestRequest,
        SelectRequestOrderBy orderBy,
        SelectRequestOffset offset,
        SelectRequestLimit limit
) {
    SqlRequest build() {
        String select = Optional
                .ofNullable(selectRequestRequest())
                .map(request -> request.build(downloadDatasetQuery().horizontalDisplay()))
                .orElse("")
                .formatted(
                        Optional.ofNullable(orderBy()).map(SelectRequestOrderBy::build).orElse(""), //$1%s
                        Optional.ofNullable(offset()).map(SelectRequestOffset::build).orElse(""),//$2%s
                        Optional.ofNullable(limit()).map(SelectRequestLimit::build).orElse("")//$3%s

                );
        return new SqlRequest(
                select,
                parameterSource()
        );
    }

    record SelectRequestRequest(
            long patternDefinitionCount,
            String dataName,
            List<BuildRemoveSqlSelectNotInValues> buildRemoveSqlSelectNotInValues,
            String from,
            SelectRequestWhereInSelect requestWhereInSelect
    ) {
        static final String TEMPLATE_WITH_PATTERNS_DEFINITION = """
                 with  rs as (
                 	SELECT DISTINCT ON (referencetype, naturalkey)
                	 referencetype, naturalkey
                	 FROM %3$s.referencevalue rs
                    WHERE
                            rs.referencetype = '%4$s'%5$s
                %%2$s --offset
                %%3$s --limit
                )
                SELECT
                          'fr.inra.oresing.persistence.DataRows' AS "@class",
                          jsonb_build_object(
                              'rowNumber', row_number() over (),
                              'totalRows', count(*) over (),
                              'rowId', array_agg(id),
                              'naturalKey', naturalkey,
                              'hierarchicalKey', hierarchicalkey,
                              'patternColumnName', array_agg(patterncolumnname),
                              'values', array_agg(refvalues) ,
                              'refsLinkedTo', array_agg(refsLinkedTo),
                               'allPatternColumnNames',array_agg(DISTINCT patterncolumnname)
                          ) AS   "json"
                    FROM rs
                    JOIN %3$s.referencevalue rv USING (referencetype, naturalkey)
                    GROUP BY naturalkey, hierarchicalkey
                    %%1$s --order by 
                """;

        static final String TEMPLATE_WITH_NO_PATTERNS_DEFINITION = """
                           SELECT 
                               'fr.inra.oresing.persistence.DataRows' AS "@class",
                               jsonb_build_object(
                                 'rowNumber', row_number() over (),
                                 'totalRows', count(*) over (),
                                 'rowId', ARRAY[id],
                                 'naturalKey', naturalkey,
                                 'hierarchicalKey', hierarchicalkey,
                                 'patternColumnName', ARRAY[patterncolumnname],
                                 'values', ARRAY[refvalues] ,
                                 'refsLinkedTo', ARRAY[refsLinkedTo],
                                  'allPatternColumnNames',ARRAY[patterncolumnname]
                ) AS   "json"
                           	FROM %3$s.referencevalue rs
                               WHERE
                                       rs.referencetype = '%4$s'%5$s
                
                           %%1$s --order by 
                           %%2$s --offset
                           %%3$s --limit
                """;

        public String build(boolean horizontalDisplay) {
            return ((patternDefinitionCount() > 1 || (patternDefinitionCount()==1 && horizontalDisplay))? TEMPLATE_WITH_PATTERNS_DEFINITION : TEMPLATE_WITH_NO_PATTERNS_DEFINITION)
                    .formatted(
                            buildRemoveSqlSelectNotInValues.stream()
                                    .map(BuildRemoveSqlSelectNotInValues::valuePathToHide)
                                    .distinct()
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.joining("")),
                            buildRemoveSqlSelectNotInValues.stream()
                                    .map(BuildRemoveSqlSelectNotInValues::refsLinkedToPathToHide)
                                    .distinct()
                                    .filter(Objects::nonNull)
                                    .collect(Collectors.joining("")),
                            from,
                            dataName(),
                            Optional.ofNullable(requestWhereInSelect())
                                    .map(SelectRequestWhere::build)
                                    .orElse("")
                    );
        }
    }

    record SelectRequestSelect(String from) {
        static final String TEMPLATE = """
                SELECT *
                FROM %1$s
                """;

        String build() {
            return TEMPLATE
                    .formatted(
                            from()
                    );
        }
    }

    record SelectRequestOffset(OutPut outPut) {
        public String build() {

            return (outPut().offset() != null && outPut().offset() >= 0) ?
                    """
                            OFFSET  %d ROWS""".formatted(outPut().offset()) :
                    "";
        }
    }

    record SelectRequestLimit(OutPut outPut) {
        public String build() {

            return (outPut().limit() != null && outPut().limit() >= 0) ?
                    """
                            LIMIT  %d  """.formatted(outPut().limit()) :
                    "";
        }
    }

    record SelectRequestOrderBy(Set<ComponentOrderBy> componentOrderBy) {
        public String build() {
            return (CollectionUtils.isNotEmpty(componentOrderBy())) ?
                    componentOrderBy().stream()
                            .map(vckob -> {
                                        final String cast = switch (vckob.sqlType()) {
                                            case final ComponentBooleanType componentBooleanType -> "BOOL";
                                            case final ComponentDateType componentDateType -> "COMPOSITE_DATE::TIMESTAMP";
                                            case final ComponentNumericType componentNumericType -> "NUMERIC";
                                            case final ComponentReferenceType componentReferenceType -> "LTREE";
                                            case final ComponentTextType componentTextType -> "TEXT";
                                        };
                                        return """
                                                (refvalues #>>'{%1$s}')::%2$s"""
                                                .formatted(
                                                        DataRequestBuilder.sanitize(vckob.componentKey()),
                                                        cast
                                                );
                                    }
                            )
                            .collect(Collectors.joining(
                                    ",\n", "ORDER BY ", "")
                            ) :
                    "";
        }

    }

}
