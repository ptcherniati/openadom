package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataValue;

public record LineCheckerResultDisplay<FT extends FieldType, U extends ListType<FT>>(
        U value,
        FT fieldTypeForOne,
        DataColumn target,
        String transformer,
        CheckerDescription checkerDescription,
        DataValue dataValue
) implements LineCheckerResult {
    public LineCheckerResultDisplay(LineCheckerResult referenceLineChecker, DataValue dataValue) {
        this(
                (U) referenceLineChecker.value(),
                (FT) referenceLineChecker.fieldTypeForOne(),
                referenceLineChecker.target(),
                referenceLineChecker.transformer(),
                referenceLineChecker.checkerDescription(),
                dataValue
        );
    }
}
