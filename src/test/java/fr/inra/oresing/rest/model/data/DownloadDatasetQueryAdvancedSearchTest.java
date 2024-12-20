package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.domain.exceptions.data.data.BadDownloadDatasetQuery;
import fr.inra.oresing.persistence.DataRepository;
import org.apache.commons.collections.CollectionUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

@Tag("MODEL_REQUEST_TEST")
@Disabled
class DownloadDatasetQueryAdvancedSearchTest {

    public final Fixture fixture = new Fixture();
    public static JsonRowMapper mapper;
    public static final Set<String> rowIds = Set.of("addf3698-88f2-43f9-8926-0b64a86f3678", "0aef7ed1-1df9-4fbf-a676-1932e87ced9d", "2c527cbe-3ed7-4883-b7d1-8f29eff99eb1");

    private fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery downloadDatasetQueryAdvancedSearch;

    @BeforeAll
    public static void init() {
        mapper = new JsonRowMapper<>();
    }

    private static void testFilter(final ComponentFilterSimpleSearch componentFilterSimpleSearch, final String filter) {
        assertTrue(componentFilterSimpleSearch.filters().contains(filter), "%s expected".formatted(filter));
    }

    private static void testcomponent(final ForComponent componentFilters, final String variable, final String componentKey) {
        assertEquals(componentKey, componentFilters.componentKey(), "component must be %s".formatted(componentKey));
    }

    @BeforeEach
    public void before() throws IOException {
        final String simpleSearchJson = """
                {
                   "application": null,
                   "applicationNameOrId": "monsores",
                   "dataType": "pem",
                   "reference": null,
                   "offset": null,
                   "limit": 10,
                   "componentSelects": [{
                         "variable": "Date",
                         "component": "day"
                       },
                       {
                         "variable": "Date",
                         "component": "time"
                       }
                     ],
                   "authorizationDescriptions": [
                     {
                       "timeScope": {
                         "from": "1984-01-01",
                         "to": "1984-01-02"
                       },
                       "requiredAuthorizations": [
                         {
                           "projet": "projet_manche",
                           "localization": "plateforme"
                         }
                       ]
                     },
                     {
                       "timeScope": {
                         "from": "1984-01-03",
                         "to": "1984-01-04"
                       },
                       "requiredAuthorizations": [
                         {
                           "projet": "atlantique",
                           "localization": "plateforme"
                         }
                       ]
                     }
                   ],
                   "componentOrderBy": [
                    {
                      "componentKey": {
                        "variable": "site",
                        "component": "plateforme"
                      },
                      "order": "ASC",
                      "type": null,
                      "format": null
                    }
                  ]
                 }
                """;

        final String advancedSearchJson = """
                {
                  "application": null,
                  "applicationNameOrId": null,
                  "dataType": null,
                  "offset": null,
                  "limit": 15,
                  "componentSelects": [{
                         "variable": "Date",
                         "component": "day"
                       },
                       {
                         "variable": "Date",
                         "component": "time"
                       }
                     ],
                  "componentFilters": [
                     {
                       "componentKey": {
                         "variable": "Date",
                         "component": "day"
                       },
                       "type": "date",
                       "format": "dd/MM/yyyy",
                       "intervalValues": {
                         "from": "01/01/1984",
                         "to": "04/01/1984"
                       }
                     },
                     {
                       "componentKey": {
                         "variable": "Date",
                         "component": "time"
                       },
                       "type": "time",
                       "format": "HH:mm:ss",
                       "intervalValues": {
                         "from": "12:20:45",
                         "to": "16:21:32"
                       }
                     },
                     {
                       "componentKey": {
                         "variable": "Date",
                         "component": "datetime"
                       },
                       "type": "datetime",
                       "format": "dd/MM/yyyy HH:mm:ss",
                       "intervalValues": {
                         "from": "01/01/1984 12:20:45",
                         "to": "01/01/1984 16:21:32"
                       }
                     },
                     {
                       "componentKey": {
                         "variable": "Nombre d'individus",
                         "component": "value"
                       },
                       "type": "numeric",
                       "intervalValues": {
                         "from": "5",
                         "to": "40"
                       },
                       "isRegExp": null
                     },
                     {
                       "componentKey": {
                         "variable": "date",
                         "component": "day"
                       },
                       "type": "date",
                       "filter": "01/01/1984",
                       "format": "dd/MM/yyyy"
                     },
                     {
                       "componentKey": {
                         "variable": "date",
                         "component": "datetime"
                       },
                       "type": "datetime",
                       "filter": "01/01/1984 01:12:43",
                       "format": "dd/MM/yyyy HH:mm:ss"
                     },
                     {
                       "componentKey": {
                         "variable": "date",
                         "component": "time"
                       },
                       "type": "time",
                       "filter": "01:12:43",
                       "format": "HH:mm:ss"
                     },
                     {
                       "componentKey": {
                         "variable": "individus",
                         "component": "value"
                       },
                       "type": "numeric",
                       "filter": "12.3"
                     },
                     {
                       "componentKey": {
                         "variable": "site",
                         "component": "bassin"
                       },
                       "filter": "lebassin"
                     },
                     {
                       "componentKey": {
                         "variable": "site",
                         "component": "projet"
                       },
                       "filter": "l[ae]bassine+",
                       "intervalValues": null,
                       "isRegExp": true
                     },
                     {
                       "componentKey": {
                         "variable": "projet",
                         "component": "value"
                       },
                       "filter": "projet_manche",
                       "type": "reference"
                     }
                   ],
                  "componentOrderBy": [
                    {
                      "componentKey": {
                        "variable": "site",
                        "component": "plateforme"
                      },
                      "order": "ASC",
                      "type": null,
                      "format": null
                    }
                  ]
                }""";
        downloadDatasetQueryAdvancedSearch = Fixture.addApplication((fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery) mapper.toObject(advancedSearchJson, fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery.class));
        fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery downloadDatasetQuerySimpleSearch = Fixture.addApplication((fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery) mapper.toObject(simpleSearchJson, fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery.class));
    }

    @Test
    void TestFilterForRowIds() throws IOException {
        downloadDatasetQueryAdvancedSearch.setRowIds(rowIds);
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery build = fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery.build(Fixture.addApplication(downloadDatasetQueryAdvancedSearch));
        assertInstanceOf(DownloadDatasetQueryByRowId.class, build, "must be an advanced Search");
        final DownloadDatasetQueryByRowId downloadDatasetQueryByRowId = (DownloadDatasetQueryByRowId) build;
        final Set<DataRowIds> dataRowIds = downloadDatasetQueryByRowId.rowIds();
        final Set<DataRowIds> expected = rowIds.stream()
                .map(UUID::fromString)
                .map(DataRowIds::new)
                .collect(Collectors.toSet());
        assertEquals(expected, dataRowIds);


    }

    @Test
    @Disabled
        //TODO
    void TestAdvancedSearch() throws IOException {
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery build = fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery.build(Fixture.addApplication(downloadDatasetQueryAdvancedSearch));
        assertInstanceOf(DownloadDatasetQueryAdvancedSearch.class, build, "must be an advanced Search");
        final DownloadDatasetQueryAdvancedSearch advancedSearch = (DownloadDatasetQueryAdvancedSearch) build;
        final Set<ComponentFilters> componentFilters =
                advancedSearch.componentFilters();
        final Set<ComponentOrderBy> componentOrderBys =
                advancedSearch.componentOrderBy();
        final Set<String> componentSelects =
                advancedSearch.componentSelects();
        if (!CollectionUtils.isEmpty(componentSelects) && componentSelects.size() == 2) {
            componentSelects.stream().forEach(
                    id -> {
                        final List<String> expecteds = List.of("Date_day", "Date_time");
                        assertTrue(expecteds.contains(id), "id %s must be in %s".formatted(id, expecteds));
                    }
            );
        } else {
            fail("select must have size 2");
        }
        if (!CollectionUtils.isEmpty(componentFilters)) {
            if (componentFilters.stream().allMatch(f -> {
                switch (f) {
                    case final NoComponentFilters nocomponentFilters -> fail("must be defined");
                    case final ComponentFilterForInterval componentFilterForInterval -> {
                        switch (componentFilterForInterval) {
                            case ComponentFiltersForIntervalByNumeric componentFiltersForIntervalByNumeric -> {
                                testcomponent(componentFiltersForIntervalByNumeric, "Nombre d'individus", "value");
                                testIntervalValue(componentFiltersForIntervalByNumeric.intervalsValues(), "5", "40");
                            }
                            case final ComponentFiltersForIntervalByDate componentFiltersForIntervalByDate -> {
                                testcomponent(componentFiltersForIntervalByDate, "Date", "day");
                                testIntervalValue(componentFiltersForIntervalByDate.intervalsValues(), "01/01/1984", "04/01/1984");
                            }
                            case final ComponentFiltersForIntervalByTime componentFiltersForIntervalByTime -> {
                                testcomponent(componentFiltersForIntervalByTime, "Date", "time");
                                testIntervalValue(componentFiltersForIntervalByTime.intervalsValues(), "12:20:45", "16:21:32");
                            }
                            case final ComponentFiltersForIntervalByDateTime componentFiltersForIntervalByDateTime -> {
                                testcomponent(componentFiltersForIntervalByDateTime, "Date", "datetime");
                                testIntervalValue(componentFiltersForIntervalByDateTime.intervalsValues(), "01/01/1984 12:20:45", "01/01/1984 16:21:32");
                            }
                            case null, default -> fail("must be defined");


                        }
                    }
                    case final ComponentFilterSimpleSearch componentFilterSimpleSearch -> {
                        switch (componentFilterSimpleSearch) {
                            case final ComponentFiltersByReference componentFiltersByReference -> {
                                testcomponent(componentFiltersByReference, "projet", "value");
                                testFilter(componentFiltersByReference, "projet_manche");
                            }
                            case final ComponentFiltersByDate componentFiltersByDate -> {
                                testcomponent(componentFiltersByDate, "date", "day");
                                testFilter(componentFiltersByDate, "01/01/1984");
                                testFormat(componentFiltersByDate, "dd/MM/yyyy");
                            }
                            case final ComponentFiltersByTime componentFiltersByTime -> {
                                testcomponent(componentFiltersByTime, "date", "time");
                                testFilter(componentFiltersByTime, "01:12:43");
                                testFormat(componentFiltersByTime, "HH:mm:ss");
                            }
                            case final ComponentFiltersByDateTime componentFiltersByDateTime -> {
                                testcomponent(componentFiltersByDateTime, "date", "datetime");
                                testFilter(componentFiltersByDateTime, "01/01/1984 01:12:43");
                                testFormat(componentFiltersByDateTime, "dd/MM/yyyy HH:mm:ss");
                            }
                            case final ComponentFiltersByNumeric componentFiltersByNumeric -> {
                                testcomponent(componentFiltersByNumeric, "individus", "value");
                                testFilter(componentFiltersByNumeric, "12.3");
                            }
                            case final ComponentFiltersForWordByPlainText componentFiltersForWordByPlainText -> {
                                testcomponent(componentFiltersForWordByPlainText, "site", "bassin");
                                testFilter(componentFiltersForWordByPlainText, "lebassin");
                            }
                            case final ComponentFiltersForWordByRegexp componentFiltersForWordByRegexp -> {
                                testcomponent(componentFiltersForWordByRegexp, "site", "projet");
                                testFilter(componentFiltersForWordByRegexp, "l[ae]bassine+");
                            }
                            case null, default -> fail("must be defined");
                        }
                    }
                    case null, default -> fail("must be defined");
                }
                return true;
            })) ;
        } else if (componentFilters.size() != 1) {
            fail("componentFilters must not be empty");
        }
        if (!CollectionUtils.isEmpty(componentOrderBys) && componentOrderBys.size() == 1) {
            componentOrderBys.forEach(componentOrderBy -> {
                assertEquals("site_plateforme", componentOrderBy.componentKey());
                assertEquals(DataRepository.Order.ASC, componentOrderBy.order());
            });
        } else {
            fail("componentOrderBys must have size 1");
        }
    }

    /*@Test
    void TestSimpleSearch() {
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery build = fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery.build(downloadDatasetQuerySimpleSearch);
        assertInstanceOf(DownloadDatasetQuerySimpleSearch.class, build, "must be an advanced Search");
        final DownloadDatasetQuerySimpleSearch advancedSearch = (DownloadDatasetQuerySimpleSearch) build;
        final Set<AuthorizationDescription> expectedAuthorizationDescriptions = Set.of(
                new AuthorizationDescription(
                        new IntervalValues("1984-01-01", "1984-01-02"),
                        List.of(
                                List.of(
                                        new RequiredAuthorization("projet", Ltree.fromSql("projet_manche")),
                                        new RequiredAuthorization("localization", Ltree.fromSql("plateforme"))
                                )
                        )

                ),
                new AuthorizationDescription(
                        new IntervalValues("1984-01-03", "1984-01-04"),
                        List.of(
                                List.of(
                                        new RequiredAuthorization("projet", Ltree.fromSql("atlantique")),
                                        new RequiredAuthorization("localization", Ltree.fromSql("plateforme"))
                                )
                        )

                )
        );
        assertEquals(expectedAuthorizationDescriptions, advancedSearch.authorizationDescriptions());
    }*/

    @ParameterizedTest
    @MethodSource("fr.inra.oresing.rest.model.data.DownloadDatasetQueryAdvancedSearchTest$TestErrorParams#params")
    @Disabled
        //TODO
    void testErrors(final TestError testError) throws IOException {
        final String replacedJson = testError.from.replace(
                testError.replace,
                testError.by
        );

        fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery downloadDatasetQuery = (fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery) mapper.toObject(
                replacedJson,
                fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery.class);
        downloadDatasetQuery = Fixture.addApplication(downloadDatasetQuery);
        try {
            final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery build = fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery.build(downloadDatasetQuery);
            fail();
        } catch (final BadDownloadDatasetQuery e) {
            assertEquals(testError.message, e.getMessage());
            comparemap(testError.params, e.getParams());
            testError.params.forEach((key, value) -> {
                assertTrue(e.getParams().containsKey(key), "params must contains %s".formatted(key));
                assertEquals(value, e.getParams().get(key), "params %s must be %s".formatted(key, value));
            });
        }
    }

    static void comparemap(final Map<String, Object> map1, final Map map2) {
        assertEquals(map1.size(), map2.size());
        map1.forEach((key, value) -> {
            assertTrue(map2.containsKey(key), "params must contains %s".formatted(key));
            assertEquals(value, map2.get(key), "params %s must be %s".formatted(key, value));
        });
    }

    private static void testFormat(final WithFormat withFormat, final String format) {
        assertEquals(format, withFormat.format());
    }

    private static void testIntervalValue(final List<? extends WithIntervalValues> intervalsValues, final String from, final String to) {
        intervalsValues.forEach(
                intervalValue -> {
                    assertEquals(from, intervalValue.from(), "Interval value from must be %s".formatted(from));
                    assertEquals(to, intervalValue.to(), "Interval value to must be %s".formatted(to));
                });
    }

    record TestError(
            String from,
            String replace,
            String by,
            String message,
            Map<String, Object> params

    ) {
    }

    public class TestErrorParams {

        static Stream<TestError> params() {

            final String advancedSearchJson = """
                    {
                      "application": null,
                      "applicationNameOrId": null,
                      "dataType": null,
                      "offset": null,
                      "limit": 15,
                      "componentSelects": [{
                             "variable": "Date",
                             "component": "day"
                           },
                           {
                             "variable": "Date",
                             "component": "time"
                           }
                         ],
                      "componentFilters": [
                         {
                           "componentKey": {
                             "variable": "Date",
                             "component": "day"
                           },
                           "type": "date",
                           "format": "dd/MM/yyyy",
                           "intervalValues": {
                             "from": "01/01/1984",
                             "to": "04/01/1984"
                           }
                         },
                         {
                           "componentKey": {
                             "variable": "Date",
                             "component": "time"
                           },
                           "type": "time",
                           "format": "HH:mm:ss",
                           "intervalValues": {
                             "from": "12:20:45",
                             "to": "16:21:32"
                           }
                         },
                         {
                           "componentKey": {
                             "variable": "Date",
                             "component": "datetime"
                           },
                           "type": "datetime",
                           "format": "dd/MM/yyyy HH:mm:ss",
                           "intervalValues": {
                             "from": "01/01/1984 12:20:45",
                             "to": "01/01/1984 16:21:32"
                           }
                         },
                         {
                           "componentKey": {
                             "variable": "Nombre d'individus",
                             "component": "value"
                           },
                           "type": "numeric",
                           "intervalValues": {
                             "from": "5",
                             "to": "40"
                           },
                           "isRegExp": null
                         },
                         {
                           "componentKey": {
                             "variable": "date",
                             "component": "day"
                           },
                           "type": "date",
                           "filter": "01/01/1984",
                           "format": "dd/MM/yyyy"
                         },
                         {
                           "componentKey": {
                             "variable": "date",
                             "component": "datetime"
                           },
                           "type": "datetime",
                           "filter": "01/01/1984 01:12:43",
                           "format": "dd/MM/yyyy HH:mm:ss"
                         },
                         {
                           "componentKey": {
                             "variable": "date",
                             "component": "time"
                           },
                           "type": "time",
                           "filter": "01:12:43",
                           "format": "HH:mm:ss"
                         },
                         {
                           "componentKey": {
                             "variable": "individus",
                             "component": "value"
                           },
                           "type": "numeric",
                           "filter": "12.3"
                         },
                         {
                           "componentKey": {
                             "variable": "site",
                             "component": "bassin"
                           },
                           "filter": "lebassin"
                         },
                         {
                           "componentKey": {
                             "variable": "site",
                             "component": "projet"
                           },
                           "filter": "l[ae]bassine+",
                           "intervalValues": null,
                           "isRegExp": true
                         },
                         {
                           "componentKey": {
                             "variable": "projet",
                             "component": "value"
                           },
                           "filter": "projet_manche",
                           "type": "reference"
                         }
                       ],
                      "componentOrderBy": [
                        {
                          "componentKey": {
                            "variable": "site",
                            "component": "plateforme"
                          },
                          "order": "ASC",
                          "type": null,
                          "format": null
                        }
                      ]
                    }""";
            return Stream.of(
                    new TestError(
                            advancedSearchJson,
                            """
                                    ,
                                           "intervalValues": {
                                             "from": "01/01/1984",
                                             "to": "04/01/1984"
                                           }
                                    """,
                            "",
                            BadDownloadDatasetQuery.FILTER_MISSING_FILTER_OR_INTERVAL,
                            Map.of(
                                    "variable", "Date",
                                    "component", "day"
                            )
                    ),
                    new TestError(
                            advancedSearchJson,
                            """
                                           "type": "date",
                                    """,
                            "",
                            BadDownloadDatasetQuery.MISSING_TYPE_FOR_INTERVAL_VALUE,
                            Map.of(
                                    "provided", "null",
                                    "accepted", "date,time,datetime,numeric",
                                    "variable", "Date",
                                    "component", "day"
                            )
                    ),
                    new TestError(
                            advancedSearchJson,
                            """
                                           "format": "dd/MM/yyyy",
                                    """,
                            "",
                            BadDownloadDatasetQuery.MISSING_FORMAT_FOR_INTERVAL_VALUE,

                            Map.of(
                                    "variable", "Date",
                                    "component", "day"
                            )
                    )
            );
        }
    }
}