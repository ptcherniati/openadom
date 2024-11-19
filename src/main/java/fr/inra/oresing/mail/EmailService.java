package fr.inra.oresing.mail;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {
    @Value("${spring.mail.from}")
    String mailFrom;
    @Autowired
    private final JavaMailSender mailSender;
    private static final String NEW_ACCOUNT_SUBJECT ="Création de compte / Account creation";
    private static final String NEW_ACCOUNT_FR ="Vous venez de créer un compte sur l'application OPENAdom. %n"  +
            "Pour valider votre e-mail, renseignez la clé de validation lors de la connexion.%n\n" ;
    private static final String NEW_ACCOUNT_EN ="You have just created an account on the OPENAdomoresie application. %n" +
            "To validate your e-mail, enter the validation key when connecting.%n\n" ;
    private static final String EMAIL_CHANGED_SUBJECT ="Validation email / Email validation";
    private static final String EMAIL_CHANGED_FR ="Vous venez de modifier votre email. %n" +
            "Pour valider votre e-mail, renseignez la clé de validation lors de la connexion.%n\n" ;
    private static final String EMAIL_CHANGED_EN ="You have just changed your email. %n" +
            "To validate your e-mail, enter the validation key when connecting.%n\n"  ;
    private static final String VALIDATION_KEY_SUBJECT ="Clef de validation / Validation key";
    private static final String MAIL_VERIFICATION_TEMPLATE = """
            %2$s%n%nVotre clé de connexion est : %n%1$s
            %3$sYour connection key is: %n%1$s
            """;
    private static final String MAIL_MESSAGE_TEMPLATE =
            "Bonjour %1$s%n%n" +
                    "%2$s%n" +
                    "L'équipe d'OpenAdom";
    @Async
    public void sendEmail(final String login, final String to, final String subject, final String message){
        final SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(to);
        mailMessage.setFrom(mailFrom);
        mailMessage.setSubject(subject);
        mailMessage.setText(String.format(MAIL_MESSAGE_TEMPLATE, login, message));
        mailSender.send(mailMessage);
    }

    public void sendEmailValidation(final String login, final String email, final String verificationKey, final MESSAGES messages) {
        String message = String.format(MAIL_VERIFICATION_TEMPLATE, verificationKey, messages.title_fr, messages.title_en);
        sendEmail(login, email, messages.subject, message);
    }
    public enum MESSAGES{

        NEW_ACCOUNT(NEW_ACCOUNT_SUBJECT,NEW_ACCOUNT_FR,NEW_ACCOUNT_EN),
        NEW_EMAIL(EMAIL_CHANGED_SUBJECT,EMAIL_CHANGED_FR,EMAIL_CHANGED_EN),
        VALIDATION_KEY(VALIDATION_KEY_SUBJECT,"","");

        MESSAGES(final String subject, final String title_fr, final String title_en) {
            this.subject = subject;
            this.title_fr = title_fr;
            this.title_en = title_en;
        }

        final String subject;
        final String title_fr;
        final String title_en;
    }
}