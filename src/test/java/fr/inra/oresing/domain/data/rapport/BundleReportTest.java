package fr.inra.oresing.domain.data.rapport;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.event.DomainProgressEvent;
import fr.inra.oresing.domain.event.ImportProgressEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link BundleReport}.
 */
@Tag("domain.model")
@DisplayName("BundleReport – constructeurs, title, message, add")
class BundleReportTest {

    private static final String ORIGIN = "test-origin";

    private Application buildApp(String name) {
        Application app = new Application();
        app.setName(name);
        return app;
    }

    private fr.inra.oresing.domain.Mapper jsonMapper() {
        return obj -> "{}";
    }

    // ─────────────────────────────────────────────────────────────────
    // Constructor with list
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Constructeur(List,Locale,String,Application,Mapper) affecte correctement les champs")
    void constructorWithList() {
        Application app = buildApp("myApp");
        var list = new ArrayList<ImportProgressEvent>();
        BundleReport report = new BundleReport(list, Locale.FRENCH, ORIGIN, app, jsonMapper());
        assertThat(report.results()).isSameAs(list);
        assertThat(report.locale()).isEqualTo(Locale.FRENCH);
        assertThat(report.origin()).isEqualTo(ORIGIN);
        assertThat(report.application()).isSameAs(app);
    }

    @Test
    @DisplayName("Constructeur(Locale,String,Application,Mapper) crée une liste vide")
    void constructorWithoutList() {
        Application app = buildApp("myApp");
        BundleReport report = new BundleReport(Locale.ENGLISH, ORIGIN, app, jsonMapper());
        assertThat(report.results()).isNotNull().isEmpty();
    }

    // ─────────────────────────────────────────────────────────────────
    // add()
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("add() ajoute l'événement dans results")
    void add() {
        Application app = buildApp("myApp");
        BundleReport report = new BundleReport(Locale.FRENCH, ORIGIN, app, jsonMapper());
        ImportProgressEvent event = new DomainProgressEvent(0.5);
        report.add(event);
        assertThat(report.results()).hasSize(1).contains(event);
    }

    // ─────────────────────────────────────────────────────────────────
    // title() en anglais et en français
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("title() en français contient le nom de l'application")
    void titleFrench() {
        Application app = buildApp("monApp");
        BundleReport report = new BundleReport(Locale.FRENCH, ORIGIN, app, jsonMapper());
        assertThat(report.title()).contains("monApp");
    }

    @Test
    @DisplayName("title() en anglais contient le nom de l'application")
    void titleEnglish() {
        Application app = buildApp("myEnglishApp");
        BundleReport report = new BundleReport(Locale.ENGLISH, ORIGIN, app, jsonMapper());
        assertThat(report.title()).contains("myEnglishApp");
    }

    // ─────────────────────────────────────────────────────────────────
    // message()
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("message() en français contient le nom de l'application")
    void messageFrench() {
        Application app = buildApp("appFR");
        BundleReport report = new BundleReport(Locale.FRENCH, ORIGIN, app, jsonMapper());
        assertThat(report.message()).contains("appFR");
    }

    @Test
    @DisplayName("message() en anglais contient le nom de l'application")
    void messageEnglish() {
        Application app = buildApp("appEN");
        BundleReport report = new BundleReport(Locale.ENGLISH, ORIGIN, app, jsonMapper());
        assertThat(report.message()).contains("appEN");
    }

    @Test
    @DisplayName("message() avec locale inconnue retombe sur le français")
    void messageFallbackToFrench() {
        Application app = buildApp("appJA");
        BundleReport report = new BundleReport(Locale.JAPANESE, ORIGIN, app, jsonMapper());
        // BODY_MESSAGES n'a pas JAPANESE → fallback FRENCH
        assertThat(report.message()).contains("appJA");
    }

    // ─────────────────────────────────────────────────────────────────
    // attachmentName()
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("attachmentName() retourne le bon nom de fichier")
    void attachmentName() {
        Application app = buildApp("coolApp");
        BundleReport report = new BundleReport(Locale.FRENCH, ORIGIN, app, jsonMapper());
        assertThat(report.attachmentName()).isEqualTo("coolApp_bundleReport.html");
    }
}
