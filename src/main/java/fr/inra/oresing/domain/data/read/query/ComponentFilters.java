package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.checker.Multiplicity;

public sealed interface ComponentFilters permits NoComponentFilters, ComponentFilterForInterval, ComponentFilterSimpleSearch {
    default Multiplicity multiplicity() {
        return Multiplicity.ONE;
    }
}
