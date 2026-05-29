package fr.inra.oresing.rest.admin.test;

import java.util.List;

/**
 * Resultat d'un envoi de mail test ( succes ou echec ) .
 *
 * @param success      true si SMTP send termine sans erreur
 * @param recipients   liste effectivement utilisee ( apres fallback )
 * @param durationMs   duree totale de l'envoi en millisecondes
 * @param errorMessage message d'erreur si {@code success=false} , sinon null
 */
public record MailTestResult(
        boolean success,
        List<String> recipients,
        long durationMs,
        String errorMessage
) {

    public static MailTestResult ok(List<String> recipients, long durationMs) {
        return new MailTestResult(true, recipients, durationMs, null);
    }

    public static MailTestResult ko(List<String> recipients, long durationMs, String error) {
        return new MailTestResult(false, recipients, durationMs, error);
    }
}
