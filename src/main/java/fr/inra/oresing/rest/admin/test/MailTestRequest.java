package fr.inra.oresing.rest.admin.test;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Requete d'envoi d'un mail de test depuis l'IHM admin .
 *
 * <p>Les champs sont fournis par l'utilisateur ( admin ) , avec les valeurs
 * par defaut servies par {@code GET /admin/test/mail/sample} . Tous les
 * champs sont obligatoires sauf {@code recipients} qui retombe sur la
 * liste configuree dans {@code AlertsProperties} si vide / null .
 *
 * <p>Validation appliquee a la deserialisation via {@code @Valid} sur le
 * controller : refuse subject vide , body > 64 KB , et emails malformes .
 *
 * @param recipients destinataires ( liste d'emails ) - vide = fallback alerts.env
 * @param subject    objet du mail ( obligatoire , > 0 char )
 * @param body       corps du mail ( texte brut , max 64 KB )
 */
public record MailTestRequest(
        List<@Email(message = "Adresse mail invalide") String> recipients,
        @NotBlank(message = "Le sujet est obligatoire")
        @Size(max = 256, message = "Sujet trop long ( max 256 caracteres )")
        String subject,
        @Size(max = 65_536, message = "Corps trop long ( max 64 KB )")
        String body
) {}
