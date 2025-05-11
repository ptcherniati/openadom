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
import java.util.stream.Collectors;


@Getter
@Setter
@ToString(callSuper = true)
public class AuthorizationInput {
    public static final String FROM_DAY = "fromDay";
    public static final String TO_DAY = "toDay";
    public static final String FORMAT = "format";
    LocalDateTimeRange timeScope = LocalDateTimeRange.always();
    private Map<String, List<Ltree>> requiredAuthorizations = new HashMap<>();

    public void setOperationTypes(Set<OperationType> operationTypes) {

        if (operationTypes.contains(OperationType.publication)) {
            operationTypes.add(OperationType.depot);
        }
        if (operationTypes.contains(OperationType.depot) || operationTypes.contains(OperationType.delete)) {
            operationTypes.add(OperationType.extraction);
        }
        this.operationTypes = operationTypes;
    }

    Set<OperationType> operationTypes = new HashSet<>();

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

    public void setTimeScope(Map<String, String> dates) {
        this.timeScope = Optional.ofNullable(dates.get(FORMAT))
                .map(DatePattern::of)
                .map(datePattern -> {
                    Class<TemporalAccessor> type = datePattern.type();
                    DateTimeFormatter formatter = datePattern.formatter();
                    if (type.equals(LocalDate.class)) {
                        LocalDate fromDay = Optional.ofNullable(dates.get(FROM_DAY))
                                .map(from->LocalDate.parse(from, formatter))
                                .orElse(LocalDate.MIN);
                        LocalDate toDay = Optional.ofNullable(dates.get(TO_DAY))
                                .map(from->LocalDate.parse(from, formatter))
                                .orElse(LocalDate.MAX);
                        return LocalDateTimeRange.between(fromDay, toDay);
                    } else if (type.equals(LocalTime.class)) {
                        LocalTime fromDay = Optional.ofNullable(dates.get(FROM_DAY))
                                .map(from->LocalTime.parse(from, formatter))
                                .orElse(LocalTime.MIN);
                        LocalTime toDay = Optional.ofNullable(dates.get(TO_DAY))
                                .map(from->LocalTime.parse(from, formatter))
                                .orElse(LocalTime.MAX);
                        return LocalDateTimeRange.between(LocalDate.now().atTime(fromDay), LocalDate.now().atTime( toDay));
                    } else if (type.equals(LocalDateTime.class)) {
                        LocalDateTime fromDay = Optional.ofNullable(dates.get(FROM_DAY))
                                .map(from->LocalDateTime.parse(from, formatter))
                                .orElse(LocalDateTime.MIN);
                        LocalDateTime toDay = Optional.ofNullable(dates.get(TO_DAY))
                                .map(from->LocalDateTime.parse(from, formatter))
                                .orElse(LocalDateTime.MAX);
                        return LocalDateTimeRange.between( fromDay,  toDay);
                    }
                    return null;
                })
                .orElse(null);
    }

    public AuthorizationInput() {
    }

    public static String timescopeToSQL(LocalDateTimeRange timeScope) {
        return String.format("'%s'", (timeScope == null ? LocalDateTimeRange.always() : timeScope).toSqlExpression());
    }

    public static String datagroupToSQL(List<String> dataGroups) {
        return dataGroups.stream()
                .map(dg -> String.format(String.format("'%s'", dg)))
                .collect(Collectors.joining(",", "array[", "]::TEXT[]"));
    }

   /* public static String requiredAuthorizationsToSQL(List<String> attributes, Map<String, Ltree> requiredAuthorizations) {
        return attributes.stream()
                .map(attribute -> requiredAuthorizations.getOrDefault(attribute, Ltree.empty()))
                .map(Ltree::getSql)
                .collect(Collectors.joining(",", "'(", ")'::%1$s.requiredAuthorizations"));
    }*/

    public static LocalDateTimeRange getTimeScope(LocalDate fromDay, LocalDate toDay) {
        LocalDateTimeRange timeScope;
        if (fromDay == null) {
            if (toDay == null) {
                timeScope = LocalDateTimeRange.always();
            } else {
                timeScope = LocalDateTimeRange.until(toDay);
            }
        } else {
            if (toDay == null) {
                timeScope = LocalDateTimeRange.since(fromDay);
            } else {
                timeScope = LocalDateTimeRange.between(fromDay, toDay);
            }
        }
        return timeScope;
    }

    /*public String getPath(List<String> attributes) {
        List<String> pathes = new LinkedList<>();
        return attributes.stream()
                .filter(attribute -> requiredAuthorizations.containsKey(attribute))
                .map(attribute -> requiredAuthorizations.get(attribute).getSql())
                .collect(Collectors.joining("."));
    }*/

    public void setIntervalDates(Map<String, LocalDate> dates) {
        this.timeScope = getTimeScope(dates.get(FROM_DAY), dates.get(TO_DAY));
    }

    /*public String toSQL(List<String> requiredAuthorizationsAttributes) {
        List<String> sql = new LinkedList<>();
        if (requiredAuthorizations == null) {
            return " ";
        } else {
            sql.add(requiredAuthorizationsToSQL(requiredAuthorizationsAttributes, requiredAuthorizations)
            );
        }
        sql.add(timescopeToSQL(timeScope));
        return sql.stream()
                .collect(Collectors.joining(",", "(", ")::%1$s.authorization"));
    }*/
}