package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.data.DataColumn;

import java.util.Optional;

public record DefaultLineCheckerResult<F extends FieldType<?>, U extends ListType<F>>(
        U value,
        F fieldTypeForOne,
        DataColumn target,
        String transformer,
        CheckerDescription checkerDescription
) implements LineCheckerResult {
    public static LineCheckerResult fromLineChecker(LineChecker lineChecker) {
        return switch (lineChecker) {
            case LineChecker.ManyChecker manyChecker -> new DefaultLineCheckerResult<>(
                    manyChecker.value(),
                    manyChecker.fieldTypeForOne(),
                    manyChecker.target(),
                    Optional.ofNullable(manyChecker.transformer()).map(LineChecker.LineTransformer::toString).orElse(""),
                    manyChecker.checkerDescription()
            );
            case LineChecker.OneChecker oneChecker -> new DefaultLineCheckerResult<>(
                    null,
                    oneChecker.fieldTypeForOne(),
                    oneChecker.target(),
                    Optional.ofNullable(oneChecker.transformer()).map(LineChecker.LineTransformer::toString).orElse(""),
                    oneChecker.checkerDescription()
            );
        };
    }
}