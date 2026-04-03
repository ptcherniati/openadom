package fr.inra.oresing.rest.fixtures;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.Fixtures;
import org.junit.jupiter.api.DynamicTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.Objects;
import java.util.stream.Stream;

import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ProgressiveFixture {

    private final Fixtures fixtures;
    private final MockMvc mockMvc;
    private Fixtures.UserConnection progressiveOwnerConnection;

    public ProgressiveFixture(Fixtures fixtures, MockMvc mockMvc) {
        this.fixtures = fixtures;
        this.mockMvc = mockMvc;
    }

    public Stream<DynamicTest> loadApplication(String yamlName) {
        return Stream.of(
                dynamicTest("Création du propriétaire et chargement de l'application " + yamlName, () -> {
                    this.progressiveOwnerConnection = fixtures.addApplicationCreatorUser("progressive");

                    try (final InputStream in = Objects.requireNonNull(getClass().getResourceAsStream(Fixtures.getProgressiveYaml().get(yamlName)))) {
                        final MockMultipartFile configuration = new MockMultipartFile("file", "progressive.yaml", "text/plain", in);
                        fixtures.loadApplication(configuration, this.progressiveOwnerConnection.jwt(), "progressive", "");
                    } catch (final Throwable e) {
                        throw new OreSiTechnicalException("Erreur lors du chargement de l'application " + yamlName, e);
                    }
                })
        );
    }

    public Stream<DynamicTest> loadReferences() {
        return Fixtures.getProgressiveYamlReferentielFiles().entrySet().stream()
                .map(e -> dynamicTest("Chargement du référentiel: " + e.getKey(), () -> {
                    try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                        final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                        mockMvc.perform(multipart("/api/v1/applications/progressive/data/{refType}", e.getKey()).file(refFile)
                                        .header("Authorization", "Bearer " + this.progressiveOwnerConnection.jwt()))
                                .andDo(print())
                                .andExpect(status().isCreated());
                    }
                }));
    }



    public Stream<DynamicTest> loadData() {
        return Fixtures.getProgressiveYamlDataFiles().entrySet().stream()
                .map(e -> dynamicTest("Chargement des données: " + e.getKey(), () -> {
                    try (final InputStream dataStream = getClass().getResourceAsStream(e.getValue())) {
                        final MockMultipartFile dataFile = new MockMultipartFile("file", e.getValue(), "text/plain", dataStream);
                        mockMvc.perform(multipart("/api/v1/applications/progressive/data/{refType}", e.getKey()).file(dataFile)
                                        .header("Authorization", "Bearer " + this.progressiveOwnerConnection.jwt()))
                                .andDo(print())
                                .andExpect(status().isCreated());
                    }
                }));
    }
}