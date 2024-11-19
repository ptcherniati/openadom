package fr.inra.oresing.domain.data.deposit.context.column;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;

import java.util.Set;

public record ReferenceStaticColumnDescription(
        ComponentPresenceConstraint presenceConstraint,
        Set<? extends Tag> tags,
        CheckerDescription checker,
        String headerName
) implements ReferenceColumnDescription, ReferenceStaticColumnDescriptionInterface {

}
