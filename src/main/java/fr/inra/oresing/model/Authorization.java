package fr.inra.oresing.model;

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
    private Map<String, String> requiredauthorizations;

    public Authorization(List<String> dataGroup, Map<String, String> requiredauthorizations, LocalDateTimeRange timeScope) {
        this.dataGroup = dataGroup;
        this.requiredauthorizations = requiredauthorizations;
        this.timeScope = timeScope;
    }

    public Authorization() {
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

   public String toSQL(List<String> attributes) {
        List<String> sql = new LinkedList<>();
       if (requiredauthorizations == null) {
           return null;
       } else {
           sql.add(attributes.stream()
                   .map(attribute -> getRequiredauthorizations().getOrDefault(attribute, ""))
                   .collect(Collectors.joining(",", "'(", ")'::%1$s.requiredauthorizations"))
           );
       }
       if(!(dataGroup == null)){
            sql.add(dataGroup.stream()
                    .map(dg -> String.format(String.format("'%s'", dg)))
                    .collect(Collectors.joining(",", "array[", "]::TEXT[]"))
            );
        }
        sql.add(String.format("'%s'", timeScope.toSqlExpression()));
        return sql.stream()
                .collect(Collectors.joining(",", "(",")::%1$s.authorization"));
    }
}