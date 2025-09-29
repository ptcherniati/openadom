package fr.inra.oresing.rest.services;

import java.util.ArrayList;
import java.util.List;

record Sql(
        List<String> select,
        List<String> refValuesTable,
        List<ReferenceJoin> referenceJoin,
        List<String> indexes,
        List<String> foreignKeys
) {
    public Sql() {
        this(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
    }
}