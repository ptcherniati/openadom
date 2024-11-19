package fr.inra.oresing.domain.data.deposit.context.column;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;

import java.util.Set;

public record ReferenceStaticComputedColumnDescription(
        ComponentPresenceConstraint presenceConstraint,
        Set<? extends Tag> tags,
        CheckerDescription checker,
        String headerName,
        TransformationConfiguration computation

) implements ReferenceColumnDescription, ReferenceStaticColumnDescriptionInterface {

}
