package fr.inra.oresing.rest.admin.test;

import fr.inra.oresing.config.AlertsProperties;
import fr.inra.oresing.rest.filesenderclient.FileInfos;
import fr.inra.oresing.rest.filesenderclient.FileSenderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

/**
 * Service d'envoi d'un fichier de test via FileSender ( admin ) .
 *
 * <p>SRP : ( a ) genere un petit fichier texte ( ~1 KB ) avec metadata
 * d'horodatage , ( b ) appelle {@link FileSenderRepository#postTransfer}
 * pour chaque destinataire , ( c ) supprime le fichier temp meme en cas
 * d'erreur , ( d ) capture duree + erreur .
 *
 * <p>DRY : reutilise l'infra FileSender existante ( {@link FileSenderRepository} ) -
 * aucune duplication de logique HTTP / chunked upload .
 *
 * <p>Permission : delegue au controller .
 */
@Slf4j
@Service
public class FileSenderTestService {

    public static final String DEFAULT_SUBJECT = "[OpenADOM] Fichier de test administrateur";
    public static final String DEFAULT_MESSAGE = """
            Ce transfert est un test envoyé depuis la console d'administration OpenADOM.

            Si vous recevez ce message avec un lien de téléchargement fonctionnel, le service de transfert de fichiers ( FileSender ) est opérationnel : OpenADOM peut bien transmettre les fichiers volumineux à ses utilisateurs.
            """;

    private static final String TEST_APP_NAME = "openadom-admin-test";
    private static final String TEST_DATA_NAME = "filesender-smoke-test";
    private static final String FILE_PREFIX = "openadom-filesender-test-";
    private static final String FILE_SUFFIX = ".txt";

    private final FileSenderRepository fileSender;
    private final AlertsProperties alertsProperties;

    public FileSenderTestService(FileSenderRepository fileSender, AlertsProperties alertsProperties) {
        this.fileSender = fileSender;
        this.alertsProperties = alertsProperties;
    }

    public List<String> resolveRecipients(List<String> requested) {
        if (requested != null && !requested.isEmpty()) {
            return requested.stream().filter(s -> s != null && !s.isBlank()).toList();
        }
        return alertsProperties.getRecipientsList();
    }

    public FileSenderTestRequest defaultSample() {
        return new FileSenderTestRequest(alertsProperties.getRecipientsList(),
                DEFAULT_SUBJECT, DEFAULT_MESSAGE);
    }

    /**
     * Genere un petit fichier , envoie via FileSender , retourne resultat
     * structure .
     *
     * @return jamais null , downloadUrl renseigne uniquement si success .
     */
    public FileSenderTestResult send(FileSenderTestRequest request) {
        List<String> recipients = resolveRecipients(request.recipients());
        long start = System.nanoTime();
        if (recipients.isEmpty()) {
            return FileSenderTestResult.ko(recipients, durationMs(start),
                    "Aucun destinataire ( requete vide + OPENADOM_ALERT_RECIPIENTS non configure ) ");
        }
        String subject = request.subject() == null || request.subject().isBlank()
                ? DEFAULT_SUBJECT : request.subject();
        String message = request.message() == null ? DEFAULT_MESSAGE : request.message();

        Path tempFile = null;
        try {
            tempFile = writeTempFile();
            long size = Files.size(tempFile);
            String downloadUrl = null;
            for (String to : recipients) {
                FileInfos infos = new FileInfos(TEST_APP_NAME, TEST_DATA_NAME, tempFile,
                        to, subject, message);
                downloadUrl = fileSender.postTransfer(infos);
            }
            long duration = durationMs(start);
            log.info("FileSenderTest OK -> recipients={} subject=\"{}\" sizeBytes={} duration={}ms",
                    recipients, subject, size, duration);
            return FileSenderTestResult.ok(recipients, downloadUrl, size, duration);
        } catch (Exception e) {
            long duration = durationMs(start);
            log.warn("FileSenderTest KO -> recipients={} subject=\"{}\" duration={}ms error={}",
                    recipients, subject, duration, e.getMessage());
            return FileSenderTestResult.ko(recipients, duration, e.getMessage());
        } finally {
            cleanup(tempFile);
        }
    }

    private Path writeTempFile() throws IOException {
        Path file = Files.createTempFile(FILE_PREFIX, FILE_SUFFIX);
        String content = """
                === OpenADOM FileSender smoke test ===

                Date  : %s
                Source : backend OpenADOM ( admin IHM )
                Scope  : verification connectivite FileSender ( upload + transfert )

                Si vous recevez ce fichier , la chaine
                backend -> FileSender REST API est operationnelle .

                Aucune action requise . Fichier supprime cote serveur apres envoi .
                """.formatted(Instant.now());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    private void cleanup(Path file) {
        if (file == null) return;
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            log.warn("Impossible de supprimer le fichier test temp : {} - {}", file, e.getMessage());
        }
    }

    private static long durationMs(long startNs) {
        return (System.nanoTime() - startNs) / 1_000_000L;
    }
}
