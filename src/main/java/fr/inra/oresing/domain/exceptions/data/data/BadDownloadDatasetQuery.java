package fr.inra.oresing.domain.exceptions.data.data;

import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import lombok.Getter;

import java.io.Serializable;
import java.util.Map;

@Getter
public class BadDownloadDatasetQuery extends OreSiTechnicalException {
    public static final String MISSING_INTERVAL_VALUE = "componentFilter.missingIntervalValue";
    public static final String MISSING_TYPE_FOR_INTERVAL_VALUE = "componentFilter.missingTypeForIntervalValue";
    public static final String MISSING_FORMAT_FOR_INTERVAL_VALUE = "componentFilter.missingFormatForIntervalValue";
    public static final String MISSING_FORMAT_FOR_FILTER = "componentFilter.missingFormatForFilter";
    public static final String MISSING_FILTER = "componentFilter.missingFilter";
    public static final String MISSING_ID_FOR_UUID = "componentFilter.missingIdForUUID";
    public static final String MISSING_COMPONENT_KEY_FOR_SEARCH = "componentFilter.missingComponentKeyForSearch";
    public static final String MISSING_COMPONENT_KEY_COMPONENT = "componentFilter.missingComponentKeyComponent";
    public static final String FILTER_BAD_FORMAT_FOR_START_DATE = "componentFilter.badFormatForStartDate";
    public static final String FILTER_BAD_FORMAT_FOR_END_DATE = "componentFilter.badFormatForEndDate";
    public static final String FILTER_BAD_FORMAT_BAD_RANGE_FOR_DATES = "componentFilter.badrangeForDates";
    public static final String FILTER_BAD_FORMAT_FOR_START_TIME = "componentFilter.badFormatForStartTime";
    public static final String FILTER_BAD_FORMAT_FOR_END_TIME = "componentFilter.badFormatForEndTime";
    public static final String FILTER_BAD_FORMAT_BAD_RANGE_FOR_TIMES = "componentFilter.badrangeForTimes";
    public static final String FILTER_BAD_FORMAT_FOR_START_DATE_TIME = "componentFilter.badFormatForStartDateTime";
    public static final String FILTER_BAD_FORMAT_FOR_END_DATE_TIME = "componentFilter.badFormatForEndDateTime";
    public static final String FILTER_BAD_FORMAT_BAD_RANGE_FOR_DATE_TIMES = "componentFilter.badrangeForDateTimes";
    public static final String FILTER_BAD_FORMAT_FOR_START_NUMERIC = "componentFilter.badFormatForStartNumeric";
    public static final String FILTER_BAD_FORMAT_FOR_END_NUMERIC = "componentFilter.badFormatForEndNumeric";
    public static final String FILTER_BAD_FORMAT_BAD_RANGE_FOR_NUMERICS = "componentFilter.badrangeForNumerics";
    public static final String FILTER_MISSING_FILTER_OR_INTERVAL = "componentFilter.missingFilterOrInterval";
    public static final String NOT_INTERVAL_VALUE_TYPE_FOR_COMPONENT = "componentFilter.notIntervalValueTypeForcomponent";


    Map<String, Serializable> params;

    public BadDownloadDatasetQuery(final String message) {
        super(message);
    }

    public BadDownloadDatasetQuery(final String message, final Map<String, Serializable> params) {
        super(message);
        this.params = params;
    }
}