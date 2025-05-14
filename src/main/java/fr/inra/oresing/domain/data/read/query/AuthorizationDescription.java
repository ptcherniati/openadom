package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.Authorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.persistence.SqlSchemaForApplication;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public record AuthorizationDescription(IntervalValues timeScope,
                                       List<List<RequiredAuthorization>> requiredAuthorizations) {

    public List<Authorization> toAuthorization(final Application application, final SqlSchemaForApplication schema) {
        final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        final String sqlStart = schema.getSqlIdentifier() + ".isauthorized(data.authorization, array[";
            final String sqlEnd = "]::" + schema.getSqlIdentifier() + ".authorization[])";
        final LocalDateTimeRange localDateTimeRange = timeScope == null ? LocalDateTimeRange.always() : LocalDateTimeRange.getTimeScope(
                (timeScope.from() == null ? null : LocalDate.parse(timeScope.from(), dateFormatter)),
                (timeScope.to() == null ? null : LocalDate.parse(timeScope.to(), dateFormatter)));
        return Optional.ofNullable(requiredAuthorizations).orElse(List.of()).stream()
                .map(requiredAuthorization -> new Authorization(
                        requiredAuthorization.stream()
                                .collect(Collectors.toMap(RequiredAuthorization::compositereferenceLabel, entry->List.of(entry.path()))),
                        localDateTimeRange
                ))
                .toList();
    }
}