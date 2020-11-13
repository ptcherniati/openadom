package fr.inra.oresing;

public class OreSiTechnicalException extends RuntimeException {

    public OreSiTechnicalException(String message) {
        super(message);
    }

    public OreSiTechnicalException(String message, Throwable cause) {
        super(message, cause);
    }
}
