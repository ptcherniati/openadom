package fr.inra.oresing.domain.data.read.query;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.*;

public record IntervalValuesDateTime(
        String from,
        String to,
        String format
) implements WithFormatForIntervalDate {

    public IntervalValuesDateTime {
        LocalDateTime fromDate = null;
        LocalDateTime toDate = null;
        if (Strings.isNullOrEmpty(format)) {
            throw new BadDownloadDatasetQuery(MISSING_FORMAT_FOR_INTERVAL_VALUE);
        }
        if (from != null) {
            try {
                fromDate = LocalDateTime.from(DateTimeFormatter.ofPattern(format).parse(from));
            } catch (final DateTimeParseException e) {
                throw new BadDownloadDatasetQuery(FILTER_BAD_FORMAT_FOR_START_DATE_TIME);
            }
        }
        if (to != null) {
            try {
                toDate = LocalDateTime.from(DateTimeFormatter.ofPattern(format).parse(to));
            } catch (final DateTimeParseException e) {
                throw new BadDownloadDatasetQuery(FILTER_BAD_FORMAT_FOR_END_DATE_TIME);
            }
        }
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BadDownloadDatasetQuery(FILTER_BAD_FORMAT_BAD_RANGE_FOR_DATE_TIMES, Map.of("from", from, "to", to));
        }
    }

}
