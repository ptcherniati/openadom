package fr.inra.oresing.rest.exceptions;

import com.google.common.base.Throwables;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.DisconnectedException;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.data.deposit.validation.ValidationCheckResultRest;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.persistence.AuthenticationFailure;
import fr.inra.oresing.domain.data.deposit.validation.CsvRowValidationCheckResult;
import fr.inra.oresing.domain.exceptions.application.NoSuchApplicationException;
import fr.inra.oresing.domain.exceptions.binaryfile.binaryfile.BadFileOrUUIDQuery;
import fr.inra.oresing.domain.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.domain.exceptions.data.data.BadBinaryFileDatasetQuery;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import fr.inra.oresing.rest.model.configuration.ValidationError;
import fr.inra.oresing.rest.reactive.ReactiveTypeError;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestControllerAdvice
@Slf4j
public class OreExceptionHandler {

    @ExceptionHandler(ValidationError.class)
    public ResponseEntity<ValidationError> handle(final ValidationError eee) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(eee);
    }

    @ExceptionHandler(DisconnectedException.class)
    public ResponseEntity<?> handle(final DisconnectedException disconnectedException) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(disconnectedException);
    }

    @ExceptionHandler(ExpiredJwtException.class)
    public ResponseEntity<ExpiredJwtException> handle(final ExpiredJwtException expiredJwtException) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(expiredJwtException);
    }

    @ExceptionHandler(SiOreIllegalArgumentException.class)
    public ResponseEntity<SiOreIllegalArgumentException> handle(final SiOreIllegalArgumentException eee) {
        return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(eee);
    }

    @ExceptionHandler(AuthenticationFailure.class)
    public ResponseEntity<AuthenticationFailure> handle(final AuthenticationFailure eee) {
        return switch (eee.getMessage()) {
            case "INACTIVE_ACCOUNT" -> {
                final HttpHeaders responseHeaders = new HttpHeaders();
                responseHeaders.set("Id", Optional.ofNullable(eee.getParams()).map(m -> (UUID) m.get("id")).map(UUID::toString).orElse(""));
                responseHeaders.set("Login", Optional.ofNullable(eee.getParams()).map(m -> (String) m.get(("login"))).orElse(""));
                responseHeaders.set("Email", Optional.ofNullable(eee.getParams()).map(m -> (String) m.get(("email"))).orElse(""));
                responseHeaders.set("Result__State", Optional.ofNullable(eee.getParams()).map(m -> (String) m.get(("state"))).orElse(""));
                yield ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                        .headers(responseHeaders)
                        .body(eee);
            }
            case "EXISTING_LOGIN"->ResponseEntity.status(HttpStatus.PRECONDITION_FAILED).body(eee);
            case "BAD_LOGIN_PASSWORD" -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(eee);
            case "BAD_PASSWORDS" -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(eee);
            case "BAD_VALIDATION_KEY" -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(eee);
            default -> ResponseEntity.status(HttpStatus.FORBIDDEN).body(eee);
        };
        //return ResponseEntity.status(HttpStatus.FORBIDDEN).body(eee.getMessage());
    }

    @ExceptionHandler
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public List<ObjectError> exception(final WebExchangeBindException ex) {
        log.error("{}", ex.getLocalizedMessage(), ex);
        return ex.getAllErrors();
    }

    @ExceptionHandler(BadSqlGrammarException.class)
    public ResponseEntity<String> handle(final BadSqlGrammarException badSqlGrammarException) {
        final Throwable rootCause = Throwables.getRootCause(badSqlGrammarException);
        if (rootCause instanceof PSQLException && rootCause.getMessage().contains("permission denied")) {
            if (log.isTraceEnabled()) {
                log.trace("erreur de permission PostgreSQL", badSqlGrammarException);
            }
            return ResponseEntity.status(HttpStatus.NOT_ACCEPTABLE).body(badSqlGrammarException.getMessage());
        }
        throw badSqlGrammarException;
    }

    @ExceptionHandler(NoSuchApplicationException.class)
    public ResponseEntity<String> handle(final NoSuchApplicationException noSuchApplicationException) {
        return ResponseEntity.badRequest().body(noSuchApplicationException.getMessage());
    }

    @ExceptionHandler(BadApplicationConfigurationException.class)
    public ResponseEntity<ConfigurationException> handle(final BadApplicationConfigurationException badApplicationConfigurationException) {
        return ResponseEntity.badRequest().body(badApplicationConfigurationException.getConfigurationException());
    }

    @ExceptionHandler(OreSiTechnicalException.class)
    public ResponseEntity<OreSiTechnicalException> handle(final OreSiTechnicalException oreSiTechnicalException) {
        if("fr.inra.oresing.domain.authorization.privilegeassessor.exception"
                .equals(oreSiTechnicalException.getClass().getPackage().getName())){
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(oreSiTechnicalException);
        }
        log.error("Technical Exception not resolved", oreSiTechnicalException);
        return ResponseEntity.badRequest().body(oreSiTechnicalException);
    }

    @ExceptionHandler(InvalidDatasetContentException.class)
    public ResponseEntity<List<ValidationCheckResultRest>> handle(final InvalidDatasetContentException invalidDatasetContentException) {
        List<ValidationCheckResultRest> validations = invalidDatasetContentException.getErrors()
                .stream()
                .map(row -> {
                    long lineNumber = row.lineNumber();
                    return row.validationCheckResult().validationCheckResultToRest(row.lineNumber());
                })
                .toList();
        return ResponseEntity.badRequest().body(validations);
    }

    @ExceptionHandler(BadBinaryFileDatasetQuery.class)
    public ResponseEntity<String> handle(final BadBinaryFileDatasetQuery badBinaryFileDatasetQuery) {
        return ResponseEntity.badRequest().body(badBinaryFileDatasetQuery.getMessage());
    }

    @ExceptionHandler(BadDownloadDatasetQuery.class)
    public ResponseEntity<String> handle(final BadDownloadDatasetQuery badDownloadDatasetQuery) {
        return ResponseEntity.badRequest().body(badDownloadDatasetQuery.getMessage());
    }

    @ExceptionHandler(BadFileOrUUIDQuery.class)
    public ResponseEntity<String> handle(final BadFileOrUUIDQuery badFileOrUUIDQuery) {
        return ResponseEntity.badRequest().body(badFileOrUUIDQuery.getMessage());
    }
}