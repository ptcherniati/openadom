package fr.inra.oresing.rest.fixtures;

import com.google.common.io.Resources;
import com.jayway.jsonpath.JsonPath;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCreatorRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationDataWriterException;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.Fixtures;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import net.minidev.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.hamcrest.core.Is;
import org.hamcrest.core.IsNull;
import org.json.JSONArray;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DynamicNode;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static fr.inra.oresing.rest.Fixtures.testZip;
import static fr.inra.oresing.rest.OreSiResourcesTest.registerFile;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

public record MonSoereFixture(Fixtures fixtures, MockMvc mockMvc, UserRepository userRepository,
                              JsonRowMapper jsonRowMapper
) {

    public static String getMonsoreApplicationConfigurationWithRepositoryResourceName() {
        return "/data/monsore/monsore-with-repository.yaml";
    }

    public static String getMonsoreApplicationConfigurationResourceName() {
        return "/data/monsore/monsore.yaml";
    }

    public static Map<String, String> getMonsoreReferentielEspecestoTrimFiles() {
        final Map<String, String> referentielFiles = new HashMap<>();
        referentielFiles.put("especes", "/data/monsore/refdatas/especesToTrim.csv");
        return referentielFiles;
    }

    public static Map<String, String> getMonsoreReferentielFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("especes", "/data/monsore/refdatas/especes.csv");
        referentielFiles.put("projet", "/data/monsore/refdatas/projet.csv");
        referentielFiles.put("type_de_sites", "/data/monsore/refdatas/type_de_sites.csv");
        referentielFiles.put("sites", "/data/monsore/refdatas/sites.csv");
        referentielFiles.put("themes", "/data/monsore/refdatas/themes.csv");
        referentielFiles.put("type_de_fichiers", "/data/monsore/refdatas/type_de_fichiers.csv");
        referentielFiles.put("site_theme_datatype", "/data/monsore/refdatas/types_de_donnees_par_themes_de_sites_et_projet.csv");
        referentielFiles.put("unites", "/data/monsore/refdatas/unites.csv");
        referentielFiles.put("valeurs_qualitatives", "/data/monsore/refdatas/valeurs_qualitatives.csv");
        referentielFiles.put("variables", "/data/monsore/refdatas/variables.csv");
        referentielFiles.put("variables_et_unites_par_types_de_donnees", "/data/monsore/refdatas/variables_et_unites_par_types_de_donnees.csv");
        referentielFiles.put("pem", "/data/monsore/data-pem.csv");
        return referentielFiles;
    }

    public static String getPemDataResourceName() {
        return "/data/monsore/data-pem.csv";
    }

    public static String getPemDataToTrimResourceName() {
        return "/data/monsore/data-pem-to-trim.csv";
    }

    public static String getPemRepositoryDataResourceName(final String projet, final String site) {
        String localSite = site
                .replaceAll("^NULL_KEY__", "");
        return String.format("/data/monsore/%s-%s-p1-pem.csv", projet, localSite);
    }

    public static String getPemRepositoryParams(final String projet, final String plateforme, final String site, final boolean toPublish) {
        return String.format("""
                {
                   "fileid":null,
                   "binaryfiledataset":{
                      "datatype":"monsore",
                      "requiredAuthorizations":{
                         "projet":["projet_%1$s"],
                         "sites":["%3$s__p1"]
                      },
                      "from":"01/01/1984",
                      "to":"06/01/1984"
                   },
                   "topublish":%4$s
                }""", projet, plateforme, site, toPublish);
    }

    public static String getPemRepositoryId(final String plateforme, final String projet, final String site) {
        return String.format("""
                {
                      "requiredAuthorizations":{
                         "projet":["projet_%2$s"],
                         "sites":["%3$s__p1"]
                      },
                      "from":"01/01/1984",
                      "to":"06/01/1984"
                   }""", plateforme, projet, site);
    }

    public static String getMonsoreApplicationName() {
        return Fixtures.Application.MONSORE.getName();
    }

    public void testPublic() {
        OreSiUser publicUser = userRepository().findByLogin("_public_").orElse(null);
        assert publicUser != null;
        UUID publicUserId = publicUser.getId();
        Assertions.assertEquals(UUID.fromString("9032ffe5-bfc1-453d-814e-287cd678484a"), publicUserId);
    }

    private String getPemData(final String jwt) throws Exception {
        return mockMvc.perform(get("/api/v1/applications/%s/data/pem/json".formatted("monsoresimple"))
                        .header("Authorization", "Bearer " + jwt)
                        .accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk()).andExpect(jsonPath("$.variables").isArray()).andExpect(jsonPath("$.variables", hasSize(10))).andExpect(jsonPath("$.variables", containsInAnyOrder("date", "site", "individusNumber_unit", "projet", "espece", "chemin", "plateforme", "color_value", "color_unit", "individusNumbervalue"))).andExpect(jsonPath("$.checkedFormatComponents.DateType", IsNull.notNullValue())).andExpect(jsonPath("$.checkedFormatComponents.ReferenceType", IsNull.notNullValue())).andExpect(jsonPath("$.checkedFormatComponents.FloatType", IsNull.notNullValue())).andExpect(jsonPath("$.rows").isArray()).andExpect(jsonPath("$.rows", hasSize(272)))
                //.andExpect(jsonPath("$.rows.value").value(list))
                .andExpect(jsonPath("$.rows[*].values.date", hasSize(272))).andExpect(jsonPath("$.rows[*].values.individusNumbervalue", hasSize(272))).andExpect(jsonPath("$.rows[*].values.color_value", hasSize(272))).andReturn().getResponse().getContentAsString();

    }

    public Stream<? extends DynamicNode> loadMonsore(AtomicReference appId) {
        final URL monSoereConfiguration = getClass().getResource(getMonsoreApplicationConfigurationResourceName());
        final URL pemResource = getClass().getResource(getPemDataResourceName());
        return Stream.of(
                dynamicTest("ajout de l'application MONSOERE sans les droits doit lancer une exception", () -> {
                    try (final InputStream in = Objects.requireNonNull(monSoereConfiguration).openStream()) {
                        final MockMultipartFile configuration = new MockMultipartFile("file", "monsoresimple.yaml", "text/plain", in);

                        // on n'a pas le droit de creer de nouvelle application
                        NotApplicationCreatorRightsException resolvedException = (NotApplicationCreatorRightsException) fixtures.loadApplicationWithError(configuration,
                                fixtures.getMonsoresimpleConnection().jwt()
                                , "monsoresimple");
                        Assertions.assertNotNull(resolvedException);
                    } catch (final Throwable e) {
                        throw new OreSiTechnicalException(e.getMessage(), e);
                    }
                }),
                dynamicContainer("chargement de l'application", Stream.of(dynamicTest("ajout de l'application MONSOERE avec les droits", () -> {
                    try (final InputStream in = Objects.requireNonNull(monSoereConfiguration).openStream()) {
                        final MockMultipartFile configuration = new MockMultipartFile("file", "monsoresimple.yaml", "text/plain", in);
                        fixtures.addUserRightCreateApplication(fixtures.getMonsoresimpleConnection().userResult().userId(), "monsoresimple");
                        final MvcResult resultApplication = fixtures.loadApplication(configuration,
                                fixtures.getMonsoresimpleConnection().jwt()
                                , "monsoresimple", "");
                        appId.set(fixtures.getIdFromApplicationResult(resultApplication));
                    }
                }), dynamicTest("On test le chargement de l'application", () -> {

                    String response = mockMvc.perform(get("/api/v1/applications/{appId}", appId).contentType(MediaType.APPLICATION_JSON).param("filter", "ALL")
                                    .header("Authorization", "Bearer " +
                                                             fixtures.getMonsoresimpleConnection().jwt()
                                    ))
                            .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                            // id
                            .andExpect(jsonPath("$.id", Is.is(appId.get()))).andReturn().getResponse().getContentAsString();

                    final ApplicationResult applicationResult = (ApplicationResult) jsonRowMapper.readValue(response, ApplicationResult.class);

                    Assertions.assertEquals("Fichier de test de l'application brokenADOM version initiale", applicationResult.comment());
                    Assertions.assertEquals("monsoresimple", applicationResult.name());
                    Assertions.assertEquals(new TreeSet<>(Set.of("themes", "especes", "site_theme_datatype", "variables", "type_de_sites", "unites", "projet", "valeurs_qualitatives", "type_de_fichiers", "variables_et_unites_par_types_de_donnees")), new TreeSet<>(applicationResult.references().keySet()));
                    Assertions.assertEquals(Set.of("pem"), ((LinkedHashMap) applicationResult.dataTypes()).keySet());
                }), dynamicTest("récupération de l'application", () ->
                        mockMvc.perform(get("/api/v1/applications/{appId}", appId).contentType(MediaType.APPLICATION_JSON).param("filter", "ALL")
                                        .header("Authorization", "Bearer " +
                                                                 fixtures.getMonsoresimpleConnection().jwt()
                                        ))
                                .andExpect(status().isOk())
                ))), dynamicContainer("Chargement des référentiels", Stream.of(dynamicContainer("chargement des référentiels avec un fichier non nettoyé", getMonsoreReferentielEspecestoTrimFiles().entrySet().stream().map(e -> dynamicTest(e.getKey(), () -> {
                    try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                        final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                        mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/{refType}", e.getKey()).file(refFile)
                                        .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()))
                                .andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }))), dynamicTest("test de la reference especetoTrim", () ->
                        mockMvc.perform(get("/api/v1/applications/monsoresimple/data/especes/json").contentType(MediaType.APPLICATION_JSON).locale(Locale.FRENCH)
                                        .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()))
                                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON)).andExpect(jsonPath("$.rows[*][?(@.hierarchicalKey=='especesKlpf')].values.esp_definition_fr", IsNull.notNullValue())).andExpect(jsonPath("$.rows[*][?(@.hierarchicalKey=='especesKlpf')].values.esp_definition_fr", hasItem("LPF"))).andReturn().getResponse().getContentAsString()
                ), dynamicContainer("chargement de tous les référentiels", getMonsoreReferentielFiles().entrySet().stream().map(e -> dynamicTest(e.getKey(), () -> {
                    try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                        final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                        final String response = mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/{refType}", e.getKey()).file(refFile)
                                        .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()))
                                .andDo(result -> {
                                    final int status = result.getResponse().getStatus();
                                    if (status > 300) {
                                        System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                                    }
                                }).andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();

                        JsonPath.parse(response).read("$.id");
                    } catch (Exception ex) {
                        throw new RuntimeException(ex);
                    }
                }))), dynamicContainer("récupération et test de sites", Stream.of(dynamicTest("récupération et test de sites au format json", () -> mockMvc.perform(get("/api/v1/applications/monsoresimple/data/sites/json")
                                .contentType(MediaType.APPLICATION_JSON)
                                .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                                ))
                        .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                        //.andExpect(jsonPath("$.totalRows", is(9)))
                        .andExpect(jsonPath("$.rows", hasSize(9))).andReturn().getResponse().getContentAsString()), dynamicTest("récupération et test de sites au format csv", () -> mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/monsoresimple/data/sites/csv").contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                                ))
                        .andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn())).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM)).andExpect(result -> {
                    final List<String> expected = """
                            "tze_type_nom";"zet_chemin_parent";"definition";"Site name";"zet_nom_key"
                            "Watershed";"";"Oir catchment";"Oir";"oir"
                            "Watershed";"";"Watershed Nivelle";"Nivelle";"nivelle"
                            "Watershed";"";"Watershed Scarff";"Scarff";"scarff"
                            "Platform";"- Oir";"";"P2";"p2"
                            "Platform";"- Oir";"";"P1";"p1"
                            "Platform";"NULL_KEY__oir - P1";"";"A";"a"
                            "Platform";"NULL_KEY__oir - P1";"";"B";"b"
                            "Platform";"- Nivelle";"";"P1";"p1"
                            "Platform";"- Scarff";"";"P1";"p1\"""".lines().collect(Collectors.toCollection(LinkedList::new));
                    final List<String> actual = new String(result.getResponse().getContentAsByteArray()).lines().collect(Collectors.toCollection(LinkedList::new));
                    Assertions.assertEquals(expected, actual, "Bad site.csv ");
                })))))),
                dynamicContainer("test des données", Stream.of(dynamicTest("chargement de pem avec l'utilisateur monsoreSimple (créateur)", () -> {
                            try (final InputStream refStream = Objects.requireNonNull(pemResource).openStream()) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
                                mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/{refType}", "pem").file(refFile)
                                                .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                                                ))
                                                .
                                        andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();
                            }

                        }), dynamicContainer("chargement des données avec l'utilisateur withRights", Stream.of(dynamicTest("Le créateur de l'application peut charger le fichier de données", () -> {
                            try (final InputStream refStream = Objects.requireNonNull(pemResource).openStream()) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
                                mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/{refType}", "pem").file(refFile)
                                                .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                                                ))
                                                .
                                        andExpect(status().isCreated()).andExpect(jsonPath("$.id", IsNull.notNullValue())).andReturn().getResponse().getContentAsString();
                            } catch (final Throwable e) {
                                throw new OreSiTechnicalException(e.getMessage(), e);
                            }
                        }), dynamicTest("Sans les droits je dois avoir une exception", () -> {
                            try (final InputStream refStream = Objects.requireNonNull(pemResource).openStream()) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
                                // sans droit on ne peut pas
                                Assertions.assertInstanceOf(NotApplicationDataWriterException.class, mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem").file(refFile)
                                                .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                        .andDo(result -> {
                                            final int status = result.getResponse().getStatus();
                                            if (status > 300) {
                                                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
                                            }
                                        }).andExpect(status().is4xxClientError()).andReturn().getResolvedException());
                            } catch (final Throwable e) {
                                throw new OreSiTechnicalException(e.getMessage(), e);
                            }
                        }), dynamicContainer("chargement de droits personnalisés", Stream.of(dynamicTest("ajout de droits oir p1", () -> {
                            String jsonRightsForMonsoere = getJsonRightsForAll(fixtures().getWithRightsUserConnection().userResult().userId().toString(), List.of(OperationType.publication.name()), "monsoresimple", "pem",
                                    fixtures.getMonsoresimpleConnection().jwt()
                            );
                            JsonPath.parse(jsonRightsForMonsoere).read("$.authorizationId");
                            try (final InputStream refStream = Objects.requireNonNull(pemResource).openStream()) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
                                mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem").file(refFile)
                                                .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                                                ))
                                        .andDo(result -> {
                                            final int status = result.getResponse().getStatus();
                                            if (status > 300) {
                                                System.out.println(result.getResolvedException().getMessage());
                                            }
                                        }).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
                                Assertions.assertNotNull(JsonPath.parse(jsonRightsForMonsoere).read("$.authorizationId"));
                            }
                        }), dynamicTest("chargement des données avec les droits", () -> {
                            try (final InputStream refStream = Objects.requireNonNull(pemResource).openStream()) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
                                mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem").file(refFile)
                                        .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                                                /*withRightsUserConnection.jwt())*/)).andDo(result -> {
                                    final int status = result.getResponse().getStatus();
                                    if (status > 300) {
                                        System.out.println(result.getResolvedException().getMessage());
                                    }
                                }).andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
                            }
                        }), dynamicTest("chargement des données avec lambda", () -> {
                            try (final InputStream refStream = Objects.requireNonNull(pemResource).openStream()) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
                                mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem").file(refFile)
                                                .header("Authorization", "Bearer " + fixtures.lambdaConnection.jwt()))
                                        .andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();
                            }
                        }), dynamicTest("ajout de data avec trim à faire", () -> {
                            final URL pemWithTrimResource = getClass().getResource(getPemDataToTrimResourceName());
                            try (final InputStream refStream = Objects.requireNonNull(pemWithTrimResource).openStream()) {
                                final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
                                mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem").file(refFile)
                                                .header("Authorization", "Bearer " + fixtures.getWithRightsUserConnection().jwt()))
                                        .andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
                                getPemData(fixtures.getMonsoresimpleConnection().jwt()
                                );
                            }
                        }), dynamicTest("test du contenu d'une extraction json", () -> {
                            final String contentAsString = mockMvc.perform(get("/api/v1/applications/monsoresimple/data/pem/json")
                                            .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                                            ))
                                            .
                                    andExpect(jsonPath("$.rows[*]..values[?(@.projet=='projet_atlantique' )][?(@.date=='date:1984-01-01T00:00:00:dd/MM/yyyy' )][?(@.chemin=='NULL_KEY__nivelle__p1' )][?(@.espece=='lpf' )][?(@.color_value=='couleur_des_individus__bleu' )].individusNumbervalue", hasItem(54.0))).andReturn().getResponse().getContentAsString();

                            net.minidev.json.JSONArray rowIds = JsonPath.parse(contentAsString).read("$.rows[*].rowId");

                            JSONObject body = new JSONObject();
                            body.put("rowIds", rowIds);
                            String query = body.toString();
                            mockMvc.perform(get("/api/v1/applications/monsoresimple/data/pem/json").param("downloadDatasetQuery", query)
                                            .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                                            ))
                                    .andExpect(status().is2xxSuccessful()).andExpect(jsonPath("$.rows.length()", equalTo(1)))
                                    //.andExpect(jsonPath("$.rows[*].rowId", hasItems(filterByRowId.get(0), filterByRowId.get(1))))
                                    .andReturn().getResponse().getContentAsString();
                        }), dynamicTest("ajout d'un fichier invalide", () -> {
                            try (final InputStream pem = getClass().getResourceAsStream(getPemDataResourceName())) {
                                final String data = IOUtils.toString(Objects.requireNonNull(pem), StandardCharsets.UTF_8);
                                final String wrongData = data.replace("plateforme", "entete_inconnu");
                                final byte[] bytes = wrongData.getBytes(StandardCharsets.UTF_8);
                                final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", bytes);
                                mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem").file(refFile)
                                                .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                                                ))
                                        .andExpect(status().isBadRequest()).andReturn().getResponse().getContentAsString();

                            } catch (final IOException e) {
                                throw new OreSiTechnicalException("impossible de lire le fichier de test", e);
                            }
                        }), dynamicTest("liste des data", () -> Assertions.assertNotNull(mockMvc.perform(get("/api/v1/applications/monsoresimple/data")
                                        .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                                        ))
                                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())), dynamicTest("extraction avec filtre", () -> {
                            final String expectedJson = Resources.toString(Objects.requireNonNull(getClass().getResource("/data/monsore/compare/export.json")), StandardCharsets.UTF_8);
                            final JSONArray jsonArray = new JSONArray(expectedJson);

                            final String actualJson = getPemData(
                                    fixtures.getMonsoresimpleConnection().jwt()
                            );

                            String filter = """
                                    {
                                      "componentFilters": [
                                        {
                                          "componentKey": {
                                            "variable": "date",
                                            "component": "value"
                                          },
                                          "intervalValues": {
                                            "from": "01/01/1984",
                                            "to": "02/01/1984"
                                          }
                                        },
                                        {
                                          "componentKey": {
                                            "variable": "Nombre d'individus",
                                            "component": "value"
                                          },
                                          "intervalValues": {
                                            "from": 20,
                                            "to": 29
                                          }
                                        },
                                        {
                                          "componentKey": {
                                            "variable": "Couleur des individus",
                                            "component": "value"
                                          },
                                          "filter": "couleur_des_individus__vert"
                                        }
                                      ],
                                      "componentOrderBy": [
                                        {
                                          "componentKey": {
                                            "variable": "site",
                                            "component": "plateforme"
                                          },
                                          "order": "ASC",
                                          "type": null,
                                          "format": null
                                        }
                                      ]
                                    }""";

                            mockMvc.perform(get("/api/v1/applications/monsoresimple/data/pem/json")
                                            .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                                            ).accept(MediaType.APPLICATION_JSON)).
                                    andExpect(status().isOk()).andExpect(jsonPath("$.rows", hasSize(272))).andExpect(jsonPath("$.rows[*].values[?(@.date == 'date:1984-01-01T00:00:00:dd/MM/yyyy')]", hasSize(56))).andExpect(jsonPath("$.rows[*].values[?(@.individusNumbervalue ==25)]", hasSize(32))).andExpect(jsonPath("$.rows[*].values[?(@.color_value =='couleur_des_individus__vert')]", hasSize(96))).andReturn().getResponse().getContentAsString();
                        }))), dynamicTest("extraction d'un zip", () -> {
                            Resources.toString(Objects.requireNonNull(getClass().getResource("/data/monsore/compare/export.csv")), StandardCharsets.UTF_8);
                            mockMvc.perform(asyncDispatch(mockMvc.perform(get("/api/v1/applications/monsoresimple/data/pem/zip")
                                            .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt())
                                            .accept(MediaType.APPLICATION_OCTET_STREAM_VALUE)).andExpect(status().isOk()).andExpect(request().asyncStarted()).andReturn())).
                                    andExpect(testZip(List.of("pem.csv", "references/especes.csv", "references/type_de_sites.csv", "references/unites.csv", "references/projet.csv", "references/valeurs_qualitatives.csv", "references/sites.csv")));
                        }), dynamicTest("test d'un fichier en erreur", () -> {
                            try (final InputStream in = getClass().getResourceAsStream(getPemDataResourceName())) {
                                final String csv = IOUtils.toString(Objects.requireNonNull(in), StandardCharsets.UTF_8);
                                final String invalidCsv = csv.replace("projet_manche", "projet_manch").replace("projet_atlantique", "projet_atlantiqu");
                                final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", invalidCsv.getBytes(StandardCharsets.UTF_8));
                                final String responseInError = mockMvc.perform(multipart("/api/v1/applications/monsoresimple/data/pem").file(refFile)
                                                .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                                                ))
                                        .andExpect(status().is4xxClientError()).andReturn().getResponse().getContentAsString();

                                Assertions.assertTrue(responseInError.contains("projet_manch"));
                                Assertions.assertTrue(responseInError.contains("projet_atlantiqu"));
                                Assertions.assertTrue(() -> Pattern.compile("\"lineNumber\" *: *5").matcher(responseInError).find(), "Il faut mentionner les lignes en erreur");
                                Assertions.assertTrue(() -> Pattern.compile("\"lineNumber\" *: *7").matcher(responseInError).find(), "Il faut mentionner les lignes en erreur");
                                Assertions.assertTrue(() -> Pattern.compile("\"lineNumber\" *: *8").matcher(responseInError).find(), "Il faut mentionner les lignes en erreur");
                                Assertions.assertTrue(() -> Pattern.compile("\"lineNumber\" *: *12").matcher(responseInError).find(), "Il faut mentionner les lignes en erreur");
                                Assertions.assertFalse(() -> Pattern.compile("\"lineNumber\" *: *4").matcher(responseInError).find(), "ligne d'en tête");
                                Assertions.assertFalse(() -> Pattern.compile("\"lineNumber\" *: *20").matcher(responseInError).find(), "L'erreur doit être tronquée");
                                Assertions.assertFalse(() -> Pattern.compile("\"lineNumber\" *: *142").matcher(responseInError).find(), "L'erreur doit être tronquée");
                            }
                        })))

                ))

        );


    }

    private String getJsonRightsForAll(final String withRigthsUserId, final List<String> roles, final String applicationName, final String datatype, String jwt) throws Exception {
        jwt = jwt == null ? fixtures.adminConnection.jwt() : jwt;
        String json = String.format("""
                {
                   "usersId":["%3$s"],
                   "uuid": null,
                   "name": "une submissionScope sur monsore%4$s",
                   "description": "une description de submissionScope sur monsore",
                   "authorizationForAll":{
                         "%1$s": ["%2$s"]
                      }
                }""", datatype, String.join("\",\"", roles), withRigthsUserId, System.currentTimeMillis());
        MockHttpServletRequestBuilder createRight = post("/api/v1/applications/%s/authorization".formatted(applicationName)).contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + jwt)
                .content(json);
        return mockMvc.perform(createRight).andDo(result -> {
            final int status = result.getResponse().getStatus();
            if (status > 300) {
                System.out.println(Objects.requireNonNull(result.getResolvedException()).getMessage());
            }
        }).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
    }

    public String createApplicationMonSore(final String jwt, final String applicationName) {
        MvcResult result;
        try (final InputStream configurationFile = getClass().getResourceAsStream(getMonsoreApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", configurationFile);
            result = fixtures().loadApplication(configuration, jwt, (applicationName == null ? "monsore" : applicationName), (applicationName == null ? "monsore" : applicationName));

            return fixtures().getIdFromApplicationResult(result);
        } catch (final Throwable e) {
            throw new OreSiTechnicalException(e.getMessage(), e);
        }
    }

    public Exception createApplicationMonSoreWithError(final String jwt, final String applicationName) throws Exception {
        try (final InputStream configurationFile = getClass().getResourceAsStream(getMonsoreApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", configurationFile);
            return fixtures().loadApplicationWithError(configuration, jwt, (applicationName == null ? "monsore" : applicationName));

        }
    }

    public Fixtures.UserConnection addMonsoreApplication() throws Exception {
        final Fixtures.UserConnection authConnection = fixtures().addApplicationCreatorUser("monsore");
        createApplicationMonSore(authConnection.jwt(), "monsore");

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : getMonsoreReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/monsore/data/{refType}", e.getKey())
                                .file(refFile)
                                .header("Authorization", "Bearer " + authConnection.jwt()))

                        .andExpect(status().isCreated());
            }
        }

        // ajout de data
        try (final InputStream refStream = getClass().getResourceAsStream(getPemDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .header("Authorization", "Bearer " + authConnection.jwt()))

                    .andExpect(status().is2xxSuccessful());
        }
        return authConnection;
    }

    public Stream<? extends DynamicNode> checkAndRegisterResults() {
        return Stream.of(dynamicTest("check and load Configuration", () -> {

            String getMonsoere = mockMvc.perform(get("/api/v1/applications/monsoresimple")
                            .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt())
                            .accept(MediaType.APPLICATION_JSON)).
                    andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
            registerFile("ui/cypress/fixtures/applications/ore/monsore/monsoere.json", getMonsoere);
        }), dynamicContainer("check and load References", getMonsoreReferentielFiles().keySet().stream().map(s -> dynamicTest("check and load Reference %s".formatted(s), () -> {
            String getReference = mockMvc.perform(get("/api/v1/applications/monsoresimple/data/{reference}/json", s)
                            .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt()
                            ).accept(MediaType.APPLICATION_JSON)).
                    andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
            registerFile("ui/cypress/fixtures/applications/ore/monsore/references/%s.json".formatted(s), getReference);
        }))), dynamicTest("check and load pem", () -> {
            String getPem = mockMvc.perform(get("/api/v1/applications/monsoresimple/data/pem/json")
                            .header("Authorization", "Bearer " + fixtures.getMonsoresimpleConnection().jwt())
                            .accept(MediaType.APPLICATION_JSON)).
                    andExpect(status().is2xxSuccessful()).andReturn().getResponse().getContentAsString();
            registerFile("ui/cypress/fixtures/applications/ore/monsore/datatypes/pem.json", getPem);
        }));
    }
}