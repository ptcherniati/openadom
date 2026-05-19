package fr.inra.oresing.domain.sql;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WithSqlIdentifierTest {

    @Test
    void escapeSqlIdentifier_keepsSimpleIdentifierUnquoted() {
        assertThat(WithSqlIdentifier.escapeSqlIdentifier("referenceValue"))
                .isEqualTo("referenceValue");
    }

    @Test
    void escapeSqlIdentifier_quotesIdentifierWithDash() {
        assertThat(WithSqlIdentifier.escapeSqlIdentifier("my-app"))
                .isEqualTo("\"my-app\"");
    }

    @Test
    void escapeSqlIdentifier_quotesIdentifierWithSpaces() {
        assertThat(WithSqlIdentifier.escapeSqlIdentifier("my app"))
                .isEqualTo("\"my app\"");
    }

    @Test
    void escapeSqlIdentifier_trimsIdentifier() {
        assertThat(WithSqlIdentifier.escapeSqlIdentifier("  abc_123  "))
                .isEqualTo("abc_123");
    }

    @Test
    void escapeSqlIdentifier_rejectsDangerousCharacters() {
        assertThatThrownBy(() -> WithSqlIdentifier.escapeSqlIdentifier("abc;drop table x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsafe SQL identifier");
    }

    @Test
    void escapeSqlIdentifier_rejectsQuotedInjectionPayload() {
        assertThatThrownBy(() -> WithSqlIdentifier.escapeSqlIdentifier("a\" or 1=1 --"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsafe SQL identifier");
    }

    @Test
    void escapeSqlIdentifier_rejectsBlankIdentifier() {
        assertThatThrownBy(() -> WithSqlIdentifier.escapeSqlIdentifier("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be blank");
    }
}
