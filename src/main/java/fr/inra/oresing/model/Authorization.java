package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@ToString(callSuper = true)
public class Authorization {
    private List<String> dataGroup;
    private Map<String, String> requiredauthorizations;
    LocalDateTimeRange timeScope;

    public void setIntervalDates(Map<String, LocalDate> dates) {
        LocalDateTimeRange timeScope = getTimeScope(dates.get("fromDay"), dates.get("toDay"));
        this.timeScope = timeScope;
    }

    public Authorization() {
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
}