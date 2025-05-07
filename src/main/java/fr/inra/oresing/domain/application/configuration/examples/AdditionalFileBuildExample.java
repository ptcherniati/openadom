package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.LinkedHashMap;

class AdditionalFileBuildExample {
    protected static final AdditionalFileType FIRST = buildAdditionalFileSchema(
            TitleExampleBuilder.buildTitle(
                    I18nExampleBuilder.buildI18n("Fichier", "File"),
                    I18nExampleBuilder.buildI18n("Fichier à joindre à l'application", "File to join to application")
            )
    );
    protected static final AdditionalFileType SECOND = buildAdditionalFileSchema(
            TitleExampleBuilder.buildTitle(
                    I18nExampleBuilder.buildI18n("Données brutes", "Initial data"),
                    I18nExampleBuilder.buildI18n("Données brutes à traiter", "Initial data to analyse")
            )
    );

    protected static AdditionalFileType buildAdditionalFileSchema(final TitleType title) {
        return new AdditionalFileType(
                getChildren(title)
        );
    }

    private static LinkedHashMap<String, ConfigurationSchemaNodeType> getChildren(TitleType title) {

        return createChildrenMap(title);
    }

    private static LinkedHashMap<String, ConfigurationSchemaNodeType> createChildrenMap(TitleType title) {
        LinkedHashMap<String, ConfigurationSchemaNodeType> map = new LinkedHashMap<>();
        map.put(ConfigurationSchemaNode.OA_I_18_N, title);
        map.put(ConfigurationSchemaNode.OA_FORM_FIELDS, createFormFieldsMap());
        return map;
    }

    private static CollectionType.MapType<FormatType> createFormFieldsMap() {
        LinkedHashMap<String, FormatType> formFields = new LinkedHashMap<>();
        formFields.put("nom", FormatExampleBuilder.NOM);
        formFields.put("projet", FormatExampleBuilder.PROJET);
        return new CollectionType.MapType<>(formFields, false, false, FormatType.EMPTY_INSTANCE());
    }

}