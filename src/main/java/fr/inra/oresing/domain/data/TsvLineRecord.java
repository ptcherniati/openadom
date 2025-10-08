package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.Mapper;
import fr.inra.oresing.domain.application.configuration.Ltree;

import java.util.*;
import java.util.stream.Collectors;

public record TsvLineRecord(String line) {
    public static TsvLineRecord of(
            Map<String, String> fields,
            String[] orderedFields,
            Mapper mapper) {

        Objects.requireNonNull(fields, "fields ne peut pas être null");
        Objects.requireNonNull(orderedFields, "orderedFields ne peut pas être null");
        Objects.requireNonNull(mapper, "jsonConverter ne peut pas être null");

        String line = buildTsvLine(fields, orderedFields, mapper);
        return new TsvLineRecord(line);
    }
    private static String buildTsvLine(
            Map<String, String> fields,
            String[] orderedFields,
            Mapper mapper) {

        StringBuilder line =
                new StringBuilder(Arrays.stream(orderedFields)
                        .map(String::toLowerCase)
                        .map(fields::get)
                        .map(field -> getField(field, mapper))
                        .collect(Collectors.joining(",")));
        line.append("\\n"); // Fin de ligne pour COPY

        return line.toString();
    }
    private static String getField(Object value, Mapper mapper) {
        if (value == null || (value instanceof String str && str.isEmpty())) {
            return "''"; // Champ vide
        } else if (value instanceof String str) {
            // Chaînes simples → entourer de guillemets simples
            return new StringBuilder().append("'").append(str.replace("'", "''")).append("'").toString();
        } else if (value instanceof UUID uuid) {
            return uuid.toString(); // UUID sans guillemets
        } else if (value instanceof Ltree ltree) {
            return ltree.getSql(); // Ltree sans guillemets
        } else if (value instanceof Map || value instanceof List) {
            // JSON → sérialiser puis entourer de guillemets simples
            String json = mapper.toJson(value);
            return new StringBuilder().append("'").append(json.replace("'", "''")).append("'").toString();
        } else {
            // Autres types → toString
            return new StringBuilder().append("'").append(value.toString().replace("'", "''")).append("'").toString();
        }
    }
}