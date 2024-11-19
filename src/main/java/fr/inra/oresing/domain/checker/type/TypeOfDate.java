package fr.inra.oresing.domain.checker.type;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.TemporalAccessor;

public enum TypeOfDate {
    DATE(LocalDate.class),
    TIME(LocalDateTime.class),
    DATETIME(LocalDateTime.class);

    TypeOfDate(final Class<? extends TemporalAccessor> localDateClass) {
    }
}