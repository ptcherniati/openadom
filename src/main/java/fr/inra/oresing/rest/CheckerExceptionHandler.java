package fr.inra.oresing.rest;

import fr.inra.oresing.checker.CheckerException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class CheckerExceptionHandler {

    @ExceptionHandler(value = CheckerException.class)
    public ResponseEntity<String> handle(CheckerException eee) {
        return ResponseEntity.badRequest().body(eee.getMessage());
    }
}