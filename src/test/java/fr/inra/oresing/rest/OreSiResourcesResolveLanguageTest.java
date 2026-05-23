package fr.inra.oresing.rest;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires de {@link OreSiResources#resolveLanguage(String)} ,
 * helper pur extrayant le code langue d'un header {@code Accept-Language} .
 *
 * <p>Sert au endpoint {@code GET /filters} qui propage cette langue au
 * calcul des {@code variables} ( filtre {@code isHiddenOrHasLangRestriction}
 * dans {@code FilterListVariablesExtractor} ) .
 */
@Tag("domain.model")
@DisplayName("OreSiResources.resolveLanguage - parsing Accept-Language header")
class OreSiResourcesResolveLanguageTest {

    @Nested
    class HappyPath {

        @Test
        @DisplayName("code langue simple 'fr' -> 'fr'")
        void simpleFr() {
            assertThat(OreSiResources.resolveLanguage("fr")).isEqualTo("fr");
        }

        @Test
        @DisplayName("code langue simple 'en' -> 'en'")
        void simpleEn() {
            assertThat(OreSiResources.resolveLanguage("en")).isEqualTo("en");
        }

        @Test
        @DisplayName("locale composée 'fr-FR' -> 'fr' ( garde le code langue )")
        void localeComposeeFrFR() {
            assertThat(OreSiResources.resolveLanguage("fr-FR")).isEqualTo("fr");
        }

        @Test
        @DisplayName("locale composée 'en-US' -> 'en'")
        void localeComposeeEnUS() {
            assertThat(OreSiResources.resolveLanguage("en-US")).isEqualTo("en");
        }
    }

    @Nested
    class QValues {

        @Test
        @DisplayName("plusieurs locales avec q-values -> garde le 1er token")
        void multipleLocalesQValues() {
            // Header HTTP : "fr-FR,fr;q=0.9,en;q=0.8"
            assertThat(OreSiResources.resolveLanguage("fr-FR,fr;q=0.9,en;q=0.8")).isEqualTo("fr");
        }

        @Test
        @DisplayName("preference forte sur 'en' au premier token")
        void enFirst() {
            assertThat(OreSiResources.resolveLanguage("en-GB,fr;q=0.5")).isEqualTo("en");
        }
    }

    @Nested
    class Fallback {

        @Test
        @DisplayName("header null -> default locale du système")
        void nullHeader() {
            assertThat(OreSiResources.resolveLanguage(null))
                    .isEqualTo(OreSiResources.DEFAULT_LANGUAGE);
        }

        @Test
        @DisplayName("header vide -> default locale du système")
        void emptyHeader() {
            assertThat(OreSiResources.resolveLanguage(""))
                    .isEqualTo(OreSiResources.DEFAULT_LANGUAGE);
        }

        @Test
        @DisplayName("header blanc -> default locale du système")
        void blankHeader() {
            assertThat(OreSiResources.resolveLanguage("   "))
                    .isEqualTo(OreSiResources.DEFAULT_LANGUAGE);
        }

        @Test
        @DisplayName("token vide après split -> default")
        void emptyFirstToken() {
            // "," tout seul casse le split -> fallback default
            assertThat(OreSiResources.resolveLanguage(","))
                    .isEqualTo(OreSiResources.DEFAULT_LANGUAGE);
        }
    }

    @Nested
    class Robustness {

        @Test
        @DisplayName("uppercase -> lowercase")
        void uppercaseNormalized() {
            assertThat(OreSiResources.resolveLanguage("FR-FR")).isEqualTo("fr");
            assertThat(OreSiResources.resolveLanguage("EN")).isEqualTo("en");
        }

        @Test
        @DisplayName("espaces autour du token -> trimmés")
        void whitespaceAroundToken() {
            assertThat(OreSiResources.resolveLanguage("  fr-FR  ,en")).isEqualTo("fr");
        }

        @Test
        @DisplayName("locale non standard ( ex 'zh-CN' ) -> code langue extrait")
        void nonStandardLocale() {
            assertThat(OreSiResources.resolveLanguage("zh-CN")).isEqualTo("zh");
            assertThat(OreSiResources.resolveLanguage("de-DE,en;q=0.5")).isEqualTo("de");
        }
    }

    @Nested
    class IntegrationWithLocale {

        @Test
        @DisplayName("le code langue retourné est utilisable par Locale.of()")
        void resultUsableInLocaleOf() {
            String lang = OreSiResources.resolveLanguage("fr-FR");
            Locale loc = Locale.of(lang);
            assertThat(loc.getLanguage()).isEqualTo("fr");
        }
    }
}
