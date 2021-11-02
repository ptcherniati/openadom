package fr.inra.oresing.rest;

import lombok.Value;

@Value
public class CsvRowValidationCheckResult {
    private static final long serialVersionUID = 1905122041950251207L;
    ValidationCheckResult validationCheckResult;
    long lineNumber;
}