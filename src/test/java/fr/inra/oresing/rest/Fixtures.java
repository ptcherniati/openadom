package fr.inra.oresing.rest;

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
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
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    public String getForetRepositoryParams(String fileName, String datatype) {
        //fougeres-fou_4_swc_j_01-01-1999_31-01-1999.csv
        final Pattern pattern = Pattern.compile("(.*)_" + datatype + "_(.*)_(.*).csv");
        final Matcher matcher = pattern.matcher(fileName);
        if(!matcher.matches()){
            return null;
        }
        String zone_etude = matcher.group(1);
        final String[] parent_site = zone_etude.split("-");
        if(parent_site.length>1){
            zone_etude = String.format("%1$s.%1$s__%2$s", parent_site[0], parent_site[1]);
        }
        final DateTimeFormatter formaterIn = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        final DateTimeFormatter formaterOut = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        final boolean isMonthly = datatype.matches(".*_m");
        final String format = (isMonthly ? "01-" : "") + "%s";
        String dateDebut =formaterOut.format(LocalDate.parse(String.format(format, matcher.group(2)), formaterIn).atStartOfDay(ZoneOffset.UTC))+" 00:00:00";
        String dateFin =formaterOut.format(LocalDate.parse(String.format(format, matcher.group(3)), formaterIn).atTime(0,0).plus(1, isMonthly ? ChronoUnit.MONTHS:ChronoUnit.DAYS))+" 00:00:00";
        return String.format("{\n" +
                "   \"fileid\":null,\n" +
                "   \"binaryfiledataset\":{\n" +
                "      \"requiredauthorizations\":{\n" +
                "         \"localization\":\"%1$s\"\n" +
                "      },\n" +
                "      \"from\":\"%2$s\",\n" +
                "      \"to\":\"%3$s\"\n" +
                "   },\n" +
                "   \"topublish\":true\n" +
                "}", zone_etude, dateDebut, dateFin);
    }

    public String getPemRepositoryParamsWithId(String projet, String plateforme, String site, String fileId, boolean toPublish) {
        return String.format("{\n" +
                "   \"fileid\":\"%1$s\",\n" +
                "   \"binaryfiledataset\":{\n" +
                "      \"requiredauthorizations\":{\n" +
                "         \"projet\":\"projet_%2$s\",\n" +
                "         \"localization\":\"%3$s.%4$s.%4$s__p1\"\n" +
                "      },\n" +
                "      \"from\":\"1984-01-01 00:00:00\",\n" +
                "      \"to\":\"1984-01-05 00:00:00\"\n" +
                "   },\n" +
                "   \"topublish\":%5$s\n" +
                "}", fileId, projet, plateforme, site, toPublish);
    }

    public String getPemRepositoryParams(String projet, String plateforme, String site, boolean toPublish) {
        return String.format("{\n" +
                "   \"fileid\":null,\n" +
                "   \"binaryfiledataset\":{\n" +
                "      \"datatype\":\"monsore\",\n" +
                "      \"requiredauthorizations\":{\n" +
                "         \"projet\":\"projet_%1$s\",\n" +
                "         \"localization\":\"%2$s.%3$s.%3$s__p1\"\n" +
                "      },\n" +
                "      \"from\":\"1984-01-01 00:00:00\",\n" +
                "      \"to\":\"1984-01-05 00:00:00\"\n" +
                "   },\n" +
                "   \"topublish\":%4$s\n" +
                "}", projet, plateforme, site, toPublish);
    }

    public String getPemRepositoryId(String plateforme, String projet, String site) {
        return String.format("{\n" +
                "      \"requiredauthorizations\":{\n" +
                "         \"projet\":\"projet_%2$s\",\n" +
                "         \"localization\":\"%1$s.%3$s.%3$s__p1\"\n" +
                "      },\n" +
                "      \"from\":\"1984-01-01 00:00:00\",\n" +
                "      \"to\":\"1984-01-05 00:00:00\"\n" +
                "   }", plateforme, projet, site);
    }

    public String getRecursivityApplicationConfigurationResourceName() {
        return "/data/recursivite/recusivite.yaml";
    }

    public Map<String, String> getRecursiviteReferentielOrderFiles() {
        Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("proprietes_taxon", "/data/recursivite/proprietes_des_taxons.csv");
        referentielFiles.put("taxon", "/data/recursivite/taxons_du_phytoplancton_reduit-test.csv");
        return referentielFiles;
    }

    public Map<String, String> getRecursiviteReferentielFiles() {
        Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("taxon", "/data/recursivite/taxons_du_phytoplancton_test.csv");
        return referentielFiles;
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
        referentielFiles.put("modalites", "/data/acbb/modalites.csv");
        referentielFiles.put("version_de_traitement", "/data/acbb/version_de_traitement.csv");
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

    public String getDuplicatedApplicationConfigurationResourceName() {
        return "/data/duplication/duplication.yaml";
    }

    public Map<String, String> getDuplicatedReferentielFiles() {
        Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("typezonewithoutduplication", "/data/duplication/typezone.csv");
        referentielFiles.put("typezonewithduplication", "/data/duplication/typezoneduplique.csv");
        referentielFiles.put("zonewithoutduplication", "/data/duplication/zone_etude.csv");
        referentielFiles.put("zonewithduplication", "/data/duplication/zone_etude_dupliqué.csv");
        referentielFiles.put("zonewithmissingparent", "/data/duplication/zone_etude_missing_parent.csv");
        return referentielFiles;
    }

    public Map<String, String> getDuplicatedDataFiles() {
        Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("data_without_duplicateds", "/data/duplication/data.csv");
        referentielFiles.put("data_with_duplicateds", "/data/duplication/data_with_duplicateds.csv");
        return referentielFiles;
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

    public Map<String, String> getForetEssaiReferentielFiles() {
        Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("types_de_zones_etudes", "/data/foret/metadata/arborescence/type_de_zones_d_etudes.csv");
        referentielFiles.put("zones_etudes", "/data/foret/metadata/arborescence/sites.csv");
        referentielFiles.put("themes", "/data/foret/metadata/arborescence/theme.csv");
        referentielFiles.put("theme_types_de_donnees_par_zone_etudes", "/data/foret/metadata/arborescence/types_de_donnees_par_themes_de_sites.csv");
        referentielFiles.put("unites", "/data/foret/metadata/metrologie/unites.csv");
        referentielFiles.put("variables", "/data/foret/metadata/metrologie/variables.csv");
        referentielFiles.put("variables_par_types_de_donnees", "/data/foret/metadata/metrologie/variables_par_types_de_donnees.csv");
        referentielFiles.put("traitements", "/data/foret/metadata/measure/traitements.csv");
        referentielFiles.put("reference", "/data/foret/metadata/measure/references.csv");
        referentielFiles.put("instruments", "/data/foret/metadata/measure/instruments.csv");
        referentielFiles.put("instruments_references", "/data/foret/metadata/measure/references_des_instruments.csv");
        referentielFiles.put("instruments_periodes", "/data/foret/metadata/measure/periodes_d_utilisation_des_instruments.csv");
        referentielFiles.put("methodes", "/data/foret/metadata/measure/methods.csv");
        referentielFiles.put("methodes_references", "/data/foret/metadata/measure/references_des_methodes.csv");
        referentielFiles.put("methodes_periodes", "/data/foret/metadata/measure/periodes_d_application_des_methodes.csv");
        referentielFiles.put("listes_infos_complementaires", "/data/foret/metadata/measure/listes_infos_complementaires.csv");
        referentielFiles.put("liste_valeur_ic", "/data/foret/metadata/measure/liste_de_valeurs_d_informations_complementaires.csv");
        referentielFiles.put("informations_complementaires", "/data/foret/metadata/measure/informations_complementaires.csv");
        referentielFiles.put("ic_site_theme_dataype_variable", "/data/foret/metadata/measure/informations_complementaires_par_site_theme_type_de_donnees_et_variable.csv");
        referentielFiles.put("types_fichiers", "/data/foret/metadata/type_de_fichiers.csv");
        return referentielFiles;

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

    public String getForetEssaiApplicationConfigurationResourceName() {
        return "/data/foret/foret_essai.yaml";
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
        try (InputStream in = getClass().getResourceAsStream(getFluxMeteoForetDataResourceName())) {
            MockMultipartFile file = new MockMultipartFile("file", "flux_meteo_dataResult.csv", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/foret/data/flux_meteo_dataResult")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        return authCookie;
    }

    public String getFluxMeteoForetDataResourceName() {
        return "/data/foret/flux_meteo_dataResult.csv";
    }

    public Map<String, String> getForetEssaiDataResourceName() {
        ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
        return builder
                .put("swc_j", "/data/foret/data/climatDuSol/journalier/fougeres-fou_4_swc_j_01-01-1999_31-01-1999.csv")
                /*.put("swc_infraj", "/data/foret/data/climatDuSol/infraj/fougeres-fou_4_swc_infraj_01-01-2001_06-01-2001.csv")
                .put("chambrefluxsol_infraj", "/data/foret/data/chambresAFlux/infraj/azerailles_chambrefluxsol_infraj_03-10-2013_05-10-2013.csv")
                .put("chambrefluxsol_j", "/data/foret/data/chambresAFlux/journalier/azerailles_chambrefluxsol_j_01-05-2013_08-05-2013.csv")
                .put("chambrefluxsol_m", "/data/foret/data/chambresAFlux/mensuel/azerailles_chambrefluxsol_m_06-2013_10-2013.csv")
                .put("flux_sh", "/data/foret/data/flux/semi-horaire/hesse-hesse_1_flux_sh_01-01-2010_02-01-2010.csv")
                .put("flux_j", "/data/foret/data/flux/journalier/hesse-hesse_1_flux_j_01-01-2008_05-01-2008.csv")
                .put("flux_m", "/data/foret/data/flux/mensuel/hesse-hesse_1_flux_m_01-2008_03-2008.csv")
                .put("meteo_sh", "/data/foret/data/meteo/semi-horaire/hesse-hesse_1_meteo_sh_01-01-2008_02-01-2008.csv")
                .put("meteo_j", "/data/foret/data/meteo/journalier/hesse-hesse_1_meteo_j_01-01-2012_03-01-2012.csv")
                .put("meteo_m", "/data/foret/data/meteo/mensuel/hesse-hesse_1_meteo_m_01-2012_03-2012.csv")*/
                .build();
    }
    public Map<String, Integer> getForetEssaiSynthesisSize() {
        ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
        return builder
                .put("swc_j", 8)
                .put("swc_infraj", 0)
                .put("chambrefluxsol_infraj", 0)
                .put("chambrefluxsol_j", 0)
                .put("chambrefluxsol_m", 0)
                .put("flux_sh", 0)
                .put("flux_j", 0)
                .put("flux_m", 0)
                .put("meteo_sh", 0)
                .put("meteo_j", 0)
                .put("meteo_m", 0)
                .build();
    }

    public void addApplicationRecursivity() throws Exception {
        Cookie authCookie = addApplicationCreatorUser();
        try (InputStream in = getClass().getResourceAsStream(getRecursivityApplicationConfigurationResourceName())) {
            MockMultipartFile configuration = new MockMultipartFile("file", "recursivity.yaml", "text/plain", in);
            mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite")
                            .file(configuration)
                            .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        String response;
        // Ajout de referentiel
        for (Map.Entry<String, String> e : getRecursiviteReferentielOrderFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }
        for (Map.Entry<String, String> e : getRecursiviteReferentielFiles().entrySet()) {
            try (InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(MockMvcRequestBuilders.multipart("/api/v1/applications/recursivite/references/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }
    }

    enum Application {
        MONSORE("monsore", ImmutableSet.of("pem")),
        ACBB("acbb", ImmutableSet.of("flux_tours", "biomasse_production_teneur", "SWC")),
        //PRO("pros", ImmutableSet.of("donnees_prelevement_pro")),
        OLAC("olac", ImmutableSet.of("condition_prelevements")),
        FORET("foret", ImmutableSet.of("flux_meteo_dataResult")),
        FAKE_APP_FOR_MIGRATION("fakeapp", ImmutableSet.of()),
        RECURSIVITY("recursivite", ImmutableSet.of());

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
}