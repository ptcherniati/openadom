package fr.inra.oresing.checker;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.model.CsvRowValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.DefaultValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.List;

/**
 * Exception levée si un jeu de données est incorrect (aussi bien entêtes que contenu)
 */
public class InvalidDatasetContentException extends OreSiTechnicalException {

    private final List<CsvRowValidationCheckResult> errors;

    private InvalidDatasetContentException(List<CsvRowValidationCheckResult> errors) {
        super("Erreurs rencontrées à l'import du fichier");
        this.errors = errors;
    }

    public static InvalidDatasetContentException forUnexpectedHeaderColumn(String expected, String actual, int headerLine) {
        return newInvalidDatasetContentException(headerLine, "unexpectedHeaderColumn", ImmutableMap.of(
                "actualHeaderColumn", actual,
                "expectedHeaderColumn", expected
        ));
    }

    public static InvalidDatasetContentException forHeaderColumnPatternNotMatching(String expectedPattern, String actual, int headerLine) {
        return newInvalidDatasetContentException(headerLine, "headerColumnPatternNotMatching", ImmutableMap.of(
                "actualHeaderColumn", actual,
                "expectedHeaderColumnPattern", expectedPattern
        ));
    }

    public static InvalidDatasetContentException forUnexpectedTokenCount(int expectedTokenCount, String actualHeader, int actualTokenCount, int headerLine) {
        return newInvalidDatasetContentException(headerLine, "unexpectedTokenCount", ImmutableMap.of(
                "expectedTokenCount", expectedTokenCount,
                "actualHeader", actualHeader,
                "actualTokenCount", actualTokenCount
        ));
    }

    public static InvalidDatasetContentException forInvalidHeaders(ImmutableSet<String> expectedColumns, ImmutableSet<String> mandatoryHeaders, ImmutableSet<String> actualColumns, int headerLine) {
        ImmutableSet<String> missingColumns = Sets.difference(mandatoryHeaders, actualColumns).immutableCopy();
        ImmutableSet<String> unknownColumns = Sets.difference(actualColumns, expectedColumns).immutableCopy();
        return newInvalidDatasetContentException(headerLine, "invalidHeaders", ImmutableMap.of(
                "expectedColumns", expectedColumns,
                "actualColumns", actualColumns,
                "missingColumns", missingColumns,
                "unknownColumns", unknownColumns
        ));
    }

    public static InvalidDatasetContentException forDuplicatedHeaders(int headerLine, ImmutableSet<String> duplicatedHeaders) {
        return newInvalidDatasetContentException(headerLine, "duplicatedHeaders", ImmutableMap.of(
                "duplicatedHeaders", duplicatedHeaders
        ));
    }

    private static InvalidDatasetContentException newInvalidDatasetContentException(int headerLine, String message, ImmutableMap<String, Object> messageParams) {
        ValidationCheckResult validationCheckResult = DefaultValidationCheckResult.error(message, messageParams);
        CsvRowValidationCheckResult csvRowValidationCheckResult = new CsvRowValidationCheckResult(validationCheckResult, headerLine);
        return new InvalidDatasetContentException(List.of(csvRowValidationCheckResult));
    }

    public static void checkHeader(ImmutableSet<String> expectedColumns, ImmutableSet<String> mandatoryColumns, ImmutableMultiset<String> actualColumns, int headerLine, boolean allowUnexpectedColumns) {
        Preconditions.checkArgument(expectedColumns.containsAll(mandatoryColumns), "il y des colonnes obligatoires qui ne font pas parti des colonnes possibles");
        if (actualColumns.contains("")) {
            throw forEmptyHeader(headerLine);
        }
        ImmutableSet<String> duplicatedHeaders = actualColumns.entrySet().stream()
                .filter(column -> column.getCount() > 1)
                .map(Multiset.Entry::getElement)
                .collect(ImmutableSet.toImmutableSet());
        if (!duplicatedHeaders.isEmpty()) {
            throw forDuplicatedHeaders(headerLine, duplicatedHeaders);
        }
        ImmutableSet<String> actualColumnsAsSet = actualColumns.elementSet();
        boolean givenColumnIsUnexpected = !(allowUnexpectedColumns || expectedColumns.containsAll(actualColumnsAsSet));
        boolean mandatoryColumnIsMissing = !actualColumnsAsSet.containsAll(mandatoryColumns);
        if (givenColumnIsUnexpected || mandatoryColumnIsMissing) {
            throw forInvalidHeaders(expectedColumns, mandatoryColumns, actualColumnsAsSet, headerLine);
        }
    }

    private static InvalidDatasetContentException forEmptyHeader(int headerLine) {
        return newInvalidDatasetContentException(headerLine, "emptyHeader", ImmutableMap.of(
                "headerLine", headerLine
        ));
    }

    public static void checkErrorsIsEmpty(List<CsvRowValidationCheckResult> errors) {
        if (!errors.isEmpty()) {
            throw new InvalidDatasetContentException(errors);
        }
    }

    public static void checkReferenceErrorsIsEmpty(List<CsvRowValidationCheckResult> errors) {
        if (!errors.isEmpty()) {
            throw new InvalidDatasetContentException(errors);
        }
    }

    public List<CsvRowValidationCheckResult> getErrors() {
        return errors;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("errors", errors)
                .toString();
    }
}