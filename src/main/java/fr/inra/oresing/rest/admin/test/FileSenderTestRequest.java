package fr.inra.oresing.rest.admin.test;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Requete d'envoi d'un fichier test via FileSender depuis l'IHM admin .
 *
 * <p>Le contenu du fichier est genere cote backend ( petit payload texte
 * informatif ) , l'utilisateur fournit uniquement les destinataires +
 * sujet . {@code recipients} retombe sur {@code AlertsProperties} si
 * vide / null .
 *
 * <p>Validation appliquee via {@code @Valid} sur le controller .
 *
 * @param recipients destinataires - vide = fallback alerts.env
 * @param subject    objet du transfert FileSender ( obligatoire )
 * @param message    message accompagnant le transfert ( max 32 KB )
 */
public record FileSenderTestRequest(
        List<@Email(message = "Adresse mail invalide") String> recipients,
        @NotBlank(message = "Le sujet est obligatoire")
        @Size(max = 256, message = "Sujet trop long ( max 256 caracteres )")
        String subject,
        @Size(max = 32_768, message = "Message trop long ( max 32 KB )")
        String message
) {}
