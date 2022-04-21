package fr.inra.oresing.rest;

import com.google.common.base.Throwables;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.checker.InvalidDatasetContentException;
import fr.inra.oresing.persistence.AuthenticationFailure;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
@Slf4j
public class OreExceptionHandler {

    @ExceptionHandler(value = AuthenticationFailure.class)
    public ResponseEntity<String> handle(AuthenticationFailure eee) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(eee.getMessage());
    }

    @ExceptionHandler(value = BadSqlGrammarException.class)
    public ResponseEntity<String> handle(BadSqlGrammarException badSqlGrammarException) {
        Throwable rootCause = Throwables.getRootCause(badSqlGrammarException);
        if (rootCause instanceof PSQLException && rootCause.getMessage().contains("permission denied")) {
            if (log.isTraceEnabled()) {
                log.trace("erreur de permission PostgreSQL", badSqlGrammarException);
            }
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(badSqlGrammarException.getMessage());
        }
        throw badSqlGrammarException;
    }

    @ExceptionHandler(value = NoSuchApplicationException.class)
    public ResponseEntity<String> handle(NoSuchApplicationException noSuchApplicationException) {
        return ResponseEntity.badRequest().body(noSuchApplicationException.getMessage());
    }

    @ExceptionHandler(value = BadApplicationConfigurationException.class)
    public ResponseEntity<ConfigurationParsingResult> handle(BadApplicationConfigurationException badApplicationConfigurationException) {
        return ResponseEntity.badRequest().body(badApplicationConfigurationException.getConfigurationParsingResult());
    }
    @ExceptionHandler(value = OreSiTechnicalException.class)
    public ResponseEntity<OreSiTechnicalException> handle(OreSiTechnicalException oreSiTechnicalException) {
        log.error("Technical Exception not resolved", oreSiTechnicalException);
        return ResponseEntity.badRequest().body(oreSiTechnicalException);
    }

    @ExceptionHandler(value = InvalidDatasetContentException.class)
    public ResponseEntity<List<CsvRowValidationCheckResult>> handle(InvalidDatasetContentException invalidDatasetContentException) {
        return ResponseEntity.badRequest().body(invalidDatasetContentException.getErrors());
    }
    
    @ExceptionHandler(value = BadBinaryFileDatasetQuery.class)
    public ResponseEntity<String> handle(BadBinaryFileDatasetQuery badBinaryFileDatasetQuery) {
        return ResponseEntity.badRequest().body(badBinaryFileDatasetQuery.getMessage());
    }

    @ExceptionHandler(value = BadDownloadDatasetQuery.class)
    public ResponseEntity<String> handle(BadDownloadDatasetQuery badDownloadDatasetQuery) {
        return ResponseEntity.badRequest().body(badDownloadDatasetQuery.getMessage());
    }

    @ExceptionHandler(value = BadFileOrUUIDQuery.class)
    public ResponseEntity<String> handle(BadFileOrUUIDQuery badFileOrUUIDQuery) {
        return ResponseEntity.badRequest().body(badFileOrUUIDQuery.getMessage());
    }
}