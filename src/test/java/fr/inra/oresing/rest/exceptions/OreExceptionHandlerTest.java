package fr.inra.oresing.rest.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.DisconnectedException;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.checker.type.BooleanType;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResultRest;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.BooleanValidationCheckResult;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.application.NoSuchApplicationException;
import fr.inra.oresing.domain.exceptions.binaryfile.binaryfile.BadFileOrUUIDQuery;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.domain.exceptions.data.data.BadBinaryFileDatasetQuery;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import fr.inra.oresing.persistence.AuthenticationFailure;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.postgresql.util.PSQLException;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.support.WebExchangeBindException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class OreExceptionHandlerTest {
    private OreExceptionHandler exceptionHandler;
    private ObjectMapper objectMapper;

    @Mock
    private WebExchangeBindException webExchangeBindException;

    @Mock
    private BadSqlGrammarException badSqlGrammarException;

    @Mock
    private PSQLException psqlException;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        exceptionHandler = new OreExceptionHandler();
        objectMapper = new ObjectMapper();

        // Configuration des mocks
        when(webExchangeBindException.getAllErrors()).thenReturn(List.of(new ObjectError("test", "message")));
        when(badSqlGrammarException.getMessage()).thenReturn("SQL error");
    }

    @Test
    public void testValidationErrorSerializability() {
        ValidationError error = new ValidationError("Test error");
        ResponseEntity<ValidationError> response = exceptionHandler.handle(error);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    public void testDisconnectedExceptionSerializability() {
        DisconnectedException exception = new DisconnectedException("User disconnected");
        ResponseEntity<DisconnectedException> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    public void testSiOreIllegalArgumentExceptionSerializability() {
        SiOreIllegalArgumentException exception = new SiOreIllegalArgumentException("Illegal argument", Map.of("param", "param"));
        ResponseEntity<SiOreIllegalArgumentException> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    public void testAuthenticationFailureSerializability() {
        // Tester différents cas de AuthenticationFailure
        testAuthFailureCase("INACTIVE_ACCOUNT");
        testAuthFailureCase("EXISTING_LOGIN");
        testAuthFailureCase("BAD_LOGIN_PASSWORD");
        testAuthFailureCase("BAD_PASSWORDS");
        testAuthFailureCase("BAD_VALIDATION_KEY");
        testAuthFailureCase("DEFAULT_CASE");
    }

    private void testAuthFailureCase(String message) {
        OreSiUser oreSiUser = new OreSiUser();
        oreSiUser.setId( UUID.randomUUID());
        oreSiUser.setLogin("testuser");
        oreSiUser.setEmail("test@example.com");
        oreSiUser.setAccountstate(OreSiUser.OreSiUserStates.active);

        AuthenticationFailure failure = new AuthenticationFailure(message,
                oreSiUser
        );
        ResponseEntity<AuthenticationFailure> response = exceptionHandler.handle(failure);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    public void testWebExchangeBindExceptionSerializability() {
        List<ObjectError> errors = exceptionHandler.exception(webExchangeBindException);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(errors));
    }

    @Test
    public void testBadSqlGrammarExceptionSerializability() {
        when(badSqlGrammarException.getCause()).thenReturn(psqlException);
        when(psqlException.getMessage()).thenReturn("permission denied");

        ResponseEntity<String> response = exceptionHandler.handle(badSqlGrammarException);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    public void testNoSuchApplicationExceptionSerializability() {
        NoSuchApplicationException exception = new NoSuchApplicationException("Application not found");
        ResponseEntity<String> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    public void testBadApplicationConfigurationExceptionSerializability() {
        ConfigurationException configException = ConfigurationException.IO_EXCEPTION;
        BadApplicationConfigurationException exception = new BadApplicationConfigurationException("Configuration error", configException);
        ResponseEntity<ConfigurationException> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    public void testOreSiTechnicalExceptionSerializability() {
        // Cas d'autorisation
        OreSiTechnicalException authException = new OreSiTechnicalException("Auth error") {
            public String getPackageName() {
                return "fr.inra.oresing.domain.authorization.privilegeassessor.exception";
            }
        };
        ResponseEntity<OreSiTechnicalException> authResponse = exceptionHandler.handle(authException);
        assertDoesNotThrow(() -> objectMapper.writeValueAsString(authResponse.getBody()));

        // Cas technique général
        OreSiTechnicalException techException = new OreSiTechnicalException("Technical error");
        ResponseEntity<OreSiTechnicalException> techResponse = exceptionHandler.handle(techException);
        assertDoesNotThrow(() -> objectMapper.writeValueAsString(techResponse.getBody()));
    }

    @Test
    public void testInvalidDatasetContentExceptionSerializability() {
        List<CsvRowValidationCheckResult> errors = List.of(
                new CsvRowValidationCheckResult(
                        new BooleanValidationCheckResult(
                                ValidationLevel.ERROR,
                                "Error in row 1",
                                Map.of("totot", "toto"),
                                null,
                                BooleanType.forExpression("return true", ImmutableMap.of())
                        ),
                        1
                ),
                new CsvRowValidationCheckResult(
                        new BooleanValidationCheckResult(
                                ValidationLevel.ERROR,
                                "Error in row 2",
                                Map.of("totot", "toto2"),
                                null,
                                BooleanType.forExpression("return false", ImmutableMap.of())
                        ),
                        2
                )
        );
        InvalidDatasetContentException exception = new InvalidDatasetContentException(errors);
        ResponseEntity<List<ValidationCheckResultRest>> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    public void testBadBinaryFileDatasetQuerySerializability() {
        BadBinaryFileDatasetQuery exception = new BadBinaryFileDatasetQuery("Bad binary file query");
        ResponseEntity<String> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    public void testBadDownloadDatasetQuerySerializability() {
        BadDownloadDatasetQuery exception = new BadDownloadDatasetQuery("Bad download query");
        ResponseEntity<String> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    public void testBadFileOrUUIDQuerySerializability() {
        BadFileOrUUIDQuery exception = new BadFileOrUUIDQuery("Bad file or UUID");
        ResponseEntity<String> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }
}