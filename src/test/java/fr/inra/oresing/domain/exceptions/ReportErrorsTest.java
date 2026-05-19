package fr.inra.oresing.domain.exceptions;

import fr.inra.oresing.domain.Mapper;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link ReportErrors} – addAll, canRegisterErrors, limite taille.
 */
@Tag("domain.model")
@DisplayName("ReportErrors – limite 15 erreurs / 1MB")
class ReportErrorsTest {

    private static final Mapper MAPPER = Object::toString;

    @Test
    @DisplayName("canRegisterErrors() est true sur une instance vide")
    void canRegisterErrorsWhenEmpty() {
        ReportErrors re = new ReportErrors(MAPPER);
        assertThat(re.canRegisterErrors()).isTrue();
    }

    @Test
    @DisplayName("addAll() ajoute tous les éléments de la collection")
    void addAllAddsAll() {
        ReportErrors re = new ReportErrors(obj -> "{}");

        CsvRowValidationCheckResult r1 = Mockito.mock(CsvRowValidationCheckResult.class);
        CsvRowValidationCheckResult r2 = Mockito.mock(CsvRowValidationCheckResult.class);
        re.addAll(List.of(r1, r2));
        assertThat(re).hasSize(2);
    }

    @Test
    @DisplayName("add() retourne false au-delà de 15 erreurs (size limit)")
    void addReturnsFalseBeyondSizeLimit() {
        ReportErrors re = new ReportErrors(obj -> "{}");
        // Ajouter 15 éléments (la limite MAX_ERRORS_SIZE)
        for (int i = 0; i < 15; i++) {
            re.add(Mockito.mock(CsvRowValidationCheckResult.class));
        }
        // canRegisterErrors() retourne false dès que size >= 15
        assertThat(re.canRegisterErrors()).isFalse();
        assertThat(re).hasSize(15);
    }

    @Test
    @DisplayName("isOverload : dépasser 1MB → add() retourne false")
    void addReturnsFalseBeyondByteLimit() {
        // Utiliser un mapper qui retourne une chaîne de plus de 1_000_000 chars
        ReportErrors re = new ReportErrors(obj -> "x".repeat(1_000_001));
        // Le premier add dépasse la limite byte de 1_000_000
        boolean result = re.add(Mockito.mock(CsvRowValidationCheckResult.class));
        assertThat(result).isFalse();
        assertThat(re.canRegisterErrors()).isFalse();
    }
}