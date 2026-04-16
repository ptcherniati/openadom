package fr.inra.oresing.domain.exceptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Optional;

@JsonIgnoreProperties({"suppressed", "stackTrace", "cause"})
public class OreSiTechnicalException extends RuntimeException {
    public static final String NO_MESSAGE = "NO_MESSAGE";

    public OreSiTechnicalException(final String message) {
        super(Optional.ofNullable(message).orElse(NO_MESSAGE));
    }

    public OreSiTechnicalException(final String message, final Throwable cause) {
        super(Optional.ofNullable(message).orElse(NO_MESSAGE), cause);
    }

    @Override
    public String getMessage() {
        return super.getMessage();
    }
}