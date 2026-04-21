package fr.inra.oresing.workflow.cascade;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Levée par {@link ZipExportRateLimiter#acquireOrThrow(String)} lorsque
 * l'utilisateur a atteint son quota d'extractions ZIP simultanées.
 *
 * <p>Annotée {@code @ResponseStatus(TOO_MANY_REQUESTS)} pour que Spring
 * MVC renvoie automatiquement un 429 quand l'exception remonte du
 * controller.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class ZipExportRateLimitExceededException extends RuntimeException {

    public ZipExportRateLimitExceededException(String userId, int active, int max) {
        super("Quota d'extractions ZIP simultanées atteint pour l'utilisateur " + userId
                + " (" + active + "/" + max + "). Veuillez réessayer plus tard.");
    }
}
