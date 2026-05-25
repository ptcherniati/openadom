package fr.inra.oresing.rest.exceptions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.checker.type.BooleanType;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResultRest;
import fr.inra.oresing.domain.data.deposit.validation.validationcheckresults.BooleanValidationCheckResult;
import fr.inra.oresing.domain.exceptions.AuthenticationFailure;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.application.NoSuchApplicationException;
import fr.inra.oresing.domain.exceptions.binaryfile.binaryfile.BadFileOrUUIDQuery;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.domain.exceptions.data.data.BadBinaryFileDatasetQuery;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.postgresql.util.PSQLException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.support.WebExchangeBindException;
import io.jsonwebtoken.ExpiredJwtException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.CantSelfRevokeApplicationRoleException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.DisconnectedException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@Tag("domain.model")
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
    void testValidationErrorSerializability() {
        ValidationError error = new ValidationError("Test error");
        ResponseEntity<ValidationError> response = exceptionHandler.handle(error);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    //@Test
    void testDisconnectedExceptionSerializability() {
        /*DisconnectedException exception = new DisconnectedException("User disconnected");
        ResponseEntity<DisconnectedException> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));*/
    }

    @Test
    void testSiOreIllegalArgumentExceptionSerializability() {
        SiOreIllegalArgumentException exception = new SiOreIllegalArgumentException("Illegal argument", Map.of("param", "param"));
        ResponseEntity<SiOreIllegalArgumentException> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    void testAuthenticationFailureSerializability() {
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
        oreSiUser.setId(UUID.randomUUID());
        oreSiUser.setLogin("testuser");
        oreSiUser.setEmail("test@example.com");
        oreSiUser.setAccountstate(OreSiUser.OreSiUserStates.active);

        AuthenticationFailure failure = new AuthenticationFailure(message,
                oreSiUser
        );
        ResponseEntity<String> response = exceptionHandler.handle(failure);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    void testWebExchangeBindExceptionSerializability() {
        List<ObjectError> errors = exceptionHandler.exception(webExchangeBindException);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(errors));
    }

    @Test
    void testBadSqlGrammarExceptionSerializability() {
        when(badSqlGrammarException.getCause()).thenReturn(psqlException);
        when(psqlException.getMessage()).thenReturn("permission denied");

        ResponseEntity<String> response = exceptionHandler.handle(badSqlGrammarException);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    void testNoSuchApplicationExceptionSerializability() {
        NoSuchApplicationException exception = new NoSuchApplicationException("Application not found");
        ResponseEntity<String> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    void testBadApplicationConfigurationExceptionSerializability() {
        ConfigurationException configException = ConfigurationException.IO_EXCEPTION;
        BadApplicationConfigurationException exception = new BadApplicationConfigurationException("Configuration error", configException);
        ResponseEntity<ConfigurationException> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    void testOreSiTechnicalExceptionSerializability() {
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

    /**
     * Vérifie que l'auto-révocation ( règle métier ) renvoie 400 et non 401
     * - le fallback générique du package {@code privilegeassessor.exception}
     * mapperait sur 401 et déclencherait une déconnexion automatique côté
     * Fetcher .
     */
    @Test
    void testCantSelfRevokeReturnsBadRequestNotUnauthorized() {
        CantSelfRevokeApplicationRoleException ex = new CantSelfRevokeApplicationRoleException();
        ResponseEntity<OreSiTechnicalException> response = exceptionHandler.handle(ex);
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertEquals(CantSelfRevokeApplicationRoleException.CANT_SELF_REVOKE_APPLICATION_ROLE,
                response.getBody().getMessage());
    }

    @Test
    void testInvalidDatasetContentExceptionSerializability() {
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
    void testBadBinaryFileDatasetQuerySerializability() {
        BadBinaryFileDatasetQuery exception = new BadBinaryFileDatasetQuery("Bad binary file query");
        ResponseEntity<String> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    void testBadDownloadDatasetQuerySerializability() {
        BadDownloadDatasetQuery exception = new BadDownloadDatasetQuery("Bad download query");
        ResponseEntity<String> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    void testBadFileOrUUIDQuerySerializability() {
        BadFileOrUUIDQuery exception = new BadFileOrUUIDQuery("Bad file or UUID");
        ResponseEntity<String> response = exceptionHandler.handle(exception);

        assertDoesNotThrow(() -> objectMapper.writeValueAsString(response.getBody()));
    }

    @Test
    void handleAccessDenied_returnsUnauthorized() {
        var resp = exceptionHandler.handleAccessDenied(new AccessDeniedException("nope"));
        assertNotNull(resp);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), resp.getStatusCode().value());
    }

    @Test
    void handleDisconnectedException_returnsUnauthorized() {
        DisconnectedException de = new DisconnectedException("disconnected");
        ResponseEntity<?> resp = exceptionHandler.handle(de);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
    }

    @Test
    void handleExpiredJwt_returnsUnauthorized() {
        ExpiredJwtException ex = new ExpiredJwtException(null, null, "expired");
        ResponseEntity<ExpiredJwtException> resp = exceptionHandler.handle(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertNotNull(resp.getBody());
    }

    @Test
    void handleBadCredentials_withExpiredJwtCause_returnsTokenExpired() {
        ExpiredJwtException cause = new ExpiredJwtException(null, null, "expired");
        BadCredentialsException ex = new BadCredentialsException("bad", cause);
        ResponseEntity<Map<String, String>> resp = exceptionHandler.handle(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals("TOKEN_EXPIRED", resp.getBody().get("code"));
    }

    @Test
    void handleBadCredentials_withoutExpiredJwtCause_returnsTokenInvalid() {
        BadCredentialsException ex = new BadCredentialsException("bad");
        ResponseEntity<Map<String, String>> resp = exceptionHandler.handle(ex);
        assertEquals("TOKEN_INVALID", resp.getBody().get("code"));
    }

    @Test
    void handleAuthenticationCredentialsNotFound_returnsTokenInvalid() {
        var ex = new AuthenticationCredentialsNotFoundException("none");
        ResponseEntity<Map<String, String>> resp = exceptionHandler.handle(ex);
        assertEquals(HttpStatus.UNAUTHORIZED, resp.getStatusCode());
        assertEquals("TOKEN_INVALID", resp.getBody().get("code"));
    }

    @Test
    void handleAuthenticationFailure_inactiveAccount_setsHeadersAndPaymentRequired() {
        OreSiUser u = new OreSiUser();
        u.setId(UUID.randomUUID());
        u.setLogin("john");
        u.setEmail("john@x.fr");
        u.setAccountstate(OreSiUser.OreSiUserStates.idle);
        AuthenticationFailure ex = new AuthenticationFailure("INACTIVE_ACCOUNT", u);
        ResponseEntity<String> resp = exceptionHandler.handle(ex);
        assertEquals(HttpStatus.PAYMENT_REQUIRED, resp.getStatusCode());
        assertNotNull(resp.getHeaders().getFirst("Id"));
        assertEquals("john", resp.getHeaders().getFirst("Login"));
        assertEquals("john@x.fr", resp.getHeaders().getFirst("Email"));
    }

    @Test
    void handleAuthenticationFailure_existingLogin_returnsPreconditionFailed() {
        OreSiUser u = newMinimalUser();
        AuthenticationFailure ex = new AuthenticationFailure("EXISTING_LOGIN", u);
        assertEquals(HttpStatus.PRECONDITION_FAILED, exceptionHandler.handle(ex).getStatusCode());
    }

    @Test
    void handleAuthenticationFailure_badRequest_mapsToUnauthorized() {
        OreSiUser u = newMinimalUser();
        AuthenticationFailure ex = new AuthenticationFailure("BAD_REQUEST", u);
        assertEquals(HttpStatus.UNAUTHORIZED, exceptionHandler.handle(ex).getStatusCode());
    }

    private static OreSiUser newMinimalUser() {
        OreSiUser u = new OreSiUser();
        u.setId(UUID.randomUUID());
        u.setLogin("u");
        u.setEmail("u@x.fr");
        return u;
    }

    @Test
    void handleBadSqlGrammar_withoutPermissionDenied_rethrows() {
        // root cause is NOT a PSQLException with "permission denied" => method rethrows
        BadSqlGrammarException ex =
                new BadSqlGrammarException("task", "select 1", new java.sql.SQLException("syntax error"));
        assertThrows(BadSqlGrammarException.class, () -> exceptionHandler.handle(ex));
    }

    @Test
    void handleBadSqlGrammar_permissionDenied_returnsNotAcceptable() throws Exception {
        PSQLException pgEx = new PSQLException(new org.postgresql.util.ServerErrorMessage(
                "S:ERROR\u0000C:42501\u0000Mpermission denied\u0000F:auth.c\u0000L:1\u0000Rcheck\u0000"));
        BadSqlGrammarException ex = new BadSqlGrammarException("task", "select 1", pgEx);
        ResponseEntity<String> resp = exceptionHandler.handle(ex);
        assertEquals(HttpStatus.NOT_ACCEPTABLE, resp.getStatusCode());
    }
}