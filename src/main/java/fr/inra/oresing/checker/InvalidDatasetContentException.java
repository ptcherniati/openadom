package fr.inra.oresing.checker;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.rest.CsvRowValidationCheckResult;
import fr.inra.oresing.rest.ValidationCheckResult;

import java.util.List;
import java.util.Map;

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
        return new InvalidDatasetContentException(List.of(new CsvRowValidationCheckResult(new ValidationCheckResult() {
            @Override
            public ValidationLevel getLevel() {
                return ValidationLevel.ERROR;
            }

            @Override
            public String getMessage() {
                return "unexpectedHeaderColumn";
            }

            @Override
            public Map<String, Object> getMessageParams() {
                return ImmutableMap.of(
                        "actualHeaderColumn", actual,
                        "expectedHeaderColumn", expected
                );
            }
        }, headerLine)));
    }

    public static InvalidDatasetContentException forHeaderColumnPatternNotMatching(String expectedPattern, String actual, int headerLine) {
        return new InvalidDatasetContentException(List.of(new CsvRowValidationCheckResult(new ValidationCheckResult() {
            @Override
            public ValidationLevel getLevel() {
                return ValidationLevel.ERROR;
            }

            @Override
            public String getMessage() {
                return "headerColumnPatternNotMatching";
            }

            @Override
            public Map<String, Object> getMessageParams() {
                return ImmutableMap.of(
                        "actualHeaderColumn", actual,
                        "expectedHeaderColumnPattern", expectedPattern
                );
            }
        }, headerLine)));
    }

    public static InvalidDatasetContentException forUnexpectedTokenCount(int expectedTokenCount, String actualHeader, int actualTokenCount, int headerLine) {
        return new InvalidDatasetContentException(List.of(new CsvRowValidationCheckResult(new ValidationCheckResult() {
            @Override
            public ValidationLevel getLevel() {
                return ValidationLevel.ERROR;
            }

            @Override
            public String getMessage() {
                // "On doit pouvoir repérer " + tokens.size() + " informations dans l'entête " + actualHeader + ", or seulement " + matcher.groupCount() + " détectés"
                return "unexpectedTokenCount";
            }

            @Override
            public Map<String, Object> getMessageParams() {
                return ImmutableMap.of(
                        "expectedTokenCount", expectedTokenCount,
                        "actualHeader", actualHeader,
                        "actualTokenCount", actualTokenCount
                );
            }
        }, headerLine)));
    }

    public static InvalidDatasetContentException forInvalidHeaders(ImmutableSet<String> expectedColumns, ImmutableMultiset<String> actualColumns, int headerLine) {
        return new InvalidDatasetContentException(List.of(new CsvRowValidationCheckResult(new ValidationCheckResult() {
            @Override
            public ValidationLevel getLevel() {
                return ValidationLevel.ERROR;
            }

            @Override
            public String getMessage() {
                return "invalidHeaders";
            }

            @Override
            public Map<String, Object> getMessageParams() {
                ImmutableSet<String> actualColumnsAsSet = ImmutableSet.copyOf(actualColumns);
                ImmutableSet<String> missingColumns = Sets.difference(expectedColumns, actualColumnsAsSet).immutableCopy();
                ImmutableSet<String> unknownColumns = Sets.difference(actualColumnsAsSet, expectedColumns).immutableCopy();
                return ImmutableMap.of(
                        "expectedColumns", expectedColumns,
                        "actualColumns", actualColumns.elementSet(),
                        "missingColumns", missingColumns,
                        "unknownColumns", unknownColumns
                );
            }
        }, headerLine)));
    }

    public static InvalidDatasetContentException forDuplicatedHeaders(int headerLine, ImmutableSet<String> duplicatedHeaders) {
        return new InvalidDatasetContentException(List.of(new CsvRowValidationCheckResult(new ValidationCheckResult() {
            @Override
            public ValidationLevel getLevel() {
                return ValidationLevel.ERROR;
            }

            @Override
            public String getMessage() {
                return "duplicatedHeaders";
            }

            @Override
            public Map<String, Object> getMessageParams() {
                return ImmutableMap.of(
                        "duplicatedHeaders", duplicatedHeaders
                );
            }
        }, headerLine)));
    }

    public static void checkHeader(ImmutableSet<String> expectedColumns, ImmutableMultiset<String> actualColumns, int headerLine) {
        ImmutableSet<String> duplicatedHeaders = actualColumns.entrySet().stream()
                .filter(column -> column.getCount() > 1)
                .map(Multiset.Entry::getElement)
                .collect(ImmutableSet.toImmutableSet());
        if (!duplicatedHeaders.isEmpty()) {
            throw InvalidDatasetContentException.forDuplicatedHeaders(headerLine, duplicatedHeaders);
        }
        if (!expectedColumns.equals(actualColumns.elementSet())) {
            throw InvalidDatasetContentException.forInvalidHeaders(expectedColumns, actualColumns, headerLine);
        }
    }

    public static void checkErrorsIsEmpty(List<CsvRowValidationCheckResult> errors) {
        if (!errors.isEmpty()) {
            throw new InvalidDatasetContentException(errors);
        }
    }

    public List<CsvRowValidationCheckResult> getErrors() {
        return errors;
    }
}
