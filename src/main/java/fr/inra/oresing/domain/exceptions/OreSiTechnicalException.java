package fr.inra.oresing.domain.exceptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties({"suppressed", "stackTrace", "cause"})
public class OreSiTechnicalException extends RuntimeException {

    public OreSiTechnicalException(final String message) {
        super(message);
    }

    public OreSiTechnicalException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
