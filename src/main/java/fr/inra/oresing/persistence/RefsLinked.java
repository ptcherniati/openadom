package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.application.configuration.Ltree;
import org.w3c.dom.stylesheets.LinkStyle;

import java.util.List;
import java.util.UUID;

public record RefsLinked(
        UUID id,
        String referenceType,
        Ltree naturalKey,
        Ltree hierarchicalKey,
        String __display_default,
        String __display_fr,
        String __display_en,
        List<RefsLinked> parents
) {
}