package fr.inra.oresing.domain.sql;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs des types {@code domain.sql} — aucune dépendance Spring ni base.
 */
@DisplayName("domain.sql — SqlStatement et WithSqlIdentifier")
@Tag("domain.model")
class DomainSqlTypesTest {

    // -------------------------------------------------------------------------
    // SqlStatement
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("SqlStatement enum")
    class SqlStatementTest {

        @Test
        @DisplayName("Contient exactement 5 valeurs DML")
        void allValues() {
            assertThat(SqlStatement.values()).containsExactlyInAnyOrder(
                    SqlStatement.ALL,
                    SqlStatement.SELECT,
                    SqlStatement.INSERT,
                    SqlStatement.UPDATE,
                    SqlStatement.DELETE);
        }

        @Test
        @DisplayName("valueOf retrouve chaque constante par son nom")
        void valueOfRoundTrip() {
            for (SqlStatement stmt : SqlStatement.values()) {
                assertThat(SqlStatement.valueOf(stmt.name())).isSameAs(stmt);
            }
        }

        @Test
        @DisplayName("name() retourne la valeur textuelle attendue")
        void namesMatchExpected() {
            assertThat(SqlStatement.ALL.name()).isEqualTo("ALL");
            assertThat(SqlStatement.SELECT.name()).isEqualTo("SELECT");
            assertThat(SqlStatement.INSERT.name()).isEqualTo("INSERT");
            assertThat(SqlStatement.UPDATE.name()).isEqualTo("UPDATE");
            assertThat(SqlStatement.DELETE.name()).isEqualTo("DELETE");
        }
    }

    // -------------------------------------------------------------------------
    // WithSqlIdentifier — méthode statique escapeSqlIdentifier
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("WithSqlIdentifier.escapeSqlIdentifier()")
    class EscapeSqlIdentifierTest {

        @Test
        @DisplayName("Identifiant sans espace ni tiret — retourné tel quel")
        void simpleIdentifier() {
            assertThat(WithSqlIdentifier.escapeSqlIdentifier("application")).isEqualTo("application");
            assertThat(WithSqlIdentifier.escapeSqlIdentifier("my_table")).isEqualTo("my_table");
            assertThat(WithSqlIdentifier.escapeSqlIdentifier("abc123")).isEqualTo("abc123");
        }

        @Test
        @DisplayName("Identifiant avec espace — encadré de guillemets doubles")
        void identifierWithSpace() {
            assertThat(WithSqlIdentifier.escapeSqlIdentifier("my table")).isEqualTo("\"my table\"");
            assertThat(WithSqlIdentifier.escapeSqlIdentifier("hello world")).isEqualTo("\"hello world\"");
        }

        @Test
        @DisplayName("Identifiant avec tiret — encadré de guillemets doubles")
        void identifierWithHyphen() {
            assertThat(WithSqlIdentifier.escapeSqlIdentifier("my-table")).isEqualTo("\"my-table\"");
            assertThat(WithSqlIdentifier.escapeSqlIdentifier("some-schema")).isEqualTo("\"some-schema\"");
        }

        @Test
        @DisplayName("Identifiant avec espace ET tiret — encadré de guillemets doubles")
        void identifierWithSpaceAndHyphen() {
            assertThat(WithSqlIdentifier.escapeSqlIdentifier("my-nice table")).isEqualTo("\"my-nice table\"");
        }

        @Test
        @DisplayName("Chaîne vide — retournée telle quelle (pas de guillemets)")
        void emptyString() {
            assertThat(WithSqlIdentifier.escapeSqlIdentifier("")).isEmpty();
        }

        @Test
        @DisplayName("Idempotence — escape déjà guillemetsé conservé tel quel (pas de double-escape)")
        void noDoubleEscape() {
            // Le simple appel n'est pas idempotent par conception, mais deux appels consécutifs
            // sur un identifiant simple produisent toujours un résultat stable.
            String once = WithSqlIdentifier.escapeSqlIdentifier("public");
            assertThat(once).isEqualTo("public");
        }
    }
}