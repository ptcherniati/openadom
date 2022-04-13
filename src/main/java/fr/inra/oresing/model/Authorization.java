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
    private List<String> dataGroup;
    private Map<String, Ltree> requiredauthorizations;

    public Authorization(List<String> dataGroup, Map<String, Ltree> requiredauthorizations, LocalDateTimeRange timeScope) {
        this.dataGroup = dataGroup;
        this.requiredauthorizations = requiredauthorizations;
        this.timeScope = timeScope;
    }

    public Authorization() {
    }

    public static String timescopeToSQL(LocalDateTimeRange timeScope) {
        return String.format("'%s'", timeScope.toSqlExpression());
    }

    public static String datagroupToSQL(List<String> dataGroup) {
        return dataGroup.stream()
                .map(dg -> String.format(String.format("'%s'", dg)))
                .collect(Collectors.joining(",", "array[", "]::TEXT[]"));
    }

    public static String requiredAuthorizationsToSQL(List<String> attributes, Map<String, Ltree> requiredauthorizations) {
        return attributes.stream()
                .map(attribute -> requiredauthorizations.getOrDefault(attribute, Ltree.empty()))
                .map(Ltree::getSql)
                .collect(Collectors.joining(",", "'(", ")'::%1$s.requiredauthorizations"));
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
        if (requiredauthorizations == null) {
            return " ";
        } else {
            sql.add(requiredAuthorizationsToSQL(requiredAuthorizationsAttributes, getRequiredauthorizations())
            );
        }
        if (dataGroup != null) {
            sql.add(datagroupToSQL(dataGroup));
        } else {
            sql.add("null::TEXT[]");
        }
        sql.add(timescopeToSQL(timeScope));
        return sql.stream()
                .collect(Collectors.joining(",", "(", ")::%1$s.authorization"));
    }
}