package fr.inra.oresing.model;

import fr.inra.oresing.persistence.Ltree;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString(callSuper = true)
public class Authorization {
    LocalDateTimeRange timeScope;
    private List<String> dataGroups = new LinkedList<>();
    private Map<String, Ltree> requiredAuthorizations;

    public Authorization(List<String> dataGroup, Map<String, Ltree> requiredAuthorizations, LocalDateTimeRange timeScope) {
        this.dataGroups = dataGroup;
        this.requiredAuthorizations = requiredAuthorizations;
        this.timeScope = timeScope;
    }

    public Authorization() {
    }

    public static String timescopeToSQL(LocalDateTimeRange timeScope) {
        return String.format("'%s'", (timeScope == null ? LocalDateTimeRange.always() : timeScope).toSqlExpression());
    }

    public static String datagroupToSQL(List<String> dataGroup) {
        return dataGroup.stream()
                .map(dg -> String.format(String.format("'%s'", dg)))
                .collect(Collectors.joining(",", "array[", "]::TEXT[]"));
    }

    public static String requiredAuthorizationsToSQL(List<String> attributes, Map<String, Ltree> requiredAuthorizations) {
        return attributes.stream()
                .map(attribute -> requiredAuthorizations.getOrDefault(attribute, Ltree.empty()))
                .map(Ltree::getSql)
                .collect(Collectors.joining(",", "'(", ")'::%1$s.requiredAuthorizations"));
    }

    public void setIntervalDates(Map<String, LocalDate> dates) {
        LocalDateTimeRange timeScope = getTimeScope(dates.get("fromDay"), dates.get("toDay"));
        this.timeScope = timeScope;
    }

    public LocalDateTimeRange getTimeScope(LocalDate fromDay, LocalDate toDay) {
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

    public String toSQL(List<String> requiredAuthorizationsAttributes) {
        List<String> sql = new LinkedList<>();
        if (requiredAuthorizations == null) {
            return " ";
        } else {
            sql.add(requiredAuthorizationsToSQL(requiredAuthorizationsAttributes, getRequiredAuthorizations())
            );
        }
        if (dataGroups != null) {
            sql.add(datagroupToSQL(dataGroups));
        } else {
            sql.add("null::TEXT[]");
        }
        sql.add(timescopeToSQL(timeScope));
        return sql.stream()
                .collect(Collectors.joining(",", "(", ")::%1$s.authorization"));
    }
}