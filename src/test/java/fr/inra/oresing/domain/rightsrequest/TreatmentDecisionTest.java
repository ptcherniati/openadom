package fr.inra.oresing.domain.rightsrequest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link TreatmentDecision}.
 * Aucun contexte Spring — instanciation directe.
 */
@Tag("domain.model")
@DisplayName("TreatmentDecision — conversion depuis chaîne")
class TreatmentDecisionTest {

    @Test
    @DisplayName("fromNullable(\"APPROVED\") retourne APPROVED")
    void fromNullableApproved() {
        assertThat(TreatmentDecision.fromNullable("APPROVED")).isEqualTo(TreatmentDecision.APPROVED);
    }

    @Test
    @DisplayName("fromNullable(\"REJECTED\") retourne REJECTED")
    void fromNullableRejected() {
        assertThat(TreatmentDecision.fromNullable("REJECTED")).isEqualTo(TreatmentDecision.REJECTED);
    }

    @Test
    @DisplayName("fromNullable(\"approved\") — insensible à la casse — retourne APPROVED")
    void fromNullableLowerCase() {
        assertThat(TreatmentDecision.fromNullable("approved")).isEqualTo(TreatmentDecision.APPROVED);
    }

    @Test
    @DisplayName("fromNullable(\"rejected\") — insensible à la casse — retourne REJECTED")
    void fromNullableLowerCaseRejected() {
        assertThat(TreatmentDecision.fromNullable("rejected")).isEqualTo(TreatmentDecision.REJECTED);
    }

    @Test
    @DisplayName("fromNullable(\"  approved  \") — espaces — retourne APPROVED")
    void fromNullableWithSpaces() {
        assertThat(TreatmentDecision.fromNullable("  approved  ")).isEqualTo(TreatmentDecision.APPROVED);
    }

    @ParameterizedTest(name = "fromNullable(\"{0}\") doit retourner APPROVED par défaut")
    @NullAndEmptySource
    @DisplayName("fromNullable(null ou vide) retourne APPROVED")
    void fromNullableNullOrEmpty(String value) {
        assertThat(TreatmentDecision.fromNullable(value)).isEqualTo(TreatmentDecision.APPROVED);
    }

    @Test
    @DisplayName("fromNullable(\"UNKNOWN\") retourne APPROVED (valeur inconnue)")
    void fromNullableUnknown() {
        assertThat(TreatmentDecision.fromNullable("UNKNOWN")).isEqualTo(TreatmentDecision.APPROVED);
    }

    @Test
    @DisplayName("fromNullable(\"   \") — uniquement espaces — retourne APPROVED")
    void fromNullableBlankOnly() {
        assertThat(TreatmentDecision.fromNullable("   ")).isEqualTo(TreatmentDecision.APPROVED);
    }

    @Test
    @DisplayName("valueOf APPROVED et REJECTED sont accessibles comme enum standards")
    void enumValues() {
        assertThat(TreatmentDecision.values())
                .containsExactlyInAnyOrder(TreatmentDecision.APPROVED, TreatmentDecision.REJECTED);
    }
}
