package fr.inra.oresing.persistence.normalized;

import fr.inra.oresing.domain.application.normalized.ReferenceJoin;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public record FromBuilder(
        String schemaName,
        List<String> refValuesTable,
        List<ReferenceJoin> referenceJoin
) {


    public String buildFrom() {
        return """
                
                \t\t%1$s.referencevalue
                \t\t%2$s%3$s
                \t\t%4$s%5$s
                \t\t%6$s
                """.formatted(
                schemaName(),
                refValuesTable().isEmpty() ? "" : ",",
                refValuesTable().isEmpty() ? "" : """
                        -- add simple fields
                                JSON_TABLE(
                                            refvalues, 
                                            '$' COLUMNS (
                                                %1$s
                                            )
                                         ) AS val"""
                        .formatted(
                                refValuesTable().stream()
                                        .filter(Objects::nonNull)
                                        .collect(
                                                Collectors.joining(",\n\t\t\t\t\t\t")
                                        )
                        ),
                referenceJoin().isEmpty() ? "" : ",",
                referenceJoin().isEmpty() ? "" : """                                                 
                        -- add references fields
                                JSON_TABLE(
                                            refslinkedto, 
                                            '$' COLUMNS (
                                                %1$s
                                            )
                                        ) AS refs"""
                        .formatted(referenceJoin().stream()
                                .filter(Objects::nonNull)
                                .map(ReferenceJoin::refsLinkedToTable)
                                .collect(Collectors.joining(",\n\t\t\t\t")
                                )
                        ),
                referenceJoin().isEmpty() ? "" :
                        referenceJoin().stream()
                                .filter(Objects::nonNull)
                                .map(ReferenceJoin::referenceJoin)
                                .collect(Collectors.joining("\n\t\t"))
        );
    }
}