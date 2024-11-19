package fr.inra.oresing.domain.data.read.query;


import fr.inra.oresing.domain.application.Application;

import java.util.*;

public record DownloadDatasetQueryAdvancedSearch(
        boolean hasPatternDefinition,
        Application application,
        String dataName,
        OutPut outPut,
        Set<String> componentSelects,
        Set<ComponentFilters> componentFilters,
        Set<ComponentOrderBy> componentOrderBy
) implements DownloadDatasetQuery {

    public enum FieldType {
        date, time, datetime, numeric, bool;

        public static Number convertToNumber(final String numericString) {
            try {
                return Float.valueOf(numericString);
            } catch (final NumberFormatException nfe) {
                return null;
            }
        }

        public static boolean convertToBoolean(final String booleanString) {
            return Boolean.getBoolean(booleanString);
        }
    }
}