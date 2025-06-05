package fr.inra.oresing.rest.fixtures;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.Fixtures;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public record HauteFrequenceFixture(Fixtures fixtures, MockMvc mockMvc) {



    public Fixtures.UserConnection addApplicationHauteFrequence() throws Exception {

        Fixtures.UserConnection authConnection = fixtures().addApplicationCreatorUser("hautefrequence");
        final Cookie authCookie = authConnection.cookie();
        try (final InputStream configurationFile = getClass().getResourceAsStream(getHauteFrequenceApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "hautefrequence.yaml", "text/plain", configurationFile);
            fixtures().getIdFromApplicationResult(fixtures().loadApplication(configuration, authCookie, "hautefrequence", "hautefrequence"));
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : getHauteFrequenceReferentielFiles().entrySet()) {
            try (final InputStream in = getClass().getResourceAsStream(AcbbFixture.getFluxToursDataResourceName())) {
                final MockMultipartFile file = new MockMultipartFile("file", e.getValue(), "text/plain", in);
                mockMvc.perform(multipart("/api/v1/applications/hautefrequence/data/{refType}", e.getKey())
                                .file(file)
                                .with(csrf().asHeader())
                                .cookie(authCookie))
                        .andExpect(status().is2xxSuccessful());
            }
        }

        // ajout de data
        try (final InputStream refStream = getClass().getResourceAsStream(getHauteFrequenceDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "hautefrequence.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/hautefrequence/data/hautefrequence")
                            .file(refFile).with(csrf().asHeader())
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
        return authConnection;
    }


    public static String getHauteFrequenceApplicationConfigurationResourceName() {
        return "/data/hautefrequence/hautefrequence.yaml";
    }

    public static Map<String, String> getHauteFrequenceReferentielFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("a", "/data/hautefrequence/a.csv");
        referentielFiles.put("b", "/data/hautefrequence/b.csv");
        referentielFiles.put("outil", "/data/hautefrequence/outil.csv");
        referentielFiles.put("projet", "/data/hautefrequence/projet.csv");
        referentielFiles.put("site", "/data/hautefrequence/site.csv");
        referentielFiles.put("plateforme", "/data/hautefrequence/plateforme.csv");
        referentielFiles.put("variable", "/data/hautefrequence/variable.csv");
        return referentielFiles;
    }

    public static String getHauteFrequenceDataResourceName() {
        return "/data/hautefrequence/rnt_bimont_haute_frequence_14-06-2016_14-03-2017.csv";
    }
}