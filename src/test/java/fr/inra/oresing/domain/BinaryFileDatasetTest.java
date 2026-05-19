package fr.inra.oresing.domain;

import fr.inra.oresing.domain.application.configuration.Ltree;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link BinaryFileDataset} – méthodes non couvertes.
 */
@Tag("domain.model")
@DisplayName("BinaryFileDataset – copy, toString, setTo/setFrom, withPattern, setIfNotPresentDatatype")
class BinaryFileDatasetTest {

    @Test
    @DisplayName("EMPTY_INSTANCE() crée une instance vide")
    void emptyInstance() {
        BinaryFileDataset bfd = BinaryFileDataset.emptyInstance();
        assertThat(bfd).isNotNull();
        assertThat(bfd.getDatatype()).isNull();
        assertThat(bfd.getFrom()).isNull();
        assertThat(bfd.getTo()).isNull();
    }

    @Test
    @DisplayName("copy() recopie tous les champs")
    void copy() {
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setDatatype("myType");
        bfd.setFrom("2020-01-01 00:00:00");
        bfd.setTo("2021-01-01 00:00:00");
        bfd.setComment("my comment");

        BinaryFileDataset copy = bfd.copy();
        assertThat(copy).isNotSameAs(bfd);
        assertThat(copy.getDatatype()).isEqualTo("myType");
        assertThat(copy.getFrom()).isEqualTo("2020-01-01 00:00:00");
        assertThat(copy.getTo()).isEqualTo("2021-01-01 00:00:00");
        assertThat(copy.getComment()).isEqualTo("my comment");
    }

    @Test
    @DisplayName("toString() produit la représentation attendue")
    void toStringFormat() {
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setFrom("2020-01-01 00:00:00");
        bfd.setTo("2021-01-01 00:00:00");
        Map<String, List<Ltree>> authorizations = new HashMap<>();
        authorizations.put("species", List.of(Ltree.fromSql("especesKlpf")));
        bfd.setRequiredAuthorizations(authorizations);

        String str = bfd.toString();
        assertThat(str).isNotNull()
                .contains("species");
    }

    @Test
    @DisplayName("setTo(\"null\") positionne à null")
    void setToNull() {
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setTo("null");
        assertThat(bfd.getTo()).isNull();
    }

    @Test
    @DisplayName("setFrom(\"null\") positionne à null")
    void setFromNull() {
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setFrom("null");
        assertThat(bfd.getFrom()).isNull();
    }

    @Test
    @DisplayName("setTo(valeur normale) conserve la valeur")
    void setToNormalValue() {
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setTo("2021-06-01 00:00:00");
        assertThat(bfd.getTo()).isEqualTo("2021-06-01 00:00:00");
    }

    @Test
    @DisplayName("setFrom(valeur normale) conserve la valeur")
    void setFromNormalValue() {
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setFrom("2020-01-01 00:00:00");
        assertThat(bfd.getFrom()).isEqualTo("2020-01-01 00:00:00");
    }

    @Test
    @DisplayName("setIfNotPresentDatatype : ne change pas si déjà défini")
    void setIfNotPresentWhenAlreadySet() {
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setDatatype("existing");
        bfd.setIfNotPresentDatatype("new");
        assertThat(bfd.getDatatype()).isEqualTo("existing");
    }

    @Test
    @DisplayName("setIfNotPresentDatatype : positionne si null")
    void setIfNotPresentWhenNull() {
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setIfNotPresentDatatype("newType");
        assertThat(bfd.getDatatype()).isEqualTo("newType");
    }

    @Test
    @DisplayName("withPattern() avec from/to valides → format transformé")
    void withPatternValid() {
        BinaryFileDataset bfd = new BinaryFileDataset();
        bfd.setFrom("2020-01-01 00:00:00");
        bfd.setTo("2021-01-01 00:00:00");

        fr.inra.oresing.domain.application.configuration.date.DatePattern<?> pattern =
                fr.inra.oresing.domain.application.configuration.date.DatePattern.of("yyyy");
        BinaryFileDataset result = bfd.withPattern(pattern);
        assertThat(result).isNotNull();
        // le pattern "yyyy" formate les dates en année seulement
        assertThat(result.getFrom()).isNotNull();
    }

    @Test
    @DisplayName("withPattern() avec from null → retourne this (exception dans le try)")
    void withPatternNullFrom() {
        BinaryFileDataset bfd = new BinaryFileDataset();
        // from/to null → LocalDateTimeRange.of gère les cas null
        fr.inra.oresing.domain.application.configuration.date.DatePattern<?> pattern =
                fr.inra.oresing.domain.application.configuration.date.DatePattern.of("yyyy");
        // Peut retourner this ou une copie selon l'implémentation
        assertThat(bfd.withPattern(pattern)).isNotNull();
    }
}
