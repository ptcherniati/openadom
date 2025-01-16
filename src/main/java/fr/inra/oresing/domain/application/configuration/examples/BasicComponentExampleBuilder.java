package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;
import org.apache.commons.collections4.CollectionUtils;
import static fr.inra.oresing.domain.application.configuration.examples.StringExampleBuilder.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

class  BasicComponentExampleBuilder {
        public static final BasicComponentType SITES_KEY = buildBasicComponents(null, BooleanExampleBuilder.FALSE, NOM_CODIQUE_DU_SITE, null, null, null, I18nExampleBuilder.LANG_RESTRICTION_FR_EN);
        public static final BasicComponentType SITES_FR = buildBasicComponents(null, BooleanExampleBuilder.FALSE, FRENCH_SITE_NAME, TitleExampleBuilder.SITES_NOM_FR, null, null, I18nExampleBuilder.LANG_RESTRICTION_FR);
        public static final BasicComponentType SITES_EN = buildBasicComponents(null, BooleanExampleBuilder.FALSE, ENGLISH_SITE_NAME, TitleExampleBuilder.SITES_NOM_EN, null, null, I18nExampleBuilder.LANG_RESTRICTION_EN);
        public static final BasicComponentType SITES_DEFINITION_FR = buildBasicComponents(null, BooleanExampleBuilder.FALSE, FRENCH_SITE_DESCRIPTION, TitleExampleBuilder.TYPE_SITES_DEFINITION_FR, null, null, I18nExampleBuilder.LANG_RESTRICTION_FR);
        public static final BasicComponentType SITES_DEFINITION_EN = buildBasicComponents(null, BooleanExampleBuilder.FALSE, ENGLISH_SITE_DESCRIPTION, TitleExampleBuilder.SITES_DEFINITION_EN, null, null, I18nExampleBuilder.LANG_RESTRICTION_EN);
        public static final BasicComponentType SITES_PARENT = buildBasicComponents(null, BooleanExampleBuilder.FALSE, NOM_DU_SITE_PARENT, TitleExampleBuilder.SITES_PARENT, ReferenceCheckerExampleBuilder.SITE, null, I18nExampleBuilder.LANG_RESTRICTION_EN);
        public static final BasicComponentType TYPE_DE_SITES_KEY = buildBasicComponents(null, BooleanExampleBuilder.FALSE, TYPE_SITE_CODIC_NAME, null, null, null, I18nExampleBuilder.LANG_RESTRICTION_FR_EN);
        public static final BasicComponentType TYPE_DE_SITES_FR = buildBasicComponents(null, BooleanExampleBuilder.FALSE, FRENCH_TYPE_SITE_NAME, TitleExampleBuilder.TYPE_SITES_NOM_FR, null, null, I18nExampleBuilder.LANG_RESTRICTION_FR);
        public static final BasicComponentType TYPE_DE_SITES_EN = buildBasicComponents(null, BooleanExampleBuilder.FALSE, ENGLISH_TYPE_SITE_NAME, TitleExampleBuilder.TYPE_SITES_NOM_EN, null, null, I18nExampleBuilder.LANG_RESTRICTION_EN);
        public static final BasicComponentType TYPE_DE_SITES_DEFINITION_FR = buildBasicComponents(null, BooleanExampleBuilder.FALSE, FRENCH_TYPE_SITE_DESCRIPTION, TitleExampleBuilder.TYPE_SITES_DEFINITION_FR, null, null, I18nExampleBuilder.LANG_RESTRICTION_FR);
        public static final BasicComponentType TYPE_DE_SITES_DEFINITION_EN = buildBasicComponents(null, BooleanExampleBuilder.FALSE, ENGLISH_TYPE_SITE_DESCRIPTION, TitleExampleBuilder.TYPE_SITES_DEFINITION_EN, null, null, I18nExampleBuilder.LANG_RESTRICTION_EN);
        public static final BasicComponentType PROJET_KEY = buildBasicComponents(null, BooleanExampleBuilder.FALSE, NOM_CODIQUE_DU_PROJET, null, null, null, I18nExampleBuilder.LANG_RESTRICTION_FR_EN);
        public static final BasicComponentType PROJET_FR = buildBasicComponents(null, BooleanExampleBuilder.FALSE, NOM_DU_PROJET_EN_FRANCAIS, TitleExampleBuilder.PROJET_NOM_FR, null, null, I18nExampleBuilder.LANG_RESTRICTION_FR);
        public static final BasicComponentType PROJET_EN = buildBasicComponents(null, BooleanExampleBuilder.FALSE, ENGLISH_PROJECT_NAME, TitleExampleBuilder.PROJET_NOM_EN, null, null, I18nExampleBuilder.LANG_RESTRICTION_EN);
        public static final BasicComponentType PROJET_DEFINITION_FR = buildBasicComponents(null, BooleanExampleBuilder.FALSE, DEFINITION_DU_PROJET_EN_FRANCAIS, TitleExampleBuilder.PROJET_DEFINITION_FR, null, null, I18nExampleBuilder.LANG_RESTRICTION_FR);
        public static final BasicComponentType PROJET_DEFINITION_EN = buildBasicComponents(null, BooleanExampleBuilder.FALSE, ENGLISH_PROJECT_DEFINITION, TitleExampleBuilder.PROJET_DEFINITION_EN, null, null, I18nExampleBuilder.LANG_RESTRICTION_EN);
        protected static final BasicComponentType ESPECES_DEFINITION_FR = buildBasicComponents(null, BooleanExampleBuilder.FALSE, SPECIES_DEFINITION_FR, null, null, null, I18nExampleBuilder.LANG_RESTRICTION_FR);
        protected static final BasicComponentType ESPECES_DEFINITION_EN = buildBasicComponents(null, BooleanExampleBuilder.FALSE, ENGLISH_SPECIES_DEFINITION, null, null, null, I18nExampleBuilder.LANG_RESTRICTION_EN);
    protected static final BasicComponentType ESPECES = buildBasicComponents(null, BooleanExampleBuilder.TRUE, ESPECE, TitleExampleBuilder.ESPECE, StringCheckerExampleBuilder.ESPECE, null, null);
    protected static final BasicComponentType TYPE_DE_SITES = buildBasicComponents(null, BooleanExampleBuilder.TRUE, TYPE_DE_SITE, TitleExampleBuilder.TYPE_DE_SITES, ReferenceCheckerExampleBuilder.TYPE_DE_SITES, null, null);
    protected static final BasicComponentType DATE_START = buildBasicComponents(null, BooleanExampleBuilder.TRUE, DATE, TitleExampleBuilder.START_DATE, DateCheckerExampleBuilder.DDMMYYYY, null, null);
    protected static final BasicComponentType HEURE = buildBasicComponents(null, BooleanExampleBuilder.TRUE, StringExampleBuilder.HEURE, TitleExampleBuilder.HEURE, DateCheckerExampleBuilder.HHMMSS, null, null), MASSE = buildBasicComponents(null, BooleanExampleBuilder.TRUE, QUANTITE, TitleExampleBuilder.MASSE, FloatCheckerExampleBuilder.OF, DefaultValueType.FLOAT_0, null);
    protected static final BasicComponentType OUTIL = buildBasicComponents(null, BooleanExampleBuilder.TRUE, StringExampleBuilder.OUTIL, TitleExampleBuilder.OUTIL, GroovyCheckerExampleBuilder.T_11, null, null);
    protected static final BasicComponentType PROPRIETE = buildBasicComponents(null, BooleanExampleBuilder.TRUE, StringExampleBuilder.PROPRIETE, null, null, null, null);
    protected static final BasicComponentType TAXON_NOM = buildBasicComponents(null, BooleanExampleBuilder.TRUE, StringExampleBuilder.TAXON_NOM, null, null, null, null);
    protected static final BasicComponentType SITE = buildBasicComponents(null, BooleanExampleBuilder.TRUE, StringExampleBuilder.SITE, TitleExampleBuilder.SITE, ReferenceCheckerExampleBuilder.SITE, null, null);
    protected static final BasicComponentType IS_ISO = buildBasicComponents(List.of(HIDDEN), BooleanExampleBuilder.TRUE, new StringType("iso"), null, BooleanCheckerExampleBuilder.BOOLEAN_CHECKER_TYPE, null, null);
    protected static final BasicComponentType REPETITION = buildBasicComponents(null, BooleanExampleBuilder.FALSE, StringExampleBuilder.REPETITION, TitleExampleBuilder.REPETITION, IntegerCheckerExampleBuilder.ZERO_DIX, null, null);
    protected static final BasicComponentType CHEMIN_PARENT = buildBasicComponents(null, BooleanExampleBuilder.FALSE, SITE_PARENT, TitleExampleBuilder.SITE_PARENT, ReferenceCheckerExampleBuilder.SITE, null, null);


    protected static BasicComponentType buildBasicComponents(final List<StringType> tags,
                                                             final BooleanType required,
                                                             final StringType importHeader,
                                                             final TitleType exportHeader,
                                                             final CheckerType checker,
                                                             DefaultValueType defaultValue,
                                                             CollectionType.ArrayType<StringType> langRestriction
    ) {
        final Map<String, ConfigurationSchemaNodeType> children = new HashMap<>();
        children.put(ConfigurationSchemaNode.OA_REQUIRED, required);
        if (importHeader != null) children.put(ConfigurationSchemaNode.OA_IMPORT_HEADER, importHeader);
        if (CollectionUtils.isNotEmpty(tags)) {
            children.put(ConfigurationSchemaNode.OA_TAGS, new CollectionType.ArrayType<>(tags, false, false, StringType.EMPTY_INSTANCE()));
        }
        if (defaultValue != null) {
            children.put(ConfigurationSchemaNode.OA_DEFAULT_VALUE, defaultValue);
        }
        if (exportHeader != null) {
            children.put(ConfigurationSchemaNode.OA_EXPORT_HEADER, exportHeader);
        }
        if (checker != null) {
            children.put(ConfigurationSchemaNode.OA_CHECKER, checker);
        }
        if (langRestriction != null) {
            children.put(ConfigurationSchemaNode.OA_LANG_RESTRICTIONS, langRestriction);
        }
        return new BasicComponentType(children);
    }
}