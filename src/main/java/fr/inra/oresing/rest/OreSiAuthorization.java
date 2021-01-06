package fr.inra.oresing.rest;

import fr.inra.oresing.model.LocalDateTimeRange;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Getter
@Setter
public class OreSiAuthorization {

    UUID userId;

    String applicationNameOrId;

    String dataset;

    String dataGroup;

    Set<UUID> referenceIds;

    LocalDate fromDay;

    LocalDate toDay;

    public Optional<LocalDateTimeRange> getTimeScope() {
        Optional<LocalDateTimeRange> timeScope;
        if (getFromDay() == null) {
            if (getToDay() == null) {
                timeScope = Optional.empty();
            } else {
                timeScope = Optional.of(LocalDateTimeRange.until(getToDay()));
            }
        } else {
            if (getToDay() == null) {
                timeScope = Optional.of(LocalDateTimeRange.since(getFromDay()));
            } else {
                timeScope = Optional.of(LocalDateTimeRange.between(getFromDay(), getToDay()));
            }
        }
        return timeScope;
    }

    public boolean isRestrictedOnReference() {
        return referenceIds != null && !referenceIds.isEmpty();
    }
}
