package fr.inra.oresing.rest;

import fr.inra.oresing.model.Authorization;
import fr.inra.oresing.persistence.OperationType;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Value
public class CreateAuthorizationRequest {
    UUID uuid;

    Set<UUID> usersId;

    String applicationNameOrId;

    String dataType;

    Map<OperationType, List<Authorization>> authorizations;

    /*public LocalDateTimeRange getTimeScope() {
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
    }*/
}