package fr.inra.oresing.domain.exceptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Optional;

@JsonIgnoreProperties({"suppressed", "stackTrace", "cause"})
public class OreSiTechnicalException extends RuntimeException {
    public static final String NO_MESSAGE = "NO_MESSAGE";

    public OreSiTechnicalException(final String message) {
        super(message);
    }

    public OreSiTechnicalException(final String message, final Throwable cause) {
        super(message, cause);
    }

    @Override
    public String getMessage() {
        return Optional.of(super.getMessage()).orElse(NO_MESSAGE);
    }
}