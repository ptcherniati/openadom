package fr.inra.oresing.domain.application.configuration.migration.context;

import java.util.Map;

public record DataInfo(
        Map<String, Long> rowCountByReferenceType,
        Map<String, Boolean> hasNullValuesByComponent
) {}