package fr.inra.oresing.domain.data.read.query;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.*;

public record IntervalValuesDate(
        String from,
        String to,
        String format
) implements WithFormatForIntervalDate {

    public IntervalValuesDate {
        LocalDate fromDate = null;
        LocalDate toDate = null;
        if (Strings.isNullOrEmpty(format)) {
            throw new BadDownloadDatasetQuery(MISSING_FORMAT_FOR_INTERVAL_VALUE);
        }
        if (from != null) {
            try {
                if (from.matches("[0-9]*")) {
                    ZoneId zone = ZoneId.of("UTC");
                    fromDate = LocalDate.ofInstant(Instant.ofEpochMilli(Long.valueOf(from)), zone);
                    from = fromDate.format(DateTimeFormatter.ofPattern(format));
                } else {
                    fromDate = LocalDate.from(DateTimeFormatter.ofPattern(format).parse(from));
                }
            } catch (final DateTimeParseException e) {
                throw new BadDownloadDatasetQuery(FILTER_BAD_FORMAT_FOR_START_DATE);
            }
        }
        if (to != null) {
            try {
                if (to.matches("[0-9]*")) {
                    ZoneId zone = ZoneId.of("UTC");
                    toDate = LocalDate.ofInstant(Instant.ofEpochMilli(Long.valueOf(to)), zone);
                    to = toDate.format(DateTimeFormatter.ofPattern(format));
                } else {
                    toDate = LocalDate.from(DateTimeFormatter.ofPattern(format).parse(to));
                }
            } catch (final DateTimeParseException e) {
                throw new BadDownloadDatasetQuery(FILTER_BAD_FORMAT_FOR_END_DATE);
            }
        }
        if (fromDate != null && toDate != null && toDate.isBefore(fromDate)) {
            throw new BadDownloadDatasetQuery(FILTER_BAD_FORMAT_BAD_RANGE_FOR_DATES, Map.of("from", from, "to", to));
        }
    }

}
