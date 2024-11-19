package fr.inra.oresing.domain.data.read.query;

import java.util.List;

public sealed interface ComponentFilterSimpleSearch extends ForComponent, ComponentFilters
        permits WithFormatForFilterDate,
        ComponentFiltersByNumeric,
        ComponentFiltersByBoolean,
        ComponentFiltersByReference,
        ComponentFiltersForWordByPlainText, ComponentFiltersForWordByRegexp {
    List<String> filters();
}
