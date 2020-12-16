package fr.inra.oresing.rest;

import com.google.common.base.Throwables;
import fr.inra.oresing.checker.CheckerException;
import org.postgresql.util.PSQLException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class OreExceptionHandler {

    @ExceptionHandler(value = CheckerException.class)
    public ResponseEntity<String> handle(CheckerException eee) {
        return ResponseEntity.badRequest().body(eee.getMessage());
    }

    @ExceptionHandler(value = SecurityException.class)
    public ResponseEntity<String> handle(SecurityException eee) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(eee.getMessage());
    }

    @ExceptionHandler(value = BadSqlGrammarException.class)
    public ResponseEntity<String> handle(BadSqlGrammarException badSqlGrammarException) {
        Throwable rootCause = Throwables.getRootCause(badSqlGrammarException);
        if (rootCause instanceof PSQLException && rootCause.getMessage().contains("permission denied")) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(badSqlGrammarException.getMessage());
        }
        throw badSqlGrammarException;
    }

    @ExceptionHandler(value = NoSuchApplicationException.class)
    public ResponseEntity<String> handle(NoSuchApplicationException noSuchApplicationException) {
        return ResponseEntity.badRequest().body(noSuchApplicationException.getMessage());
    }
}