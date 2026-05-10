package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.authorization.GetGrantableResult;

import java.util.List;
import java.util.Map;
import java.util.Set;


public record GetDataResult(
        long patternDefinitionCount,
        Set<String> variables,
        List<DataRowResult> rows,
        //Long totalRows,
        List<fr.inra.oresing.persistence.FilterList> filterLists, Map<String, Map<String, fr.inra.oresing.rest.model.data.LineCheckerResult>> checkedFormatComponents,
        Map<String, List<GetGrantableResult.ReferenceScope>> referenceScopes
) {
}