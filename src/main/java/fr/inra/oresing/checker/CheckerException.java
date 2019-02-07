package fr.inra.oresing.checker;

public class CheckerException extends Exception {
    public CheckerException() {
        super();
    }
    public CheckerException(String message) {
        super(message);
    }
    public CheckerException(Throwable cause) {
        super(cause);
    }
    public CheckerException(String message, Throwable cause) {
        super(message, cause);
    }
}
