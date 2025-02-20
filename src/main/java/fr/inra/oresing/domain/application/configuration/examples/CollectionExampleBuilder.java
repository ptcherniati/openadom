package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.*;

class CollectionExampleBuilder {
    protected static final CollectionType.MapType<BasicComponentType> ESPECE_BASIC_COMPONENTS = new CollectionType.MapType<>(
            createEspeceBasicComponentsMap(),
            false,
            false,
            BasicComponentType.EMPTY_INSTANCE()
    );

    private static Map<String, BasicComponentType> createEspeceBasicComponentsMap() {
        Map<String, BasicComponentType> map = new LinkedHashMap<>();
        map.put("spe_definition_fr", BasicComponentExampleBuilder.ESPECES_DEFINITION_FR);
        map.put("spe_definition_en", BasicComponentExampleBuilder.ESPECES_DEFINITION_EN);
        map.put("spe_species", BasicComponentExampleBuilder.ESPECES);
        map.put("spe_date", BasicComponentExampleBuilder.DATE_START);
        map.put("spe_heure", BasicComponentExampleBuilder.HEURE);
        map.put("spe_weight", BasicComponentExampleBuilder.MASSE);
        map.put("spe_tool", BasicComponentExampleBuilder.OUTIL);
        map.put("spe_site", BasicComponentExampleBuilder.SITE);
        map.put("spe_is_iso", BasicComponentExampleBuilder.IS_ISO);
        map.put("spe_repetition", BasicComponentExampleBuilder.REPETITION);
        return map;
    }

    protected static final CollectionType.MapType<BasicComponentType> SITES_BASIC_COMPONENTS = new CollectionType.MapType<>(
            createSitesBasicComponentsMap(),
            false,
            false,
            BasicComponentType.EMPTY_INSTANCE()
    );

    private static Map<String, BasicComponentType> createSitesBasicComponentsMap() {
        Map<String, BasicComponentType> map = new LinkedHashMap<>();
        map.put("tze_type_nom", BasicComponentExampleBuilder.TYPE_DE_SITES);
        map.put("zet_nom_key", BasicComponentExampleBuilder.SITES_KEY);
        map.put("zet_nom_fr", BasicComponentExampleBuilder.SITES_FR);
        map.put("zet_nom_en", BasicComponentExampleBuilder.SITES_EN);
        map.put("zet_description_fr", BasicComponentExampleBuilder.SITES_DEFINITION_FR);
        map.put("zet_description_en", BasicComponentExampleBuilder.SITES_DEFINITION_EN);
        map.put("zet_chemin_parent", BasicComponentExampleBuilder.SITES_PARENT);
        return map;
    }

    protected static final CollectionType.MapType<ComputedComponentType> SITES_COMPUTED_COMPONENTS = new CollectionType.MapType<>(
            createSitesComputedComponentsMap(),
            false,
            false,
            ComputedComponentType.EMPTY_INSTANCE()
    );

    protected static final CollectionType.MapType<BasicComponentType> PROPRIETE_TAXON_BASIC_COMPONENTS = new CollectionType.MapType<>(
            createProprieteTaxonBasicComponentsMap(),
            false,
            false,
            BasicComponentType.EMPTY_INSTANCE()
    );

    private static Map<String, ComputedComponentType> createSitesComputedComponentsMap() {
        Map<String, ComputedComponentType> map = new LinkedHashMap<>();
        map.put("zet_computed_key", ComputedComponentExampleBuilder.SITES);
        return map;
    }

    private static Map<String, BasicComponentType> createProprieteTaxonBasicComponentsMap() {
        Map<String, BasicComponentType> map = new LinkedHashMap<>();
        map.put("ptx_date", BasicComponentExampleBuilder.DATE_START);
        map.put("ptx_propriete", BasicComponentExampleBuilder.PROPRIETE);
        return map;
    }

    protected static final CollectionType.MapType<BasicComponentType> TAXON_BASIC_COMPONENTS = new CollectionType.MapType<>(Map.of("tax_taxon", BasicComponentExampleBuilder.TAXON_NOM), false, false, BasicComponentType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<DynamicComponentType> TAXON_DYNAMIC_COMPONENTS = new CollectionType.MapType<>(Map.of("tax_propriete_taxon", DynamicComponentsExampleBuilder.PROPRIETE_TAXON), false, false, DynamicComponentType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<ComputedComponentType> ESPECE_COMPUTED_COMPONENTS = new CollectionType.MapType<>(Map.of("spe_date_heure", ComputedComponentExampleBuilder.DATE_HEURE), false, false, ComputedComponentType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<BasicComponentType> PROJET_BASIC_COMPONENTS = new CollectionType.MapType<>(
            createProjetBasicComponentsMap(),
            false,
            false,
            BasicComponentType.EMPTY_INSTANCE()
    );

    protected static final CollectionType.MapType<BasicComponentType> TYPE_DE_SITES_BASIC_COMPONENTS = new CollectionType.MapType<>(
            createTypeDeSitesBasicComponentsMap(),
            false,
            false,
            BasicComponentType.EMPTY_INSTANCE()
    );

    protected static final CollectionType.MapType<BasicComponentType> DATA_BASIC_COMPONENTS = new CollectionType.MapType<>(
            createDataBasicComponentsMap(),
            false,
            false,
            BasicComponentType.EMPTY_INSTANCE()
    );

    private static Map<String, BasicComponentType> createProjetBasicComponentsMap() {
        Map<String, BasicComponentType> map = new LinkedHashMap<>();
        map.put("pro_nom_key", BasicComponentExampleBuilder.PROJET_KEY);
        map.put("pro_nom_fr", BasicComponentExampleBuilder.PROJET_FR);
        map.put("pro_nom_en", BasicComponentExampleBuilder.PROJET_EN);
        map.put("pro_definition_fr", BasicComponentExampleBuilder.PROJET_DEFINITION_FR);
        map.put("pro_definition_en", BasicComponentExampleBuilder.PROJET_DEFINITION_EN);
        return map;
    }

    private static Map<String, BasicComponentType> createTypeDeSitesBasicComponentsMap() {
        Map<String, BasicComponentType> map = new LinkedHashMap<>();
        map.put("tze_nom_key", BasicComponentExampleBuilder.TYPE_DE_SITES_KEY);
        map.put("tze_nom_fr", BasicComponentExampleBuilder.TYPE_DE_SITES_FR);
        map.put("tze_nom_en", BasicComponentExampleBuilder.TYPE_DE_SITES_EN);
        map.put("tze_definition_fr", BasicComponentExampleBuilder.TYPE_DE_SITES_DEFINITION_FR);
        map.put("tze_definition_en", BasicComponentExampleBuilder.TYPE_DE_SITES_DEFINITION_EN);
        return map;
    }

    private static Map<String, BasicComponentType> createDataBasicComponentsMap() {
        Map<String, BasicComponentType> map = new LinkedHashMap<>();
        map.put("dat_date", BasicComponentType.EMPTY_INSTANCE());
        map.put("dat_heure", BasicComponentType.EMPTY_INSTANCE());
        return map;
    }

    protected static final CollectionType.MapType<ComputedComponentType> DATA_COMPUTED_COMPONENTS = new CollectionType.MapType<>(
            Map.of("dat_date_heure", ComputedComponentExampleBuilder.DATA_DATE_HEURE), false, false, ComputedComponentType.EMPTY_INSTANCE());

    protected static final CollectionType.MapType<PatternComponentType> DATA_PATTERN_COMPONENTS = new CollectionType.MapType<>(
            createDataPatternComponentsMap(),
            false,
            false,
            PatternComponentType.EMPTY_INSTANCE()
    );

    protected static final CollectionType.MapType<ConstantComponentType> DATA_CONSTANT_COMPONENTS = new CollectionType.MapType<>(
            createDataConstantComponentsMap(),
            false,
            false,
            ConstantComponentType.EMPTY_INSTANCE()
    );

    protected static final CollectionType.MapType<ValidationType> DATA_VALIDATIONS = new CollectionType.MapType<>(
            createDataValidationsMap(),
            false,
            false,
            ValidationType.EMPTY_INSTANCE()
    );

    protected static final CollectionType.MapType<AdditionalFileType> ADITIONNAL_FILES = new CollectionType.MapType<>(
            createAdditionalFilesMap(),
            false,
            false,
            AdditionalFileType.EMPTY_INSTANCE()
    );

    protected static final CollectionType.MapType<ApplicationType.ComponentType> COMPONENT_QUALIFIERS = new CollectionType.MapType<>(
            createComponentQualifiersMap(),
            false,
            false,
            PatternComponentQualifierType.EMPTY_INSTANCE()
    );

    protected static final CollectionType.MapType<FormatType> RIGHT_REQUEST_FORM_FIELDS = new CollectionType.MapType<>(
            createRightRequestFormFieldsMap(),
            false,
            false,
            FormatType.EMPTY_INSTANCE()
    );

    private static Map<String, PatternComponentType> createDataPatternComponentsMap() {
        Map<String, PatternComponentType> map = new LinkedHashMap<>();
        map.put("swc", PatternComponentExampleBuilder.SWC);
        map.put("smp", PatternComponentExampleBuilder.SMP);
        return map;
    }

    private static Map<String, ConstantComponentType> createDataConstantComponentsMap() {
        Map<String, ConstantComponentType> map = new LinkedHashMap<>();
        map.put("dat_type_site", ConstantComponentExampleBuilder.TYPE_SITE);
        map.put("dat_site", ConstantComponentExampleBuilder.SITE);
        map.put("dat_start_date", ConstantComponentExampleBuilder.START_DATE);
        map.put("dat_end_date", ConstantComponentExampleBuilder.END_DATE);
        return map;
    }

    private static Map<String, ValidationType> createDataValidationsMap() {
        Map<String, ValidationType> map = new LinkedHashMap<>();
        map.put("type_site_validation", ValidationExampleBuilder.TYPE_SITE);
        map.put("site_validation", ValidationExampleBuilder.SITE);
        map.put("start_date_validation", ValidationExampleBuilder.START_DATE);
        map.put("end_date_validation", ValidationExampleBuilder.END_DATE);
        map.put("date_validation", ValidationExampleBuilder.DATE);
        map.put("interval_date_validation", ValidationExampleBuilder.INTERVAL_DATE);
        return map;
    }

    private static Map<String, AdditionalFileType> createAdditionalFilesMap() {
        Map<String, AdditionalFileType> map = new LinkedHashMap<>();
        map.put("firstAdditionalfile", AdditionalFileBuildExample.FIRST);
        map.put("secondAdditionalfile", AdditionalFileBuildExample.SECOND);
        return map;
    }

    private static Map<String, ApplicationType.ComponentType> createComponentQualifiersMap() {
        Map<String, ApplicationType.ComponentType> map = new LinkedHashMap<>();
        map.put("profondeur", PatternComponentQualifierExampleBuilder.PROFONDEUR);
        map.put("repetition", PatternComponentQualifierExampleBuilder.REPETITION);
        return map;
    }

    private static Map<String, FormatType> createRightRequestFormFieldsMap() {
        Map<String, FormatType> map = new LinkedHashMap<>();
        map.put("nom", FormatExampleBuilder.NOM);
        map.put("projet", FormatExampleBuilder.PROJET);
        map.put("start_date", FormatExampleBuilder.START_DATE);
        map.put("end_date", FormatExampleBuilder.ORGANISME);
        return map;
    }

    protected static final CollectionType.MapType<I18nType> ESPECE_DEFINITION = buildI18nColumns(Map.of("spe_definition_fr", I18nExampleBuilder.buildI18n("spe_definition_fr", "spe_definition_en")));
    protected static final CollectionType.MapType<I18nType> NOM = buildI18nColumns(Map.of("pro_nom_key", I18nExampleBuilder.buildI18n("pro_nom_fr", "pro_nom_en")));
    protected static final CollectionType.MapType<I18nType> SITE_NOM = buildI18nColumns(Map.of("zet_nom_key", I18nExampleBuilder.buildI18n("zet_nom_fr", "zet_nom_en")));
    protected static final CollectionType.MapType<I18nType> TAXON_COLUMNS = buildI18nColumns(Map.of("tax_taxon", I18nExampleBuilder.buildI18n("Nom du taxon", "Taxa name")));
    protected static final CollectionType.MapType<I18nType> PROPRIETE_TAXON_COLUMNS = buildI18nColumns(Map.of("ptx_propriete", I18nExampleBuilder.buildI18n("Nom de la propriété de taxon", "Taxa property name")));
    protected static final CollectionType.MapType<I18nType> TYPE_SITE_NOM_DEFINITION = buildI18nColumns(
            createTypeSiteNomDefinitionMap()
    );

    private static Map<String, I18nType> createTypeSiteNomDefinitionMap() {
        Map<String, I18nType> map = new LinkedHashMap<>();
        map.put("tze_nom_key", I18nExampleBuilder.buildI18n("tze_nom_fr", "tze_nom_en"));
        map.put("tze_definition_fr", I18nExampleBuilder.buildI18n("tze_definition_fr", "tze_definition_en"));
        return map;
    }

    protected static final TitleType ESPECE_DISPLAY = TitleExampleBuilder.buildTitle(
            I18nExampleBuilder.buildI18n("\"{spe_species}\"", "\"{spe_species}\""),
            I18nExampleBuilder.buildI18n("\"{spe_definition_fr}\"", "\"{spe_definition_en}\"")
    );
    protected static final TitleType NOM_ZET_DISPLAY = TitleExampleBuilder.buildTitle(
            I18nExampleBuilder.buildI18n("\"'{zet_nom_fr}'\"", "\"'{zet_nom_en}'\""),
            I18nExampleBuilder.buildI18n("\"'{zet_description_fr}'\"", "\"'{zet_description_fr}'\"")
    );
    protected static final TitleType PROJET_DISPLAY = TitleExampleBuilder.buildTitle(
            I18nExampleBuilder.buildI18n("\"'{pro_nom_fr}'\"", "\"'{pro_nom_en}'\""),
            I18nExampleBuilder.buildI18n("\"'{pro_definition_fr}'\"", "\"'{pro_definition_en}'\"")
    );
    protected static final TitleType NOM_TZE_DISPLAY = TitleExampleBuilder.buildTitle(
            I18nExampleBuilder.buildI18n("\"'De type : {tze_nom_fr}'\"", "\"'Of type : {tze_nom_en}'\""),
            I18nExampleBuilder.buildI18n("\"'{tze_definition_fr}'\"", "\"'{tze_definition_en}'\"")
    );
    protected static final TitleType TAXON_DISPLAY = TitleExampleBuilder.buildTitle(
            I18nExampleBuilder.buildI18n("\"'{tax_taxon}'\"", "\"'{tax_taxon}'\""),
            null
    );
    protected static final TitleType PROPRIETE_TAXON_DISPLAY = TitleExampleBuilder.buildTitle(
            I18nExampleBuilder.buildI18n("\"'{ptx_propriete}'\"", "\"'{ptx_propriete}'\""),
            null
    );


    protected static CollectionType.MapType<I18nType> buildI18nColumns(final Map<String, I18nType> columns) {
        return new CollectionType.MapType<>(columns, false, false, I18nType.EMPTY_INSTANCE());
    }

    protected static CollectionType.ArrayType<CollectionType.MapType<PatternComponentQualifierType>> COMPONENT_QUALIFIERS(final String prefix) {
        return new CollectionType.ArrayType<>(
                createComponentQualifiersList(prefix),
                false,
                false,
                CollectionType.MapType.PATTERN_COMPONENT_QUALIFIER_EMPTY_INSTANCE()
        );
    }

    private static List<CollectionType.MapType<PatternComponentQualifierType>> createComponentQualifiersList(final String prefix) {
        List<CollectionType.MapType<PatternComponentQualifierType>> list = new ArrayList<>();
        list.add(createComponentQualifierMap(prefix, "profondeur", PatternComponentQualifierExampleBuilder.PROFONDEUR));
        list.add(createComponentQualifierMap(prefix, "repetition", PatternComponentQualifierExampleBuilder.REPETITION));
        return list;
    }

    private static CollectionType.MapType<PatternComponentQualifierType> createComponentQualifierMap(
            String prefix, String suffix, PatternComponentQualifierType value) {
        Map<String, PatternComponentQualifierType> map = new HashMap<>();
        map.put(String.format("%s_%s", prefix, suffix), value);
        return new CollectionType.MapType<>(map, false, false, PatternComponentQualifierType.EMPTY_INSTANCE());
    }

    protected static CollectionType.ArrayType<CollectionType.MapType<PatternComponentAdjacentType>> COMPONENT_ADJACENTS(final String prefix) {
        return new CollectionType.ArrayType<>(
                createComponentAdjacentsList(prefix),
                false,
                false,
                CollectionType.MapType.PATTERN_COMPONENT_ADJACENT_EMPTY_INSTANCE()
        );
    }

    private static List<CollectionType.MapType<PatternComponentAdjacentType>> createComponentAdjacentsList(final String prefix) {
        List<CollectionType.MapType<PatternComponentAdjacentType>> list = new ArrayList<>();
        list.add(createComponentAdjacentMap(prefix, "sd", PatternComponentAdjacentExampleBuilder.STANDARD_DEVIATION));
        list.add(createComponentAdjacentMap(prefix, "qc", PatternComponentAdjacentExampleBuilder.QUALITY_CLASS));
        return list;
    }

    private static CollectionType.MapType<PatternComponentAdjacentType> createComponentAdjacentMap(
            String prefix, String suffix, PatternComponentAdjacentType value) {
        Map<String, PatternComponentAdjacentType> map = new HashMap<>();
        map.put(String.format("%s_%s", prefix, suffix), value);
        return new CollectionType.MapType<>(map, false, false, PatternComponentAdjacentType.EMPTY_INSTANCE());
    }

}