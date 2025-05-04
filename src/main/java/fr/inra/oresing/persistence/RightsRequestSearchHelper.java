package fr.inra.oresing.persistence;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.RightRequestDescription;
import fr.inra.oresing.rest.model.rightsrequest.RightsRequestInfos;
import lombok.Getter;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class RightsRequestSearchHelper {
    final RightsRequestInfos rightsRequestInfos;
    final Application application;
    private final AtomicInteger i = new AtomicInteger();

    @Getter
    private final MapSqlParameterSource paramSource;

    private String addArgumentAndReturnSubstitution(final Object value) {
        final int i = this.i.incrementAndGet();
        final String paramName = String.format("arg%d", i);
        paramSource.addValue(paramName, value);
        return String.format(":%s", paramName);
    }

    public RightsRequestSearchHelper(final Application application, final RightsRequestInfos rightsRequestInfos) {
        super();
        this.application = application;
        this.rightsRequestInfos = rightsRequestInfos;
        paramSource = new MapSqlParameterSource("applicationId", application.getId());
    }

    String filterBy() {
        final List<String> where = new LinkedList<>();
        Optional.ofNullable(rightsRequestInfos)
                .map(RightsRequestInfos::getUuids)
                .filter(uuids -> !CollectionUtils.isEmpty(uuids))
                .ifPresent(list -> where.add(list.stream()
                        .map(this::addArgumentAndReturnSubstitution)
                        .collect(Collectors.joining(",", " (\nid in (", ")\n) "))
                ));
        Optional.ofNullable(rightsRequestInfos)
                .map(RightsRequestInfos::getAuthorizations)
                .filter(authorizations -> !CollectionUtils.isEmpty(authorizations))
                .ifPresent(list -> where.add(list.stream()
                        .map(this::addArgumentAndReturnSubstitution)
                        .collect(Collectors.joining(",", " (\nassociate @> ARRAY[", "]\n) "))
                ));
        Optional.ofNullable(rightsRequestInfos)
                .ifPresent(rightsRequestInfos -> where.add(whereForRightsRequest(rightsRequestInfos)));

        return CollectionUtils.isEmpty(where) ? "" : where.stream()
                .filter(w->w!=null && !Strings.isNullOrEmpty(w))
                .collect(Collectors.joining(" or ", "(", ")"));
    }

    private String whereForRightsRequest(final RightsRequestInfos rightsRequestInfos) {
        Set<RightsRequestInfos.FieldFilters> fieldFilters = rightsRequestInfos.getFieldFilters();
        RightRequestDescription rightsRequestDescription = application.getConfiguration().rightsRequest();
        final List<String> where = new LinkedList<>();
        if (!CollectionUtils.isEmpty(fieldFilters)) {
            Optional.of(fieldFilters)
                    .map(filters -> filters.stream()
                            .map(this::whereForField)
                            .collect(Collectors.joining(" and ", "(", ")")))
                    .ifPresent(where::add);
        }
        return CollectionUtils.isEmpty(where) ? "" : where.stream()
                .filter(Objects::nonNull).collect(Collectors
                        .joining(" and ", "(", ")"));
    }



    private String whereForField(final RightsRequestInfos.FieldFilters filter) {
        final boolean isRegExp = filter.isRegExp != null && filter.isRegExp;
        final List<String> filters = new LinkedList<>();
        if (!Strings.isNullOrEmpty(filter.filter)) {
            filters.add(String.format(
                            "rightsrequestform #> '{\"%s\"}'  @@ ('$ like_regex \"'||%s||'\"')::jsonpath",
                    JsonTableInApplicationSchemaRepositoryTemplate.escapeSql(filter.getField()),
                            /*String.format(isRegExp ? "~ %s" : "ilike '%%'||%s||'%%'", */
                            addArgumentAndReturnSubstitution(filter.getFilter())//)
                    )
            );

        } else if (filter.intervalValues != null && List.of("date", "time", "datetime").contains(filter.type)) {
            if (!Strings.isNullOrEmpty(filter.intervalValues.from) || !Strings.isNullOrEmpty(filter.intervalValues.to)) {
                filters.add(
                        String.format(
                                "fileinfos #> '{\"%1$s\"}'@@ ('$ >= \"date:'||%2$s||'\" && $ <= \"date:'||%3$s||'Z\"')::jsonpath",
                                JsonTableInApplicationSchemaRepositoryTemplate.escapeSql(filter.getField()),
                                addArgumentAndReturnSubstitution(Strings.isNullOrEmpty(filter.intervalValues.from) ? "0" : filter.intervalValues.from),
                                addArgumentAndReturnSubstitution(Strings.isNullOrEmpty(filter.intervalValues.to) ? "9" : filter.intervalValues.to)
                        )
                );
            }
        } else if (filter.intervalValues != null && "numeric".equals(filter.type)) {
            if (!Strings.isNullOrEmpty(filter.intervalValues.from) || !Strings.isNullOrEmpty(filter.intervalValues.to)) {
                //fileinfos #> '{"t","value"}'@@ '$. double() >= 1 && $. double() <= 2'
                final List<String> filterList = new LinkedList<>();
                if (!Strings.isNullOrEmpty(filter.intervalValues.from)) {
                    filterList.add(String.format(
                                    "$. double() >= '||%s||'",
                                    addArgumentAndReturnSubstitution(filter.intervalValues.from)
                            )
                    );
                }
                if (!Strings.isNullOrEmpty(filter.intervalValues.to)) {
                    filterList.add(String.format(
                                    "$. double() <= '||%s||'",
                                    addArgumentAndReturnSubstitution(filter.intervalValues.to)
                            )
                    );
                }
                filters.add(
                        String.format("fileinfos #> '{\"%s\"}'@@ ('%s')::jsonpath",
                                JsonTableInApplicationSchemaRepositoryTemplate.escapeSql(filter.getField()),
                                String.join(" && ", filterList)
                        )
                );
            }
        }
        if (CollectionUtils.isEmpty(filters)) {
            return "";
        }
        return filters.stream()
                .filter(f -> !Strings.isNullOrEmpty(f))
                .collect(Collectors.joining(" AND ", "(", ")"));
    }

    public String buildRequest(final String sqlStart, final String sqlEnd) {
        String filterBy = filterBy();
        if (!Strings.isNullOrEmpty(filterBy)) {
            filterBy = String.join("\n", "where ", filterBy);
        }
        return String.join("\n ",
                sqlStart,
                filterBy,
                sqlEnd
        );

    }

    public String buildWhereRequest() {
        return rightsRequestInfos==null?null:filterBy();
    }
}