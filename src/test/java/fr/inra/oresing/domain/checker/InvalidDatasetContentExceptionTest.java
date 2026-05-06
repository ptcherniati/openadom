package fr.inra.oresing.domain.checker;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires pour InvalidDatasetContentException (sans Spring).
 * Couvre les factories statiques et checkHeader().
 */
@DisplayName("InvalidDatasetContentException – tests unitaires")
@Tag("domain.checker")
class InvalidDatasetContentExceptionTest {

    private static final int HEADER_LINE = 1;

    // =========================================================================
    //  Factories statiques
    // =========================================================================

    @Nested
    @DisplayName("forInvalidHeaders()")
    class ForInvalidHeadersTest {

        @Test
        @DisplayName("crée une exception avec un seul résultat d'erreur")
        void createsOneValidationResult() {
            ImmutableSet<String> expected = ImmutableSet.of("colA", "colB");
            ImmutableSet<String> mandatory = ImmutableSet.of("colA");
            ImmutableSet<String> actual    = ImmutableSet.of("colA", "colC");

            InvalidDatasetContentException ex =
                    InvalidDatasetContentException.forInvalidHeaders(expected, mandatory, actual, HEADER_LINE);

            assertNotNull(ex);
            assertEquals(1, ex.getErrors().size());
            CsvRowValidationCheckResult result = ex.getErrors().get(0);
            assertEquals(HEADER_LINE, result.lineNumber());
        }

        @Test
        @DisplayName("getMessage() contient le message de l'exception parente")
        void getMessageNotNull() {
            InvalidDatasetContentException ex =
                    InvalidDatasetContentException.forInvalidHeaders(
                            ImmutableSet.of("a"), ImmutableSet.of("a"), ImmutableSet.of("b"), 1);
            assertNotNull(ex.getMessage());
        }
    }

    @Nested
    @DisplayName("forMissingMandatoryColumns()")
    class ForMissingMandatoryColumnsTest {

        @Test
        @DisplayName("crée une exception avec les colonnes manquantes")
        void createsExceptionWithMissingColumns() {
            Set<String> missing = Set.of("requiredCol");
            InvalidDatasetContentException ex =
                    InvalidDatasetContentException.forMissingMandatoryColumns(missing, HEADER_LINE);

            assertNotNull(ex);
            assertEquals(1, ex.getErrors().size());
        }
    }

    @Nested
    @DisplayName("forDuplicatedHeaders()")
    class ForDuplicatedHeadersTest {

        @Test
        @DisplayName("crée une exception avec les colonnes dupliquées")
        void createsExceptionWithDuplicates() {
            ImmutableSet<String> dupes = ImmutableSet.of("colA");
            InvalidDatasetContentException ex =
                    InvalidDatasetContentException.forDuplicatedHeaders(HEADER_LINE, dupes);

            assertNotNull(ex);
            assertEquals(1, ex.getErrors().size());
        }
    }

    // =========================================================================
    //  checkHeader()
    // =========================================================================

    @Nested
    @DisplayName("checkHeader()")
    class CheckHeaderTest {

        @Test
        @DisplayName("retourne les entêtes quand tout est valide")
        void returnsHeadersWhenValid() {
            ImmutableList<String> headers   = ImmutableList.of("colA", "colB");
            ImmutableSet<String> expected   = ImmutableSet.of("colA", "colB");
            ImmutableSet<String> mandatory  = ImmutableSet.of("colA");
            ImmutableMultiset<String> actual = ImmutableMultiset.of("colA", "colB");

            ImmutableList<String> result = InvalidDatasetContentException.checkHeader(
                    headers, expected, mandatory, actual, null, HEADER_LINE, false);

            assertEquals(headers, result);
        }

        @Test
        @DisplayName("lève une exception si une colonne obligatoire est manquante")
        void throwsWhenMandatoryColumnMissing() {
            ImmutableList<String> headers   = ImmutableList.of("colA");
            ImmutableSet<String> expected   = ImmutableSet.of("colA", "colB");
            ImmutableSet<String> mandatory  = ImmutableSet.of("colA", "colB");   // colB obligatoire
            ImmutableMultiset<String> actual = ImmutableMultiset.of("colA");     // colB absente

            assertThrows(InvalidDatasetContentException.class, () ->
                    InvalidDatasetContentException.checkHeader(
                            headers, expected, mandatory, actual, null, HEADER_LINE, false));
        }

        @Test
        @DisplayName("lève une exception si un entête est vide ('')")
        void throwsWhenEmptyHeader() {
            ImmutableList<String> headers   = ImmutableList.of("colA", "");
            ImmutableSet<String> expected   = ImmutableSet.of("colA", "colB");
            ImmutableSet<String> mandatory  = ImmutableSet.of("colA");
            ImmutableMultiset<String> actual = ImmutableMultiset.of("colA", "");

            assertThrows(InvalidDatasetContentException.class, () ->
                    InvalidDatasetContentException.checkHeader(
                            headers, expected, mandatory, actual, null, HEADER_LINE, false));
        }

        @Test
        @DisplayName("lève une exception si une colonne inconnue est présente et allowUnexpected=false")
        void throwsWhenUnknownColumnAndNotAllowed() {
            ImmutableList<String> headers   = ImmutableList.of("colA", "unknown");
            ImmutableSet<String> expected   = ImmutableSet.of("colA");
            ImmutableSet<String> mandatory  = ImmutableSet.of("colA");
            ImmutableMultiset<String> actual = ImmutableMultiset.of("colA", "unknown");

            assertThrows(InvalidDatasetContentException.class, () ->
                    InvalidDatasetContentException.checkHeader(
                            headers, expected, mandatory, actual, null, HEADER_LINE, false));
        }

        @Test
        @DisplayName("accepte une colonne inconnue si allowUnexpected=true")
        void acceptsUnknownColumnWhenAllowed() {
            ImmutableList<String> headers   = ImmutableList.of("colA", "unknown");
            ImmutableSet<String> expected   = ImmutableSet.of("colA");
            ImmutableSet<String> mandatory  = ImmutableSet.of("colA");
            ImmutableMultiset<String> actual = ImmutableMultiset.of("colA", "unknown");

            assertDoesNotThrow(() ->
                    InvalidDatasetContentException.checkHeader(
                            headers, expected, mandatory, actual, null, HEADER_LINE, true));
        }

        @Test
        @DisplayName("lève une exception si un entête est dupliqué")
        void throwsWhenDuplicatedHeader() {
            ImmutableList<String> headers   = ImmutableList.of("colA", "colA");
            ImmutableSet<String> expected   = ImmutableSet.of("colA", "colB");
            ImmutableSet<String> mandatory  = ImmutableSet.of("colA");
            ImmutableMultiset<String> actual = ImmutableMultiset.copyOf(List.of("colA", "colA"));

            assertThrows(InvalidDatasetContentException.class, () ->
                    InvalidDatasetContentException.checkHeader(
                            headers, expected, mandatory, actual, null, HEADER_LINE, false));
        }

        @Test
        @DisplayName("lève IllegalArgumentException si une colonne obligatoire n'est pas dans les colonnes possibles")
        void throwsIllegalArgumentWhenMandatoryNotInExpected() {
            ImmutableList<String> headers   = ImmutableList.of("colA");
            ImmutableSet<String> expected   = ImmutableSet.of("colA");
            ImmutableSet<String> mandatory  = ImmutableSet.of("colA", "notInExpected"); // violation
            ImmutableMultiset<String> actual = ImmutableMultiset.of("colA");

            assertThrows(IllegalArgumentException.class, () ->
                    InvalidDatasetContentException.checkHeader(
                            headers, expected, mandatory, actual, null, HEADER_LINE, false));
        }
    }

    // =========================================================================
    //  checkErrorsIsEmpty()
    // =========================================================================

    @Nested
    @DisplayName("checkErrorsIsEmpty()")
    class CheckErrorsIsEmptyTest {

        @Test
        @DisplayName("ne lève pas d'exception si la liste est vide")
        void doesNotThrowWhenEmpty() {
            fr.inra.oresing.domain.exceptions.ReportErrors errors =
                    new fr.inra.oresing.domain.exceptions.ReportErrors(null);
            assertDoesNotThrow(() ->
                    InvalidDatasetContentException.checkErrorsIsEmpty(errors));
        }

        @Test
        @DisplayName("lève InvalidDatasetContentException si la liste est non-vide")
        void throwsWhenNotEmpty() {
            fr.inra.oresing.domain.Mapper mockMapper = org.mockito.Mockito.mock(fr.inra.oresing.domain.Mapper.class);
            org.mockito.Mockito.when(mockMapper.toJson(org.mockito.ArgumentMatchers.any())).thenReturn("{}");

            fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult vr =
                    fr.inra.oresing.domain.data.deposit.validation.DefaultValidationCheckResult
                            .error("testError", com.google.common.collect.ImmutableMap.of(), null);
            fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult row =
                    new CsvRowValidationCheckResult(vr, 1);

            fr.inra.oresing.domain.exceptions.ReportErrors errors =
                    new fr.inra.oresing.domain.exceptions.ReportErrors(mockMapper);
            errors.add(row);

            assertThrows(InvalidDatasetContentException.class, () ->
                    InvalidDatasetContentException.checkErrorsIsEmpty(errors));
        }
    }

    // =========================================================================
    //  toString()
    // =========================================================================

    @Test
    @DisplayName("toString() ne lève pas d'exception et contient 'errors'")
    void toStringContainsErrors() {
        InvalidDatasetContentException ex =
                InvalidDatasetContentException.forMissingMandatoryColumns(Set.of("col"), 1);
        String str = ex.toString();
        assertNotNull(str);
        assertTrue(str.contains("errors"));
    }
}