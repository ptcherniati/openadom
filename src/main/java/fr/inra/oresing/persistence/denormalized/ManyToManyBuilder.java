package fr.inra.oresing.persistence.denormalized;

import java.util.List;
import java.util.stream.Collectors;

public record ManyToManyBuilder(String schemaName, String tableName, java.util.Map<String, List<String>> normalizedJoinManyToManies) {

    public String buildManyToMany() {
        return normalizedJoinManyToManies().entrySet().stream()
                .map(manytoManyEntry ->
                        """
                                CREATE TABLE %1$s_dn.%2$s_%3$s (
                                  %2$s_id UUID NOT NULL REFERENCES %1$s_dn.%2$s(id),
                                  %3$s_id UUID NULL REFERENCES %1$s_dn.%3$s(id), -- NULL autorisé pour les éléments vides
                                  origine_colonne TEXT NOT NULL,                  -- nom de la colonne d'origine
                                  position INT NOT NULL,                          -- position dans le tableau d'origine
                                  PRIMARY KEY (%2$s_id, %3$s_id, origine_colonne, position)
                                );
                                
                                %4$s"""
                                .formatted(
                                        schemaName(),
                                        tableName(),
                                        manytoManyEntry.getKey(),
                                        populateManyClass(manytoManyEntry.getKey(), manytoManyEntry.getValue())
                                )
                )
                .collect(Collectors.joining("\""));
    }

    private String populateManyClass(String foreignTable, List<String> referencedColumns) {
        return referencedColumns.stream()
                .map(referencedColumn ->
                        """
                                INSERT INTO %1$s_dn.%2$s_%3$s(
                                    %2$s_id, 
                                    %3$s_id, 
                                    origine_colonne, 
                                    position
                                )
                                SELECT 
                                    id, 
                                    local_value, 
                                    '%4$s_id',
                                    local_pos
                                FROM
                                    %1$s_dn.%2$s,
                                    UNNEST("%4$s_id") WITH ORDINALITY sub(local_value, local_pos)
                                ON CONFLICT DO NOTHING;;
                                """.formatted(
                                schemaName(),
                                tableName(),
                                foreignTable,
                                referencedColumn
                        ))
                .collect(Collectors.joining("\n"));
    }
}