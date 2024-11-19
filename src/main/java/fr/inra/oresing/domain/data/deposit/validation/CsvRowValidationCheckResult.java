package fr.inra.oresing.domain.data.deposit.validation;

public record CsvRowValidationCheckResult(ValidationCheckResult validationCheckResult, long lineNumber) {
    private static final long serialVersionUID = 1905122041950251207L;

    public CsvRowValidationCheckResult(final DuplicationLineValidationCheckResult validationCheckResult, final long lineNumber) {
        this(new DefaultValidationCheckResult(validationCheckResult), lineNumber);
    }
}