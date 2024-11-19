package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.data.DataColumn;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;


public record GetDataResult(Set<String> variables, List<DataRowResult> rows, Long totalRows,
                            Map<String, Map<String, fr.inra.oresing.rest.model.data.LineCheckerResult>> checkedFormatComponents,
                            Map<String, String> referenceTypeForReferencingColumns) {
}