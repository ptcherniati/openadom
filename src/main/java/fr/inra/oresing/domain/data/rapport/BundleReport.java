package fr.inra.oresing.domain.data.rapport;

import fr.inra.oresing.domain.Mapper;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.event.ImportProgressEvent;
import fr.inra.oresing.domain.filesenderclient.MessageInformations;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rapport d'un import bundle — enregistre les événements d'avancement.
 *
 * <p>Le {@link Mapper} est injecté par le constructeur (Ports & Adapters) :
 * la couche REST passe un {@code JsonRowMapper} qui implémente {@code Mapper}.
 * Le domaine ne connaît jamais l'implémentation concrète.
 */
public record BundleReport(List<ImportProgressEvent> results, Locale locale, String origin, Application application, Mapper mapper) implements MessageInformations {
    record Results(Application application, List<ImportProgressEvent> results) {}
    private static final Map<Locale, String> TITLE_MESSAGES = Map.of(
            Locale.ENGLISH, "Restoration report of %s",
            Locale.FRENCH, "Rapport de restauration de %s"
    );

    private static final Map<Locale, String> BODY_MESSAGES = Map.of(
            Locale.ENGLISH, "To view the restoration report for %s, open the attached report.html in your browser.",
            Locale.FRENCH, "Pour consulter le rapport de restauration de %s, ouvrez le fichier joint report.html dans votre navigateur."
    );

    public Path attachmentFile() throws IOException {
        String htmlTemplate;
        try (InputStream inputStream = getClass().getResourceAsStream("report.html")) {
            htmlTemplate = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
        final String json = mapper().toJson(this);
        String html = htmlTemplate.formatted(json, application().getName(), origin(), "bundleReport");

        // Utilisation de Files.createTempFile pour éviter les collisions de noms (S5443)
        Path tempFile = Files.createTempFile(application().getName() + "_bundleReport", ".html");
        Files.writeString(tempFile, html, StandardCharsets.UTF_8);
        return tempFile;
    }


    public String attachmentName() {
        return "%s_bundleReport.html".formatted(application().getName());
    }


    public BundleReport(Locale locale, String origin, Application application, Mapper mapper) {
        this(new LinkedList<>(), locale, origin, application, mapper);
    }

    public void add(ImportProgressEvent event) {
        results().add(event);
    }

    public String title() {
        return TITLE_MESSAGES.get(locale()).formatted(application().getName());
    }

    public String message() {
        return BODY_MESSAGES.getOrDefault(locale(), BODY_MESSAGES.get(Locale.FRENCH))
                .formatted(application().getName());
    }
}