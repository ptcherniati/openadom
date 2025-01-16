package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

import static fr.inra.oresing.domain.application.configuration.examples.I18nExampleBuilder.*;

class ValidationExampleBuilder {
        protected static final ValidationType TYPE_SITE = buildValidation(
            buildI18n("Validation du type de sites", "Site type validation"),
                true,
    ReferenceCheckerExampleBuilder.TYPE_DE_SITES,
            List.of("dat_type_site")
            );
        protected static final ValidationType SITE = buildValidation(
            buildI18n("Validation du site", "Site validation"),
                true,
    ReferenceCheckerExampleBuilder.SITE,
            List.of("dat_site")
            );
        protected static final ValidationType START_DATE = buildValidation(
            buildI18n("Validation de la borne inférieure de date", "Min date validation"),
                false,
    DateCheckerExampleBuilder.DDMMYYYY2,
            List.of("dat_start_date")
            );
        protected static final ValidationType END_DATE = buildValidation(
            buildI18n("Validation de la borne supérieure de date", "Max date validation"),
                false,
    DateCheckerExampleBuilder.DDMMYYYY2,
            List.of("dat_end_date")
            );
        protected static final ValidationType DATE = buildValidation(
            buildI18n("Validation de la date", "Date validation"),
                true,
    DateCheckerExampleBuilder.DDMMYYYY2,
            List.of("dat_end_date")
            );
        protected static final ValidationType INTERVAL_DATE = buildValidation(
            buildI18n("Vérifie que la date est comprise dans l'interval", "Checks that the date is included in the interval"),
                true,
    GroovyCheckerExampleBuilder.INTERVAL_DATE,
            List.of("dat_end_date")
            );

    protected ValidationExampleBuilder() {
        super();
    }

    static ValidationType buildValidation(final I18nType i18n, final boolean required, final CheckerType checker, final List<String> columns) {
        LinkedHashMap<String, ConfigurationSchemaNodeType> map = new LinkedHashMap<>();
        map.put(ConfigurationSchemaNode.OA_I_18_N, i18n);
        map.put(ConfigurationSchemaNode.OA_REQUIRED, new BooleanType(required));
        map.put(ConfigurationSchemaNode.OA_CHECKER, checker);

        LinkedList<StringType> columnTypes = new LinkedList<>();
        for (String column : columns) {
            columnTypes.add(new StringType(column));
        }
        map.put(ConfigurationSchemaNode.OA_COMPONENTS, new CollectionType.ArrayType<>(columnTypes, false, false, StringType.EMPTY_INSTANCE()));

        return new ValidationType(map);
    }
}