package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.application.configuration.Ltree;

import java.util.UUID;

public record RefsLinked(
        UUID id,
        String referenceType,
        Ltree naturalKey,
        Ltree hierarchicalKey,
        String __display_default,
        String __display_fr,
        String __display_en
) {
}