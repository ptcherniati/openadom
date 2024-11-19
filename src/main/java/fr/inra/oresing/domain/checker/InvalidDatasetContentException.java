package fr.inra.oresing.domain.checker;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import fr.inra.oresing.domain.data.deposit.context.column.ContextHeader;
import fr.inra.oresing.domain.data.deposit.context.column.PatternColumnFactory;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.DefaultValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import lombok.Getter;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Exception levée si un jeu de données est incorrect (aussi bien entêtes que contenu)
 */
@Getter
public class InvalidDatasetContentException extends OreSiTechnicalException {

    private final List<CsvRowValidationCheckResult> errors;

    private InvalidDatasetContentException(final List<CsvRowValidationCheckResult> errors) {
        super("Erreurs rencontrées à l'import du fichier");
        this.errors = errors;
    }

    public static InvalidDatasetContentException forUnexpectedHeaderColumn(final String expected, final String actual, final int headerLine) {
        return newInvalidDatasetContentException(headerLine, "unexpectedHeaderColumn", ImmutableMap.of(
                "actualHeaderColumn", actual,
                "expectedHeaderColumn", expected
        ));
    }

    public static InvalidDatasetContentException forUnexpectedHeaderColumnsInList(final String expected, final List<Map.Entry<String, String>> actual, final int headerLine) {
        return newInvalidDatasetContentException(headerLine, "unexpectedHeaderColumnsInList", ImmutableMap.of(
                "actualHeaderColumns", actual,
                "expectedHeaderColumn", expected
        ));
    }

    public static InvalidDatasetContentException forHeaderColumnPatternNotMatching(final String expectedPattern, final String actual, final int headerLine) {
        return newInvalidDatasetContentException(headerLine, "headerColumnPatternNotMatching", ImmutableMap.of(
                "actualHeaderColumn", actual,
                "expectedHeaderColumnPattern", expectedPattern
        ));
    }

    public static InvalidDatasetContentException forUnexpectedTokenCount(final int expectedTokenCount, final String actualHeader, final int actualTokenCount, final int headerLine) {
        return newInvalidDatasetContentException(headerLine, "unexpectedTokenCount", ImmutableMap.of(
                "expectedTokenCount", expectedTokenCount,
                "actualHeader", actualHeader,
                "actualTokenCount", actualTokenCount
        ));
    }

    public static InvalidDatasetContentException forInvalidHeaders(final ImmutableSet<String> expectedColumns, final ImmutableSet<String> mandatoryHeaders, final ImmutableSet<String> actualColumns, final int headerLine) {
        final Set<String> missingComponents = SetUtils.difference(mandatoryHeaders, actualColumns);
        final Set<String> unknownComponents = SetUtils.difference(actualColumns, expectedColumns);
        return newInvalidDatasetContentException(headerLine, "invalidHeaders", ImmutableMap.of(
                "expectedColumns", expectedColumns,
                "actualColumns", actualColumns,
                "missingComponents", missingComponents,
                "unknownComponents", unknownComponents
        ));
    }

    public static InvalidDatasetContentException forDuplicatedHeaders(final int headerLine, final ImmutableSet<String> duplicatedHeaders) {
        return newInvalidDatasetContentException(headerLine, "duplicatedHeaders", ImmutableMap.of(
                "duplicatedHeaders", duplicatedHeaders
        ));
    }

    private static InvalidDatasetContentException newInvalidDatasetContentException(final int headerLine, final String message, final ImmutableMap<String, Object> messageParams) {
        final ValidationCheckResult validationCheckResult = DefaultValidationCheckResult.error(message, messageParams, null);
        final CsvRowValidationCheckResult csvRowValidationCheckResult = new CsvRowValidationCheckResult(validationCheckResult, headerLine);
        return new InvalidDatasetContentException(List.of(csvRowValidationCheckResult));
    }

    public static ImmutableList<String> checkHeader(
            final ImmutableList<String> headersForRow,
            final ImmutableSet<String> expectedColumns,
            final ImmutableSet<String> mandatoryColumns,
            final ImmutableMultiset<String> actualColumns,
            final PatternColumnFactory patternColumnFactory,
            final int headerLine,
            final boolean allowUnexpectedColumns) {
        Preconditions.checkArgument(expectedColumns.containsAll(mandatoryColumns), "il y des colonnes obligatoires qui ne font pas parti des colonnes possibles");
        if (actualColumns.contains("")) {
            throw forEmptyHeader(headerLine);
        }
        final ImmutableSet<String> actualColumnsAsSet = actualColumns.elementSet();
        final Boolean givenColumnIsUnexpected = !(allowUnexpectedColumns || expectedColumns.containsAll(actualColumnsAsSet));
        final Boolean mandatoryColumnIsMissing = !actualColumnsAsSet.containsAll(mandatoryColumns);
        if (givenColumnIsUnexpected || mandatoryColumnIsMissing) {
            if(!mandatoryColumnIsMissing && patternColumnFactory!=null) {
                List<ContextHeader> notOrdinaryColumns = headersForRow.stream()
                        .filter(column -> !expectedColumns.contains(column))
                        .map(columnHeader -> new ContextHeader(columnHeader, headersForRow))
                        .toList();
                if(patternColumnFactory.test(notOrdinaryColumns)){
                    return headersForRow;
                }
            }

            throw forInvalidHeaders(expectedColumns, mandatoryColumns, actualColumnsAsSet, headerLine);
        }
        final ImmutableSet<String> duplicatedHeaders = actualColumns.entrySet().stream()
                .filter(column -> column.getCount() > 1)
                .map(Multiset.Entry::getElement)
                .collect(ImmutableSet.toImmutableSet());
        if (!duplicatedHeaders.isEmpty()) {
            throw forDuplicatedHeaders(headerLine, duplicatedHeaders);
        }
        return headersForRow;
    }

    private static InvalidDatasetContentException forEmptyHeader(final int headerLine) {
        return newInvalidDatasetContentException(headerLine, "emptyHeader", ImmutableMap.of(
                "headerLine", headerLine
        ));
    }

    public static void checkErrorsIsEmpty(final ReportErrors errors) {
        if (!errors.isEmpty()) {
            throw new InvalidDatasetContentException(errors);
        }
    }

    public static void checkReferenceErrorsIsEmpty(final List<CsvRowValidationCheckResult> errors) {
        if (!errors.isEmpty()) {
            throw new InvalidDatasetContentException(errors);
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("errors", errors)
                .toString();
    }
}