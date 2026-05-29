package fr.inra.oresing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration de branding applique par les SPAs ( frontend + oa-live )
 * + Swagger UI .
 *
 * <p>Source unique : variables d'env {@code OPENADOM_BRANDING_APP_TITLE}
 * et {@code OPENADOM_BRANDING_FAVICON_URL} ( cf {@code config/frontend/branding.env} ) .
 *
 * <p>Used by :
 * <ul>
 *   <li>{@code BrandingResources} : sert ces valeurs aux SPAs au boot</li>
 *   <li>{@code application.yml} : substitue {@code springdoc.swagger-ui.title}
 *       depuis {@code OPENADOM_BRANDING_APP_TITLE}</li>
 * </ul>
 *
 * <p>Fallback : "si-ore" pour le titre et favicon vide ( la SPA utilise
 * son default bake' dans index.html ) si non configure .
 */
@ConfigurationProperties(prefix = "openadom.branding")
public class BrandingProperties {

    /** Titre applicatif affiche dans l'onglet navigateur + entete SPA . */
    private String appTitle = "si-ore";

    /**
     * URL ( relative ou absolue ) du favicon a appliquer dynamiquement par
     * les SPAs . Vide = pas d'override , utilise le favicon bake' .
     */
    private String faviconUrl = "";

    public String getAppTitle() {
        return appTitle;
    }

    public void setAppTitle(String appTitle) {
        this.appTitle = appTitle == null || appTitle.isBlank() ? "si-ore" : appTitle;
    }

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl == null ? "" : faviconUrl;
    }
}
