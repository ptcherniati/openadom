package fr.inra.oresing.domain;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
public class Authorization {
    LocalDateTimeRange timeScope= LocalDateTimeRange.always();
    private Map<String, List<Ltree>> requiredAuthorizations;

    public Authorization(final Map<String, List<Ltree>> requiredAuthorizations, final LocalDateTimeRange timeScope) {
        super();
        this.requiredAuthorizations = requiredAuthorizations;
        this.timeScope = timeScope;
    }

    public Authorization() {
        super();
    }

    public static String timescopeToSQL(final LocalDateTimeRange timeScope) {
        return String.format("'%s'", (timeScope == null ? LocalDateTimeRange.always() : timeScope).toSqlExpression());
    }

    /*public static String requiredAuthorizationsToSQL(final List<String> attributes, final Map<String, List<Ltree>> requiredAuthorizations) {
        return attributes.stream()
                .map(attribute -> requiredAuthorizations.getOrDefault(attribute, Ltree.empty()))
                .map(Ltree::getSql)
                .collect(Collectors.joining(",", "'(", ")'::%1$s.requiredAuthorizations"));
    }*/

    /*public static LocalDateTimeRange getTimeScope(final LocalDate fromDay, final LocalDate toDay) {
        final LocalDateTimeRange timeScope;
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
    }*/

    /*public String toSQL(final List<String> requiredAuthorizationsAttributes) {
        final List<String> sql = new LinkedList<>();
        if (requiredAuthorizations == null) {
            return " ";
        } else {
            sql.add(requiredAuthorizationsToSQL(requiredAuthorizationsAttributes, requiredAuthorizations)
            );
        }
        sql.add(timescopeToSQL(timeScope));
        return sql.stream()
                .collect(Collectors.joining(",", "(", ")::%1$s.\"authorization\""));
    }*/

}