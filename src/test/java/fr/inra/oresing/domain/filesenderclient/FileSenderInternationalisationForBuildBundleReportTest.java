package fr.inra.oresing.domain.filesenderclient;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.ApplicationDescription;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import org.junit.jupiter.api.Tag;

/**
 * Tests unitaires pour {@link FileSenderInternationalisationForBuildBundleReport}.
 */
@Tag("domain.i18n")
class FileSenderInternationalisationForBuildBundleReportTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static Application applicationWithConfig(Locale defaultLang, String titleFr, String titleEn,
                                                     String descFr, String descEn) {
        Application app = Mockito.mock(Application.class);
        Configuration config = Mockito.mock(Configuration.class);
        ApplicationDescription appDesc = Mockito.mock(ApplicationDescription.class);
        Internationalizations i18n = new Internationalizations();
        InternationalizationTitle i18nApp = new InternationalizationTitle();
        i18nApp.setTitle(Map.of(Locale.FRENCH, titleFr, Locale.ENGLISH, titleEn));
        i18nApp.setDescription(Map.of(Locale.FRENCH, descFr, Locale.ENGLISH, descEn));
        i18n.setApplication(i18nApp);
        when(app.getConfiguration()).thenReturn(config);
        when(config.i18n()).thenReturn(i18n);
        when(config.applicationDescription()).thenReturn(appDesc);
        when(appDesc.defaultLanguage()).thenReturn(defaultLang);
        return app;
    }

    private static Application applicationWithoutConfig() {
        Application app = Mockito.mock(Application.class);
        when(app.getConfiguration()).thenReturn(null);
        when(app.getName()).thenReturn("mon-app");
        return app;
    }

    private static BuildBundleReport bundleReport(Application app, Locale locale,
                                                   List<String> avecDonnees,
                                                   List<String> exemple,
                                                   List<String> enErreur) {
        return new BuildBundleReport(app, avecDonnees, exemple, enErreur, locale);
    }

    // -------------------------------------------------------------------------
    // subjectPattern & messagePattern — fallback to default language
    // -------------------------------------------------------------------------
    @Nested
    class Patterns {

        @Test
        void subjectPatternInFrench() {
            Application app = applicationWithConfig(Locale.FRENCH, "App FR", "App EN", "Desc FR", "Desc EN");
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.FRENCH, List.of(), List.of(), List.of()));
            // Le pattern français contient "Fichier ZIP"
            assertThat(sut.subjectPattern()).contains("Fichier ZIP");
        }

        @Test
        void subjectPatternInEnglish() {
            Application app = applicationWithConfig(Locale.ENGLISH, "App FR", "App EN", "Desc FR", "Desc EN");
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.ENGLISH, List.of(), List.of(), List.of()));
            assertThat(sut.subjectPattern()).contains("bulk");
        }

        @Test
        void subjectPatternFallbackToDefaultLanguageWhenLocaleUnknown() {
            Application app = applicationWithConfig(Locale.FRENCH, "App FR", "App EN", "Desc FR", "Desc EN");
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.GERMAN, List.of(), List.of(), List.of()));
            // Fallback vers le français (langue par défaut de l'application)
            assertThat(sut.subjectPattern()).contains("Fichier ZIP");
        }

        @Test
        void messagePatternInFrench() {
            Application app = applicationWithConfig(Locale.FRENCH, "App FR", "App EN", "Desc FR", "Desc EN");
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.FRENCH, List.of(), List.of(), List.of()));
            assertThat(sut.messagePattern()).contains("ZIP");
        }

        @Test
        void messagePatternInEnglish() {
            Application app = applicationWithConfig(Locale.ENGLISH, "App FR", "App EN", "Desc FR", "Desc EN");
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.ENGLISH, List.of(), List.of(), List.of()));
            assertThat(sut.messagePattern()).contains("ZIP");
        }
    }

    // -------------------------------------------------------------------------
    // getDefaultLanguage
    // -------------------------------------------------------------------------
    @Nested
    class DefaultLanguage {

        @Test
        void returnsConfiguredLanguage() {
            Application app = applicationWithConfig(Locale.ENGLISH, "t", "t", "d", "d");
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.ENGLISH, List.of(), List.of(), List.of()));
            assertThat(sut.getDefaultLanguage()).isEqualTo(Locale.ENGLISH);
        }

        @Test
        void fallbackToFrenchWhenConfigNull() {
            Application app = applicationWithoutConfig();
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.ENGLISH, List.of(), List.of(), List.of()));
            assertThat(sut.getDefaultLanguage()).isEqualTo(Locale.FRENCH);
        }
    }

    // -------------------------------------------------------------------------
    // getInternationnalizedApplication
    // -------------------------------------------------------------------------
    @Nested
    class InternationalisedApplication {

        @Test
        void returnsTitleForLocale() {
            Application app = applicationWithConfig(Locale.FRENCH, "Titre FR", "Title EN", "Desc FR", "Desc EN");
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.FRENCH, List.of(), List.of(), List.of()));
            assertThat(sut.getInternationnalizedApplication(Locale.FRENCH)).isEqualTo("Titre FR");
        }

        @Test
        void fallbackToAppNameWhenNoConfig() {
            Application app = applicationWithoutConfig();
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.FRENCH, List.of(), List.of(), List.of()));
            assertThat(sut.getInternationnalizedApplication(Locale.FRENCH)).isEqualTo("mon-app");
        }
    }

    // -------------------------------------------------------------------------
    // getInternationnalizedApplicationDescription
    // -------------------------------------------------------------------------
    @Nested
    class InternationalisedApplicationDescription {

        @Test
        void returnsDescriptionForLocale() {
            Application app = applicationWithConfig(Locale.FRENCH, "Titre FR", "Title EN", "Description FR", "Description EN");
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.FRENCH, List.of(), List.of(), List.of()));
            assertThat(sut.getInternationnalizedApplicationDescription(Locale.FRENCH)).isEqualTo("Description FR");
        }

        @Test
        void fallbackToAppNameWhenNoConfig() {
            Application app = applicationWithoutConfig();
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.FRENCH, List.of(), List.of(), List.of()));
            assertThat(sut.getInternationnalizedApplicationDescription(Locale.FRENCH)).isEqualTo("mon-app");
        }
    }

    // -------------------------------------------------------------------------
    // getInternationnalizedDataName — doit lever NotImplementedException
    // -------------------------------------------------------------------------
    @Test
    void getInternationnalizedDataNameThrowsNotImplemented() {
        Application app = applicationWithoutConfig();
        var sut = new FileSenderInternationalisationForBuildBundleReport(
                bundleReport(app, Locale.FRENCH, List.of(), List.of(), List.of()));
        assertThatThrownBy(() -> sut.getInternationnalizedDataName(Locale.FRENCH, "ref"))
                .isInstanceOf(NotImplementedException.class);
    }

    // -------------------------------------------------------------------------
    // mailMessagefor
    // -------------------------------------------------------------------------
    @Nested
    class MailMessage {

        @Test
        void frenchMailContainsSummaryData() {
            Application app = applicationWithConfig(Locale.FRENCH, "App FR", "App EN", "d", "d");
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.FRENCH,
                            List.of("ref1", "ref2"),
                            List.of("ref3"),
                            List.of("ref4")));
            String mail = sut.mailMessagefor("Corps du message", 30);
            assertThat(mail).contains("Corps du message");
            assertThat(mail).contains("30");
            assertThat(mail).contains("ref1, ref2");
            assertThat(mail).contains("ref3");
            assertThat(mail).contains("ref4");
        }

        @Test
        void englishMailContainsSummaryData() {
            Application app = applicationWithConfig(Locale.ENGLISH, "App FR", "App EN", "d", "d");
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.ENGLISH,
                            List.of("refA"),
                            List.of(),
                            List.of()));
            String mail = sut.mailMessagefor("Message body", 7);
            assertThat(mail).contains("Message body");
            assertThat(mail).contains("7");
            assertThat(mail).contains("refA");
        }

        @Test
        void emptyReferentialLists() {
            Application app = applicationWithConfig(Locale.FRENCH, "App FR", "App EN", "d", "d");
            var sut = new FileSenderInternationalisationForBuildBundleReport(
                    bundleReport(app, Locale.FRENCH,
                            List.of(), List.of(), List.of()));
            String mail = sut.mailMessagefor("msg", 10);
            assertThat(mail).isNotBlank();
            // les trois champs sont vides mais le template tient
            assertThat(mail).contains("10");
        }
    }
}