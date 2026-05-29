package fr.inra.oresing.rest.admin.test;

import java.util.List;

/**
 * Requete d'envoi d'un fichier test via FileSender depuis l'IHM admin .
 *
 * <p>Le contenu du fichier est genere cote backend ( petit payload texte
 * informatif ) , l'utilisateur fournit uniquement les destinataires +
 * sujet . {@code recipients} retombe sur {@code AlertsProperties} si
 * vide / null .
 *
 * @param recipients destinataires - vide = fallback alerts.env
 * @param subject    objet du transfert FileSender
 * @param message    message accompagnant le transfert
 */
public record FileSenderTestRequest(
        List<String> recipients,
        String subject,
        String message
) {}
