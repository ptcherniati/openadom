package fr.inra.oresing.persistence;

import com.google.common.base.Strings;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.rightsrequest.RightsRequestInfos;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.util.CollectionUtils;
import org.testcontainers.shaded.org.apache.commons.lang.StringEscapeUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class RightsRequestSearchHelper {
    RightsRequestInfos rightsRequestInfos;
    Application application;
    private AtomicInteger i = new AtomicInteger();

    public MapSqlParameterSource getParamSource() {
        return paramSource;
    }

    private MapSqlParameterSource paramSource = new MapSqlParameterSource();

    private String addArgumentAndReturnSubstitution(Object value) {
        int i = this.i.incrementAndGet();
        String paramName = String.format("arg%d", i);
        paramSource.addValue(paramName, value);
        return String.format(":%s", paramName);
    }

    public RightsRequestSearchHelper(Application application, RightsRequestInfos rightsRequestInfos) {
        this.application = application;
        this.rightsRequestInfos = rightsRequestInfos;
        this.paramSource = new MapSqlParameterSource("applicationId", application.getId());
    }

    String filterBy() {
        List<String> where = new LinkedList<>();
        Optional.ofNullable(this.rightsRequestInfos.getUuids())
                .filter(uuids -> !CollectionUtils.isEmpty(uuids))
                .ifPresent(list -> {
                    where.add(list.stream()
                            .map(this::addArgumentAndReturnSubstitution)
                            .collect(Collectors.joining(",", " (\nid in (", ")\n) "))
                    );
                });
        Optional.ofNullable(this.rightsRequestInfos.getAuthorizations())
                .filter(authorizations -> !CollectionUtils.isEmpty(authorizations))
                .ifPresent(list -> {
                    where.add(list.stream()
                            .map(this::addArgumentAndReturnSubstitution)
                            .collect(Collectors.joining(",", " (\nassociate @> ARRAY[", "]\n) "))
                    );
                });
        Optional.ofNullable(this.rightsRequestInfos)
                .ifPresent(rightsRequestInfos -> where.add(whereForRightsRequest(rightsRequestInfos)));

        return CollectionUtils.isEmpty(where) ? "" : where.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.joining(" or ", "(", ")"));
    }

    private String whereForRightsRequest(RightsRequestInfos rightsRequestInfos) {
        final Set<RightsRequestInfos.FieldFilters> fieldFilters = rightsRequestInfos.getFieldFilters();
        final Configuration.RightsRequestDescription rightsRequestDescription = application.getConfiguration().getRightsRequest();
        List<String> where = new LinkedList<>();
        if (!CollectionUtils.isEmpty(fieldFilters)) {
            Optional.ofNullable(fieldFilters)
                    .map(filters -> filters.stream()
                            .map(filter -> whereForField(filter, rightsRequestDescription.getFormat().get(filter.field)))
                            .collect(Collectors.joining(" and ", "(", ")")))
                    .ifPresent(whereElement -> where.add(whereElement));
        }
        return CollectionUtils.isEmpty(where) ? "" : where.stream()
                .filter(Objects::nonNull).collect(Collectors
                        .joining(" and ", "(", ")"));
    }



    private String whereForField(RightsRequestInfos.FieldFilters filter, Configuration.FieldFormat rightsRequestFieldFormat) {
        boolean isRegExp = filter.isRegExp != null && filter.isRegExp;
        List<String> filters = new LinkedList<>();
        if (!Strings.isNullOrEmpty(filter.filter)) {
            filters.add(String.format(
                            "rightsrequestform #> '{\"%s\"}'  @@ ('$ like_regex \"'||%s||'\"')::jsonpath",
                            StringEscapeUtils.escapeSql(filter.getField()),
                            /*String.format(isRegExp ? "~ %s" : "ilike '%%'||%s||'%%'", */
                            addArgumentAndReturnSubstitution(filter.getFilter())//)
                    )
            );

        } else if (filter.intervalValues != null && List.of("date", "time", "datetime").contains(filter.type)) {
            if (!Strings.isNullOrEmpty(filter.intervalValues.from) || !Strings.isNullOrEmpty(filter.intervalValues.to)) {
                filters.add(
                        String.format(
                                "fileinfos #> '{\"%1$s\"}'@@ ('$ >= \"date:'||%2$s||'\" && $ <= \"date:'||%2$s||'Z\"')::jsonpath",
                                StringEscapeUtils.escapeSql(filter.getField()),
                                addArgumentAndReturnSubstitution(Strings.isNullOrEmpty(filter.intervalValues.from) ? "0" : filter.intervalValues.from),
                                addArgumentAndReturnSubstitution(Strings.isNullOrEmpty(filter.intervalValues.to) ? "9" : filter.intervalValues.to)
                        )
                );
            }
        } else if (filter.intervalValues != null && "numeric".equals(filter.type)) {
            if (!Strings.isNullOrEmpty(filter.intervalValues.from) || !Strings.isNullOrEmpty(filter.intervalValues.to)) {
                //fileinfos #> '{"t","value"}'@@ '$. double() >= 1 && $. double() <= 2'
                List<String> filterList = new LinkedList<>();
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
                if (!CollectionUtils.isEmpty(filterList)) {
                    filters.add(
                            String.format("fileinfos #> '{\"%s\"}'@@ ('%s')::jsonpath",
                                    StringEscapeUtils.escapeSql(filter.getField()),
                                    filterList.stream().collect(Collectors.joining(" && "))
                            )
                    );
                }
            }
        }
        if (CollectionUtils.isEmpty(filters)) {
            return "";
        }
        return filters.stream()
                .filter(f -> !Strings.isNullOrEmpty(f))
                .collect(Collectors.joining(" AND ", "(", ")"));
    }

    public String buildRequest(String sqlStart, String sqlEnd) {
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
        return filterBy();
    }
}