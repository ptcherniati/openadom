package fr.inra.oresing.domain.exceptions;

/**
 * Levee quand l'envoi d'un email echoue ( serveur SMTP injoignable ,
 * authentification refusee , timeout reseau , etc. ) .
 *
 * <p>Contrat : flow appelant ne doit JAMAIS persister une mutation
 * irreversible AVANT envoi du mail . Cette exception est traduite
 * en HTTP 503 ( Service Unavailable ) cote {@code OreExceptionHandler} ,
 * permettant au frontend d'afficher un message clair invitant
 * l'utilisateur a reessayer plus tard .
 *
 * <p>Cas d'usage : envoi de cle de validation ( activation , mot de
 * passe oublie , changement d'email ) - cf {@code EmailService.sendEmailValidation} .
 */
public class MailServiceUnavailableException extends OreSiTechnicalException {

    public static final String CODE = "MAIL_SERVICE_UNAVAILABLE";

    public MailServiceUnavailableException(final String detail, final Throwable cause) {
        super(CODE + ": " + detail, cause);
    }
}
