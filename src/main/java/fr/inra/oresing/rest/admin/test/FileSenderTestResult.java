package fr.inra.oresing.rest.admin.test;

import java.util.List;

/**
 * Resultat d'un envoi FileSender test ( succes ou echec ) .
 *
 * @param success      true si transfert termine
 * @param recipients   liste effectivement utilisee
 * @param downloadUrl  URL de telechargement publiee par FileSender ( si success )
 * @param fileSizeBytes taille du fichier test genere
 * @param durationMs   duree totale en millisecondes
 * @param errorMessage erreur si {@code success=false} , sinon null
 */
public record FileSenderTestResult(
        boolean success,
        List<String> recipients,
        String downloadUrl,
        long fileSizeBytes,
        long durationMs,
        String errorMessage
) {

    public static FileSenderTestResult ok(List<String> recipients, String downloadUrl,
                                          long fileSizeBytes, long durationMs) {
        return new FileSenderTestResult(true, recipients, downloadUrl, fileSizeBytes, durationMs, null);
    }

    public static FileSenderTestResult ko(List<String> recipients, long durationMs, String error) {
        return new FileSenderTestResult(false, recipients, null, 0L, durationMs, error);
    }
}
