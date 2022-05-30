package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.google.common.collect.Iterables;
import com.google.common.io.Resources;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.ValidationLevel;
import fr.inra.oresing.checker.InvalidDatasetContentException;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.exceptions.configuration.BadApplicationConfigurationException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matchers;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsEqual;
import org.hamcrest.core.IsNull;
import org.json.JSONArray;
import org.junit.*;
import org.junit.experimental.categories.Category;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.SpringBootDependencyInjectionTestExecutionListener;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureWebMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.util.NestedServletException;

import javax.servlet.http.Cookie;
import java.io.*;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OreSiNg.class)
@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureWebMvc
@AutoConfigureMockMvc
@TestExecutionListeners({SpringBootDependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Slf4j
public class TestReferencesErrors {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JsonRowMapper jsonRowMapper;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private Fixtures fixtures;

    private Cookie authCookie;

    private UUID userId;
    private CreateUserResult lambdaUser;
    public static final Map<String, String> responses = new HashMap<>();

    @Before
    public void createUser() throws Exception {
        userId = authenticationService.createUser("poussin", "xxxxxxxx").getUserId();
        lambdaUser = authenticationService.createUser("lambda", "xxxxxxxx");
        authCookie = mockMvc.perform(post("/api/v1/login")
                        .param("login", "poussin")
                        .param("password", "xxxxxxxx"))
                .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
    }
    @AfterClass
    public static void registerErrors() throws IOException {
        final String errorsAsString = new ObjectMapper().writeValueAsString(responses);
        File errorsFile = new File("ui/cypress/fixtures/applications/errors/ref_ola_errors.json");
        log.debug(errorsFile.getAbsolutePath());
        BufferedWriter writer = new BufferedWriter(new FileWriter(errorsFile));
        writer.write(errorsAsString);
        writer.close();
    }

    @Test
    public void testRecursivity() throws Exception {

        URL resource = getClass().getResource(fixtures.getRecursivityApplicationConfigurationResourceName());
        try (InputStream in = Objects.requireNonNull(resource).openStream()) {
            MockMultipartFile configuration = new MockMultipartFile("file", "recursivity.yaml", "text/plain", in);
            //définition de l'application
            authenticationService.addUserRightCreateApplication(userId);

            String response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", IsNull.notNullValue()))
                    .andReturn().getResponse().getContentAsString();

            JsonPath.parse(response).read("$.id");


            response = mockMvc.perform(MockMvcRequestBuilders.get("/api/v1/applications/recursivite")
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful())
                    .andExpect(jsonPath("$.references.taxon.dynamicColumns['propriétés de taxons'].reference", IsEqual.equalTo("proprietes_taxon")))
                    .andExpect(jsonPath("$.references.taxon.dynamicColumns['propriétés de taxons'].headerPrefix", IsEqual.equalTo("pt_")))
                    .andExpect(jsonPath("$.internationalization.references.taxon.internationalizedDynamicColumns['propriétés de taxons'].en", IsEqual.equalTo("Properties of Taxa")))
                    .andReturn().getResponse().getContentAsString();

        }

        String response;
        // Ajout de referentiel
        String proprietes_taxon_path = fixtures.getRecursiviteReferentielOrderFiles().get("proprietes_taxon");
        StringBuilder textBuilder = new StringBuilder();
        try (InputStream refStream = getClass().getResourceAsStream(proprietes_taxon_path)) {
            try (Reader reader = new BufferedReader(new InputStreamReader
                    (refStream, Charset.forName(StandardCharsets.UTF_8.name())))) {
                int c = 0;
                while ((c = reader.read()) != -1) {
                    textBuilder.append((char) c);
                }
            }
        }
        String monCSV = textBuilder.toString();

        for (Map.Entry<String, List<String>> e : fixtures.getRecursiviteReferentielErrorsStringReplace().entrySet()) {
            String textCsvModify = monCSV.replace(e.getValue().get(0), e.getValue().get(1));
            try (InputStream refStream = new ByteArrayInputStream(textCsvModify.getBytes(StandardCharsets.UTF_8))) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getKey() + ".csv", "text/plain", refStream);
                log.info(e.getKey());
                response = mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/references/{refType}","proprietes_taxon")
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().is4xxClientError())
                        .andReturn().getResponse().getContentAsString();

                Assert.assertEquals(e.getValue().get(2), response);
                responses.put(e.getKey(), response);
            }
        }

        // ajout de data
        /*try (InputStream refStream = fixtures.getClass().getResourceAsStream(fixtures.getFluxMeteoForetDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "flux_meteo_dataResult.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/foret/data/flux_meteo_dataResult")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }*/
    }
}
