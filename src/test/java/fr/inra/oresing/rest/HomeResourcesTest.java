package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.rest.services.AbstractIntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Test simple pour vérifier que la racine (/) redirige vers Swagger UI
 */

@SpringBootTest(classes = {OreSiNg.class})
@Tag("core.basic")
public class HomeResourcesTest extends AbstractIntegrationTest {

    /**
     * Teste que la page d'accueil redirige correctement vers la documentation Swagger UI
     * sans authentification
     */
    @Test
    void testHomeRedirectsToSwaggerUI() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }

    /**
     * Teste qu'une requête OPTIONS à la racine est autorisée
     * (utile pour les vérifications CORS préliminaires)
     */
    @Test
    void testOptionRequestToHomeIsAllowed() throws Exception {
        mockMvc.perform(options("/"))
                .andExpect(status().isOk());
    }
}