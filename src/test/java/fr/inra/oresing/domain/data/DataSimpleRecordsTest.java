package fr.inra.oresing.domain.data;

import fr.inra.oresing.domain.application.configuration.Ltree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des records simples du package domain.data.
 */
@Tag("domain.model")
@DisplayName("Data domain simple records – DataColumn, LinkedLines, RefsLinkedToValue")
class DataSimpleRecordsTest {

    // ─── DataColumn ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("DataColumn.asString() et toJsonForDatabase() retournent la colonne")
    void dataColumnAccessors() {
        DataColumn col = new DataColumn("myCol");
        assertThat(col.asString()).isEqualTo("myCol");
        assertThat(col.toJsonForDatabase()).isEqualTo("myCol");
        assertThat(col.column()).isEqualTo("myCol");
    }

    @Test
    @DisplayName("DataColumn.getInternationalizedKey() ajoute WithComponent")
    void dataColumnInternationalizedKey() {
        DataColumn col = new DataColumn("colA");
        assertThat(col.getInternationalizedKey("prefix")).isEqualTo("prefixWithComponent");
    }

    @Test
    @DisplayName("DataColumn.toHumanReadableString() retourne la colonne")
    void dataColumnToHumanReadableString() {
        DataColumn col = new DataColumn("xyz");
        assertThat(col.toHumanReadableString()).isEqualTo("xyz");
    }

    @Test
    @DisplayName("DataColumn.forDisplayName(Locale) construit le nom display")
    void dataColumnForDisplayNameLocale() {
        DataColumn col = DataColumn.forDisplayName(Locale.FRENCH);
        assertThat(col.column()).contains(DataColumn.DISPLAY);
        assertThat(col.column()).contains("fr");
    }

    @Test
    @DisplayName("DataColumn.forDisplayName(String) construit le nom display")
    void dataColumnForDisplayNameString() {
        DataColumn col = DataColumn.forDisplayName("en");
        assertThat(col.column()).contains("__display_");
        assertThat(col.column()).contains("en");
    }

    @Test
    @DisplayName("DataColumn.forDisplayDescription(Locale) construit la description")
    void dataColumnForDisplayDescriptionLocale() {
        DataColumn col = DataColumn.forDisplayDescription(Locale.ENGLISH);
        assertThat(col.column()).contains("description_");
        assertThat(col.column()).contains("en");
    }

    @Test
    @DisplayName("DataColumn.forDisplayDescription(String) construit la description")
    void dataColumnForDisplayDescriptionString() {
        DataColumn col = DataColumn.forDisplayDescription("fr");
        assertThat(col.column()).contains("description_");
        assertThat(col.column()).contains("fr");
    }

    @Test
    @DisplayName("DataColumn constantes DISPLAY, DEFAULT, DISPLAY_NAME, DISPLAY_DESCRIPTION")
    void dataColumnConstants() {
        assertThat(DataColumn.DISPLAY).isEqualTo("__display_");
        assertThat(DataColumn.DEFAULT).isEqualTo("default");
        assertThat(DataColumn.DISPLAY_NAME).isNotBlank();
        assertThat(DataColumn.DISPLAY_DESCRIPTION).isNotBlank();
    }

    // ─── LinkedLines ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("LinkedLines.uuids() retourne les UUIDs passés en constructeur")
    void linkedLinesAccessor() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();
        LinkedLines ll = new LinkedLines(Set.of(id1, id2));
        assertThat(ll.uuids()).contains(id1, id2);
        assertThat(ll.uuids()).hasSize(2);
    }

    @Test
    @DisplayName("LinkedLines avec ensemble vide")
    void linkedLinesEmpty() {
        LinkedLines ll = new LinkedLines(Set.of());
        assertThat(ll.uuids()).isEmpty();
    }

    // ─── RefsLinkedToValue ───────────────────────────────────────────────────

    @Test
    @DisplayName("RefsLinkedToValue accesseurs")
    void refsLinkedToValue() {
        UUID id = UUID.randomUUID();
        Ltree hk = Ltree.fromSql("root.child");
        RefsLinkedToValue ref = new RefsLinkedToValue(Set.of(id), hk);
        assertThat(ref.uuids()).containsExactly(id);
        assertThat(ref.hierarchicalKey()).isEqualTo(hk);
    }
}
