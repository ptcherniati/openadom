package fr.inra.oresing.persistence.requestbuilder.data;

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
                        Optional.ofNullable(limit()).map(SelectRequestLimit::build).orElse("")//%3$s

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
                WITH rs AS (
                    SELECT DISTINCT ON (referencetype, naturalkey)
                        referencetype, naturalkey
                    FROM %3$s.referencevalue
                    WHERE referencetype = '%4$s'%5$s
                    %%2$s --offset
                    %%3$s --limit
                )
                SELECT
                    'fr.inra.oresing.persistence.DataRows' AS "@class",
                    jsonb_build_object(
                        'rowId', array_agg(rv.id),
                        'refslinked', COALESCE(refs_agg.linked_data, '[]'::jsonb),
                        'naturalKey', rv.naturalkey,
                        'hierarchicalKey', rv.hierarchicalkey,
                        'patternColumnName', array_agg(rv.patterncolumnname),
                        'values', array_agg(rv.refvalues),
                        'refsLinkedTo', array_agg(rv.refsLinkedTo),
                        'allPatternColumnNames', array_agg(DISTINCT rv.patterncolumnname)
                    ) AS "json"
                FROM rs
                JOIN %3$s.referencevalue rv USING (referencetype, naturalkey)
                LEFT JOIN LATERAL (
                    SELECT jsonb_agg(
                        jsonb_build_object(
                            'referenceType', refs.referencetype,
                            'hierarchicalKey', refs.hierarchicalkey,
                            'naturalKey', refs.naturalkey,
                            '__display_default', refs.refvalues->'__display_default',
                            '__display_fr', refs.refvalues->'__display_fr',
                            '__display_en', refs.refvalues->'__display_en',
                            'id', refs.id
                        )
                    ) AS linked_data
                    FROM %3$s.reference_reference rr
                    JOIN %3$s.referencevalue refs ON rr.referencesby = refs.id
                    WHERE rr.referenceid = rv.id
                ) AS refs_agg ON true
                GROUP BY rv.naturalkey, rv.hierarchicalkey, refs_agg.linked_data
                 %1$s --order by;
                """;

        static final String TEMPLATE_WITH_NO_PATTERNS_DEFINITION = """
                SELECT
                    'fr.inra.oresing.persistence.DataRows' AS "@class",
                    jsonb_build_object(
                      --'rowNumber', row_number() over (),
                      --'totalRows', count(*) over (),
                      'refsLinked' , ref_aggregate.refs_linked,
                      'rowId', ARRAY[rs.id],
                      'naturalKey', rs.naturalkey,
                      'hierarchicalKey', rs.hierarchicalkey,
                      'patternColumnName', ARRAY[rs.patterncolumnname],
                      'values', ARRAY[rs.refvalues] ,
                      'refsLinkedTo', ARRAY[rs.refsLinkedTo],
                       'allPatternColumnNames',ARRAY[rs.patterncolumnname]
                    ) AS   "json"
                    FROM %3$s.referencevalue rs
                     CROSS JOIN LATERAL (
                         SELECT jsonb_agg(
                             jsonb_build_object(
                                 'referenceType', referenceby.referencetype,
                                 'hierarchicalKey', referenceby.hierarchicalkey,
                                 'naturalKey', referenceby.naturalkey,
                                 '__display_default', referenceby.refvalues->'__display_default',
                                 '__display_fr', referenceby.refvalues->'__display_fr',
                                 '__display_en', referenceby.refvalues->'__display_en',
                                 'id', referenceby.id
                             )
                         ) AS refs_linked
                         FROM %3$s.reference_reference rr
                         JOIN %3$s.referencevalue referenceby 
                             ON referenceby.id = rr.referencesby
                         WHERE rr.referenceid = rs.id
                     ) ref_aggregate
                    WHERE
                            rs.referencetype = '%4$s'%5$s
                
                %%1$s --order by
                %%2$s --offset
                %%3$s --limit
                """;

        public String build(boolean horizontalDisplay) {
            final String param1 = buildRemoveSqlSelectNotInValues.stream()
                    .map(BuildRemoveSqlSelectNotInValues::valuePathToHide)
                    .distinct()
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(""));
            final String param5 = Optional.ofNullable(requestWhereInSelect())
                    .map(SelectRequestWhere::build)
                    .orElse("");
            return (patternDefinitionCount() == 1 && horizontalDisplay) ? TEMPLATE_WITH_PATTERNS_DEFINITION : TEMPLATE_WITH_NO_PATTERNS_DEFINITION
                    .formatted(
                            param1, //select
                            param1,
                            from,
                            dataName(),
                            param5 //where
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

            return outPut().offset() >= 0 ?
                    """
                            OFFSET  %d ROWS""".formatted(outPut().offset()) :
                    "";
        }
    }

    record SelectRequestLimit(OutPut outPut) {
        public String build() {

            return (outPut().limit() != null && outPut().limit() >= 0) ?
                    """
                            LIMIT  %d""".formatted(outPut().limit()) :
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