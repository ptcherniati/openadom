package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.reactive.*;
import fr.inra.oresing.rest.security.AuthorizationFilter;
import jakarta.servlet.http.Cookie;
import lombok.Getter;
import org.apache.commons.io.IOUtils;
import org.hamcrest.Matchers;
import org.hamcrest.core.IsEqual;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@Component
public class Fixtures {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private UserRepository userRepository;
    @Getter
    private Cookie cookie;


    static List<ReactiveTypeInfo> getInfos(final MvcResult result) throws UnsupportedEncodingException {
        return Optional.ofNullable(getReactiveResultFromResult(result)
                        .get(ReactiveType.REACTIVE_INFO))
                .orElseGet(List::of)
                .stream()
                .map(ReactiveTypeInfo.class::cast)
                .collect(Collectors.toList());
    }

    static List<ReactiveTypeError> getErrors(final MvcResult result) throws UnsupportedEncodingException {
        return Optional.ofNullable(getReactiveResultFromResult(result)
                        .get(ReactiveType.REACTIVE_ERROR))
                .orElseGet(List::of)
                .stream()
                .map(ReactiveTypeError.class::cast)
                .collect(Collectors.toList());
    }

    public String getIdFromApplicationResult(final MvcResult result) throws UnsupportedEncodingException {
        return (String) getResults(result).getFirst().result();

    }

    static List<ReactiveTypeResult> getResults(final MvcResult result) throws UnsupportedEncodingException {
        return Optional.ofNullable(getReactiveResultFromResult(result)
                        .get(ReactiveType.REACTIVE_RESULT))
                .orElseGet(List::of)
                .stream()
                .map(ReactiveTypeResult.class::cast)
                .collect(Collectors.toList());
    }

    static List<ReactiveTypeProgress> getProgress(final MvcResult result) throws UnsupportedEncodingException {
        return Optional.ofNullable(getReactiveResultFromResult(result)
                        .get(ReactiveType.REACTIVE_PROGRESS))
                .orElseGet(List::of)
                .stream()
                .map(ReactiveTypeProgress.class::cast)
                .collect(Collectors.toList());
    }

    static Map<ReactiveType, List<ReactiveResult>> getReactiveResultFromResult(final MvcResult result) throws UnsupportedEncodingException {
        return Arrays.stream(result.getResponse().getContentAsString().split("\n"))
                .map(o -> {
                    try {
                        return new ObjectMapper().readValue(o, Map.class);
                    } catch (final JsonProcessingException e) {
                        throw new RuntimeException(e);
                    }
                })
                .collect(
                        Collectors.groupingBy(
                                m -> ReactiveType.valueOf((String) m.get("type")),
                                Collectors.collectingAndThen(Collectors.toList(),
                                        list -> list.stream().map(el -> switch (ReactiveType.valueOf((String) el.get("type"))) {
                                            case REACTIVE_RESULT -> new ReactiveTypeResult(el.get("result"));
                                            case REACTIVE_INFO -> new ReactiveTypeInfo(el.get("result"));
                                            case REACTIVE_ERROR -> new ReactiveTypeError(el.get("result"));
                                            case REACTIVE_PROGRESS -> new ReactiveTypeProgress(el.get("result"));
                                        }).collect(Collectors.toList())))
                );
    }

    public Exception loadApplicationWithError(final MockMultipartFile file,
                                              final Cookie cookie,
                                              final String applicationName) throws Exception {
        final MvcResult result = mockMvc
                .perform(multipart("/api/v1/applications/{applicationName}", applicationName)
                        .file(file)
                        .cookie(cookie)
                )
                .andExpect(request().asyncStarted())
                .andReturn();
        if (result.getAsyncResult() instanceof Exception) {
            return (Exception) result.getAsyncResult();
        }
        return mockMvc.perform(asyncDispatch(result))
                .andReturn()
                .getResolvedException();
    }

    MvcResult validateApplication(final MockMultipartFile file,
                                  final Cookie cookie) throws Exception {
        final ResultActions result = mockMvc.perform(
                multipart("/api/v1/validate-configuration")
                        .file(file)
                        .accept(MediaType.APPLICATION_NDJSON)
                        .cookie(cookie)
        );
        return mockMvc.perform(asyncDispatch(
                        result
                                .andDo(result1 -> {
                                    if(result1.getResponse().getStatus() > 250){
                                        System.out.println(result1);
                                    }
                                })
                                .andExpect(request().asyncStarted())
                                .andReturn())
                )
                .andReturn();
    }

    public MvcResult loadApplication(final MockMultipartFile file,
                                     final Cookie cookie,
                                     final String applicationName,
                                     final String comment) throws Throwable {
        try {
            final ResultActions result = mockMvc.perform(
                    multipart("/api/v1/applications/{applicationName}", applicationName)
                            .file(file)
                            .param("comment", comment != null ? comment : "")
                            .accept(MediaType.APPLICATION_NDJSON)
                            .cookie(cookie)
            );
            return mockMvc.perform(asyncDispatch(
                            result
                                    //.andExpect(request().asyncStarted())
                                    .andReturn())
                    )
                    .andReturn();
        } catch (final Exception e) {
            throw e.getCause()==null?e:e.getCause();
        }
    }


    MvcResult changeConfiguration(final MockMultipartFile file,
                                  final Cookie cookie,
                                  final String applicationName,
                                  final String comment) throws Exception {
        final ResultActions result = mockMvc.perform(
                multipart("/api/v1/applications/{applicationName}/configuration", applicationName)
                        .file(file)
                        .param("comment", comment != null ? comment : "")
                        .accept(MediaType.APPLICATION_NDJSON)
                        .cookie(cookie)
        );
        return mockMvc.perform(asyncDispatch(
                        result
                                .andExpect(request().asyncStarted())
                                .andReturn())
                )
                .andReturn();
    }

    public static String getApplicationWithComputedComponentsWithReferences() {
        return "/data/minotaur/minotaur.yaml";
    }

    public static Map<String, String> getApplicationWithComputedComponentsWithReferencesReferences() {
        final Map<String, String> referentielFiles = new HashMap<>();
        referentielFiles.put("site", "/data/minotaur/references/site.csv");
        referentielFiles.put("parcelle", "/data/minotaur/references/parcelle.csv");
        referentielFiles.put("bloc", "/data/minotaur/references/bloc.csv");
        return referentielFiles;
    }

    public static Map<String, String> getApplicationWithComputedComponentsWithReferencesData() {
        final Map<String, String> dataFiles = new HashMap<>();
        dataFiles.put("dataset", "/data/minotaur/data/datateSet.csv");
        return dataFiles;
    }

    public static String getMonsoreApplicationName() {
        return Application.MONSORE.getName();
    }

    public static String getTeledetectionConfigurationResourceName() {
        return "/data/teledetection/teledetection.yaml";
    }

    public static Map<String, String> getTeledetectionReferencesFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("tr_variable_standard_vstd", "/data/teledetection/ref/0.tr_variable_standard_vstd.csv");
        referentielFiles.put("tr_metadata_entity_ment", "/data/teledetection/ref/1t.r_metadata_entity_ment.csv");
        referentielFiles.put("tr_variable_local_metadata_vlm", "/data/teledetection/ref/2.tr_variable_local_metadata_vlm.csv");
        referentielFiles.put("tr_variable_local_vloc", "/data/teledetection/ref/3.tr_variable_local_vloc.csv");
        referentielFiles.put("tr_site_sit", "/data/teledetection/ref/4.tr_site_sit.csv");
        referentielFiles.put("tr_plot_type_pty", "/data/teledetection/ref/5.tr_plot_type_pty.csv");
        referentielFiles.put("tr_treatment_tre", "/data/teledetection/ref/6.tr_treatment_tre.csv");
        referentielFiles.put("tr_plot_plo", "/data/teledetection/ref/7.1.tr_plot_plo.csv");
        referentielFiles.put("tr_plot_plo2", "/data/teledetection/ref/7.2..tr_plot_plo.csv");
        return referentielFiles;
    }

    public static Map<String, String> getTeledetectionDataFiles() {
        final Map<String, String> data = new LinkedHashMap<>();
        data.put("t_teledetection_tel", "/data/teledetection/data/Statistiques_T30TYS_P1_DH_Dam_corrige.csv");
        return data;
    }

    public static String getComputedWithNaturalKeyColumns() {
        return "/data/computedwithnaturalkeycolumns/computedWithNaturalKeyColumns.yaml";
    }

    public static Map<String, String> getDataComputedWithNaturalKeyColumns() {
        final Map<String, String> data = new LinkedHashMap<>();
        data.put("type_site_tsi", "/data/computedwithnaturalkeycolumns/data/type_site_tsi.csv");
        data.put("site_sit", "/data/computedwithnaturalkeycolumns/data/site_sit.csv");
        return data;
    }


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

    public static String getForetRepositoryParams(final String fileName, final String datatype) {
        //fougeres-fou_4_swc_j_01-01-1999_31-01-1999.csv
        Pattern pattern = Pattern.compile("(.*)_" + datatype + "_(.*)_(.*).csv");
        Matcher matcher = pattern.matcher(fileName);
        if (!matcher.matches()) {
            return null;
        }
        String zone_etude = matcher.group(1);
        String[] parent_site = zone_etude.split("-");
        if (parent_site.length > 1) {
            zone_etude = String.format("%1$s.%1$s__%2$s", parent_site[0], parent_site[1]);
        }
        DateTimeFormatter formaterIn = DateTimeFormatter.ofPattern("dd-MM-yyyy");
        DateTimeFormatter formaterOut = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        boolean isMonthly = datatype.matches(".*_m");
        String format = (isMonthly ? "01-" : "") + "%s";
        final String dateDebut = formaterOut.format(LocalDate.parse(String.format(format, matcher.group(2)), formaterIn).atStartOfDay(ZoneOffset.UTC)) + " 00:00:00";
        final String dateFin = formaterOut.format(LocalDate.parse(String.format(format, matcher.group(3)), formaterIn).atTime(0, 0).plus(1, isMonthly ? ChronoUnit.MONTHS : ChronoUnit.DAYS)) + " 00:00:00";
        return String.format("""
                {
                   "fileid":null,
                   "binaryfiledataset":{
                      "requiredAuthorizations":{
                         "localization":"%1$s"
                      },
                      "from":"%2$s",
                      "to":"%3$s"
                   },
                   "topublish":true
                }""", zone_etude, dateDebut, dateFin);
    }

    public static String getPemRepositoryParamsWithId(final String projet, final String plateforme, final String site, final String fileId, final boolean toPublish) {
        return String.format("""
                {
                   "fileid":"%1$s",
                   "binaryfiledataset":{
                      "requiredAuthorizations":{
                         "projet":["projetKprojet_%2$s"],
                         "sites":["type_de_sitesK%3$s.sitesK%4$s.sitesK%4$s__p1"]
                      },
                      "from":"1984-01-01 00:00:00",
                      "to":"1984-01-05 00:00:00"
                   },
                   "topublish":%5$s
                }""", fileId, projet, plateforme, site, toPublish);
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
                      "from":"1984-01-01 00:00:00",
                      "to":"1984-01-05 00:00:00"
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
                      "from":"1984-01-01 00:00:00",
                      "to":"1984-01-05 00:00:00"
                   }""", plateforme, projet, site);
    }


    public static String getConditionsPrelevementRepositoryId(final String site) {
        return String.format("""
                {
                      "requiredAuthorizations":{
                         "localization_site":"%s"
                      },
                      "from":"2020-01-01 00:00:00",
                      "to":"2020-31-12 00:00:00"
                   }""", site);
    }

    public static String getConditionsPrelevementDataResourceName() {
        return "/data/recursivite/suivi_des_lacs_leman_conditions_prelevements_01-01-2020_31-12-2020.csv";
    }

    public static String getConditionsPrelevementRepositoryResourceName(final String site) {
        return String.format("/data/recursivite/suivi_des_lacs_%s_conditions_prelevements_01-01-2020_31-12-2020.csv", site);
    }

    public static Map<String, String> getProgressiveYaml() {
        final Map<String, String> yamls = new LinkedHashMap<>();
        yamls.put("testAuthorizationScopeWithoutReference", "/data/progressiveyaml/testAuthorizationScopeWithoutReference.yaml");
        yamls.put("testAuthorizationScopeWithReferenceAndNoHierarchicalReference", "/data/progressiveyaml/testAuthorizationScopeWithReferenceAndNoHierarchicalReference.yaml");
        yamls.put("yamlWithEmptyDatagroup", "/data/progressiveyaml/testEmptyDatagroup.yaml");
        yamls.put("yamlWithoutAuthorization", "/data/progressiveyaml/noAuthorization.yaml");
        yamls.put("testProgressiveYamlWithoutAuthorizationScope", "/data/progressiveyaml/noAuthorizationScope.yaml");
        yamls.put("testProgressiveYamlWithoutTimescopeScope", "/data/progressiveyaml/noTimeScope.yaml");
        return yamls;
    }

    public static Map<String, String> getProgressiveYamlReferentielFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("agroecosystem", "/data/progressiveyaml/references/agroecosystem.csv");
        referentielFiles.put("sites", "/data/progressiveyaml/references/sites.csv");
        referentielFiles.put("parcelles", "/data/progressiveyaml/references/parcelles.csv");
        return referentielFiles;
    }

    public static Map<String, String> getProgressiveYamlDataFiles() {
        final Map<String, String> dataFiles = new LinkedHashMap<>();
        dataFiles.put("date_de_visite", "/data/progressiveyaml/data/date_de_visite.csv");
        return dataFiles;
    }

    public static String getPatternApplicationConfigurationResourceName() {
        return "/data/pattern/pattern.yaml";
    }

    public static Map<String, String> getPatternReferentielOrderFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("site", "/data/pattern/sites.csv");
        referentielFiles.put("proprietes_taxon", "/data/pattern/proprietes_des_taxons.csv");
        referentielFiles.put("taxon", "/data/pattern/taxons_du_phytoplancton-reduit-pour-pattern.csv");
        return referentielFiles;
    }

    public static String getRecursivityApplicationConfigurationResourceName() {
        return "/data/recursivite/recusivite.yaml";
    }

    public static String getRepeatedColumnsWithAllowUnexpectedColumnsApplicationConfigurationResourceName() {
        return "/data/repeatedcolumns/repeatedcolumnswithallowunexpectedcolumns.yaml";
    }

    public static Map<String, List<String>> getRepeatedColumnsgWithAllowUnexpectedColumnsDataErrorsStringReplace() {
        final Map<String, List<String>> DataTypeErrors = new LinkedHashMap<>();
        DataTypeErrors.put("missingMandatoryColumns", List.of(
                "\"Nom parcelle\";\"Nom traitement\";\"Date\";\"Time\";\"SWC_1_15\";\"qc\";\"SWC_2_15\";\"qc\";\"SWC_1_45\";\"qc\";\"SWC_2_45\";\"qc\";\"SWC_1_75\";\"qc\";\"SWC_2_75\";\"qc\";\"SWC_1_105\";\"qc\";\"SWC_2_105\";\"qc\";\"SWC_1_135\";\"qc\";\"SWC_2_135\";\"qc\";\"SWC_1_165\";\"qc\";\"SWC_2_165\";\"qc\";\"SWC_1_195\";\"qc\";\"SWC_2_195\";\"qc\";\"SWC_1_235\";\"qc\";\"DateTime\";\"SWC_2_235\";\"qc\"",
                "\"Nom parcelle\";\"Nom traitement\";\"Dates\";\"Time\";\"SWC_1_15\";\"qc\";\"SWC_2_15\";\"qc\";\"SWC_1_45\";\"qc\";\"SWC_2_45\";\"qc\";\"SWC_1_75\";\"qc\";\"SWC_2_75\";\"qc\";\"SWC_1_105\";\"qc\";\"SWC_2_105\";\"qc\";\"SWC_1_135\";\"qc\";\"SWC_2_135\";\"qc\";\"SWC_1_165\";\"qc\";\"SWC_2_165\";\"qc\";\"SWC_1_195\";\"qc\";\"SWC_2_195\";\"qc\";\"SWC_1_235\";\"qc\";\"DateTime\";\"SWC_2_235\";\"qc\"",
                "[{\"type\":\"DefaultValidationCheckResult\",\"message\":\"missingMandatoryColumns\",\"params\":{\"missingMandatoryColumns\":[\"Date\"]},\"lineNumber\":7}]"
        ));
        return DataTypeErrors;
    }

    public static String getRepeatedColumnsApplicationConfigurationResourceName() {
        return "/data/repeatedcolumns/repeatedcolumns.yaml";
    }

    public static Map<String, String> getRepeatedColumnsReferentielOrderFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("agroecosystemes", "/data/repeatedcolumns/agroecosysteme.csv");
        referentielFiles.put("sites", "/data/repeatedcolumns/sites.csv");
        referentielFiles.put("parcelles", "/data/repeatedcolumns/parcelle.csv");
        referentielFiles.put("unites", "/data/repeatedcolumns/unites.csv");
        referentielFiles.put("modalites", "/data/repeatedcolumns/modalites.csv");
        referentielFiles.put("version_de_traitement", "/data/repeatedcolumns/version_de_traitement.csv");
        return referentielFiles;
    }

    public static String getSWCRepositoryResourceName() {
        return "/data/repeatedcolumns/SWC_truncated.csv";
    }

    public static Map<String, List<String>> getRepeatedColumnsDataErrorsStringReplace() {
        final Map<String, List<String>> DataTypeErrors = new LinkedHashMap<>();
        DataTypeErrors.put("unexpectedHeaderColumnsInList", List.of(
                "\"Nom parcelle\";\"Nom traitement\";\"Date\";\"Time\";\"SWC_1_15\";\"qc\";\"SWC_2_15\";\"qc\";\"SWC_1_45\";\"qc\";\"SWC_2_45\";\"qc\";\"SWC_1_75\";\"qc\";\"SWC_2_75\";\"qc\";\"SWC_1_105\";\"qc\";\"SWC_2_105\";\"qc\";\"SWC_1_135\";\"qc\";\"SWC_2_135\";\"qc\";\"SWC_1_165\";\"qc\";\"SWC_2_165\";\"qc\";\"SWC_1_195\";\"qc\";\"SWC_2_195\";\"qc\";\"SWC_1_235\";\"qc\";\"DateTime\";\"SWC_2_235\";\"qc\"",
                "\"Nom parcelle\";\"Nom traitement\";\"Dates\";\"Time\";\"SWC_1_15\";\"qc\";\"SWC_2_15\";\"qc\";\"SWC_1_45\";\"qc\";\"SWC_2_45\";\"qc\";\"SWC_1_75\";\"qc\";\"SWC_2_75\";\"qc\";\"SWC_1_105\";\"qc\";\"SWC_2_105\";\"qc\";\"SWC_1_135\";\"qc\";\"SWC_2_135\";\"qc\";\"SWC_1_165\";\"qc\";\"SWC_2_165\";\"qc\";\"SWC_1_195\";\"qc\";\"SWC_2_195\";\"qc\";\"SWC_1_235\";\"qc\";\"DateTime\";\"SWC_2_235\";\"qc\"",
                "[{\"type\":\"DefaultValidationCheckResult\",\"message\":\"missingMandatoryColumns\",\"params\":{\"missingMandatoryColumns\":[\"Date\"]},\"lineNumber\":7}]"
        ));
        return DataTypeErrors;
    }

    public static Map<String, String> getRecursiviteReferentielOrderFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("site", "/data/recursivite/sites.csv");
        referentielFiles.put("proprietes_taxon", "/data/recursivite/proprietes_des_taxons.csv");
        referentielFiles.put("taxon", "/data/recursivite/taxons_du_phytoplancton-reduit-pour-recursivite.csv");
        return referentielFiles;
    }

    public static Map<String, String> getRecursiviteReferentielTaxon() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("taxon", "/data/recursivite/taxons_du_phytoplancton.csv");
        return referentielFiles;
    }

    public static Map<String, List<String>> getRecursiviteReferentielErrorsStringReplace() {
        final Map<String, List<String>> referentielErrors = new LinkedHashMap<>();
        referentielErrors.put("invalidHeaders", List.of(
                "définition_en",
                "définition_en;définition_es",
                "[{\"type\":\"DefaultValidationCheckResult\",\"message\":\"invalidHeaders\",\"params\":{\"expectedColumns\":[\"Date\",\"site\",\"isFloatValue\",\"nom de la propriété_en\",\"nom de la propriété_fr\",\"type associé\",\"définition_en\",\"définition_fr\",\"isQualitative\",\"nom de la propriété_key\",\"ordre d'affichage\"],\"actualColumns\":[\"Date\",\"nom de la propriété_key\",\"nom de la propriété_fr\",\"nom de la propriété_en\",\"définition_fr\",\"définition_en\",\"définition_es\",\"isFloatValue\",\"isQualitative\",\"type associé\",\"ordre d'affichage\",\"site\"],\"missingComponents\":[],\"unknownComponents\":[\"définition_es\"]},\"lineNumber\":1}]"
        ));
        referentielErrors.put("emptyHeader", List.of(
                "définition_en",
                "",
                "[{\"type\":\"DefaultValidationCheckResult\",\"message\":\"emptyHeader\",\"params\":{\"headerLine\":1},\"lineNumber\":1}]"
        ));
        referentielErrors.put("duplicatedHeaders", List.of(
                "définition_en",
                "définition_en;définition_fr",
                "[{\"type\":\"DefaultValidationCheckResult\",\"message\":\"duplicatedHeaders\",\"params\":{\"duplicatedHeaders\":[\"définition_fr\"]},\"lineNumber\":1}]"
        ));
        referentielErrors.put("invalidDateWithComponent", List.of(
                "02/01/2016",
                "01/01/16",
                "[{\"type\":\"DateValidationCheckResult\",\"message\":\"invalidDateWithComponent\",\"params\":{\"pattern\":\"dd/MM/yyyy\",\"value\":\"01/01/16\",\"target\":{\"column\":\"date\"}},\"lineNumber\":2}]"
        ));
        referentielErrors.put("invalidFloatWithColumn", List.of(
                "55,22",
                "x",
                "[{\"type\":\"FloatValidationCheckResult\",\"message\":\"invalidFloatWithComponent\",\"params\":{\"target\":{\"column\":\"isFloatValue\"},\"value\":\"x\"},\"lineNumber\":5}]"
        ));
        referentielErrors.put("invalidIntegerWithComponent", List.of(
                "4",
                "x",
                "[{\"type\":\"IntegerValidationCheckResult\",\"message\":\"invalidIntegerWithComponent\",\"params\":{\"target\":{\"column\":\"ordre_affichage\"},\"value\":\"x\"},\"lineNumber\":5}]"
        ));
        referentielErrors.put("duplicatedLineInReference", List.of(
                "01/01/2016;Notes sur les biovolumes;Notes sur les biovolumes;Notes on biovolumes;;;39,22;false;Phytoplancton;38",
                "01/01/2016;Notes libres;Notes libres;Free notes;;;39,22;false;Phytoplancton;39",
                "[{\"type\":\"DefaultValidationCheckResult\",\"message\":\"duplicatedLineInReference\",\"params\":{\"file\":\"proprietes_taxon\",\"lineNumber\":40,\"otherLines\":[39,40],\"duplicateKey\":\"proprietes_taxonKnotes_libres\"},\"lineNumber\":40}]"
        ));
        // me renvois une erreur "invalidHeaders"
        referentielErrors.put("unexpectedHeaderColumn", List.of(
                "Date",
                "Date;martin",
                "[{\"type\":\"DefaultValidationCheckResult\",\"message\":\"invalidHeaders\",\"params\":{\"expectedColumns\":[\"Date\",\"site\",\"isFloatValue\",\"nom de la propriété_en\",\"nom de la propriété_fr\",\"type associé\",\"définition_en\",\"définition_fr\",\"isQualitative\",\"nom de la propriété_key\",\"ordre d'affichage\"],\"actualColumns\":[\"Date\",\"martin\",\"nom de la propriété_key\",\"nom de la propriété_fr\",\"nom de la propriété_en\",\"définition_fr\",\"définition_en\",\"isFloatValue\",\"isQualitative\",\"type associé\",\"ordre d'affichage\",\"site\"],\"missingComponents\":[],\"unknownComponents\":[\"martin\"]},\"lineNumber\":1}]"
        ));
        referentielErrors.put("invalidReferenceWithComponent", List.of(
                "38;",
                "38;martin",
                "[{\"type\":\"ReferenceValidationCheckResult\",\"message\":\"invalidReferenceWithComponent\",\"params\":{\"target\":\"site\",\"referenceValues\":[],\"refType\":\"site\",\"value\":\"martin\"},\"lineNumber\":39}]"
        ));
        referentielErrors.put("patternNotMatchedWithComponent", List.of(
                "02/01/2016",
                "12:00:00",
                "[{\"type\":\"DateValidationCheckResult\",\"message\":\"invalidDateWithComponent\",\"params\":{\"target\":{\"column\":\"date\"},\"pattern\":\"dd/MM/yyyy\",\"value\":\"12:00:00\"},\"lineNumber\":2}]"
        ));
        return referentielErrors;
    }

    public static Map<String, List<String>> getRecursiviteDataErrorsStringReplace() {
        final Map<String, List<String>> DataTypeErrors = new LinkedHashMap<>();
        // problème liste de site non fixe donc le test ne passe pas mais le message d'erreur est bon
        DataTypeErrors.put("invalidDate", List.of(
                "suivi des lacs;leman;SHL2;24/02/2020;00:00:00;Tracté par la Daphnie;8;1;ensoleille;clair;;1;979;plat;propre;;10;vert-vert",
                "suivi des lacs;leman;SHL2;x24/02/2020;00:00:00;Tracté par la Daphnie;8;1;ensoleille;clair;;1;979;plat;propre;;10;vert-vert",
                "[{\"type\":\"DateValidationCheckResult\",\"message\":\"invalidDateWithComponent\",\"params\":{\"target\":{\"column\":\"date_day\"},\"pattern\":\"dd/MM/yyyy\",\"value\":\"x24/02/2020\"},\"lineNumber\":3}]"
        ));
        DataTypeErrors.put("invalidInt", List.of(
                "suivi des lacs;leman;SHL2;24/02/2020;00:00:00;Tracté par la Daphnie;8;1;ensoleille;clair;;1;979;plat;propre;;10;vert-vert",
                "suivi des lacs;leman;SHL2;24/02/2020;00:00:00;Tracté par la Daphnie;x8;1;ensoleille;clair;;1;979;plat;propre;;10;vert-vert",
                "[{\"type\":\"IntegerValidationCheckResult\",\"message\":\"invalidIntegerWithComponent\",\"params\":{\"target\":{\"column\":\"temperatureDeLAir\"},\"value\":\"x8\"},\"lineNumber\":3}]"
        ));
        DataTypeErrors.put("invalidFloat", List.of(
                "suivi des lacs;leman;SHL2;24/02/2020;00:00:00;Tracté par la Daphnie;8;1;ensoleille;clair;;1;979;plat;propre;;10;vert-vert",
                "suivi des lacs;leman;SHL2;24/02/2020;00:00:00;Tracté par la Daphnie;8;1;ensoleille;clair;;1;979;plat;propre;;x10;vert-vert",
                "[{\"type\":\"FloatValidationCheckResult\",\"message\":\"invalidFloatWithComponent\",\"params\":{\"target\":{\"column\":\"transparenceParSecchi\"},\"value\":\"x10\"},\"lineNumber\":3}]"
        ));
        DataTypeErrors.put("requiredValue", List.of(
                "suivi des lacs;leman;SHL2;24/02/2020;00:00:00;Tracté par la Daphnie;8;1;ensoleille;clair;;1;979;plat;propre;;10;vert-vert",
                "suivi des lacs;leman;SHL2;;00:00:00;Tracté par la Daphnie;8;1;ensoleille;clair;;1;979;plat;propre;;10;vert-vert",
                "[{\"type\":\"DefaultCheckerValidationCheckResult\",\"message\":\"requiredValueWithComponent\",\"params\":{\"target\":{\"column\":\"date_day\"}},\"lineNumber\":3}]"
        ));
        DataTypeErrors.put("duplicatedLineInDatatype", List.of(
                "suivi des lacs;leman;SHL2;24/02/2020;00:00:00;Tracté par la Daphnie;8;1;ensoleille;clair;;1;979;plat;propre;;10;vert-vert",
                "suivi des lacs;leman;SHL2;22/01/2020;08:45:00;Tracté par la Daphnie;8;1;ensoleille;clair;;1;979;plat;propre;;10;vert-vert",
                "[{\"type\":\"DefaultValidationCheckResult\",\"message\":\"duplicatedLineInReference\",\"params\":{\"file\":\"condition_prelevements\",\"lineNumber\":3,\"otherLines\":[2,3],\"duplicateKey\":\"condition_prelevementsK22_01_2020__08COLON45COLON00__leman\"},\"lineNumber\":3}]"
        ));
        return DataTypeErrors;
    }

    public static Map<String, String> getRecursiviteReferentielFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("taxon", "/data/recursivite/taxons_du_phytoplancton_test.csv");
        return referentielFiles;
    }

    public static String getAcbbApplicationName() {
        return Application.ACBB.getName();
    }

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

    public static String getFluxToursDataResourceName() {
        return "/data/acbb/Flux_tours.csv";
    }

    public static String getBiomasseProductionTeneurDataResourceName() {
        return "/data/acbb/biomasse_production_teneur.csv";
    }

    public InputStream openSwcDataResourceName(final boolean truncated) {
        final String resourceName = "/data/acbb/SWC.csv";
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

    public static String getMigrationApplicationConfigurationResourceName(final int version) {
        return "/data/migration/fake-app_v" + version + ".yaml";
    }

    public static String getMigrationApplicationDataResourceName() {
        return "/data/migration/fake-data.csv";
    }

    public static String getMigrationApplicationReferenceResourceName() {
        return "/data/migration/couleurs.csv";
    }

    public Cookie addopenAdomAdmin(final String applicationPattern) throws Exception {
        if (cookie == null) {
            final String aPassword = "xxxxxxxx";
            final String aLogin = "openAdomAdmin";
            final CreateUserResult createUserResult = authenticationService.createUser(aLogin, aPassword, aLogin + "@inrae.fr");
            authenticationService.addUserRightCreateApplication(createUserResult.userId(), applicationPattern);
            cookie = mockMvc.perform(post("/api/v1/login")
                            .param("login", aLogin)
                            .param("password", aPassword))
                    .andReturn().getResponse().getCookie(AuthorizationFilter.JWT_COOKIE_NAME);
        }
        return cookie;
    }@Transactional
    void addRoleAdmin(final CreateUserResult dbUserResult) {
        String sql = """
        GRANT "openAdomAdmin" TO :userId WITH INHERIT TRUE
        """;

        namedParameterJdbcTemplate.update(
                sql,
                Map.of("userId", dbUserResult.userId().toString())
        );
    }

    @Transactional
    void setToActive(final UUID userId) {
        String sql = """
        UPDATE public.OreSiUser 
        SET accountstate = 'active' 
        WHERE id = :id
        """;

        namedParameterJdbcTemplate.update(
                sql,
                Map.of("id", userId)
        );
    }


    public Cookie addApplicationCreatorUser(final String applicationPattern) throws Exception {
        if (cookie == null) {
            final String aPassword = "xxxxxxxx";
            final String aLogin = "poussin";
            final CreateUserResult createUserResult = authenticationService.createUser(aLogin, aPassword, aLogin + "@inrae.fr");
            setToActive(createUserResult.userId());
            addRoleAdmin(createUserResult);
            MockHttpServletResponse response = mockMvc.perform(post("/api/v1/login")
                            .param("login", aLogin)
                            .param("password", aPassword))
                    .andReturn().getResponse();
            cookie = response.getCookie(AuthorizationFilter.JWT_COOKIE_NAME);
        }
        final String aPassword = "xxxxxxxx";
        final CreateUserResult createUserResult = authenticationService.createUser(applicationPattern, aPassword, applicationPattern + "@inrae.fr");
        setToActive(createUserResult.userId());
        UUID userId = createUserResult.userId();
        final ResultActions resultActions = mockMvc.perform(put("/api/v1/authorization/applicationCreator")
                        .param("userIdOrLogin", userId.toString())
                        .param("applicationPattern", applicationPattern)
                        .cookie(cookie))
                .andExpect(status().is2xxSuccessful())
                .andExpect(jsonPath("$.roles.currentUser", IsEqual.equalTo(userId.toString())))
                .andExpect(jsonPath("$.roles.memberOf", Matchers.hasItem("applicationCreator")))
                .andExpect(jsonPath("$.authorizations", Matchers.hasItem(applicationPattern)))
                .andExpect(jsonPath("$.id", IsEqual.equalTo(userId.toString())));
        OreSiUser user = userRepository.findById(userId);
        assertTrue(user.getAuthorizations().contains(applicationPattern));
        setToActive(createUserResult.userId());
        return mockMvc.perform(post("/api/v1/login")
                        .param("login", applicationPattern)
                        .param("password", aPassword))
                .andReturn().getResponse().getCookie(AuthorizationFilter.JWT_COOKIE_NAME);
    }

    public String createApplicationMonSore(final Cookie authCookie, final String applicationName) {
        MvcResult result;
        try (final InputStream configurationFile = getClass().getResourceAsStream(getMonsoreApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", configurationFile);
            result = loadApplication(configuration, authCookie, (applicationName == null ? "monsore" : applicationName), (applicationName == null ? "monsore" : applicationName));

            return getIdFromApplicationResult(result);
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }
    }

    public Exception createApplicationMonSoreWithError(final Cookie authCookie, final String applicationName) throws Exception {
        try (final InputStream configurationFile = getClass().getResourceAsStream(getMonsoreApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "monsore.yaml", "text/plain", configurationFile);
            return loadApplicationWithError(configuration, authCookie, (applicationName == null ? "monsore" : applicationName));

        }
    }

    public Cookie addMonsoreApplication() throws Exception {
        final Cookie authCookie = addApplicationCreatorUser("monsore");
        final String result = createApplicationMonSore(authCookie, "monsore");

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : getMonsoreReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/monsore/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }

        // ajout de data
        try (final InputStream refStream = getClass().getResourceAsStream(getPemDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "data-pem.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/monsore/data/pem")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
        return authCookie;
    }

    public Cookie addMigrationApplication() throws Exception {
        final Cookie authCookie = addApplicationCreatorUser("fakeapp");
        try (final InputStream configurationFile = getClass().getResourceAsStream(getMigrationApplicationConfigurationResourceName(1))) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "fake-app.yaml", "text/plain", configurationFile);

            getIdFromApplicationResult(loadApplication(configuration, authCookie, "fakeapp", "fakeapp"));
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }

        // Ajout de referentiel
        try (final InputStream refStream = getClass().getResourceAsStream(getMigrationApplicationReferenceResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "reference.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/fakeapp/data/couleurs")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data
        try (final InputStream refStream = getClass().getResourceAsStream(getMigrationApplicationDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "data.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/fakeapp/data/jeu1")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }

        return authCookie;
    }

    public Cookie addApplicationAcbb(Cookie authCookie) throws Exception {
        Cookie authCookie1 = authCookie;
        if (authCookie1 == null) {
            authCookie1 = addApplicationCreatorUser("acbb");
        }
        try (final InputStream configurationFile = getClass().getResourceAsStream(getAcbbApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "acbb.yaml", "text/plain", configurationFile);
            getIdFromApplicationResult(loadApplication(configuration, authCookie1, "acbb", "acbb"));
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : getAcbbReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/acbb/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie1))
                        .andExpect(status().isCreated());
            }
        }

        // ajout de data
        addFluxTours(authCookie1);

        addBiomasse(authCookie1);

        addSWC(authCookie1);
        return authCookie1;
    }

    private void addSWC(final Cookie authCookie) throws Exception {
        try (final InputStream in = openSwcDataResourceName(true)) {
            final MockMultipartFile file = new MockMultipartFile("file", "SWC.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/acbb/data/SWC")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    private void addBiomasse(final Cookie authCookie) throws Exception {
        try (final InputStream in = getClass().getResourceAsStream(getBiomasseProductionTeneurDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "biomasse_production_teneur.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/acbb/data/biomasse_production_teneur")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    private void addFluxTours(final Cookie authCookie) throws Exception {
        try (final InputStream in = getClass().getResourceAsStream(getFluxToursDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "Flux_tours.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/acbb/data/flux_tours")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
    }

    public static String getValidationApplicationConfigurationResourceName() {
        return "/data/validation/broken-fake-app.yaml";
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

    public Cookie addApplicationHauteFrequence() throws Exception {
        final Cookie authCookie = addApplicationCreatorUser("hautefrequence");
        try (final InputStream configurationFile = getClass().getResourceAsStream(getHauteFrequenceApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "hautefrequence.yaml", "text/plain", configurationFile);
            loadApplication(configuration, authCookie, "hautefrequence", "hautefrequence");
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : getHauteFrequenceReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/hautefrequence/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().is2xxSuccessful());
            }
        }

        // ajout de data
        try (final InputStream refStream = getClass().getResourceAsStream(getHauteFrequenceDataResourceName())) {
            final MockMultipartFile refFile = new MockMultipartFile("file", "hautefrequence.csv", "text/plain", refStream);
            mockMvc.perform(multipart("/api/v1/applications/hautefrequence/data/hautefrequence")
                            .file(refFile)
                            .cookie(authCookie))
                    .andExpect(status().is2xxSuccessful());
        }
        return authCookie;
    }

    public static String getDuplicatedApplicationConfigurationResourceName() {
        return "/data/duplication/duplication.yaml";
    }

    public static Map<String, String> getDuplicatedReferentielFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("typezonewithoutduplication", "/data/duplication/typezone.csv");
        referentielFiles.put("typezonewithduplication", "/data/duplication/typezoneduplique.csv");
        referentielFiles.put("zonewithoutduplication", "/data/duplication/zone_etude.csv");
        referentielFiles.put("zonewithduplication", "/data/duplication/zone_etude_dupliqué.csv");
        referentielFiles.put("zonewithmissingparent", "/data/duplication/zone_etude_missing_parent.csv");
        return referentielFiles;
    }

    public static Map<String, String> getDuplicatedDataFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("data_without_duplicateds", "/data/duplication/data.csv");
        referentielFiles.put("data_with_duplicateds", "/data/duplication/data_with_duplicateds.csv");
        return referentielFiles;
    }

    public static Map<String, String> getOlaReferentielFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("themes", "/data/olac/themes.csv");
        referentielFiles.put("projets", "/data/olac/projets.csv");
        referentielFiles.put("type_sites", "/data/olac/types_de_site.csv");
        referentielFiles.put("sites", "/data/olac/sites.csv");
        referentielFiles.put("type_plateformes", "/data/olac/types_de_plateforme.csv");
        referentielFiles.put("plateformes", "/data/olac/plateformes.csv");
        referentielFiles.put("valeurs_qualitatives", "/data/olac/valeurs_qualitatives.csv");
        return referentielFiles;
    }

    public static String getOlaApplicationConfigurationResourceName() {
        return "/data/olac/olac.yaml";
    }

    public Cookie addApplicationOLAC() throws Exception {
        final Cookie authCookie = addApplicationCreatorUser("olac");
        try (final InputStream configurationFile = getClass().getResourceAsStream(getOlaApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "olac.yaml", "text/plain", configurationFile);

            loadApplication(configuration, authCookie, "olac", "olac");
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : getOlaReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/olac/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }

        // ajout de data condition_prelevements
        try (final InputStream in = getClass().getResourceAsStream(getConditionPrelevementDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "condition_prelevements.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/condition_prelevements")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data physico-chimie
        try (final InputStream in = getClass().getResourceAsStream(getPhysicoChimieDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "physico-chimie.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/physico-chimie")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data sonde_truncated
        try (final InputStream in = getClass().getResourceAsStream(getSondeDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "sonde_truncated.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/sonde_truncated")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data phytoplancton_aggregated
        try (final InputStream in = getClass().getResourceAsStream(getPhytoAggregatedDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "phytoplancton_aggregated.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/phytoplancton_aggregated")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data phytoplancton_truncated
        try (final InputStream in = getClass().getResourceAsStream(getPhytoplanctonDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "phytoplancton_truncated.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/phytoplancton__truncated")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data  zooplancton_truncated
        try (final InputStream in = getClass().getResourceAsStream(getZooplanctonDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "zooplancton_truncated.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/zooplancton__truncated")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        // ajout de data zooplancton_biovolumes
        try (final InputStream in = getClass().getResourceAsStream(getZooplactonBiovolumDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "zooplancton_biovolumes.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/olac/data/zooplancton_biovolumes")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        return authCookie;
    }

    public static String getConditionPrelevementDataResourceName() {
        return "/data/olac/condition_prelevements.csv";
    }

    public static String getPhysicoChimieDataResourceName() {
        return "/data/olac/physico-chimie.csv";
    }

    public static String getSondeDataResourceName() {
        return "/data/olac/sonde_truncated.csv";
    }

    public static String getPhytoAggregatedDataResourceName() {
        return "/data/olac/phytoplancton_aggregated.csv";
    }

    public static String getPhytoplanctonDataResourceName() {
        return "/data/olac/phytoplancton__truncated.csv";
    }

    public static String getZooplanctonDataResourceName() {
        return "/data/olac/zooplancton__truncated.csv";
    }

    public static String getZooplactonBiovolumDataResourceName() {
        return "/data/olac/zooplancton_biovolumes.csv";
    }

    public static Map<String, String> getForetEssaiReferentielFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
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

    public static Map<String, String> getForetReferentielFiles() {
        final Map<String, String> referentielFiles = new LinkedHashMap<>();
        referentielFiles.put("types_de_zones_etudes", "/data/foret/contexte_dispositif_types_de_zones_etudes.csv");
        referentielFiles.put("zones_etudes", "/data/foret/contexte_dispositif_zones_etudes.csv");
        referentielFiles.put("traitements", "/data/foret/contexte_dispositif_traitements.csv");
        referentielFiles.put("themes", "/data/foret/contexte_dispositif_themes.csv");
        referentielFiles.put("data_types", "/data/foret/contexte_dispositif_data_types.csv");
        referentielFiles.put("theme_types_de_donnees_par_zone_etudes", "/data/foret/contexte_dispositif_theme_types_de_donnees_par_zone_etudes.csv");
        referentielFiles.put("variables_par_types_de_donnees", "/data/foret/contexte_mesure_variables_par_types_de_donnees.csv");
        return referentielFiles;
    }

    public static String getForetApplicationConfigurationResourceName() {
        return "/data/foret/foret.yaml";
    }

    public static String getForetEssaiApplicationConfigurationResourceName() {
        return "/data/foret/foret_essai.yaml";
    }

    public Cookie addApplicationFORET() throws Exception {
        final Cookie authCookie = addApplicationCreatorUser("foret");
        try (final InputStream configurationFile = getClass().getResourceAsStream(getForetApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "foret.yaml", "text/plain", configurationFile);
            loadApplication(configuration, authCookie, "foret", "foret");
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }

        // Ajout de referentiel
        for (final Map.Entry<String, String> e : getForetReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/foret/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }

        // ajout de data
        try (final InputStream in = getClass().getResourceAsStream(getFluxMeteoForetDataResourceName())) {
            final MockMultipartFile file = new MockMultipartFile("file", "flux_meteo_dataResult.csv", "text/plain", in);
            mockMvc.perform(multipart("/api/v1/applications/foret/data/flux_meteo_dataResult")
                            .file(file)
                            .cookie(authCookie))
                    .andExpect(status().isCreated());
        }

        return authCookie;
    }

    public static String getFluxMeteoForetDataResourceName() {
        return "/data/foret/flux_meteo_dataResult.csv";
    }

    public static Map<String, String> getForetEssaiDataResourceName() {
        final ImmutableMap.Builder<String, String> builder = ImmutableMap.builder();
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

    public static Map<String, Integer> getForetEssaiSynthesisSize() {
        final ImmutableMap.Builder<String, Integer> builder = ImmutableMap.builder();
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
        final Cookie authCookie = addApplicationCreatorUser("recursivite");
        try (final InputStream in = getClass().getResourceAsStream(getRecursivityApplicationConfigurationResourceName())) {
            final MockMultipartFile configuration = new MockMultipartFile("file", "recursivity.yaml", "text/plain", in);
            loadApplication(configuration, authCookie, "recursivite", "recursivite");
        } catch (final Throwable e) {
            throw new RuntimeException(e);
        }

        String response;
        // Ajout de referentiel
        for (final Map.Entry<String, String> e : getRecursiviteReferentielOrderFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/recursivite/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }
        for (final Map.Entry<String, String> e : getRecursiviteReferentielFiles().entrySet()) {
            try (final InputStream refStream = getClass().getResourceAsStream(e.getValue())) {
                final MockMultipartFile refFile = new MockMultipartFile("file", e.getValue(), "text/plain", refStream);
                mockMvc.perform(multipart("/api/v1/applications/recursivite/data/{refType}", e.getKey())
                                .file(refFile)
                                .cookie(authCookie))
                        .andExpect(status().isCreated());
            }
        }
    }

    public static String getMultiplicityMany() {
        return "/data/multiplicity/multiplicity.yaml";
    }

    public static Map<String, String> getMultiplicityReferencesFiles() {
        final Map<String, String> references = new LinkedHashMap<>();
        references.put("reference1", "/data/multiplicity/references/reference1.csv");
        references.put("reference2", "/data/multiplicity/references/reference2.csv");
        references.put("bugs", "/data/multiplicity/data/bugs.csv");
        return references;
    }

    public static String getMultiplicityManyData() {
        return "/data/multiplicity/data/bugs.csv";
    }

    @Getter
    public enum Application {
        MONSORE("monsore", ImmutableSet.of("pem")),
        ACBB("acbb", ImmutableSet.of("flux_tours", "biomasse_production_teneur", "SWC")),
        //PRO("pros", ImmutableSet.of("donnees_prelevement_pro")),
        OLAC("olac", ImmutableSet.of("condition_prelevements")),
        FORET("foret", ImmutableSet.of("flux_meteo_dataResult")),
        FAKE_APP_FOR_MIGRATION("fakeapp", ImmutableSet.of()),
        RECURSIVITY("recursivite", ImmutableSet.of());

        private final String name;

        private final ImmutableSet<String> dataTypes;

        Application(final String name, final ImmutableSet<String> dataTypes) {
            this.name = name;
            this.dataTypes = dataTypes;
        }

    }
}
