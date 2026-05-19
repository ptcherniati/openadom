package fr.inra.oresing.domain.application;

import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.exceptions.FieldNameTooLongForSqlFieldException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires purs de {@link SqlIdentifierUtils} — aucune dépendance Spring ni base.
 */
@DisplayName("SqlIdentifierUtils — règles métier identifiants SQL")
@Tag("domain.model")
class SqlIdentifierUtilsTest {

    // -------------------------------------------------------------------------
    // getIsValidIdentifierPattern
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("getIsValidIdentifierPattern()")
    class ValidIdentifierPatternTest {

        @Test
        @DisplayName("Identifiant valide : commence par minuscule, contient [a-z_0-9]")
        void validIdentifiers() {
            var pred = SqlIdentifierUtils.getIsValidIdentifierPattern(2, 40);
            assertThat(pred.test("myapp")).isTrue();
            assertThat(pred.test("my_app_2")).isTrue();
            assertThat(pred.test("ab")).isTrue();
        }

        @Test
        @DisplayName("Identifiant invalide : commence par chiffre ou majuscule")
        void invalidStartChar() {
            var pred = SqlIdentifierUtils.getIsValidIdentifierPattern(1, 50);
            assertThat(pred.test("1app")).isFalse();
            assertThat(pred.test("MyApp")).isFalse();
            assertThat(pred.test("_app")).isFalse();
        }

        @Test
        @DisplayName("Longueur insuffisante (< min) → invalide")
        void tooShort() {
            var pred = SqlIdentifierUtils.getIsValidIdentifierPattern(4, 40);
            assertThat(pred.test("ab")).isFalse();
            assertThat(pred.test("abc")).isFalse();
            assertThat(pred.test("abcd")).isTrue();
        }

        @Test
        @DisplayName("min ≤ 0 est ajusté à 1 (pas de NPE)")
        void minAdjustedToOne() {
            var pred = SqlIdentifierUtils.getIsValidIdentifierPattern(0, 40);
            assertThat(pred.test("a")).isTrue(); // longueur 1, min effective = 1
        }

        @Test
        @DisplayName("max ≥ 64 est ajusté à 63")
        void maxAdjustedTo63() {
            var pred = SqlIdentifierUtils.getIsValidIdentifierPattern(1, 100);
            String s63 = "a" + "b".repeat(62);
            String s64 = "a" + "b".repeat(63);
            assertThat(pred.test(s63)).isTrue();
            assertThat(pred.test(s64)).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // IdentifierTest.identifierForApplicationName
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("IdentifierTest.identifierForApplicationName()")
    class ApplicationNameTest {

        @Test
        @DisplayName("Noms valides (longueur 2-40, [a-z][a-z_0-9]*)")
        void validApplicationNames() {
            assertThat(SqlIdentifierUtils.IdentifierTest.identifierForApplicationName("myapp")).isTrue();
            assertThat(SqlIdentifierUtils.IdentifierTest.identifierForApplicationName("aa")).isTrue();
            assertThat(SqlIdentifierUtils.IdentifierTest.identifierForApplicationName("acbb_2024")).isTrue();
        }

        @Test
        @DisplayName("Trop court (1 char) → invalide")
        void tooShort() {
            assertThat(SqlIdentifierUtils.IdentifierTest.identifierForApplicationName("a")).isFalse();
        }

        @Test
        @DisplayName("Commence par majuscule ou chiffre → invalide")
        void invalidStart() {
            assertThat(SqlIdentifierUtils.IdentifierTest.identifierForApplicationName("MyApp")).isFalse();
            assertThat(SqlIdentifierUtils.IdentifierTest.identifierForApplicationName("2app")).isFalse();
        }

        @Test
        @DisplayName("Contient un tiret ou espace → invalide")
        void invalidChars() {
            assertThat(SqlIdentifierUtils.IdentifierTest.identifierForApplicationName("my-app")).isFalse();
            assertThat(SqlIdentifierUtils.IdentifierTest.identifierForApplicationName("my app")).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // IdentifierTest.identifierForObject
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("IdentifierTest.identifierForObject()")
    class ObjectNameTest {

        @Test
        @DisplayName("Longueur 1 acceptée (contrairement aux noms d'application)")
        void singleCharValid() {
            assertThat(SqlIdentifierUtils.IdentifierTest.identifierForObject("a")).isTrue();
        }

        @Test
        @DisplayName("Noms valides (longueur 1-50)")
        void validObjectNames() {
            assertThat(SqlIdentifierUtils.IdentifierTest.identifierForObject("data_type")).isTrue();
            assertThat(SqlIdentifierUtils.IdentifierTest.identifierForObject("z")).isTrue();
        }
    }

    // -------------------------------------------------------------------------
    // IdentifierTest.forStringIdentifier — testAndQuote / testAndReturnIdentifier
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("IdentifierTest.forStringIdentifier() — quote et retour")
    class ForStringIdentifierTest {

        @Test
        @DisplayName("testAndQuote encadre de guillemets doubles")
        void testAndQuote() {
            String result = SqlIdentifierUtils.IdentifierTest
                    .forStringIdentifier("mycolumn")
                    .testAndQuote();
            assertThat(result).isEqualTo("\"mycolumn\"");
        }

        @Test
        @DisplayName("testAndReturnIdentifier retourne la valeur brute")
        void testAndReturnIdentifier() {
            String result = SqlIdentifierUtils.IdentifierTest
                    .forStringIdentifier("mycolumn")
                    .testAndReturnIdentifier();
            assertThat(result).isEqualTo("mycolumn");
        }

        @Test
        @DisplayName("testAndQuoteForRefsLinkedTo préfixe par 'refs_linked_to_'")
        void testAndQuoteForRefsLinkedTo() {
            String result = SqlIdentifierUtils.IdentifierTest
                    .forStringIdentifier("taxon")
                    .testAndQuoteForRefsLinkedTo();
            assertThat(result).isEqualTo("\"refs_linked_to_taxon\"");
        }

        @Test
        @DisplayName("null → FieldNameTooLongForSqlFieldException")
        void nullThrows() {
            assertThatThrownBy(() -> SqlIdentifierUtils.IdentifierTest.forStringIdentifier(null))
                    .isInstanceOf(FieldNameTooLongForSqlFieldException.class);
        }

        @Test
        @DisplayName("Identifiant de 64 caractères → FieldNameTooLongForSqlFieldException")
        void tooLongThrows() {
            String too_long = "a".repeat(64);
            assertThatThrownBy(() -> SqlIdentifierUtils.IdentifierTest.forStringIdentifier(too_long))
                    .isInstanceOf(FieldNameTooLongForSqlFieldException.class);
        }

        @Test
        @DisplayName("Identifiant de 63 caractères — juste en limite → pas d'exception")
        void maxLengthAccepted() {
            String maxLen = "a".repeat(63);
            String result = SqlIdentifierUtils.IdentifierTest
                    .forStringIdentifier(maxLen)
                    .testAndReturnIdentifier();
            assertThat(result).hasSize(63);
        }
    }

    // -------------------------------------------------------------------------
    // IdentifierTest — suffixes métier (forNaturalKey, forHierachicalKey, etc.)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("IdentifierTest — suffixes métier")
    class SuffixTest {

        @Test
        @DisplayName("forNaturalKey() ajoute '_naturalkey'")
        void forNaturalKey() {
            String result = SqlIdentifierUtils.IdentifierTest
                    .forStringIdentifier("taxon")
                    .forNaturalKey()
                    .testAndReturnIdentifier();
            assertThat(result).isEqualTo("taxon_naturalkey");
        }

        @Test
        @DisplayName("forHierachicalKey() ajoute '_hierachicakkey'")
        void forHierachicalKey() {
            String result = SqlIdentifierUtils.IdentifierTest
                    .forStringIdentifier("taxon")
                    .forHierachicalKey()
                    .testAndReturnIdentifier();
            assertThat(result).isEqualTo("taxon_hierachicakkey");
        }

        @Test
        @DisplayName("forId() ajoute '_id'")
        void forId() {
            String result = SqlIdentifierUtils.IdentifierTest
                    .forStringIdentifier("taxon")
                    .forId()
                    .testAndReturnIdentifier();
            assertThat(result).isEqualTo("taxon_id");
        }

        @Test
        @DisplayName("forOneValueFromTheManyArray() ajoute '_value'")
        void forOneValueFromTheManyArray() {
            String result = SqlIdentifierUtils.IdentifierTest
                    .forStringIdentifier("taxon")
                    .forOneValueFromTheManyArray()
                    .testAndReturnIdentifier();
            assertThat(result).isEqualTo("taxon_value");
        }

        @Test
        @DisplayName("Chaînage forNaturalKey �� forId sur identifiant court reste ≤ 63 chars")
        void chainedSuffixesUnderLimit() {
            // "ab" + "_naturalkey" + "_id" = 14 chars < 63
            String result = SqlIdentifierUtils.IdentifierTest
                    .forStringIdentifier("ab")
                    .forNaturalKey()
                    .forId()
                    .testAndReturnIdentifier();
            assertThat(result).isEqualTo("ab_naturalkey_id");
            assertThat(result).hasSizeLessThanOrEqualTo(63);
        }
    }

    // -------------------------------------------------------------------------
    // IdentifierTest.forReference (depuis DataColumn)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("IdentifierTest.forReference(DataColumn)")
    class ForReferenceTest {

        @Test
        @DisplayName("Construit depuis un DataColumn valide")
        void fromValidDataColumn() {
            DataColumn col = new DataColumn("myref");
            String result = SqlIdentifierUtils.IdentifierTest
                    .forReference(col)
                    .testAndReturnIdentifier();
            assertThat(result).isEqualTo("myref");
        }
    }
}