package fr.inra.oresing.domain.application;

import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.exceptions.FieldNameTooLongForSqlFieldException;

import java.util.Optional;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

/**
 * Règles métier de validation et de manipulation des identifiants SQL
 * (noms d'applications, de types de données, de colonnes…).
 * <p>
 * Classe purement statique, sans dépendance Spring ni couche technique.
 */
public final class SqlIdentifierUtils {

    private static final String IDENTIFIER_PATTERN = "[a-z][a-z_0-9]{%d,%d}";

    private SqlIdentifierUtils() {
        // classe utilitaire, non instanciable
    }

    /**
     * Retourne un prédicat qui valide qu'une chaîne est un identifiant SQL exploitable
     * (commence par une lettre minuscule, ne contient que {@code [a-z_0-9]},
     * longueur comprise entre {@code min} et {@code max}).
     *
     * @param min longueur minimale (ajustée à 1 si ≤ 0)
     * @param max longueur maximale (ajustée à 63 si ≥ 64)
     */
    public static Predicate<String> getIsValidIdentifierPattern(final int min, final int max) {
        final int min1 = min > 0 ? min : 1;
        final int max1 = max < 64 ? max : 63;
        return Pattern.compile(String.format(IDENTIFIER_PATTERN, min1 - 1, max1 - 1)).asMatchPredicate();
    }

    // -------------------------------------------------------------------------
    //  IdentifierTest
    // -------------------------------------------------------------------------

    /**
     * Valide et transforme un identifiant SQL (longueur ≤ 63, structure {@code [a-z][a-z_0-9]*}).
     * Fonctionne pour les noms d'applications comme pour les noms de colonnes/types de données.
     */
    public static class IdentifierTest {

        private String identifier;

        private IdentifierTest(final String label) {
            identifier = Optional.ofNullable(label).orElse("");
            testlabelLength();
        }

        /** Construit un {@code IdentifierTest} à partir du nom de colonne d'un {@link DataColumn}. */
        public static IdentifierTest forReference(final DataColumn reference) {
            return forStringIdentifier(reference.column());
        }

        /**
         * Retourne {@code true} si {@code applicationName} est un identifiant valide
         * pour une application (longueur 2 à 40).
         */
        public static boolean identifierForApplicationName(final String applicationName) {
            return getIsValidIdentifierPattern(2, 40).test(applicationName);
        }

        /**
         * Retourne {@code true} si {@code objectName} est un identifiant valide
         * pour un type de données ou un objet (longueur 1 à 50).
         */
        public static boolean identifierForObject(final String objectName) {
            return getIsValidIdentifierPattern(1, 50).test(objectName);
        }

        /**
         * Construit un {@code IdentifierTest} pour l'identifiant donné.
         *
         * @throws FieldNameTooLongForSqlFieldException si {@code identifier} est {@code null}
         *         ou dépasse 63 caractères
         */
        public static IdentifierTest forStringIdentifier(final String identifier) {
            return Optional.ofNullable(identifier)
                    .map(IdentifierTest::new)
                    .orElseThrow(() -> new FieldNameTooLongForSqlFieldException(identifier));
        }

        /** Lève {@link FieldNameTooLongForSqlFieldException} si la longueur dépasse 63. */
        public IdentifierTest testlabelLength() {
            return Optional.of(identifier)
                    .filter(l -> l.length() <= 63)
                    .map(l -> this)
                    .orElseThrow(() -> new FieldNameTooLongForSqlFieldException(identifier));
        }

        /** Retourne l'identifiant encadré de guillemets doubles SQL. */
        public String testAndQuote() {
            return "\"" + identifier + "\"";
        }

        /** Comme {@link #testAndQuote()} mais préfixe le nom par {@code refs_linked_to_}. */
        public String testAndQuoteForRefsLinkedTo() {
            return "\"refs_linked_to_" + identifier + "\"";
        }

        /** Retourne l'identifiant brut (non quoté). */
        public String testAndReturnIdentifier() {
            return identifier;
        }

        public IdentifierTest forHierachicalKey() {
            identifier = identifier + "_hierachicakkey";
            return testlabelLength();
        }

        public IdentifierTest forNaturalKey() {
            identifier = identifier + "_naturalkey";
            return testlabelLength();
        }

        public IdentifierTest forOneValueFromTheManyArray() {
            identifier = identifier + "_value";
            return testlabelLength();
        }

        public IdentifierTest forId() {
            identifier = identifier + "_id";
            return testlabelLength();
        }

        public IdentifierTest forDynamicReferenceHierachicakKey(final int count) {
            identifier = Pattern.compile(".{1," + 64 + "}")
                    .matcher(String.format("_%d%s_hierachicakKey", count, identifier))
                    .results()
                    .map(MatchResult::group)
                    .findFirst()
                    .orElse("");
            return this;
        }
    }
}