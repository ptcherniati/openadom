package fr.inra.oresing.persistence.requestBuilder.data;

import fr.inra.oresing.domain.data.read.query.*;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

record DeleteRequest(
        DownloadDatasetQuery downloadDatasetQuery,
        MapSqlParameterSource parameterSource,
        DeleteRequestRequest deleteRequestRequest
) {
    SqlRequest build() {
        String delete = Optional.ofNullable(deleteRequestRequest()).map(DeleteRequestRequest::build).orElse("");
        return new SqlRequest(
                delete,
                parameterSource()
        );
    }

    record DeleteRequestRequest(
            String dataname,
            String from,
            SelectRequestWhereInSelect requestWhereInSelect
    ) {
        static final String TEMPLATE = """
                    DELETE
                        FROM %1$s.referencevalue rs
                    WHERE
                        (('"'||referencetype||'"')::jsonb) @@ 'exists($ ? (@ == "%2$s"))'
                        %3$s
                    RETURNING id;
                """;

        public String build() {
            return TEMPLATE.formatted(
                    from,
                    dataname(),
                    Optional.ofNullable(requestWhereInSelect()).map(SelectRequestWhere::build).orElse("")
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
                            OFFSET  %d ROWS
                            """.formatted(outPut().offset()) :
                    "";
        }
    }

    record SelectRequestLimit(OutPut outPut) {
        public String build() {

            return (outPut().limit() != null && outPut().limit() >= 0) ?
                    """
                            OFFSET  %d ROWS
                            """.formatted(outPut().limit()) :
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
                                            case final ComponentDateType componentDateType ->
                                                    "COMPOSITE_DATE::TIMESTAMP";
                                            case final ComponentNumericType componentNumericType -> "NUMERIC";
                                            case final ComponentReferenceType componentReferenceType -> "LTREE";
                                            case final ComponentTextType componentTextType -> "TEXT";
                                        };
                                        return """
                                                (json #>>'{%1$s}')::%2$s"""
                                                .formatted(
                                                        DataRequestBuilder.sanitize(vckob.componentKey()),
                                                        cast
                                                );
                                    }
                            )
                            .collect(Collectors.joining(
                                    ",\n")
                            ) :
                    "";
        }

    }

}
