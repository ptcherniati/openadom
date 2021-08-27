package fr.inra.oresing.rest;

import lombok.Value;

@Value
public class CsvRowValidationCheckResult {
    ValidationCheckResult validationCheckResult;
    long lineNumber;
}
