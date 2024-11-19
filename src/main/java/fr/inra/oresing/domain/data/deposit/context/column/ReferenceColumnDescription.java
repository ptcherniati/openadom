package fr.inra.oresing.domain.data.deposit.context.column;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.Tag;

import java.util.Set;

public sealed interface ReferenceColumnDescription permits ReferenceDynamicColumnDescription, ReferenceStaticColumnDescription, ReferenceStaticComputedColumnDescription, ReferenceStaticNotComputedColumnDescription {
    ComponentPresenceConstraint presenceConstraint();

    Set<? extends Tag> tags();

}
