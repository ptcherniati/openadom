package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.checker.type.DateType;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

public sealed interface WithFormatForIntervalDate extends WithFormat, WithIntervalValues permits IntervalValuesDate, IntervalValuesDateTime, IntervalValuesTime {
    default Timestamp fromTimestamp() {
        return Optional.ofNullable(from())
                .map(f -> DateType.valueToDate(DateTimeFormatter.ofPattern(format()), f))
                .map(Timestamp::valueOf)
                .orElse(Timestamp.from(Instant.MIN));
    }

    default Timestamp toTimestamp() {
        return Optional.ofNullable(to())
                .map(f -> DateType.valueToDate(DateTimeFormatter.ofPattern(format()), f))
                .map(Timestamp::valueOf)
                .orElse(Timestamp.from(Instant.MAX));
    }

    default String getFromIsoString() {
        return Optional.ofNullable(fromTimestamp())
                .map(Timestamp::toLocalDateTime)
                .map(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")::format)
                .orElse(LocalDateTime.MIN.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }

    default String getToIsoString() {
        return Optional.ofNullable(toTimestamp())
                .map(Timestamp::toLocalDateTime)
                .map(dateTime -> dateTime.plusDays(1))
                .map(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")::format)
                .orElse(LocalDateTime.MAX.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
    }
}
