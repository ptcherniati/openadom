package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map;

class CollectionExampleBuilder {
    protected static final CollectionType.MapType<BasicComponentType> ESPECE_BASIC_COMPONENTS = new CollectionType.MapType<>(new LinkedHashMap<>() {{
        put("spe_definition_fr", BasicComponentExampleBuilder.ESPECES_DEFINITION_FR);
        put("spe_definition_en", BasicComponentExampleBuilder.ESPECES_DEFINITION_EN);
        put("spe_species", BasicComponentExampleBuilder.ESPECES);
        put("spe_date", BasicComponentExampleBuilder.DATE_START);
        put("spe_heure", BasicComponentExampleBuilder.HEURE);
        put("spe_weight", BasicComponentExampleBuilder.MASSE);
        put("spe_tool", BasicComponentExampleBuilder.OUTIL);
        put("spe_site", BasicComponentExampleBuilder.SITE);
        put("spe_is_iso", BasicComponentExampleBuilder.IS_ISO);
        put("spe_repetition", BasicComponentExampleBuilder.REPETITION);

    }}, false, false, BasicComponentType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<BasicComponentType> SITES_BASIC_COMPONENTS = new CollectionType.MapType<>(new LinkedHashMap<>() {{
        put("tze_type_nom", BasicComponentExampleBuilder.TYPE_DE_SITES);
        put("zet_nom_key", BasicComponentExampleBuilder.SITES_KEY);
        put("zet_nom_fr", BasicComponentExampleBuilder.SITES_FR);
        put("zet_nom_en", BasicComponentExampleBuilder.SITES_EN);
        put("zet_description_fr", BasicComponentExampleBuilder.SITES_DEFINITION_FR);
        put("zet_description_en", BasicComponentExampleBuilder.SITES_DEFINITION_EN);
        put("zet_chemin_parent", BasicComponentExampleBuilder.SITES_PARENT);
    }}, false, false, BasicComponentType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<ComputedComponentType> SITES_COMPUTED_COMPONENTS = new CollectionType.MapType<>(new LinkedHashMap<>() {{
        put("zet_computed_key", ComputedComponentExampleBuilder.SITES);
    }}, false, false, ComputedComponentType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<BasicComponentType> PROPRIETE_TAXON_BASIC_COMPONENTS = new CollectionType.MapType<>(new LinkedHashMap<>() {{
        put("ptx_date", BasicComponentExampleBuilder.DATE_START);
        put("ptx_propriete", BasicComponentExampleBuilder.PROPRIETE);
    }}, false, false, BasicComponentType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<BasicComponentType> TAXON_BASIC_COMPONENTS = new CollectionType.MapType<>(Map.of("tax_taxon", BasicComponentExampleBuilder.TAXON_NOM), false, false, BasicComponentType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<DynamicComponentType> TAXON_DYNAMIC_COMPONENTS = new CollectionType.MapType<>(Map.of("tax_propriete_taxon", DynamicComponentsExampleBuilder.PROPRIETE_TAXON), false, false, DynamicComponentType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<ComputedComponentType> ESPECE_COMPUTED_COMPONENTS = new CollectionType.MapType<>(Map.of("spe_date_heure", ComputedComponentExampleBuilder.DATE_HEURE), false, false, ComputedComponentType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<BasicComponentType> PROJET_BASIC_COMPONENTS = new CollectionType.MapType<>(new LinkedHashMap<>() {{
        put("pro_nom_key", BasicComponentExampleBuilder.PROJET_KEY);
        put("pro_nom_fr", BasicComponentExampleBuilder.PROJET_FR);
        put("pro_nom_en", BasicComponentExampleBuilder.PROJET_EN);
        put("pro_definition_fr", BasicComponentExampleBuilder.PROJET_DEFINITION_FR);
        put("pro_definition_en", BasicComponentExampleBuilder.PROJET_DEFINITION_EN);
    }}, false, false, BasicComponentType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<BasicComponentType> TYPE_DE_SITES_BASIC_COMPONENTS = new CollectionType.MapType<>(new LinkedHashMap<>() {{
        put("tze_nom_key", BasicComponentExampleBuilder.TYPE_DE_SITES_KEY);
        put("tze_nom_fr", BasicComponentExampleBuilder.TYPE_DE_SITES_FR);
        put("tze_nom_en", BasicComponentExampleBuilder.TYPE_DE_SITES_EN);
        put("tze_definition_fr", BasicComponentExampleBuilder.TYPE_DE_SITES_DEFINITION_FR);
        put("tze_definition_en", BasicComponentExampleBuilder.TYPE_DE_SITES_DEFINITION_EN);
    }}, false, false, BasicComponentType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<BasicComponentType> DATA_BASIC_COMPONENTS = new CollectionType.MapType<>(
            new LinkedHashMap<>() {{

                put("dat_date", BasicComponentType.EMPTY_INSTANCE());
                put("dat_heure", BasicComponentType.EMPTY_INSTANCE());
            }}, false, false, BasicComponentType.EMPTY_INSTANCE()
    );
    protected static final CollectionType.MapType<ComputedComponentType> DATA_COMPUTED_COMPONENTS = new CollectionType.MapType<>(
            Map.of("dat_date_heure", ComputedComponentExampleBuilder.DATA_DATE_HEURE), false, false, ComputedComponentType.EMPTY_INSTANCE());

    protected static final CollectionType.MapType<PatternComponentType> DATA_PATTERN_COMPONENTS = new CollectionType.MapType<>(new LinkedHashMap<>() {
        {
            put("swc", PatternComponentExampleBuilder.SWC);
            put("smp", PatternComponentExampleBuilder.SMP);
        }
    }, false, false, PatternComponentType.EMPTY_INSTANCE()
    );
    protected static final CollectionType.MapType<ConstantComponentType> DATA_CONSTANT_COMPONENTS = new CollectionType.MapType<>(new LinkedHashMap<>() {
        {
            put("dat_type_site", ConstantComponentExampleBuilder.TYPE_SITE);
            put("dat_site", ConstantComponentExampleBuilder.SITE);
            put("dat_start_date", ConstantComponentExampleBuilder.START_DATE);
            put("dat_end_date", ConstantComponentExampleBuilder.END_DATE);
        }
    }, false, false, ConstantComponentType.EMPTY_INSTANCE()
    );
    protected static final CollectionType.MapType<ValidationType> DATA_VALIDATIONS = new CollectionType.MapType<>(new LinkedHashMap<>() {
        {
            put("type_site_validation", ValidationExampleBuilder.TYPE_SITE);
            put("site_validation", ValidationExampleBuilder.SITE);
            put("start_date_validation", ValidationExampleBuilder.START_DATE);
            put("end_date_validation", ValidationExampleBuilder.END_DATE);
            put("date_validation", ValidationExampleBuilder.DATE);
            put("interval_date_validation", ValidationExampleBuilder.INTERVAL_DATE);
        }
    }, false, false, ValidationType.EMPTY_INSTANCE()
    );
    protected static final CollectionType.MapType<AdditionalFileType> ADITIONNAL_FILES = new CollectionType.MapType<>(
            new LinkedHashMap<>() {{
                put("firstAdditionalfile", AdditionalFileBuildExample.FIRST);
                put("secondAdditionalfile", AdditionalFileBuildExample.SECOND);
            }}
            , false, false, AdditionalFileType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<ApplicationType.ComponentType> COMPONENT_QUALIFIERS = new CollectionType.MapType<>(
            new LinkedHashMap<>() {{
                put("profondeur", PatternComponentQualifierExampleBuilder.PROFONDEUR);
                put("repetition", PatternComponentQualifierExampleBuilder.REPETITION);
            }},
            false, false, PatternComponentQualifierType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<FormatType> RIGHT_REQUEST_FORM_FIELDS = new CollectionType.MapType<>(
            new LinkedHashMap<>() {{
                put("nom", FormatExampleBuilder.NOM);
                put("projet", FormatExampleBuilder.PROJET);
                put("start_date", FormatExampleBuilder.START_DATE);
                put("end_date", FormatExampleBuilder.ORGANISME);
            }},
            false, false, FormatType.EMPTY_INSTANCE());
    protected static final CollectionType.MapType<I18nType> ESPECE_DEFINITION = buildI18nColumns(Map.of("spe_definition_fr", I18nExampleBuilder.buildI18n("spe_definition_fr", "spe_definition_en")));
    protected static final CollectionType.MapType<I18nType> NOM = buildI18nColumns(Map.of("pro_nom_key", I18nExampleBuilder.buildI18n("pro_nom_fr", "pro_nom_en")));
    protected static final CollectionType.MapType<I18nType> SITE_NOM = buildI18nColumns(Map.of("zet_nom_key", I18nExampleBuilder.buildI18n("zet_nom_fr", "zet_nom_en")));
    protected static final CollectionType.MapType<I18nType> TAXON_COLUMNS = buildI18nColumns(Map.of("tax_taxon", I18nExampleBuilder.buildI18n("Nom du taxon", "Taxa name")));
    protected static final CollectionType.MapType<I18nType> PROPRIETE_TAXON_COLUMNS = buildI18nColumns(Map.of("ptx_propriete", I18nExampleBuilder.buildI18n("Nom de la propriété de taxon", "Taxa property name")));
    protected static final CollectionType.MapType<I18nType> TYPE_SITE_NOM_DEFINITION = buildI18nColumns(new LinkedHashMap<>() {{
        put("tze_nom_key", I18nExampleBuilder.buildI18n("tze_nom_fr", "tze_nom_en"));
        put("tze_definition_fr", I18nExampleBuilder.buildI18n("tze_definition_fr", "tze_definition_en"));
    }});
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
                new LinkedList<>() {{
                    add(new CollectionType.MapType<>(new HashMap<>() {{
                        put("%s_profondeur".formatted(prefix), PatternComponentQualifierExampleBuilder.PROFONDEUR);
                    }}, false, false, PatternComponentQualifierType.EMPTY_INSTANCE()
                    ));
                    add(new CollectionType.MapType<>(new HashMap<>() {{
                        put("%s_repetition".formatted(prefix), PatternComponentQualifierExampleBuilder.REPETITION);
                    }}, false, false, PatternComponentQualifierType.EMPTY_INSTANCE()
                    ));
                }},
                false, false, CollectionType.MapType.PATTERN_COMPONENT_QUALIFIER_EMPTY_INSTANCE());
    }

    protected static CollectionType.ArrayType<CollectionType.MapType<PatternComponentAdjacentType>> COMPONENT_ADJACENTS(final String prefix) {
        return new CollectionType.ArrayType<>(
                new LinkedList<>() {{
                    add(new CollectionType.MapType<>(new HashMap<>() {{
                        put("%s_sd".formatted(prefix), PatternComponentAdjacentExampleBuilder.STANDARD_DEVIATION);
                    }}, false, false, PatternComponentAdjacentType.EMPTY_INSTANCE()
                    ));
                    add(new CollectionType.MapType<>(new HashMap<>() {{
                        put("%s_qc".formatted(prefix), PatternComponentAdjacentExampleBuilder.QUALITY_CLASS);
                    }}, false, false, PatternComponentAdjacentType.EMPTY_INSTANCE()
                    ));
                }},
                false, false, CollectionType.MapType.PATTERN_COMPONENT_ADJACENT_EMPTY_INSTANCE());
    }
}