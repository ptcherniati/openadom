package fr.inra.oresing.domain.data.deposit.validation.transformer.data;

import com.google.common.collect.ImmutableList;
import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.LinkedLines;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;

import java.util.Map;

public record ReferenceDatumAfterChecking(
        long lineNumber,
        String patternColumnName,
        DataDatum referenceDatumBeforeChecking,
        DataDatum referenceDatumAfterChecking,
        Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo,
        ImmutableList<CsvRowValidationCheckResult> errors
) {
}