package fr.inra.oresing.rest;

import fr.inra.oresing.model.LocalDateTimeRange;
import lombok.Value;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@Value
public class CreateAuthorizationRequest {

    UUID userId;

    String applicationNameOrId;

    String dataType;

    String dataGroup;

    Map<String, String> authorizedScopes;

    LocalDate fromDay;

    LocalDate toDay;

    public LocalDateTimeRange getTimeScope() {
        LocalDateTimeRange timeScope;
        if (getFromDay() == null) {
            if (getToDay() == null) {
                timeScope = LocalDateTimeRange.always();
            } else {
                timeScope = LocalDateTimeRange.until(getToDay());
            }
        } else {
            if (getToDay() == null) {
                timeScope = LocalDateTimeRange.since(getFromDay());
            } else {
                timeScope = LocalDateTimeRange.between(getFromDay(), getToDay());
            }
        }
        return timeScope;
    }
}
