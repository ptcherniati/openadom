package fr.inra.oresing.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests unitaires pour {@link BrandingProperties} - defauts + setters .
 */
@Tag("core.config")
@DisplayName("BrandingProperties - defauts + setters")
class BrandingPropertiesTest {

    @Test
    @DisplayName("appTitle defaut = si-ore ( back-compat )")
    void defaultAppTitle() {
        assertEquals("si-ore", new BrandingProperties().getAppTitle());
    }

    @Test
    @DisplayName("faviconUrl defaut = chaine vide")
    void defaultFaviconUrl() {
        assertEquals("", new BrandingProperties().getFaviconUrl());
    }

    @Test
    @DisplayName("setAppTitle null ou blank -> fallback si-ore")
    void appTitleFallback() {
        BrandingProperties props = new BrandingProperties();
        props.setAppTitle(null);
        assertEquals("si-ore", props.getAppTitle());
        props.setAppTitle("   ");
        assertEquals("si-ore", props.getAppTitle());
    }

    @Test
    @DisplayName("setAppTitle valide -> stocke")
    void appTitleSet() {
        BrandingProperties props = new BrandingProperties();
        props.setAppTitle("OpenADOM");
        assertEquals("OpenADOM", props.getAppTitle());
    }

    @Test
    @DisplayName("setFaviconUrl null -> chaine vide ( jamais null )")
    void faviconUrlNeverNull() {
        BrandingProperties props = new BrandingProperties();
        props.setFaviconUrl(null);
        assertEquals("", props.getFaviconUrl());
    }
}
