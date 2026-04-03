package fr.inra.oresing.rest.fixtures;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.Fixtures;
import org.junit.jupiter.api.DynamicTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.Objects;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ComputedWithNaturalKeyColumnsFixture {

    private final Fixtures fixtures;
    private final MockMvc mockMvc;
    private Fixtures.UserConnection computedWithNaturalKeyColumnsOwnerConnection;

    public ComputedWithNaturalKeyColumnsFixture(Fixtures fixtures, MockMvc mockMvc) {
        this.fixtures = fixtures;
        this.mockMvc = mockMvc;
    }

    public Stream<DynamicTest> loadApplication() {
        return Stream.of(
                dynamicTest("Création du propriétaire et chargement de l'application computedWithNaturalKeyColumns", () -> {
                    this.computedWithNaturalKeyColumnsOwnerConnection = fixtures.addApplicationCreatorUser("computedwithnaturalkeycolumns");

                    try (final InputStream in = Objects.requireNonNull(getClass().getResourceAsStream(Fixtures.getComputedWithNaturalKeyColumns()))) {
                        final MockMultipartFile configuration = new MockMultipartFile("file", "computedWithNaturalKeyColumns.yaml", "text/plain", in);
                        fixtures.loadApplication(configuration, this.computedWithNaturalKeyColumnsOwnerConnection.jwt(), "computedwithnaturalkeycolumns", "");
                    } catch (final Throwable e) {
                        throw new OreSiTechnicalException("Erreur lors du chargement de l'application computedWithNaturalKeyColumns", e);
                    }
                })
        );
    }

    public Stream<DynamicTest> loadData() {
        return Fixtures.getDataComputedWithNaturalKeyColumns().entrySet().stream()
                .map(e -> dynamicTest("Chargement des données: " + e.getKey(), () -> {
                    try (final InputStream dataStream = getClass().getResourceAsStream(e.getValue())) {
                        final MockMultipartFile dataFile = new MockMultipartFile("file", e.getValue(), "text/plain", dataStream);
                        mockMvc.perform(multipart("/api/v1/applications/computedwithnaturalkeycolumns/data/{refType}", e.getKey()).file(dataFile)
                                        .header("Authorization", "Bearer " + this.computedWithNaturalKeyColumnsOwnerConnection.jwt()))
                                .andDo(print())
                                .andExpect(status().isCreated());
                    }
                }));
    }

    public Stream<DynamicTest> checkResults() {
        return Stream.of(
                dynamicTest("Vérification des résultats", () -> {
                    mockMvc.perform(get("/api/v1/applications/computedwithnaturalkeycolumns/data/{refType}/json", "site_sit")
                                    .header("Authorization", "Bearer " + this.computedWithNaturalKeyColumnsOwnerConnection.jwt()))
                            .andExpect(status().is2xxSuccessful())
                            .andExpect(jsonPath("$.rows[*].values.site_natural_key", contains("type_de_site1__site1", "type_de_site1__site2", "type_de_site2__site1", "type_de_site2__site2")))
                            .andExpect(jsonPath("$.rows[*].refsLinkedTo.site_sit.site_natural_key", hasSize(4)))
                            .andExpect(jsonPath("$.rows[0].values.site_natural_key_multi", containsInAnyOrder("type_de_site1__site1", "type_de_site2__site1")))
                            .andExpect(jsonPath("$.rows[1].values.site_natural_key_multi", containsInAnyOrder("type_de_site1__site2", "type_de_site2__site2")))
                            .andExpect(jsonPath("$.rows[2].values.site_natural_key_multi", contains("")))
                            .andExpect(jsonPath("$.rows[3].values.site_natural_key_multi", contains("")))
                            .andExpect(jsonPath("$.rows[0].refsLinkedTo.site_sit.site_natural_key_multi.uuids", hasSize(1)))
                            .andExpect(jsonPath("$.rows[0].refsLinkedTo.type_site_tsi.tsi_noms.uuids", hasSize(1)))
                            .andExpect(jsonPath("$.rows[1].refsLinkedTo.site_sit.site_natural_key_multi.uuids", hasSize(1)))
                            .andExpect(jsonPath("$.rows[1].refsLinkedTo.type_site_tsi.tsi_noms.uuids", hasSize(1)))
                            .andExpect(jsonPath("$.rows[2].refsLinkedTo.site_sit.keys()", hasSize(1)))
                            .andExpect(jsonPath("$.rows[2].refsLinkedTo.type_site_tsi.keys()", hasSize(1)))
                            .andExpect(jsonPath("$.rows[*].refsLinkedTo.site_sit.site_natural_key", hasSize(4)))
                            .andExpect(jsonPath("$.rows[0].values.site_natural_key_multi", containsInAnyOrder("type_de_site1__site1", "type_de_site2__site1")))
                            .andExpect(jsonPath("$.rows[1].values.site_natural_key_multi", containsInAnyOrder("type_de_site1__site2", "type_de_site2__site2")))
                            .andExpect(jsonPath("$.rows[2].values.site_natural_key_multi", contains("")))
                            .andExpect(jsonPath("$.rows[3].values.site_natural_key_multi", contains("")));
                })
        );
    }
}