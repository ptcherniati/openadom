package fr.inra.oresing.domain.data.deposit.validation;

public record CsvRowValidationCheckResult(ValidationCheckResult validationCheckResult, long lineNumber) {
    public CsvRowValidationCheckResult(final DuplicationLineValidationCheckResult validationCheckResult, final long lineNumber) {
        this(new DefaultValidationCheckResult(validationCheckResult), lineNumber);
    }
}