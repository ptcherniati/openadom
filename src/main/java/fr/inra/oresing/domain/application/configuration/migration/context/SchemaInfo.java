package fr.inra.oresing.domain.application.configuration.migration.context;

import java.util.Map;

public record SchemaInfo(
        Map<String, Boolean> indexesByReferenceType,
        Map<String, Boolean> policiesByReferenceType
) {}