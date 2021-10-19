package fr.inra.oresing.rest;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.persistence.AuthenticationService;
import org.apache.commons.io.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import javax.servlet.http.Cookie;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Component
public class Fixtures {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticationService authenticationService;

    private Cookie cookie;

    enum Application {
        MONSORE("monsore", ImmutableSet.of("pem")),
        ACBB("acbb", ImmutableSet.of("flux_tours", "biomasse_production_teneur", "SWC")),
        PRO("pros", ImmutableSet.of("donnees_prelevement_pro")),
        OLAC("olac", ImmutableSet.of("condition_prelevements")),
        FORET("foret", ImmutableSet.of("flux_meteo_dataResult")),
        FAKE_APP_FOR_MIGRATION("fakeapp", ImmutableSet.of());

        private final String name;

        private final ImmutableSet<String> dataTypes;

        Application(String name, ImmutableSet<String> dataTypes) {
            this.name = name;
            this.dataTypes = dataTypes;
        }

        public String getName() {
            return name;
        }

        public ImmutableSet<String> getDataTypes() {
            return dataTypes;
        }
    }

    public String getMonsoreApplicationName() {
        return Application.MONSORE.getName();
    }

    public String getMonsoreApplicationConfigurationWithRepositoryResourceName() {
        return "/data/monsore/monsore-with-repository.yaml";
    }

    public String getMonsoreApplicationConfigurationResourceName() {
        return "/data/monsore/monsore.yaml";
    }

    public Map<String, String> getMonsoreReferentielFiles() {
        Map<String, String> referentielFiles = new HashMap<>();
        referentielFiles.put("especes", "/data/monsore/refdatas/especes.csv");
        referentielFiles.put("projet", "/data/monsore/refdatas/projet.csv");
        referentielFiles.put("sites", "/data/monsore/refdatas/sites.csv");
        referentielFiles.put("themes", "/data/monsore/refdatas/themes.csv");
        referentielFiles.put("type de fichiers", "/data/monsore/refdatas/type_de_fichiers.csv");
        referentielFiles.put("type_de_sites", "/data/monsore/refdatas/type_de_sites.csv");
        referentielFiles.put("types_de_donnees_par_themes_de_sites_et_projet", "/data/monsore/refdatas/types_de_donnees_par_themes_de_sites_et_projet.csv");
        referentielFiles.put("unites", "/data/monsore/refdatas/unites.csv");
        referentielFiles.put("valeurs_qualitatives", "/data/monsore/refdatas/valeurs_qualitatives.csv");
        referentielFiles.put("variables", "/data/monsore/refdatas/variables.csv");
        referentielFiles.put("variables_et_unites_par_types_de_donnees", "/data/monsore/refdatas/variables_et_unites_par_types_de_donnees.csv");
        return referentielFiles;
    }

    public String getPemDataResourceName() {
        return "/data/monsore/data-pem.csv";
    }

    public String getPemRepositoryDataResourceName(String projet, String site) {
        return String.format("/data/monsore/%s-%s-p1-pem.csv", projet, site);
    }

    public String getPemRepositoryParamsWithId(String projet, String site, String fileId, boolean toPublish) {
        return String.format("{\n" +
                "   \"fileid\":\"%s\",\n" +
                "   \"binaryfiledataset\":{\n" +
                "      \"requiredauthorizations\":{\n" +
                "         \"projet\":\"projet_%s\",\n" +
                "         \"localization\":\"%s__p1\"\n" +
                "      },\n" +
                "      \"from\":\"1984-01-01 00:00:00\",\n" +
                "      \"to\":\"1984-01-05 00:00:00\"\n" +
                "   },\n" +
                "   \"topublish\":%s\n" +
                "}", fileId, projet, site, toPublish);
    }

    public String getPemRepositoryParams(String projet, String site, boolean toPublish) {
        return String.format("{\n" +
                "   \"fileid\":null,\n" +
                "   \"binaryfiledataset\":{\n" +
                "      \"requiredauthorizations\":{\n" +
                "         \"projet\":\"projet_%s\",\n" +
                "         \"localization\":\"%s__p1\"\n" +
                "      },\n" +
                "      \"from\":\"1984-01-01 00:00:00\",\n" +
                "      \"to\":\"1984-01-05 00:00:00\"\n" +
                "   },\n" +
                "   \"topublish\":%s\n" +
                "}", projet, site, toPublish);
    }

    public String getPemRepositoryId(String projet, String site) {
        return String.format("{\n" +
                "      \"requiredauthorizations\":{\n" +
                "         \"projet\":\"projet_%s\",\n" +
                "         \"localization\":\"%s__p1\"\n" +
                "      },\n" +
                "      \"from\":\"1984-01-01 00:00:00\",\n" +
                "      \"to\":\"1984-01-05 00:00:00\"\n" +
                "   }", projet, site);
    }

    public String getAcbbApplicationName() {
        return Application.ACBB.getName();
    }

    public String getAcbbApplicationConfigurationResourceName() {
        return "/data/acbb/acbb.yaml";
    }

    public Map<String, String> getAcbbReferentielFiles() {
        Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("agroecosystemes", "/data/acbb/agroecosysteme.csv");
        referentielFiles.put("sites", "/data/acbb/sites.csv");
        referentielFiles.put("parcelles", "/data/acbb/parcelle.csv");
        referentielFiles.put("unites", "/data/acbb/unites.csv");
        return referentielFiles;
    }

    public String getFluxToursDataResourceName() {
        return "/data/acbb/Flux_tours.csv";
    }

    public String getBiomasseProductionTeneurDataResourceName() {
        return "/data/acbb/biomasse_production_teneur.csv";
    }

    public InputStream openSwcDataResourceName(boolean truncated) {
        String resourceName = "/data/acbb/SWC.csv";
        if (truncated) {
            try {
                String collect = Resources.asCharSource(getClass().getResource(resourceName), Charsets.UTF_8).lines()
                        .limit(100)
                        .collect(Collectors.joining("\n"));
                return IOUtils.toInputStream(collect, Charsets.UTF_8);
            } catch (IOException e) {
                throw new OreSiTechnicalException("ne devrait pas arriver", e);
            }
        } else {
            return getClass().getResourceAsStream(resourceName);
        }
    }

    public String getMigrationApplicationConfigurationResourceName(int version) {
        return "/data/migration/fake-app_v" + version + ".yaml";
    }

    public String getMigrationApplicationDataResourceName() {
        return "/data/migration/fake-data.csv";
    }

    public String getMigrationApplicationReferenceResourceName() {
        return "/data/migration/couleurs.csv";
    }

    private Cookie addApplicationCreatorUser() throws Exception {
        if (cookie == null) {
            String aPassword = "xxxxxxxx";
            String aLogin = "poussin";
            CreateUserResult createUserResult = authenticationService.createUser(aLogin, aPassword);
            authenticationService.addUserRightCreateApplication(createUserResult.getUserId());
            cookie = mockMvc.perform(post("/api/v1/login")
                    .param("login", aLogin)
                    .param("password", aPassword))
                    .andReturn().getResponse().getCookie(AuthHelper.JWT_COOKIE_NAME);
        }
        return cookie;
    }

    public Cookie addMonsoreApplication() throws Exception {
        Cookie authCookie = addApplicationCreatorUser();
        try (InputStream configurationFile = getClass().getResourceAsStream(getMonsoreApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore")
                    .file(configuration)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().isCreated());
        }

        // Ajout de referentiel
        for (Map.Entry<String, String> e : getMonsoreReferentielFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/references/{refType}", e.getKey())
                        .file(refFile)
                        .cookie(authCookie))
                        .andExpect(MockMvcResultMatchers.status().isCreated());
            }
        }

        // ajout de data
        try (InputStream refStream = getClass().getResourceAsStream(getPemDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/monsore/data/pem")
                    .file(refFile)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }
        return authCookie;
    }

    public Cookie addMigrationApplication() throws Exception {
        Cookie authCookie = addApplicationCreatorUser();
        try (InputStream configurationFile = getClass().getResourceAsStream(getMigrationApplicationConfigurationResourceName(1))) {
            MockMultipartFile configuration = new MockMultipartFile("file", "fake-app.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/fakeapp")
                    .file(configuration)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().isCreated());
        }

        // Ajout de referentiel
        try (InputStream refStream = getClass().getResourceAsStream(getMigrationApplicationReferenceResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "reference.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/fakeapp/references/couleurs")
                    .file(refFile)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().isCreated());
        }

        // ajout de data
        try (InputStream refStream = getClass().getResourceAsStream(getMigrationApplicationDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "data.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/fakeapp/data/jeu1")
                    .file(refFile)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

        return authCookie;
    }

    public Cookie addApplicationAcbb() throws Exception {
        Cookie authCookie = addApplicationCreatorUser();
        try (InputStream configurationFile = getClass().getResourceAsStream(getAcbbApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "acbb.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb")
                    .file(configuration)
                    .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // Ajout de referentiel
        for (Map.Entry<String, String> e : getAcbbReferentielFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb/references/{refType}", e.getKey())
                        .file(refFile)
                        .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }

        // ajout de data
        try (InputStream in = getClass().getResourceAsStream(getFluxToursDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "Flux_tours.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb/data/flux_tours")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }

        try (InputStream in = getClass().getResourceAsStream(getBiomasseProductionTeneurDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "biomasse_production_teneur.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb/data/biomasse_production_teneur")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }

        try (InputStream in = openSwcDataResourceName(true)) {
            MockMultipartFile file = new MockMultipartFile("file", "SWC.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/acbb/data/SWC")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
        return authCookie;
    }

    public String getValidationApplicationConfigurationResourceName() {
        return "/data/validation/fake-app.yaml";
    }

    public String getHauteFrequenceApplicationConfigurationResourceName() {
        return "/data/hautefrequence/hautefrequence.yaml";
    }

    public Map<String, String> getHauteFrequenceReferentielFiles() {
        Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("a", "/data/hautefrequence/a.csv");
        referentielFiles.put("b", "/data/hautefrequence/b.csv");
        referentielFiles.put("outil", "/data/hautefrequence/outil.csv");
        referentielFiles.put("projet", "/data/hautefrequence/projet.csv");
        referentielFiles.put("site", "/data/hautefrequence/site.csv");
        referentielFiles.put("plateforme", "/data/hautefrequence/plateforme.csv");
        referentielFiles.put("variable", "/data/hautefrequence/variable.csv");
        return referentielFiles;
    }

    public String getHauteFrequenceDataResourceName() {
        return "/data/hautefrequence/rnt_bimont_haute_frequence_14-06-2016_14-03-2017.csv";
    }

    public Cookie addApplicationHauteFrequence() throws Exception {
        Cookie authCookie = addApplicationCreatorUser();
        try (InputStream configurationFile = getClass().getResourceAsStream(getHauteFrequenceApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "hautefrequence.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/hautefrequence")
                    .file(configuration)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }

        // Ajout de referentiel
        for (Map.Entry<String, String> e : getHauteFrequenceReferentielFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/hautefrequence/references/{refType}", e.getKey())
                        .file(refFile)
                        .cookie(authCookie))
                        .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
            }
        }

        // ajout de data
        try (InputStream refStream = getClass().getResourceAsStream(getHauteFrequenceDataResourceName())) {
            MockMultipartFile refFile = new MockMultipartFile("file", "hautefrequence.csv", "text/plain", refStream);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/hautefrequence/data/hautefrequence")
                    .file(refFile)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
        }
        return authCookie;
    }


    public Map<String, String> getProReferentielFiles() {
        Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("dispositifs", "/data/pros/dispositif_complet.csv");
        referentielFiles.put("blocs", "/data/pros/bloc_complet.csv");
        referentielFiles.put("parcelles_elementaires", "/data/pros/parcelle_complet.csv");
        referentielFiles.put("placettes", "/data/pros/placette_complet.csv");
        referentielFiles.put("traitements", "/data/pros/traitement_complet.csv");
        referentielFiles.put("type_lieu", "/data/pros/type_lieu.csv");
        referentielFiles.put("Pays", "/data/pros/pays.csv");
        referentielFiles.put("Region", "/data/pros/region.csv");
        referentielFiles.put("Commune", "/data/pros/commune.csv");
        referentielFiles.put("Departement", "/data/pros/departement.csv");
        referentielFiles.put("Echelle_prélèvement", "/data/pros/echelle_de_prelevement.csv");
        referentielFiles.put("Facteur", "/data/pros/facteur.csv");
        referentielFiles.put("Liste raisonnement", "/data/pros/liste_de_raisonnement.csv");
        referentielFiles.put("Réseau essai ou Observatoire", "/data/pros/reseau_d_essai_ou_observatoire.csv");
        return referentielFiles;
    }

    public String getProApplicationConfigurationResourceName() {
        return "/data/pros/pro.yaml";
    }

    public Cookie addApplicationPRO() throws Exception {
        Cookie authCookie = addApplicationCreatorUser();
        try (InputStream configurationFile = getClass().getResourceAsStream(getProApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "pro.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/pros")
                    .file(configuration)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().isCreated());
        }

        // Ajout de referentiel
        for (Map.Entry<String, String> e : getProReferentielFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/pros/references/{refType}", e.getKey())
                        .file(refFile)
                        .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }

        // ajout de data Prelevement
        try (InputStream in = getClass().getResourceAsStream(getPrelevementProDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "donnees_prelevement_pro.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/pros/data/donnees_prelevement_pro")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data PhysicoChimieSols
        try (InputStream in = getClass().getResourceAsStream(getPhysicoChimieSolsProDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "physico_chimie_sols.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/pros/data/physico_chimie_sols")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        return authCookie;
    }

    public String getPrelevementProDataResourceName() {
        return "/data/pros/donnees_prelevement_pro.csv";
    }
    public String getPhysicoChimieSolsProDataResourceName() {
        return "/data/pros/physico_chimie_sols.csv";
    }


    public Map<String, String> getOlaReferentielFiles() {
        Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("themes", "/data/olac/themes.csv");
        referentielFiles.put("projets", "/data/olac/projets.csv");
        referentielFiles.put("typeSites", "/data/olac/types_de_site.csv");
        referentielFiles.put("sites", "/data/olac/sites.csv");
        referentielFiles.put("typePlateformes", "/data/olac/types_de_plateforme.csv");
        referentielFiles.put("plateformes", "/data/olac/plateformes.csv");
        referentielFiles.put("valeurs_qualitatives", "/data/olac/valeurs_qualitatives.csv");
        return referentielFiles;
    }

    public String getOlaApplicationConfigurationResourceName() {
        return "/data/olac/olac.yaml";
    }


    public Cookie addApplicationOLAC() throws Exception {
        Cookie authCookie = addApplicationCreatorUser();
        try (InputStream configurationFile = getClass().getResourceAsStream(getOlaApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "olac.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac")
                    .file(configuration)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().isCreated());
        }

        // Ajout de referentiel
        for (Map.Entry<String, String> e : getOlaReferentielFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/references/{refType}", e.getKey())
                        .file(refFile)
                        .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }

        // ajout de data condition_prelevements
        try (InputStream in = getClass().getResourceAsStream(getConditionPrelevementDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "condition_prelevements.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/condition_prelevements")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data physico-chimie
        try (InputStream in = getClass().getResourceAsStream(getPhysicoChimieDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "physico-chimie.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/physico-chimie")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data sonde_truncated
        try (InputStream in = getClass().getResourceAsStream(getSondeDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "sonde_truncated.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/sonde_truncated")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data phytoplancton_aggregated
        try (InputStream in = getClass().getResourceAsStream(getPhytoAggregatedDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "phytoplancton_aggregated.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/phytoplancton_aggregated")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data phytoplancton_truncated
        try (InputStream in = getClass().getResourceAsStream(getPhytoplanctonDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "phytoplancton_truncated.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/phytoplancton__truncated")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data  zooplancton_truncated
        try (InputStream in = getClass().getResourceAsStream(getZooplanctonDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "zooplancton_truncated.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/zooplancton__truncated")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data zooplancton_biovolumes
        try (InputStream in = getClass().getResourceAsStream(getZooplactonBiovolumDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "zooplancton_biovolumes.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/olac/data/zooplancton_biovolumes")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        return authCookie;
    }

    public String getConditionPrelevementDataResourceName() {
        return "/data/olac/condition_prelevements.csv";
    }

    public String getPhysicoChimieDataResourceName() {
        return "/data/olac/physico-chimie.csv";
    }

    public String getSondeDataResourceName() {
        return "/data/olac/sonde_truncated.csv";
    }

    public String getPhytoAggregatedDataResourceName() {
        return "/data/olac/phytoplancton_aggregated.csv";
    }

    public String getPhytoplanctonDataResourceName() {
        return "/data/olac/phytoplancton__truncated.csv";
    }

    public String getZooplanctonDataResourceName() {
        return "/data/olac/zooplancton__truncated.csv";
    }

    public String getZooplactonBiovolumDataResourceName() {
        return "/data/olac/zooplancton_biovolumes.csv";
    }

    public Map<String, String> getForetReferentielFiles() {
        Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("types_de_zones_etudes", "/data/foret/contexte_dispositif_types_de_zones_etudes.csv");
        referentielFiles.put("zones_etudes", "/data/foret/contexte_dispositif_zones_etudes.csv");
        referentielFiles.put("traitements", "/data/foret/contexte_dispositif_traitements.csv");
        referentielFiles.put("themes", "/data/foret/contexte_dispositif_themes.csv");
        referentielFiles.put("data_types", "/data/foret/contexte_dispositif_data_types.csv");
        referentielFiles.put("theme_types_de_donnees_par_zone_etudes", "/data/foret/contexte_dispositif_theme_types_de_donnees_par_zone_etudes.csv");
        referentielFiles.put("variables_par_types_de_donnees", "/data/foret/contexte_mesure_variables_par_types_de_donnees.csv");
        return referentielFiles;
    }

    public String getForetApplicationConfigurationResourceName() {
        return "/data/foret/foret.yaml";
    }

    public Cookie addApplicationFORET() throws Exception {
        Cookie authCookie = addApplicationCreatorUser();
        try (InputStream configurationFile = getClass().getResourceAsStream(getForetApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "foret.yaml", "text/plain", configurationFile);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/foret")
                    .file(configuration)
                    .cookie(authCookie))
                    .andExpect(MockMvcResultMatchers.status().isCreated());
        }

        // Ajout de referentiel
        for (Map.Entry<String, String> e : getForetReferentielFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/foret/references/{refType}", e.getKey())
                        .file(refFile)
                        .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }

        // ajout de data
        try (InputStream in = getClass().getResourceAsStream(getdFluxMeteoForetDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "flux_meteo_dataResult.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/foret/data/flux_meteo_dataResult")
                    .file(file)
                    .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        return authCookie;
    }

    public String getdFluxMeteoForetDataResourceName() {
        return "/data/foret/flux_meteo_dataResult.csv";
    }
}
