package fr.inra.oresing;

public class OreSiException extends Exception {

    public OreSiException(String message) {
        super(message);
    }

    public OreSiException(String message, Throwable cause) {
        super(message, cause);
    }
}
