package fr.inra.oresing.rest.exceptions;

import fr.inra.oresing.domain.exceptions.ExceptionMessage;
import fr.inra.oresing.domain.exceptions.FieldNameTooLongForSqlFieldException;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.rest.exceptions.filesenderclient.FileSenderServiceException;
import fr.inra.oresing.rest.model.authorization.exception.AuthorizationRequestError;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires des exceptions REST et leurs DTOs d'erreur.
 */
@Tag("domain.model")
@DisplayName("REST exceptions et erreurs — constructeurs, messages et constantes")
class RestExceptionsAndErrorsTest {

    // ─── ExceptionMessage ────────────────────────────────────────────────────

    @Nested
    @DisplayName("ExceptionMessage")
    class ExceptionMessageTest {
        @Test
        void toMessageReturnsNonBlankForAll() {
            for (ExceptionMessage msg : ExceptionMessage.values()) {
                assertThat(msg.toMessage()).isNotBlank();
            }
        }

        @Test
        void toMessageIsLowerCamelCase() {
            // NULL_LABEL → nullLabel
            assertThat(ExceptionMessage.NULL_LABEL.toMessage()).isEqualTo("nullLabel");
            assertThat(ExceptionMessage.IO_EXCEPTION.toMessage()).isEqualTo("ioException");
        }

        @Test
        void allValuesPresent() {
            assertThat(ExceptionMessage.values()).hasSizeGreaterThan(5);
        }
    }

    // ─── OreSiIOException ────────────────────────────────────────────────────

    @Nested
    @DisplayName("OreSiIOException")
    class OreSiIOExceptionTest {
        @Test
        void constructor() {
            OreSiIOException ex = new OreSiIOException("TEST");
            assertThat(ex.getMessage()).isEqualTo("TEST");
        }

        @Test
        void factoryMethod() {
            OreSiIOException ex = OreSiIOException.ORE_SI_IOEXCEPTION_CANT_LOAD_FILE();
            assertThat(ex.getMessage()).isNotBlank();
        }
    }

    // ─── FieldNameTooLongForSqlFieldException ────────────────────────────────

    @Nested
    @DisplayName("FieldNameTooLongForSqlFieldException")
    class FieldNameTooLongForSqlFieldExceptionTest {
        @Test
        void constructor() {
            FieldNameTooLongForSqlFieldException ex = new FieldNameTooLongForSqlFieldException("msg");
            assertThat(ex.getMessage()).isEqualTo("msg");
        }
    }

    // ─── FileSenderServiceException ──────────────────────────────────────────

    @Nested
    @DisplayName("FileSenderServiceException")
    class FileSenderServiceExceptionTest {
        @Test
        void constructor() {
            FileSenderServiceException ex = new FileSenderServiceException("file error");
            assertThat(ex.getMessage()).isEqualTo("file error");
        }
    }

    // ─── AuthorizationRequestError ───────────────────────────────────────────

    @Nested
    @DisplayName("AuthorizationRequestError")
    class AuthorizationRequestErrorTest {
        @Test
        void constructorWithEnum() {
            AuthorizationRequestError err = new AuthorizationRequestError(
                    AuthorizationRequestException.NO_AUTHORIZATION_NAME,
                    Map.of("k", "v"));
            assertThat(err.getMessage()).isNotBlank();
        }

        @Test
        void constructorWithString() {
            AuthorizationRequestError err = new AuthorizationRequestError("direct message");
            assertThat(err.getMessage()).isEqualTo("direct message");
        }
    }

    // ─── ValidationError ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("ValidationError")
    class ValidationErrorTest {
        @Test
        void constructorWithEnum() {
            ValidationError err = new ValidationError(ConfigurationException.exception, Map.of("x", 1));
            assertThat(err.getMessage()).isNotBlank();
            assertThat(err.getParams()).containsEntry("x", 1);
        }

        @Test
        void constructorWithString() {
            ValidationError err = new ValidationError("some error");
            assertThat(err.getMessage()).isEqualTo("some error");
        }

        @Test
        void getParam() {
            ValidationError err = new ValidationError(ConfigurationException.exception, Map.of("p1", "val"));
            assertThat(err.getParam("p1")).isEqualTo("val");
        }

        @Test
        void getParamMissing() {
            ValidationError err = new ValidationError("x");
            assertThat(err.getParam("missing")).isEqualTo("");
        }
    }
}