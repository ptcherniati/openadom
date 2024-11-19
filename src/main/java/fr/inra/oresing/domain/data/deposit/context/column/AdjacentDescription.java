package fr.inra.oresing.domain.data.deposit.context.column;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.checker.Multiplicity;

public record AdjacentDescription (String componentKey, String adjacentColumnName, ComponentPresenceConstraint mandatoryForComponentComponent, Multiplicity multiplicityForComponentComponent) {

}
