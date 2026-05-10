package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.application.configuration.Ltree;

import java.util.List;
import java.util.UUID;

/**
 * Value object représentant une référence liée à un enregistrement de données.
 * Déplacé de {@code persistence} vers {@code domain.data} (Phase 1 — indépendance domaine).
 */
public record RefsLinked(
        UUID id,
        Boolean isHierarchique,
        String referenceType,
        Ltree naturalKey,
        Ltree hierarchicalKey,
        String __display_default,
        String __display_fr,
        String __display_en,
        List<RefsLinked> parents,
        List<String> components
) {
}