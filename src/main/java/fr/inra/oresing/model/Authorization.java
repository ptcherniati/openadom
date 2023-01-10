package fr.inra.oresing.model;

import fr.inra.oresing.persistence.Ltree;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString(callSuper = true)
public class Authorization {
    LocalDateTimeRange timeScope;
    private List<String> dataGroups = new LinkedList<>();
    private Map<String, Ltree> requiredAuthorizations;

    public Authorization(List<String> dataGroups, Map<String, Ltree> requiredAuthorizations, LocalDateTimeRange timeScope) {
        this.dataGroups = dataGroups;
        this.requiredAuthorizations = requiredAuthorizations;
        this.timeScope = timeScope;
    }
    public String getPath(List<String> attributes){
        List<String> pathes = new LinkedList<>();
        return attributes.stream()
                .filter(attribute->getRequiredAuthorizations().containsKey(attribute))
                .map(attribute->getRequiredAuthorizations().get(attribute).getSql())
                .collect(Collectors.joining("."));
    }

    public Authorization() {
    }

    public static String timescopeToSQL(LocalDateTimeRange timeScope) {
        return String.format("'%s'", (timeScope == null ? LocalDateTimeRange.always() : timeScope).toSqlExpression());
    }

    public static String datagroupToSQL(List<String> dataGroups) {
        return dataGroups.stream()
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

    public String toDataTablePolicyExpression() {
        Set<String> authAsSqlClauses = new LinkedHashSet<>();
        if (getRequiredAuthorizations() == null || getRequiredAuthorizations().isEmpty()) {
            // pas de contrainte sur le périmètre
        } else {
            // exemple
            //     'grand_lac.leman'::ltree <@ COALESCE(("authorization").requiredAuthorizations.localisation_site, ''::ltree)
            // AND 'suivi_des_lacs'::ltree <@ COALESCE(("authorization").requiredAuthorizations.localisation_projet, ''::ltree)
            String scopeSqlClause = getRequiredAuthorizations().entrySet().stream().map(entry -> {
                String scope = entry.getKey();
                Ltree authorizedScope = entry.getValue();
                return String.format("'%s'::ltree <@ COALESCE((\"authorization\").requiredAuthorizations.%s, ''::ltree)", authorizedScope.getSql(), scope);
            }).collect(Collectors.joining(" AND "));
            authAsSqlClauses.add(scopeSqlClause);
        }
        if (getDataGroups() == null || getDataGroups().isEmpty()) {
            // pas de contrainte sur le groupe de données, on ouvre accès à tous les groupes
        } else {
            String dataGroupClause = getDataGroups().stream()
                    .collect(Collectors.joining(",", "(\"authorization\").datagroups[1] = ANY ('{", "}'::text[])"));
            authAsSqlClauses.add(dataGroupClause);
        }
        if (getTimeScope() == null || getTimeScope().equals(LocalDateTimeRange.always())) {
            // pas de contrainte sur la fenêtre de temps
        } else {
            String timeScopeAsSql = getTimeScope().toSqlExpression();
            String timeScopeClause = String.format(
                    "'%s'::tsrange @> COALESCE((\"authorization\").timescope, '(,)'::tsrange)",
                    timeScopeAsSql
            );
            authAsSqlClauses.add(timeScopeClause);
        }
        String expression;
        if (authAsSqlClauses.isEmpty()) {
            expression = "TRUE";
        } else {
            expression = authAsSqlClauses.stream()
                    .map(statement -> "(" + statement + ")")
                    .collect(Collectors.joining(" AND "));
        }
        return expression;
    }
}