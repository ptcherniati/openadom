package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.data.DataColumn;

public interface LineCheckerResult {
    ListType value();

    FieldType<?> fieldTypeForOne();

    DataColumn target();

    String transformer();

    CheckerDescription checkerDescription();
}