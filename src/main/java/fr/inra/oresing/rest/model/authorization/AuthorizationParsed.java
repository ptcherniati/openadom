package fr.inra.oresing.rest.model.authorization;

import com.google.common.collect.Range;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.authorization.request.AuthorizationForScope;
import fr.inra.oresing.domain.repository.authorization.OperationType;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;


public record AuthorizationParsed(
        Set<OperationType> operationTypes,
        Map<String, Set<String>> requiredAuthorizations,
        LocalDate fromDay,
        LocalDate toDay) {
    public static AuthorizationParsed of(AuthorizationForScope authorizationForScope) {
        Map<String, Set<String>> authorizationScopes = Optional.ofNullable(authorizationForScope)
                .map(AuthorizationForScope::authorizationScope)
                .orElse(Map.of()).entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream().map(Ltree::getSql).collect(Collectors.toSet())
                ));

        LocalDate fromDate = Optional.ofNullable(Objects.requireNonNull(authorizationForScope).timeScope())
                .map(LocalDateTimeRange::getRange).filter(Range::hasLowerBound)
                .map(range -> range.lowerEndpoint().toLocalDate()).orElse(LocalDate.MIN);

        LocalDate toDate = Optional.ofNullable(authorizationForScope.timeScope())
                .map(LocalDateTimeRange::getRange).filter(Range::hasUpperBound).map(range -> range.upperEndpoint().toLocalDate()).orElse(LocalDate.MAX);

        return new AuthorizationParsed(
                authorizationForScope.operationTypes(),
                authorizationScopes,
                fromDate,
                toDate
        );
    }
}