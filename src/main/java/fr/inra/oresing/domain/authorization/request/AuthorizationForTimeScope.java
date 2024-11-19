package fr.inra.oresing.domain.authorization.request;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public record AuthorizationForTimeScope(Set<OperationType> operationTypes, LocalDateTimeRange timeScope) implements AuthorizationForScope {
}
