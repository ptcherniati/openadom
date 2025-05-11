package fr.inra.oresing.domain.data.read.query;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import org.apache.commons.collections4.CollectionUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery.*;

public record ComponentFiltersByDate(String componentKey, String format, List<String> filters,
                                     Multiplicity multiplicity) implements WithFormatForFilterDate {
    public ComponentFiltersByDate {
        if (componentKey == null) {
            throw new BadDownloadDatasetQuery(MISSING_COMPONENT_KEY_FOR_SEARCH);
        }
        if (Strings.isNullOrEmpty(format)) {
            throw new BadDownloadDatasetQuery(MISSING_FORMAT_FOR_FILTER);
        }
        if (CollectionUtils.isEmpty(filters) || filters.stream().anyMatch(Strings::isNullOrEmpty)) {
            throw new BadDownloadDatasetQuery(MISSING_FILTER);
        }
        filters = filters.stream().map(filter -> {
            if (filter.matches("[0-9]*")) {
                ZoneId zone = ZoneId.of("UTC");
                LocalDate localDateTime = LocalDate.ofInstant(Instant.ofEpochMilli(Long.parseLong(filter)), zone);
                filter = localDateTime.format(DateTimeFormatter.ofPattern(format));
            }
            return filter;
        }).toList();
    }
}