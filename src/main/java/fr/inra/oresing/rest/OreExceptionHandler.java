package fr.inra.oresing.rest;

import com.google.common.base.Throwables;
import fr.inra.oresing.persistence.AuthenticationFailure;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.util.PSQLException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
}