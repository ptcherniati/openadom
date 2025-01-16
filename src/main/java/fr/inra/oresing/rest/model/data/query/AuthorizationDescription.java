package fr.inra.oresing.rest.model.data.query;

import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.data.read.query.RequiredAuthorization;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Setter
/*

 */
public class AuthorizationDescription {

    private IntervalValues timeScope;
    private Map<String, Ltree> requiredAuthorizations = new HashMap<>();

    public static fr.inra.oresing.domain.data.read.query.AuthorizationDescription build(
            final AuthorizationDescription authorizationDescription
    ) {
        final List<List<RequiredAuthorization>> requiredAuthorizations =
                authorizationDescription.getRequiredAuthorizations().entrySet().stream()
                                .map(entry -> List.of(new RequiredAuthorization(entry.getKey(), entry.getValue())))
                                .toList();

        return new fr.inra.oresing.domain.data.read.query.AuthorizationDescription(
                new fr.inra.oresing.domain.data.read.query.IntervalValues(authorizationDescription.getTimeScope().getFrom(), authorizationDescription.getTimeScope().getTo()),
                requiredAuthorizations
        );
    }/*

    public List<Authorization> toAuthorization(final Application application, final SqlSchemaForApplication schema) {
        final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        final String sqlStart = schema.getSqlIdentifier() + ".isauthorized(data.authorization, array[";
        final String sqlEnd = "]::" + schema.getSqlIdentifier() + ".authorization[])";
        final LocalDateTimeRange localDateTimeRange = timeScope == null ? LocalDateTimeRange.always() : LocalDateTimeRange.getTimeScope(
                (timeScope.from == null ? null : LocalDate.parse(timeScope.from, dateFormatter)),
                (timeScope.to == null ? null : LocalDate.parse(timeScope.to, dateFormatter)));
        return List.of(new Authorization(
                        requiredAuthorizations,
                        localDateTimeRange
                )
        );
    }*/
}
