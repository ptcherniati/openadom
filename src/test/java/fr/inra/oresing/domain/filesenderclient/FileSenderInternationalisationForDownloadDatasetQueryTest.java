package fr.inra.oresing.domain.filesenderclient;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.ApplicationDescription;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQueryNoFilter;
import fr.inra.oresing.domain.data.read.query.OutPut;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs de {@link FileSenderInternationalisationForDownloadDatasetQuery}.
 * Aucun contexte Spring.
 */
@Tag("domain.i18n")
@DisplayName("FileSenderInternationalisationForDownloadDatasetQuery")
class FileSenderInternationalisationForDownloadDatasetQueryTest {

    // ─────────────────────────────────────────────────────────────────────────
    //  Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static Application mockAppWithDefaultLang(Locale defaultLang) {
        Application app = Mockito.mock(Application.class);
        Configuration config = Mockito.mock(Configuration.class);
        ApplicationDescription appDesc = Mockito.mock(ApplicationDescription.class);
        when(app.getConfiguration()).thenReturn(config);
        when(config.applicationDescription()).thenReturn(appDesc);
        when(appDesc.defaultLanguage()).thenReturn(defaultLang);
        return app;
    }

    private static Application mockAppWithI18n(Locale defaultLang, String titleFr, String titleEn) {
        Application app = mockAppWithDefaultLang(defaultLang);
        Configuration config = app.getConfiguration();
        Internationalizations i18n = new Internationalizations();
        InternationalizationTitle i18nApp = new InternationalizationTitle();
        i18nApp.setTitle(Map.of(Locale.FRENCH, titleFr, Locale.ENGLISH, titleEn));
        i18n.setApplication(i18nApp);
        when(config.i18n()).thenReturn(i18n);
        return app;
    }

    private static FileSenderInternationalisationForDownloadDatasetQuery senderFor(Application app, Locale locale) {
        DownloadDatasetQueryNoFilter query = new DownloadDatasetQueryNoFilter(
                app, "testData", new OutPut(locale, 0L, 100L), Set.of(), Set.of(), false);
        return new FileSenderInternationalisationForDownloadDatasetQuery(query);
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  subjectPattern()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("subjectPattern()")
    class SubjectPatternTest {

        @Test
        @DisplayName("locale FRENCH retourne le pattern français")
        void frenchLocale() {
            FileSenderInternationalisationForDownloadDatasetQuery sender =
                    senderFor(mockAppWithDefaultLang(Locale.FRENCH), Locale.FRENCH);
            assertThat(sender.subjectPattern()).contains("Chargement");
        }

        @Test
        @DisplayName("locale ENGLISH retourne le pattern anglais")
        void englishLocale() {
            FileSenderInternationalisationForDownloadDatasetQuery sender =
                    senderFor(mockAppWithDefaultLang(Locale.FRENCH), Locale.ENGLISH);
            assertThat(sender.subjectPattern()).contains("Loading");
        }

        @Test
        @DisplayName("locale inconnue utilise la locale par défaut de l'application")
        void unknownLocale() {
            FileSenderInternationalisationForDownloadDatasetQuery sender =
                    senderFor(mockAppWithDefaultLang(Locale.FRENCH), Locale.GERMAN);
            assertThat(sender.subjectPattern()).contains("Chargement");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  messagePattern()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("messagePattern()")
    class MessagePatternTest {

        @Test
        @DisplayName("locale FRENCH retourne le pattern français")
        void frenchLocale() {
            FileSenderInternationalisationForDownloadDatasetQuery sender =
                    senderFor(mockAppWithDefaultLang(Locale.FRENCH), Locale.FRENCH);
            assertThat(sender.messagePattern()).contains("Resultat");
        }

        @Test
        @DisplayName("locale ENGLISH retourne le pattern anglais")
        void englishLocale() {
            FileSenderInternationalisationForDownloadDatasetQuery sender =
                    senderFor(mockAppWithDefaultLang(Locale.FRENCH), Locale.ENGLISH);
            assertThat(sender.messagePattern()).contains("Result data");
        }
    }

    @Nested
    @DisplayName("mailMessagefor()")
    class MailMessageforTest {

        @Test
        @DisplayName("locale FRENCH intègre le message et le délai dans le template")
        void frenchMail() {
            FileSenderInternationalisationForDownloadDatasetQuery sender =
                    senderFor(mockAppWithDefaultLang(Locale.FRENCH), Locale.FRENCH);
            String mail = sender.mailMessagefor("http://example.com", 7);
            assertThat(mail).contains("http://example.com")
                    .contains("7")
                    .contains("Vous pourrez");
        }

        @Test
        @DisplayName("locale ENGLISH intègre le message et le délai dans le template anglais")
        void englishMail() {
            FileSenderInternationalisationForDownloadDatasetQuery sender =
                    senderFor(mockAppWithDefaultLang(Locale.FRENCH), Locale.ENGLISH);
            String mail = sender.mailMessagefor("http://link.com", 14);
            assertThat(mail).contains("http://link.com")
                    .contains("14")
                    .contains("You can");
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  getDefaultLanguage()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getDefaultLanguage()")
    class GetDefaultLanguageTest {

        @Test
        @DisplayName("retourne la locale configurée dans l'application")
        void returnsConfiguredLocale() {
            FileSenderInternationalisationForDownloadDatasetQuery sender =
                    senderFor(mockAppWithDefaultLang(Locale.ENGLISH), Locale.FRENCH);
            assertThat(sender.getDefaultLanguage()).isEqualTo(Locale.ENGLISH);
        }

        @Test
        @DisplayName("retourne FRENCH si getConfiguration() est null")
        void returnsFrenchWhenNoConfig() {
            Application app = Mockito.mock(Application.class);
            when(app.getConfiguration()).thenReturn(null);
            FileSenderInternationalisationForDownloadDatasetQuery sender = senderFor(app, Locale.FRENCH);
            assertThat(sender.getDefaultLanguage()).isEqualTo(Locale.FRENCH);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  getInternationnalizedApplication()
    // ─────────────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getInternationnalizedApplication()")
    class GetInternationalisedApplicationTest {

        @Test
        @DisplayName("retourne le titre dans la locale demandée")
        void returnsTitleForLocale() {
            Application app = mockAppWithI18n(Locale.FRENCH, "Mon App", "My App");
            FileSenderInternationalisationForDownloadDatasetQuery sender = senderFor(app, Locale.FRENCH);
            assertThat(sender.getInternationnalizedApplication(Locale.FRENCH)).isEqualTo("Mon App");
            assertThat(sender.getInternationnalizedApplication(Locale.ENGLISH)).isEqualTo("My App");
        }

        @Test
        @DisplayName("retourne null si la configuration est absente")
        void returnsNullWhenNoConfig() {
            Application app = Mockito.mock(Application.class);
            when(app.getConfiguration()).thenReturn(null);
            FileSenderInternationalisationForDownloadDatasetQuery sender = senderFor(app, Locale.FRENCH);
            assertThat(sender.getInternationnalizedApplication(Locale.FRENCH)).isNull();
        }
    }
}
