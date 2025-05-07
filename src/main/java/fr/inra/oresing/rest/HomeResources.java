package fr.inra.oresing.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

@RestController
@Tag(name = "Accueil", description = "Entrée principale de l'API")
public class HomeResources {

    /**
     * Point d'entrée principal de l'application.
     * Redirige vers la documentation Swagger UI.
     *
     * @return Une vue de redirection vers la documentation Swagger UI
     */
    @GetMapping("/")
    @Operation(
            summary = "Page d'accueil de l'API",
            description = "Redirige automatiquement vers la documentation Swagger UI"
    )
    @ApiResponses(value = {
            @ApiResponse(responseCode = "302", description = "Redirection vers Swagger UI")
    })
    public RedirectView home() {
        final RedirectView result = new RedirectView();
        result.setContextRelative(true);
        result.setUrl("/swagger-ui/index.html");
        return result;
    }
}