package fr.inra.oresing.persistence.denormalized;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record ForeignKeysBuilder(String schemaName, String tableName, Map<String, List<String>> foreignKeys){
    public String buildForeignKeys() {
        return foreignKeys().entrySet()
                .stream()
                .map(foreignKeyEntry -> {
                    String refType = foreignKeyEntry.getKey();
                    return foreignKeyEntry.getValue().stream()
                            .map(foreignKeyFieldName -> """
                                    
                                    ALTER TABLE IF EXISTS %1$s_dn.%4$s
                                        ADD CONSTRAINT "%2$s__%3$s_fk" FOREIGN KEY ("%3$s")
                                            REFERENCES %1$s_dn.%2$s(id);"""
                                    .formatted(
                                            schemaName(),
                                            refType,
                                            foreignKeyFieldName.replace("\"", ""),
                                            tableName()
                                    )
                            )
                            .collect(Collectors.joining("\n"));

                })
                .collect(Collectors.joining("\n"));
    }
}