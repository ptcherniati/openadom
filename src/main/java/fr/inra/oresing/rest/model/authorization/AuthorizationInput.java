package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Getter
@Setter
@ToString(callSuper = true)
public class AuthorizationInput {
    public static final String FROM_DAY = "fromDay";
    public static final String TO_DAY = "toDay";
    public static final String FORMAT = "format";
    LocalDateTimeRange timeScope = LocalDateTimeRange.always();
    Set<OperationType> operationTypes = new HashSet<>();
    private Map<String, List<Ltree>> requiredAuthorizations = new HashMap<>();

    public AuthorizationInput(Map<String, List<Ltree>> requiredAuthorizations,
                              LocalDateTimeRange timeScope,
                              Set<OperationType> operationTypes) {
        this.requiredAuthorizations = requiredAuthorizations;
        this.timeScope = timeScope;
        operationTypes = new HashSet<>(operationTypes);
        if (operationTypes.contains(OperationType.publication)) {
            operationTypes.add(OperationType.depot);
            operationTypes.add(OperationType.delete);
        }
        if (operationTypes.contains(OperationType.depot) || operationTypes.contains(OperationType.delete)) {
            operationTypes.add(OperationType.extraction);
        }
        this.operationTypes = operationTypes;
    }

    public AuthorizationInput() {
    }

    public void setOperationTypes(Set<OperationType> operationTypes) {

        if (operationTypes.contains(OperationType.publication)) {
            operationTypes.add(OperationType.depot);
        }
        if (operationTypes.contains(OperationType.depot) || operationTypes.contains(OperationType.delete)) {
            operationTypes.add(OperationType.extraction);
        }
        this.operationTypes = operationTypes;
    }

    public void setTimeScope(Map<String, String> dates) {
        this.timeScope = Optional.ofNullable(dates.get(FORMAT))
                .map(DatePattern::of)
                .map(datePattern -> {
                    Class<TemporalAccessor> type = datePattern.type();
                    DateTimeFormatter formatter = datePattern.formatter();
                    if (type.equals(LocalDate.class)) {
                        LocalDate fromDay = Optional.ofNullable(dates.get(FROM_DAY))
                                .map(from -> LocalDate.parse(from, formatter))
                                .orElse(LocalDate.MIN);
                        LocalDate toDay = Optional.ofNullable(dates.get(TO_DAY))
                                .map(from -> LocalDate.parse(from, formatter))
                                .orElse(LocalDate.MAX);
                        return LocalDateTimeRange.between(fromDay, toDay);
                    } else if (type.equals(LocalTime.class)) {
                        LocalTime fromDay = Optional.ofNullable(dates.get(FROM_DAY))
                                .map(from -> LocalTime.parse(from, formatter))
                                .orElse(LocalTime.MIN);
                        LocalTime toDay = Optional.ofNullable(dates.get(TO_DAY))
                                .map(from -> LocalTime.parse(from, formatter))
                                .orElse(LocalTime.MAX);
                        return LocalDateTimeRange.between(LocalDate.now().atTime(fromDay), LocalDate.now().atTime(toDay));
                    } else if (type.equals(LocalDateTime.class)) {
                        LocalDateTime fromDay = Optional.ofNullable(dates.get(FROM_DAY))
                                .map(from -> LocalDateTime.parse(from, formatter))
                                .orElse(LocalDateTime.MIN);
                        LocalDateTime toDay = Optional.ofNullable(dates.get(TO_DAY))
                                .map(from -> LocalDateTime.parse(from, formatter))
                                .orElse(LocalDateTime.MAX);
                        return LocalDateTimeRange.between(fromDay, toDay);
                    }
                    return null;
                })
                .orElse(null);
    }

    public AuthorizationInput withRestrictionWithDependants(String dataName, Function<String, Boolean> isVersionningStrategy) {
        return new AuthorizationInput(
                getRequiredAuthorizations(),
                getTimeScope(),
                getOperationTypes().stream()
                        .flatMap(operationType -> {
                            final Boolean isVersionning = isVersionningStrategy.apply(dataName);
                            if(operationType==null){
                                return Stream.of();
                            }
                            if(OperationType.extraction.equals(operationType)) {
                                return Stream.of(operationType);
                            }
                            if(Set.of(OperationType.depot, OperationType.publication).contains(operationType)){
                                return isVersionning?
                                        Stream.of(OperationType.depot, OperationType.publication, OperationType.delete, OperationType.extraction):
                                        Stream.of(OperationType.depot, OperationType.publication, OperationType.extraction);
                            }
                            return Stream.of(OperationType.depot, OperationType.publication, OperationType.delete, OperationType.extraction);
                        })
                        .collect(Collectors.toSet())
        );
    }
}