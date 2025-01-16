package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class DataExampleBuilder {

    protected static final DataType ESPECE = buildDataSchemas(1, 2, List.of("spe_species"), TitleExampleBuilder.ESPECE, List.of("data"), CollectionExampleBuilder.ESPECE_DISPLAY, CollectionExampleBuilder.ESPECE_BASIC_COMPONENTS, CollectionExampleBuilder.ESPECE_COMPUTED_COMPONENTS, null, null, null, null, null, null);
    protected static final DataType PROJET = buildDataSchemas(1, 2, List.of("pro_nom_key"), TitleExampleBuilder.PROJET, List.of("context", "data"), CollectionExampleBuilder.PROJET_DISPLAY, CollectionExampleBuilder.PROJET_BASIC_COMPONENTS, null, null, null, null, null, null, null);
    protected static final DataType ZONE_ETUDE = buildDataSchemas(1, 2, List.of("zet_chemin_parent", "zet_nom_key"), TitleExampleBuilder.SITE, List.of("context", "data"), CollectionExampleBuilder.NOM_ZET_DISPLAY, CollectionExampleBuilder.SITES_BASIC_COMPONENTS, CollectionExampleBuilder.SITES_COMPUTED_COMPONENTS, null, null, null, null, null, null);
    protected static final DataType TYPE_DE_SITES = buildDataSchemas(1, 2, List.of("tze_nom_key"), TitleExampleBuilder.TYPE_DE_SITE, List.of("context"), CollectionExampleBuilder.NOM_TZE_DISPLAY, CollectionExampleBuilder.TYPE_DE_SITES_BASIC_COMPONENTS, null, null, null, null, null, null, null);
    protected static final DataType PROPRIETE_TAXON = buildDataSchemas(1, 2, List.of("ptx_propriete"), TitleExampleBuilder.PROPRIETE_TAXON, List.of("context"), CollectionExampleBuilder.PROPRIETE_TAXON_DISPLAY, CollectionExampleBuilder.PROPRIETE_TAXON_BASIC_COMPONENTS, null, null, null, null, null, null, null);
    protected static final DataType TAXON = buildDataSchemas(1, 2, List.of("tax_taxon"), TitleExampleBuilder.TAXON, List.of("context"), CollectionExampleBuilder.TAXON_DISPLAY, CollectionExampleBuilder.TAXON_BASIC_COMPONENTS, null, CollectionExampleBuilder.TAXON_DYNAMIC_COMPONENTS, null, null, null, null, null);

    protected static final DataType DATA = buildDataSchemas(4, 7, List.of("dat_date"), TitleExampleBuilder.DATA, List.of("context", "\"__DATA__\""), null, CollectionExampleBuilder.DATA_BASIC_COMPONENTS, CollectionExampleBuilder.DATA_COMPUTED_COMPONENTS, null,

            CollectionExampleBuilder.DATA_PATTERN_COMPONENTS, CollectionExampleBuilder.DATA_CONSTANT_COMPONENTS, CollectionExampleBuilder.DATA_VALIDATIONS, SumissionTypeExampleBuilder.DATA_SUBMISSION, AuthorizationExampleBuilder.DATA_AUTHORIZATION);

    protected static DataType buildDataSchemas(final int headerLine, final int firstRowLine, final List<String> naturalKey, final TitleType title, final List<String> tags, final TitleType displayPattern, final CollectionType.MapType<BasicComponentType> basicComponents, final CollectionType.MapType<ComputedComponentType> computedComponents, final CollectionType.MapType<DynamicComponentType> dynamicComponents, final CollectionType.MapType<PatternComponentType> patternComponents, final CollectionType.MapType<ConstantComponentType> constantComponents, final CollectionType.MapType<ValidationType> validations, final SubmissionType submission, final AuthorizationType authorization) {
        return new DataType(createDataSchemasMap(headerLine, firstRowLine, naturalKey, title, tags, displayPattern, basicComponents, computedComponents, dynamicComponents, patternComponents, constantComponents, validations, submission, authorization));
    }

    private static Map<String, ConfigurationSchemaNodeType> createDataSchemasMap(int headerLine, int firstRowLine, List<String> naturalKey, TitleType title, List<String> tags, TitleType displayPattern, CollectionType.MapType<BasicComponentType> basicComponents, CollectionType.MapType<ComputedComponentType> computedComponents, CollectionType.MapType<DynamicComponentType> dynamicComponents, CollectionType.MapType<PatternComponentType> patternComponents, CollectionType.MapType<ConstantComponentType> constantComponents, CollectionType.MapType<ValidationType> validations, SubmissionType submission, AuthorizationType authorization) {
        Map<String, ConfigurationSchemaNodeType> map = new LinkedHashMap<>();
        map.put(ConfigurationSchemaNode.OA_HEADER_LINE, new IntegerType(headerLine));
        map.put(ConfigurationSchemaNode.OA_FIRST_ROW_LINE, new IntegerType(firstRowLine));
        map.put(ConfigurationSchemaNode.OA_NATURAL_KEY, new CollectionType.ArrayType<>(naturalKey.stream().map(StringType::new).toList(), true, false, StringType.EMPTY_INSTANCE()));
        map.put(ConfigurationSchemaNode.OA_TAGS, new CollectionType.ArrayType<>(TagExampleBuilder.buildTagArray(tags), false, false, StringType.EMPTY_INSTANCE()));
        map.put(ConfigurationSchemaNode.OA_I_18_N, title);
        map.put(ConfigurationSchemaNode.OA_I_18_N_DISPLAY_PATTERN, displayPattern);
        map.put(ConfigurationSchemaNode.OA_BASIC_COMPONENTS, basicComponents);
        map.put(ConfigurationSchemaNode.OA_COMPUTED_COMPONENTS, computedComponents);
        map.put(ConfigurationSchemaNode.OA_DYNAMIC_COMPONENTS, dynamicComponents);
        map.put(ConfigurationSchemaNode.OA_PATTERN_COMPONENTS, patternComponents);
        map.put(ConfigurationSchemaNode.OA_CONSTANT_COMPONENTS, constantComponents);
        map.put(ConfigurationSchemaNode.OA_VALIDATIONS, validations);
        map.put(ConfigurationSchemaNode.OA_SUBMISSION, submission);
        map.put(ConfigurationSchemaNode.OA_AUTHORIZATION, authorization);
        return map;
    }


    public static CollectionType.MapType<DataType> buildDataType() {
        return new CollectionType.MapType<>(createDataTypeMap(), false, false, DataType.EMPTY_INSTANCE());
    }

    private static Map<String, DataType> createDataTypeMap() {
        Map<String, DataType> map = new LinkedHashMap<>();
        map.put("tr_espece_spe", ESPECE);
        map.put("tr_projet_pro", PROJET);
        map.put("tr_type_zone_etude_tze", TYPE_DE_SITES);
        map.put("tr_zone_etude_zet", ZONE_ETUDE);
        map.put("tr_propriete_taxon_ptx", PROPRIETE_TAXON);
        map.put("tr_taxon_tax", TAXON);
        map.put("t_data_dat", DATA);
        return map;
    }
}