package fr.inra.oresing.domain.data.deposit.validation.transformer.data;

import fr.inra.oresing.domain.data.DataDatum;
import fr.inra.oresing.domain.data.LinkedLines;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import java.util.List;
import java.util.Map;

public record ReferenceDatumAfterChecking(
        long lineNumber,
        String patternColumnName,
        DataDatum referenceDatumBeforeChecking,
        DataDatum referenceDatumAfterChecking,
        Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo,
        List<CsvRowValidationCheckResult> errors
) {
}