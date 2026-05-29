package fr.inra.oresing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Centralise la configuration des destinataires d'alertes administratives .
 *
 * <p>Source unique : variable d'environnement {@code OPENADOM_ALERT_RECIPIENTS}
 * ( meme valeur que celle injectee dans Grafana via
 * {@code config/infra/alerts.env} ) , exposee au backend via le bloc
 * {@code environment} de {@code backend/compose.yml} .
 *
 * <p>Used by : {@code MailTestService} ( destinataires par defaut du test
 * mail ) et {@code FileSenderTestService} ( destinataires par defaut du test
 * FileSender ) . User peut toujours override per-request .
 *
 * <p>Format : liste comma-separated ( ex {@code admin@inrae.fr,ops@inrae.fr} )
 * - parsing tolerant aux espaces , filtre les valeurs vides .
 */
@ConfigurationProperties(prefix = "openadom.alert")
public class AlertsProperties {

    /** Liste brute comma-separated lue depuis env . Jamais {@code null} . */
    private String recipients = "";

    public String getRecipients() {
        return recipients;
    }

    public void setRecipients(String recipients) {
        this.recipients = recipients == null ? "" : recipients;
    }

    /**
     * Liste des destinataires nettoyee ( split sur virgule , trim , filtre
     * vides ) . Liste immutable , jamais {@code null} - vide si aucune
     * configuration .
     */
    public List<String> getRecipientsList() {
        if (recipients.isBlank()) return List.of();
        return Arrays.stream(recipients.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toUnmodifiableList());
    }
}
