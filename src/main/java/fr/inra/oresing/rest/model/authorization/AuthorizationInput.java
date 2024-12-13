package fr.inra.oresing.rest.model.authorization;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;


@Getter
@Setter
@ToString(callSuper = true)
public class AuthorizationInput {
    LocalDateTimeRange timeScope = LocalDateTimeRange.always();
    private Map<String, List<Ltree>> requiredAuthorizations = new HashMap<>();

    public void setOperationTypes(Set<OperationType> operationTypes) {

        if(operationTypes.contains(OperationType.publication)){
            operationTypes.add(OperationType.depot);
        }
        if(operationTypes.contains(OperationType.depot) || operationTypes.contains(OperationType.delete)){
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
        if(operationTypes.contains(OperationType.publication)){
            operationTypes.add(OperationType.depot);
            operationTypes.add(OperationType.delete);
        }
        if(operationTypes.contains(OperationType.depot) || operationTypes.contains(OperationType.delete)){
            operationTypes.add(OperationType.extraction);
        }
        this.operationTypes = operationTypes;
    }

    public void setTimeScope(final Map<String, LocalDate> dates) {
        final LocalDateTimeRange timeScope = getTimeScope(dates.get("fromDay"), dates.get("toDay"));
        this.timeScope = timeScope;
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
        LocalDateTimeRange timeScope = getTimeScope(dates.get("fromDay"), dates.get("toDay"));
        this.timeScope = timeScope;
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
