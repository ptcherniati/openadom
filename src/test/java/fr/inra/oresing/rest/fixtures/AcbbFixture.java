package fr.inra.oresing.rest.fixtures;

import com.google.common.io.Resources;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.rest.Fixtures;
import jakarta.servlet.http.Cookie;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.core.IsNull;
import org.junit.jupiter.api.*;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fr.inra.oresing.rest.Fixtures.testZip;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

public record AcbbFixture(Fixtures fixtures, MockMvc mockMvc) {
    public static String getAcbbApplicationConfigurationResourceName() {
        return "/data/acbb/acbb_openAdom_V2.yaml";
    }

    public static Map<String, String> getAcbbReferentielFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("tr_agroecosystemes_agr", "/data/acbb/agroecosysteme.csv");
        referentielFiles.put("tr_sites_sit", "/data/acbb/sites.csv");
        referentielFiles.put("tr_parcelles_par", "/data/acbb/parcelle.csv");
        referentielFiles.put("tr_unites_unit", "/data/acbb/unites.csv");
        referentielFiles.put("tr_modalites_mod", "/data/acbb/modalites.csv");
        referentielFiles.put("tr_version_de_traitement_vdt", "/data/acbb/version_de_traitement.csv");
        return referentielFiles;
    }

    public static String getAcbbApplicationName() {
        return Fixtures.Application.ACBB.getName();
    }

    public static String getFluxToursDataResourceName() {
        return "/data/acbb/Flux_tours.csv";
    }

    public static String getBiomasseProductionTeneurDataResourceName() {
        return "/data/acbb/biomasse_production_teneur.csv";
    }

    public InputStream openSwcDataResourceName(final boolean truncated) {
        final String resourceName = "/data/acbb/SWC_truncated.csv";
        if (truncated) {
            try {
                final String collect = Resources.asCharSource(Objects.requireNonNull(getClass().getResource(resourceName)), StandardCharsets.UTF_8).lines()
                        .limit(100)
                        .collect(Collectors.joining("\n"));
                return IOUtils.toInputStream(collect, StandardCharsets.UTF_8);
            } catch (final IOException e) {
                throw new OreSiTechnicalException("ne devrait pas arriver", e);
            }
        } else {
            return getClass().getResourceAsStream(resourceName);
        }
    }

    public Fixtures.UserConnection addApplicationAcbb() throws Exception {
        Fixtures.UserConnection authConnection = fixtures().addApplicationCreatorUser("acbb");
        final Cookie authCookie = authConnection.cookie();
        try (final InputStream configurationFile = getClass().getResourceAsStream(AcbbFixture.getAcbbApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "acbb.yaml", "text/plain", configurationFile);
            fixtures().getIdFromApplicationResult(fixtures().loadApplication(configuration, authCookie, "acbb", "acbb"));
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : AcbbFixture.getAcbbReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/acbb/data/{refType}", e.getKey())
                                .file(refFile).with(csrf().asHeader())
                                .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }

        // ajout de data
       addFluxTours(authCookie);

       // addBiomasse(authCookie);

        //addSWC(authCookie);
        return authConnection;
    }

    private void addSWC(final Cookie authCookie) throws Exception {
        try (final InputStream in = openSwcDataResourceName(true)) {
            final MockMultipartFile file = new MockMultipartFile("file", "SWC.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/acbb/data/t_swc_swc")
                            .file(file).with(csrf().asHeader())
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    private void addBiomasse(final Cookie authCookie) throws Exception {
        try (final InputStream in = getClass().getResourceAsStream(getBiomasseProductionTeneurDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "biomasse_production_teneur.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/acbb/data/t_swc_swc")
                            .file(file).with(csrf().asHeader())
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    private void addFluxTours(final Cookie authCookie) throws Exception {
        try (final InputStream in = getClass().getResourceAsStream(AcbbFixture.getFluxToursDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "Flux_tours.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/acbb/data/t_flux_tours_flx")
                            .file(file).with(csrf().asHeader())
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    public Stream<DynamicTest> loadAcbbReferences() {
        return AcbbFixture.getAcbbReferentielFiles().entrySet()
                .stream().map(
                        e -> dynamicTest(
                                e.getKey(),
                                () -> {
                                    try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                                        final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);

                                        final String response = mockMvc.perform(multipart("/api/v1/applications/acbb_openadom_v2/data/{refType}", e.getKey())
                                                        .file(refFile).with(csrf().asHeader())
                                                        .cookie(fixtures().adminConnection.cookie()))
                                                .andDo(result -> {
                                                    final int status = result.getResponse().getStatus();
                                                    if (status > 300) {
                                                        System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                                                    }
                                                })
                                                .andDo(result -> {
                                                    final int status = result.getResponse().getStatus();
                                                    if (status > 300) {
                                                        System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                                                    }
                                                })
                                                .andExpect(status().isCreated())
                                                .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                                                .andReturn().getResponse().getContentAsString();

                                        JsonPath.parse(response).read("$.id");
                                    }
                                }
                        )
                );
    }

    private void addDataSWC() throws Exception {
        try (final InputStream in = openSwcDataResourceName(true)) {
            final MockMultipartFile file = new MockMultipartFile("file", "SWC.csv", "text/plain", in);

            mockMvc.perform(multipart("/api/v1/applications/acbb_openadom_v2/data/SWC")
                            .file(file).with(csrf().asHeader())
                            .cookie(fixtures().adminConnection.cookie()))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();


        }

        {
            final String actualJson = mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/SWC/json")
                            .cookie(fixtures().adminConnection.cookie())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            Assertions.assertEquals(2912, StringUtils.countMatches(actualJson, "\"SWC\":"));
        }

        {
            final MvcResult mvcResult = mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/SWC/zip")
                            .cookie(fixtures().adminConnection.cookie())
                            .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .andExpect(status().isOk())
                    .andExpect(request().asyncStarted())
                    .andReturn();

            Objects.requireNonNull(mvcResult.getRequest().getAsyncContext()).setTimeout(120000);
            mockMvc.perform(asyncDispatch(mvcResult))
                    .andExpect(testZip(List.of("SWC.csv")));
        }
    }

    private void addDataBiomassProduction() throws Exception {
        // ajout de data
        try (final InputStream in = getClass().getResourceAsStream(getBiomasseProductionTeneurDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "biomasse_production_teneur.csv", "text/plain", in);

            mockMvc.perform(multipart("/api/v1/applications/acbb_openadom_v2/data/biomasse_production_teneur")
                            .file(file).with(csrf().asHeader())
                            .cookie(fixtures().adminConnection.cookie()))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();


        }

        {
            final String actualJson = mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/biomasse_production_teneur/json")
                            .cookie(fixtures().adminConnection.cookie())
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();
            Assertions.assertEquals(252, StringUtils.countMatches(actualJson, "prairie permanente"));
        }

        {
            mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/biomasse_production_teneur/zip")
                                    .cookie(fixtures().adminConnection.cookie())
                                    .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                            .andExpect(request().asyncStarted())
                            .andExpect(status().is2xxSuccessful())
                            .andReturn()))
                    .andExpect(testZip(List.of("biomasse_production_teneur.csv")));
        }
    }

    private void addDataFluxTours() throws Exception {
        // ajout de data
        try (final InputStream in = getClass().getResourceAsStream(AcbbFixture.getFluxToursDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "Flux_tours.csv", "text/plain", in);

            mockMvc.perform(multipart("/api/v1/applications/acbb_openadom_v2/data/t_flux_tours_flx")
                            .file(file).with(csrf().asHeader())
                            .param("params", """
                                    {
                                        "fileid":null,
                                        "binaryfiledataset":{
                                            "datatype":"t_flux_tours_flx",
                                            "requiredAuthorizations":{
                                               "tr_sites_sit":["laqueuille"]
                                            },
                                            "from":"2003-12-31 23:00:00",
                                            "to":"2004-12-31 23:00:00",
                                            "comment":null
                                        },
                                        "topublish":true}"""

                            )
                            .cookie(fixtures().adminConnection.cookie()))
                    .andDo(result -> {
                        final int status = result.getResponse().getStatus();
                        if (status > 300) {
                            System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                        }
                    })
                    .andExpect(status().is2xxSuccessful())
                    .andReturn().getResponse().getContentAsString();


        }

        // restitution de data json
        {
//            String expectedJson = Resources.toString(getClass().getResource("/data/acbb_openadom_v2/compare/export.json"), StandardCharsets.UTF_8);
            mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/t_flux_tours_flx/json")
                            .cookie(fixtures().adminConnection.cookie())
                            .accept(MediaType.APPLICATION_JSON))
                    .andDo(result -> {
                        final int status = result.getResponse().getStatus();
                        if (status > 300) {
                            System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                        }
                    })
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.rows[*].[? (@.values.flx_day =~ /^.*date:2004.*$/)]", hasSize(17568)))
                    .andExpect(jsonPath("$.rows[*]", hasSize(17568)))
//                    .andExpect(content().json(expectedJson))
                    .andReturn().getResponse().getContentAsString();
        }

        // restitution de data csv
        {
            final MvcResult mvcResult = mockMvc.perform(get("/api/v1/applications/acbb_openadom_v2/data/t_flux_tours_flx/zip")
                            .cookie(fixtures().adminConnection.cookie())
                            .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                    .andExpect(request().asyncStarted())
                    .andExpect(status().isOk())
                    .andReturn();
            Objects.requireNonNull(mvcResult.getRequest().getAsyncContext()).setTimeout(120000);

            mockMvc.perform(asyncDispatch(
                            mvcResult
                    ))
                    .andExpect(testZip(List.of("t_flux_tours_flx.csv")));
        }
    }
}