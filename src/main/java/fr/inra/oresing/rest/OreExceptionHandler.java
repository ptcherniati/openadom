package fr.inra.oresing.rest;

import fr.inra.oresing.checker.CheckerException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
}