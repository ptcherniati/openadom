package fr.inra.oresing.persistence.requestBuilder.data;

import fr.inra.oresing.domain.application.configuration.Authorization;
import fr.inra.oresing.domain.application.configuration.AuthorizationScopeComponentData;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.data.read.query.AuthorizationDescription;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuerySimpleSearch;
import fr.inra.oresing.domain.data.read.query.IntervalValues;
import fr.inra.oresing.domain.data.read.query.RequiredAuthorization;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public record DownloadDatasetQuerySimpleSearchQueryBuilder(DownloadDatasetQuerySimpleSearch simpleSearchQuery) {
    private static final String DATA_NAME_TEMPLATE = "(referencetype)::ltree <@ '%s'::ltree";
    private static final String AUTHORIZATION_SCOPE_TEMPLATE = "(\"authorization\").requiredauthorizations.%1$s <@ '%2$s'::ltree";
    private static final String TIMESCOPE_TEMPLATE = "(\"authorization\").timescope <@ '[\"%1$s\",\"%2$s\")'::tsrange";
    String sql(){
        List<String> where = new ArrayList<>();
        List<String> dataForAuthorization = simpleSearchQuery().application().findData(simpleSearchQuery().dataName())
                .map(StandardDataDescription::authorization)
                .map(Authorization::authorizationScope)
                .map(authorizationScopeComponentData -> authorizationScopeComponentData.stream().map(AuthorizationScopeComponentData::data).toList())
                .orElse(List.of());
        Function<AuthorizationDescription, String> toSql = authorizationDescription -> toSql(dataForAuthorization, authorizationDescription);
        where.add(DATA_NAME_TEMPLATE.formatted(simpleSearchQuery().dataName()));
        List<String> authorizationFilters = simpleSearchQuery().authorizationDescriptions().stream()
                .distinct()
                .map(toSql)
                .filter(Objects::nonNull)
                .toList();
        if(CollectionUtils.isNotEmpty(authorizationFilters)){
            where.add(authorizationFilters.stream()
                    .collect(Collectors.joining("\n\t\t OR \n", "\t\t(\n", "\n\t\t)\n")));
        }
        return where.stream().collect(Collectors.joining("\n\t)\n AND (\n", "\n\t(\n\t\t", "\t)\n"));
    }



    private String toSql(List<String> dataForAuthorization, AuthorizationDescription authorizationDescription) {
        List<String> where = new ArrayList<>();
        if(authorizationDescription==null){
            return null;
        };
        Function<RequiredAuthorization, String> toSql = requiredAuthorization -> toSql(dataForAuthorization, requiredAuthorization);
        Predicate<RequiredAuthorization> filterValidData = requiredAuthorization -> dataForAuthorization.contains(requiredAuthorization.compositereferenceLabel());
        authorizationDescription.requiredAuthorizations().stream()
                .filter(Predicate.not(List::isEmpty))
                .map(List::getFirst)
                .filter(filterValidData)
                .map(toSql)
                .filter(Objects::nonNull)
                .forEach(where::add);
        Optional.ofNullable(authorizationDescription)
                .map(AuthorizationDescription::timeScope)
                .map(this::toSql)
                .filter(Objects::nonNull)
                .ifPresent(where::add);
        return where.stream()
                .collect(Collectors.joining("\n\t\t\t\t AND \n\t\t\t\t\t", "\t\t\t(\n\t\t\t\t\t", "\n\t\t\t)"));
    }

    private String toSql(IntervalValues intervalValues) {
        return TIMESCOPE_TEMPLATE.formatted(intervalValues.from(), intervalValues.to());
    }


    private String toSql(List<String> dataForAuthorization, RequiredAuthorization requiredAuthorization) {
        return AUTHORIZATION_SCOPE_TEMPLATE.formatted(requiredAuthorization.compositereferenceLabel(), requiredAuthorization.path().getSql());
    }
}
