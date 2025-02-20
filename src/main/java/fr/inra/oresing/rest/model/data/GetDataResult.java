package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.rest.model.authorization.GetGrantableResult;

import java.util.List;
import java.util.Map;
import java.util.Set;


public record GetDataResult(
        long patternDefinitionCount,
        Set<String> variables,
        List<DataRowResult> rows,
        //Long totalRows,
        Map<String, Map<String, fr.inra.oresing.rest.model.data.LineCheckerResult>> checkedFormatComponents,
        Map<String, String> referenceTypeForReferencingColumns,
        Map<String, List<GetGrantableResult.ReferenceScope>> referenceScopes
) {
}