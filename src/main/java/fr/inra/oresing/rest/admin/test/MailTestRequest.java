package fr.inra.oresing.rest.admin.test;

import java.util.List;

/**
 * Requete d'envoi d'un mail de test depuis l'IHM admin .
 *
 * <p>Les champs sont fournis par l'utilisateur ( admin ) , avec les valeurs
 * par defaut servies par {@code GET /admin/test/mail/sample} . Tous les
 * champs sont obligatoires sauf {@code recipients} qui retombe sur la
 * liste configuree dans {@code AlertsProperties} si vide / null .
 *
 * @param recipients destinataires ( liste d'emails ) - vide = fallback alerts.env
 * @param subject    objet du mail
 * @param body       corps du mail ( texte brut )
 */
public record MailTestRequest(
        List<String> recipients,
        String subject,
        String body
) {}
