package fr.inra.oresing.rest.filesenderclient;

import java.io.IOException;

/**
 * Exception dédiée aux erreurs de communication avec le service FileSender.
 * Remplace les {@code throws Exception} génériques (Sonar S2166).
 */
public class FileSenderException extends IOException {

    public FileSenderException(String message) {
        super(message);
    }

    public FileSenderException(String message, Throwable cause) {
        super(message, cause);
    }
}
