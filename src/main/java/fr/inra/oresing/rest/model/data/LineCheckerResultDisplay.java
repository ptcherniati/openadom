package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataValue;

public record LineCheckerResultDisplay<F extends FieldType<?>, U extends ListType<F>>(
        U value,
        F fieldTypeForOne,
        DataColumn target,
        String transformer,
        CheckerDescription checkerDescription,
        DataValue dataValue
) implements LineCheckerResult {
    public LineCheckerResultDisplay(LineCheckerResult referenceLineChecker, DataValue dataValue) {
        this(
                (U) referenceLineChecker.value(),
                (F) referenceLineChecker.fieldTypeForOne(),
                referenceLineChecker.target(),
                referenceLineChecker.transformer(),
                referenceLineChecker.checkerDescription(),
                dataValue
        );
    }
}