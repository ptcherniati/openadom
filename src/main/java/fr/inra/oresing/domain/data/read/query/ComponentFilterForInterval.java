package fr.inra.oresing.domain.data.read.query;

public sealed interface ComponentFilterForInterval extends ForComponent, ComponentFilters permits ComponentFiltersForIntervalByTemporal, ComponentFiltersForIntervalByNumeric {

}
