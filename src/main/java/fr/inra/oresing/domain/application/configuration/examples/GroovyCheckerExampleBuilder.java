package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;
import fr.inra.oresing.domain.checker.Multiplicity;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

class GroovyCheckerExampleBuilder {
    protected static final GroovyCheckerType T_11;

    static {
        LinkedHashMap<String, I18nType> exceptionMessages = new LinkedHashMap<>();
        Map<String, String> translations = new LinkedHashMap<>();
        translations.put("fr", "la valeur ${value} doit être l'une des valeurs de ${values}");
        translations.put("en", "value ${value} must be in ${values}");
        exceptionMessages.put("BAD_VALUE", new I18nType(translations));

        T_11 = buildGroovyChecker("""
                        import fr.inra.oresing.domain.groovy.exception.GroovyException;
                        List<String> values = ["T_11", "T_12", "U_13", "U_14"];
                        if(values.contains(value)){
                            return true;
                        };
                        throw new GroovyException(
                                                "BAD_VALUE",
                                                java.util.Map.of("valeur", value, "valeurs",values)
                        
                                        );""",
                exceptionMessages,
                Multiplicity.ONE
        );
    }

    protected static final CheckerType INTERVAL_DATE;

    static {
        LinkedHashMap<String, I18nType> exceptionMessages = new LinkedHashMap<>();

        Map<String, String> missingDateMessages = new LinkedHashMap<>();
        missingDateMessages.put("fr", "la date est manquante");
        missingDateMessages.put("en", "missing date");
        exceptionMessages.put("MISSING_DATE", new I18nType(missingDateMessages));

        Map<String, String> dateNotInIntervalMessages = new LinkedHashMap<>();
        dateNotInIntervalMessages.put("fr", "la date ${date} n'est pas dans l'intervale de dates [${dateDebut},${dateFin}]");
        dateNotInIntervalMessages.put("en", "the date ${date} is not in date intervale [${dateDebut},${dateFin}]");
        exceptionMessages.put("DATE_NOT_IN_INTERVAL", new I18nType(dateNotInIntervalMessages));

        Map<String, String> badDateFormatMessages = new LinkedHashMap<>();
        badDateFormatMessages.put("fr", "la date ${date} n'est pas au format ${format}");
        badDateFormatMessages.put("en", "the date ${date} is not in format ${format}");
        exceptionMessages.put("BAD_DATE_FORMAT", new I18nType(badDateFormatMessages));

        INTERVAL_DATE = buildGroovyChecker("""
                        if (datum.data_dat == null) {
                                                        throw new fr.inra.oresing.domain.groovy.exception.GroovyException(
                                                                "MISSING_DATE"
                                                        );
                                                    };
                                                    java.time.LocalDate date = java.time.LocalDate.parse(datum.data_dat, DateTimeFormatter.ofPattern("dd/MM/yyyy")); if (datum.start_date_dat != null) {
                                                        try {
                                                            java.time.LocalDate startDate = java.time.LocalDate.parse(datum.start_date_dat, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                                                            if (startDate.isAfter(date)) {
                                                                throw new fr.inra.oresing.domain.groovy.exception.GroovyException(
                                                                        "DATE_NOT_IN_INTERVAL",
                                                                        java.util.Map.of(
                                                                                "date", date,
                                                                                "dateDebut", datum.start_date_dat,
                                                                                "dateFin", datum.end_date_dat
                                                                        )
                                                                )
                                                            }
                                                        } catch (java.time.format.DateTimeParseException e) {
                                                            throw new fr.inra.oresing.domain.groovy.exception.GroovyException(
                                                                    "BAD_DATE_FORMAT",
                                                                    java.util.Map.of(
                                                                            "date", datum.start_date_dat
                                                                    )
                                                            )
                                                        }
                                                    };
                                                    if (datum.end_date_dat != null) {
                                                        try {
                                                            java.time.LocalDate endDate = java.time.LocalDate.parse(datum.end_date_dat, DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                                                            if (endDate.isBefore(date)) {
                                                                throw new fr.inra.oresing.domain.groovy.exception.GroovyException(
                                                                        "DATE_NOT_IN_INTERVAL",
                                                                        java.util.Map.of(
                                                                                "date", date,
                                                                                "dateDebut", datum.start_date_dat,
                                                                                "dateFin", datum.end_date_dat
                                                                        )
                                                                )
                                                            }
                                                        } catch (java.time.format.DateTimeParseException e) {
                                                            throw new fr.inra.oresing.domain.groovy.exception.GroovyException(
                                                                    "BAD_DATE_FORMAT",
                                                                    java.util.Map.of(
                                                                            "date", datum.end_date_dat
                                                                    )
                                                            )
                                                        }
                                                    };
                                                    return true;""",
                exceptionMessages,
                Multiplicity.ONE
        );
    }

    protected static GroovyCheckerType buildGroovyChecker(final String expression, Map<String, I18nType> exceptionMessages, final Multiplicity multiplicity) {
        final Map<String, ConfigurationSchemaNodeType> children = new HashMap<>();
        final HashMap<String, ConfigurationSchemaNodeType> params = new HashMap<>();
        final EnumType oaMultiplicity = EnumExampleBuilder.buildMultiplicityType(multiplicity);
        params.put(ConfigurationSchemaNode.OA_MULTIPLICITY, oaMultiplicity);
        params.put(ConfigurationSchemaNode.OA_GROOVY, new GroovyExpressionType(Map.of(ConfigurationSchemaNode.OA_EXPRESSION, new StringType(expression), ConfigurationSchemaNode.OA_GROOVY_EXCEPTIONS, new CollectionType.MapType<>(exceptionMessages, false, false, I18nType.EMPTY_INSTANCE()))));
        children.put(ConfigurationSchemaNode.OA_PARAMS, new GroovyCheckerParamsType(params));
        return new GroovyCheckerType(children);
    }
}