package fr.inra.oresing.workflow.cascade;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Levee par {@link ImportRateLimiter#acquireOrThrow(String)} lorsque
 * l'utilisateur a atteint son quota d'imports simultanes.
 *
 * <p>Annotee {@code @ResponseStatus(TOO_MANY_REQUESTS)} pour que Spring
 * MVC renvoie automatiquement un 429 quand l'exception remonte du
 * controller.
 */
@ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
public class ImportRateLimitExceededException extends RuntimeException {

    public ImportRateLimitExceededException(String userId, int active, int max) {
        super("Quota d'imports simultanes atteint pour l'utilisateur " + userId
                + " (" + active + "/" + max + "). Veuillez reessayer plus tard.");
    }
}
