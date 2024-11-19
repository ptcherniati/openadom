package fr.inra.oresing.domain.data.read.query;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.*;

public record IntervalValuesTime(
        String from,
        String to,
        String format
) implements WithFormatForIntervalDate {


    public IntervalValuesTime {
        LocalTime fromDate = null;
        LocalTime toDate = null;
        if (Strings.isNullOrEmpty(format)) {
            throw new BadDownloadDatasetQuery(MISSING_FORMAT_FOR_INTERVAL_VALUE);
        }
        if (from != null) {
            try {
                fromDate = LocalTime.from(DateTimeFormatter.ofPattern(format).parse(from));
            } catch (final DateTimeParseException e) {
                throw new BadDownloadDatasetQuery(FILTER_BAD_FORMAT_FOR_START_TIME);
            }
        }
        if (to != null) {
            try {
                toDate = LocalTime.from(DateTimeFormatter.ofPattern(format).parse(to));
            } catch (final DateTimeParseException e) {
                throw new BadDownloadDatasetQuery(FILTER_BAD_FORMAT_FOR_END_TIME);
            }
        }
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BadDownloadDatasetQuery(FILTER_BAD_FORMAT_BAD_RANGE_FOR_TIMES, Map.of("from", from, "to", to));
        }
    }

}
