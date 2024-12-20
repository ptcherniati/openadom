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
                new LinkedHashMap<>() {{
                    put(ConfigurationSchemaNode.OA_I_18_N, title);
                    put(ConfigurationSchemaNode.OA_FORM_FIELDS, new CollectionType.MapType<>(
                            new LinkedHashMap<>() {{
                                put("nom", FormatExampleBuilder.NOM);
                                put("projet", FormatExampleBuilder.PROJET);
                            }},
                            false,
                            false,
                            FormatType.EMPTY_INSTANCE()));
                }}
        );
    }
}