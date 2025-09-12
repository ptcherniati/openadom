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
import io.swagger.v3.oas.models.links.Link;
import lombok.Getter;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;

import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Exception levée si un jeu de données est incorrect (aussi bien entêtes que contenu)
 */
@Getter
public class InvalidDatasetContentException extends OreSiTechnicalException {

    private final List<CsvRowValidationCheckResult> errors;

    public InvalidDatasetContentException(final List<CsvRowValidationCheckResult> errors) {
        super("Erreurs rencontrées à l'import du fichier");
        this.errors = errors;
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

    public static InvalidDatasetContentException forMissingMandatoryColumns(final Set<String> missingMandatoryColumns, final int headerLine) {
        return newInvalidDatasetContentException(headerLine, "missingMandatoryColumns", ImmutableMap.of(
                "missingMandatoryColumns", missingMandatoryColumns
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
        boolean areAllBasicColumns = expectedColumns.containsAll(actualColumnsAsSet);
        final boolean givenColumnIsUnexpected = !(allowUnexpectedColumns || areAllBasicColumns);
        final boolean mandatoryColumnIsMissing = !actualColumnsAsSet.containsAll(mandatoryColumns);
        if (!areAllBasicColumns || mandatoryColumnIsMissing) {
            if (!mandatoryColumnIsMissing && patternColumnFactory != null) {
                List<ContextHeader> notOrdinaryColumns = headersForRow.stream()
                        .filter(column -> !expectedColumns.contains(column))
                        .map(columnHeader -> new ContextHeader(columnHeader, headersForRow))
                        .toList();
                if (patternColumnFactory.test(notOrdinaryColumns, allowUnexpectedColumns)) {
                    return headersForRow;
                } else if (!patternColumnFactory.getExtraColumns().isEmpty()) {
                    return headersForRow.stream()
                            .filter(Predicate.not(patternColumnFactory.getExtraColumns()::contains))
                            .collect(ImmutableList.toImmutableList());
                }
            } else if (mandatoryColumnIsMissing) {
                Set<String> missingMandatoryColumns = mandatoryColumns.stream()
                        .filter(Predicate.not(actualColumns::contains))
                        .collect(Collectors.toUnmodifiableSet());
                throw forMissingMandatoryColumns(missingMandatoryColumns, headerLine);
            } else if (!givenColumnIsUnexpected) {
                final ImmutableSet<String> duplicatedHeaders = actualColumns.entrySet().stream()
                        .filter(column -> column.getCount() > 1)
                        .map(Multiset.Entry::getElement)
                        .collect(ImmutableSet.toImmutableSet());
                if (!duplicatedHeaders.isEmpty()) {
                    throw forDuplicatedHeaders(headerLine, duplicatedHeaders);
                }
                return headersForRow;
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
            throw new InvalidDatasetContentException(new LinkedList<>(errors));
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("errors", errors)
                .toString();
    }
}