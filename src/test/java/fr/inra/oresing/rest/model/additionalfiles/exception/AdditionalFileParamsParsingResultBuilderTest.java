package fr.inra.oresing.rest.model.additionalfiles.exception;

import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires purs de {@link AdditionalFileParamsParsingResult.Builder} — aucun contexte Spring.
 * Couvre la collecte d'erreurs de parsing des paramètres d'un fichier additionnel.
 */
@Tag("domain.model")
@DisplayName("AdditionalFileParamsParsingResult.Builder")
class AdditionalFileParamsParsingResultBuilderTest {

    @Test
    @DisplayName("unknownAdditionalFilename() ajoute une erreur de validation")
    void unknownAdditionalFilenameRecordsError() throws Exception {
        AdditionalFileParamsParsingResult.Builder builder = AdditionalFileParamsParsingResult.builder();
        builder.unknownAdditionalFilename("missing.csv", Set.of("known.csv"));

        // Access the list via reflection since it is private
        var field = AdditionalFileParamsParsingResult.Builder.class
                .getDeclaredField("validationCheckResults");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<ValidationCheckResult> errors =
                (java.util.List<ValidationCheckResult>) field.get(builder);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message())
                .isEqualTo("unknownAdditionalFileNameInAdditionalFileError");
    }

    @Test
    @DisplayName("unknownFieldAdditionalFilename() ajoute une erreur de validation")
    void unknownFieldAdditionalFilenameRecordsError() throws Exception {
        AdditionalFileParamsParsingResult.Builder builder = AdditionalFileParamsParsingResult.builder();
        builder.unknownFieldAdditionalFilename("file.csv", "badField", Set.of("goodField"));

        var field = AdditionalFileParamsParsingResult.Builder.class
                .getDeclaredField("validationCheckResults");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.List<ValidationCheckResult> errors =
                (java.util.List<ValidationCheckResult>) field.get(builder);

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).message())
                .isEqualTo("unknownFieldForAdditionalFileNameInAdditionalFileError");
    }

    @Test
    @DisplayName("builder() retourne une nouvelle instance distincte à chaque appel")
    void builderReturnsNewInstance() {
        AdditionalFileParamsParsingResult.Builder b1 = AdditionalFileParamsParsingResult.builder();
        AdditionalFileParamsParsingResult.Builder b2 = AdditionalFileParamsParsingResult.builder();
        assertThat(b1).isNotSameAs(b2);
    }
}
