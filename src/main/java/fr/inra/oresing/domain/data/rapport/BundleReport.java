package fr.inra.oresing.domain.data.rapport;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.filesenderclient.MessageInformations;
import fr.inra.oresing.rest.reactive.ReactiveResult;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record BundleReport(List<ReactiveResult> results, Locale locale, String origin, Application application) implements MessageInformations {
    record Results(Application application, List<ReactiveResult> results) {}
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
        final String json = new JsonRowMapper<BundleReport>().toJson(this);
        String html = htmlTemplate.formatted(json, application().getName(), origin(), "bundleReport");

        File tempFile = new File(System.getProperty("java.io.tmpdir"), attachmentName());
        Files.writeString(tempFile.toPath(), html, StandardCharsets.UTF_8);
        return tempFile.toPath();
    }


    public String attachmentName() {
        return "%s_bundleReport.html".formatted(application().getName());
    }


    public BundleReport(List<ReactiveResult> results, Locale locale, String origin, Application application) {
        this.results = results;
        this.application = application;
        this.locale = locale;
        this.origin = origin;
    }

    public BundleReport(Locale locale, String origin, Application application) {
        this(new LinkedList<>(), locale, origin, application);
    }

    public void add(ReactiveResult reactiveResult) {
        results().add(reactiveResult);
    }

    public String title() {
        return TITLE_MESSAGES.get(locale()).formatted(application().getName());
    }

    public String message() {
        return BODY_MESSAGES.getOrDefault(locale(), BODY_MESSAGES.get(Locale.FRENCH))
                .formatted(application().getName());
    }
}