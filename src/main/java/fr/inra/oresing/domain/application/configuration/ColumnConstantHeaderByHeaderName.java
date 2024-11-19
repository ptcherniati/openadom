package fr.inra.oresing.domain.application.configuration;

import java.util.List;
import java.util.Locale;

public record ColumnConstantHeaderByHeaderName(
        ConstantImportHeaderType type,
        int rowNumber,
        String headerName,
        List<Locale>langRestrictions
) implements ColumnConstantHeader {
}
