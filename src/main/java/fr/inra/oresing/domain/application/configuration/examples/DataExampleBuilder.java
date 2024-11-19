package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.LinkedHashMap;
import java.util.List;

class DataExampleBuilder {

    protected static final DataType ESPECE = buildDataSchemas(
            1, 2, List.of("spe_species"),
            TitleExampleBuilder.ESPECE,
            List.of("data"),
            CollectionExampleBuilder.ESPECE_DISPLAY,
            CollectionExampleBuilder.ESPECE_BASIC_COMPONENTS,
            CollectionExampleBuilder.ESPECE_COMPUTED_COMPONENTS,
            null,
            null,
            null,
            null,
            null,
            null);
    protected static final DataType PROJET = buildDataSchemas(
            1, 2, List.of("pro_nom_key"),
            TitleExampleBuilder.PROJET,
            List.of("context", "data"),
            CollectionExampleBuilder.PROJET_DISPLAY,
            CollectionExampleBuilder.PROJET_BASIC_COMPONENTS,
            null,
            null,
            null,
            null,
            null,
            null, null);
    protected static final DataType ZONE_ETUDE = buildDataSchemas(
            1, 2, List.of("zet_chemin_parent", "zet_nom_key"),
            TitleExampleBuilder.SITE,
            List.of("context", "data"),
            CollectionExampleBuilder.NOM_ZET_DISPLAY,
            CollectionExampleBuilder.SITES_BASIC_COMPONENTS,
            CollectionExampleBuilder.SITES_COMPUTED_COMPONENTS,
            null,
            null,
            null,
            null,
            null, null);
    protected static final DataType TYPE_DE_SITES = buildDataSchemas(
            1, 2, List.of("tze_nom_key"),
            TitleExampleBuilder.TYPE_DE_SITE,
            List.of("context"),
            CollectionExampleBuilder.NOM_TZE_DISPLAY,
            CollectionExampleBuilder.TYPE_DE_SITES_BASIC_COMPONENTS,
            null,
            null,
            null,
            null,
            null,
            null, null);
    protected static final DataType PROPRIETE_TAXON = buildDataSchemas(
            1, 2, List.of("ptx_propriete"),
            TitleExampleBuilder.PROPRIETE_TAXON,
            List.of("context"),
            CollectionExampleBuilder.PROPRIETE_TAXON_DISPLAY,
            CollectionExampleBuilder.PROPRIETE_TAXON_BASIC_COMPONENTS,
            null,
            null,
            null,
            null,
            null,
            null, null);
    protected static final DataType TAXON = buildDataSchemas(
            1, 2, List.of("tax_taxon"),
            TitleExampleBuilder.TAXON,
            List.of("context"),
            CollectionExampleBuilder.TAXON_DISPLAY,
            CollectionExampleBuilder.TAXON_BASIC_COMPONENTS,
            null,
            CollectionExampleBuilder.TAXON_DYNAMIC_COMPONENTS,
            null,
            null,
            null,
            null, null);

    protected static final DataType DATA = buildDataSchemas(
            4,
            7,
            List.of("dat_date"),
            TitleExampleBuilder.DATA,
            List.of("context", "\"__DATA__\""),
            null,
            CollectionExampleBuilder.DATA_BASIC_COMPONENTS,
            CollectionExampleBuilder.DATA_COMPUTED_COMPONENTS,
            null,

            CollectionExampleBuilder.DATA_PATTERN_COMPONENTS,
            CollectionExampleBuilder.DATA_CONSTANT_COMPONENTS,
            CollectionExampleBuilder.DATA_VALIDATIONS,
            SumissionTypeExampleBuilder.DATA_SUBMISSION,
            AuthorizationExampleBuilder.DATA_AUTHORIZATION);

    protected static DataType buildDataSchemas(
            final int headerLine,
            final int firstRowLine,
            final List<String> naturalKey,
            final TitleType title,
            final List<String> tags,
            final TitleType displayPattern,
            final CollectionType.MapType<BasicComponentType> basicComponents,
            final CollectionType.MapType<ComputedComponentType> computedComponents,
            final CollectionType.MapType<DynamicComponentType> dynamicComponents,
            final CollectionType.MapType<PatternComponentType> patternComponents,
            final CollectionType.MapType<ConstantComponentType> constantComponents,
            final CollectionType.MapType<ValidationType> validations,
            final SubmissionType submission,
            final AuthorizationType authorization) {
        return new DataType(new LinkedHashMap<String, ConfigurationSchemaNodeType>() {{
            put(ConfigurationSchemaNode.OA_HEADER_LINE, new IntegerType(headerLine));
            put(ConfigurationSchemaNode.OA_FIRST_ROW_LINE, new IntegerType(firstRowLine));
            put(ConfigurationSchemaNode.OA_NATURAL_KEY, new CollectionType.ArrayType<StringType>(naturalKey.stream().map(StringType::new).toList(), true, false, StringType.EMPTY_INSTANCE()));
            put(ConfigurationSchemaNode.OA_TAGS, new CollectionType.ArrayType<StringType>(TagExampleBuilder.buildTagArray(tags), false, false, StringType.EMPTY_INSTANCE()));
            put(ConfigurationSchemaNode.OA_I_18_N, title);
            put(ConfigurationSchemaNode.OA_I_18_N_DISPLAY_PATTERN, displayPattern);
            put(ConfigurationSchemaNode.OA_BASIC_COMPONENTS, basicComponents);
            put(ConfigurationSchemaNode.OA_COMPUTED_COMPONENTS, computedComponents);
            put(ConfigurationSchemaNode.OA_DYNAMIC_COMPONENTS, dynamicComponents);
            put(ConfigurationSchemaNode.OA_PATTERN_COMPONENTS, patternComponents);
            put(ConfigurationSchemaNode.OA_CONSTANT_COMPONENTS, constantComponents);
            put(ConfigurationSchemaNode.OA_VALIDATIONS, validations);
            put(ConfigurationSchemaNode.OA_SUBMISSION, submission);
            put(ConfigurationSchemaNode.OA_AUTHORIZATION, authorization);
        }});
    }

    public static CollectionType.MapType<DataType> buildDataType() {
        return new CollectionType.MapType<>(
                new LinkedHashMap<>() {{
                    put("tr_espece_spe", ESPECE);
                    put("tr_projet_pro", PROJET);
                    put("tr_type_zone_etude_tze", TYPE_DE_SITES);
                    put("tr_zone_etude_zet", ZONE_ETUDE);
                    put("tr_propriete_taxon_ptx", PROPRIETE_TAXON);
                    put("tr_taxon_tax", TAXON);
                    put("t_data_dat", DATA);

                }},
                false, false, DataType.EMPTY_INSTANCE()
        );
    }
}