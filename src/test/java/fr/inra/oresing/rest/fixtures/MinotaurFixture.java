package fr.inra.oresing.rest.fixtures;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.Fixtures;
import org.junit.jupiter.api.DynamicTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.Objects;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class MinotaurFixture {

    private final Fixtures fixtures;
    private final MockMvc mockMvc;
    private Fixtures.UserConnection minotaurOwnerConnection;

    public MinotaurFixture(Fixtures fixtures, MockMvc mockMvc) {
        this.fixtures = fixtures;
        this.mockMvc = mockMvc;
    }

    public Stream<DynamicTest> loadApplication() {
        return Stream.of(
                dynamicTest("Création du propriétaire et chargement de l'application Minotaur", () -> {
                    this.minotaurOwnerConnection = fixtures.addApplicationCreatorUser("minotaur");

                    try (final InputStream in = Objects.requireNonNull(getClass().getResourceAsStream(Fixtures.getApplicationWithComputedComponentsWithReferences()))) {
                        final MockMultipartFile configuration = new MockMultipartFile("file", "minotaur.yaml", "text/plain", in);
                        fixtures.loadApplication(configuration, this.minotaurOwnerConnection.jwt(), "minotaur", "");
                    } catch (final Throwable e) {
                        throw new OreSiTechnicalException("Erreur lors du chargement de l'application Minotaur", e);
                    }
                })
        );
    }

    public Stream<DynamicTest> loadReferences() {
        return Fixtures.getApplicationWithComputedComponentsWithReferencesReferences().entrySet().stream()
                .map(e -> dynamicTest("Chargement du référentiel: " + e.getKey(), () -> {
                    try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                        final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                        mockMvc.perform(multipart("/api/v1/applications/minotaur/data/{refType}", e.getKey()).file(refFile)
                                        .header("Authorization", "Bearer " + this.minotaurOwnerConnection.jwt()))
                                .andDo(print())
                                .andExpect(status().isCreated());
                    }
                }));
    }

    public Stream<DynamicTest> loadData() {
        return Fixtures.getApplicationWithComputedComponentsWithReferencesData().entrySet().stream()
                .map(e -> dynamicTest("Chargement des données: " + e.getKey(), () -> {
                    try (final InputStream dataStream = getClass().getResourceAsStream(e.getValue())) {
                        final MockMultipartFile dataFile = new MockMultipartFile("file", e.getValue(), "text/plain", dataStream);
                        mockMvc.perform(multipart("/api/v1/applications/minotaur/data/{refType}", e.getKey()).file(dataFile)
                                        .header("Authorization", "Bearer " + this.minotaurOwnerConnection.jwt()))
                                .andDo(print())
                                .andExpect(status().isCreated());
                    }
                }));
    }

    public Stream<DynamicTest> checkResults() {
        return Stream.of(
                dynamicTest("Vérification des résultats", () -> {
                    mockMvc.perform(get("/api/v1/applications/minotaur/data/dataset/json")
                                    .header("Authorization", "Bearer " + this.minotaurOwnerConnection.jwt()))
                            .andDo(print())
                            .andExpect(status().is2xxSuccessful())
                            .andExpect(jsonPath("$.rows[*].values.site", containsInAnyOrder("id0227", "", "", "", "", "", "")))
                            .andExpect(jsonPath("$.rows[*].values.parcelle", containsInAnyOrder("", "p1", "p2", "", "", "", "")))
                            .andExpect(jsonPath("$.rows[*].values.bloc", containsInAnyOrder("", "", "", "b1_1", "b1_2", "b2_1", "b2_2")));
                })
        );
    }
}