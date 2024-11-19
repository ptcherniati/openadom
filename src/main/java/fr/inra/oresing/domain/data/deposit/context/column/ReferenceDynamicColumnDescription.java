package fr.inra.oresing.domain.data.deposit.context.column;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.Tag;

import java.util.Map;
import java.util.Set;

public record ReferenceDynamicColumnDescription(
        ComponentPresenceConstraint presenceConstraint,
        Set<? extends Tag> tags,
        Map<String, String> internationalizationName,
        String headerPrefix,
        String reference,
        String referenceColumnToLookForHeader
)
        implements ReferenceColumnDescription {
}
