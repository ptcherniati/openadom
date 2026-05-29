package fr.inra.oresing.rest;

import fr.inra.oresing.config.BrandingProperties;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint public ( sans auth ) servant la config de branding
 * applicatif ( titre + favicon ) aux SPAs au boot .
 *
 * <p>Public car charge par index.html avant que l'utilisateur ne se
 * soit authentifie - la page de login elle-meme doit afficher le bon
 * titre + favicon .
 */
@RestController
@RequestMapping(value = "/api/v1/branding", produces = MediaType.APPLICATION_JSON_VALUE)
public class BrandingResources {

    private final BrandingProperties properties;

    public BrandingResources(BrandingProperties properties) {
        this.properties = properties;
    }

    public record BrandingDto(String appTitle, String faviconUrl) {}

    @Operation(summary = "Configuration de branding ( titre + favicon ) pour les SPAs")
    @GetMapping
    public ResponseEntity<BrandingDto> get() {
        return ResponseEntity.ok(new BrandingDto(
                properties.getAppTitle(),
                properties.getFaviconUrl()
        ));
    }
}
