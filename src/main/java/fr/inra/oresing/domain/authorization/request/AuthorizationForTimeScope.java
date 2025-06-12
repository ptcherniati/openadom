package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.util.Set;

public record AuthorizationForTimeScope(Set<OperationType> operationTypes,
                                        LocalDateTimeRange timeScope) implements AuthorizationForScope {
}