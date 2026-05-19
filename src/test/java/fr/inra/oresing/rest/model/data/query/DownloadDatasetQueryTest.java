package fr.inra.oresing.rest.model.data.query;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;
import fr.inra.oresing.domain.application.configuration.checker.DateChecker;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.domain.repository.data.DataRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.time.LocalDate;
import java.util.*;

@Slf4j
@org.junit.jupiter.api.Tag("domain.model")
class DownloadDatasetQueryTest {
    public static final long OFFSET = 6L;
    public static final long LIMIT = 10L;
    public static final Locale FRENCH = Locale.FRENCH;
    public static final UUID UUID_1 = java.util.UUID.randomUUID();
    public static final UUID UUID_2 = java.util.UUID.randomUUID();
    public static final Set<String> ROW_IDS = Set.of(UUID_1.toString(), UUID_2.toString());
    public static final Set<Ltree> NATURAL_KEYS = Set.of(Ltree.fromSql("key1"), Ltree.fromSql("key2"));
    public static final Set<String> COMPONENT_ORDER_BY = Set.of("orderby1", "orderby2");
    public static final Set<String> COMPONENT_SELECT = Set.of("select1", "select2");
    public static final IntervalValues INTERVAL_VALUE_1 = new IntervalValues("2014-01-02", "2015-05-01");
    public static final AuthorizationDescription AUTHORIZATION_1 = new AuthorizationDescription(
            INTERVAL_VALUE_1,
            Map.of("auth1", Ltree.fromSql("auth1"))
    );
    public static final IntervalValues INTERVAL_VALUE_2 = new IntervalValues("2016-05-08", "2017-12-31");
    public static final AuthorizationDescription AUTHORIZATION_2 = new AuthorizationDescription(
            INTERVAL_VALUE_2,
            Map.of("auth2", Ltree.fromSql("auth2"))
    );
    public static final BasicComponent BASIC_COMPONENT = new BasicComponent(
            ComponentDescription.ComponentDescriptionType.BasicComponent,
            "key1",
            null,
            Set.of(Tag.OrderTag.ORDER_TAG_ONE),
            "Basic",
            "basic",
            List.of(Locale.FRENCH),
            true,
            ComponentPresenceConstraint.MANDATORY,
            new DateChecker(
                    CheckerDescription.CheckerDescriptionType.DateChecker,
                    Multiplicity.MANY,
                    true,
                    "yyyy-MM-dd",
                    LocalDate.of(2012, 5, 24),
                    LocalDate.of(2012, 5, 25),
                    "1 Day"
            ),
            "toto"

    );
    public static final ComputedComponent COMPUTED_COMPONENT = new ComputedComponent(
            ComponentDescription.ComponentDescriptionType.ComputedComponent,
            "key2",
            Set.of(Tag.buildTag("computed")),
            "Basic",
            List.of(Locale.FRENCH),
            true,
            ComponentPresenceConstraint.MANDATORY,
            new ReferenceChecker(
                    CheckerDescription.CheckerDescriptionType.ReferenceChecker,
                    "key2",
                    Multiplicity.ONE,
                    true,
                    "key1",
                    false,
                    false
            ),
            new ComputationChecker(
                    CheckerDescription.CheckerDescriptionType.GroovyExpressionChecker,
                    Multiplicity.ONE,
                    false,
                    "ref1",
                    Set.of("ref1"),
                    Set.of()),
            "toto"

    );
    private static final String DATATYPE_NAME = "datatypeName";
    @Mock
    Application application;
    DownloadDatasetQuery downloadDatasetQuery;

    private static OutPut buildOutput() {
        return new OutPut(FRENCH, OFFSET, LIMIT);
    }

    private static Set<ComponentFilters> buildComponentFilter() {
        return Set.of(
                new ComponentFilters(
                        "key1",
                        List.of("filter1", "filter2"),
                        List.of(
                                INTERVAL_VALUE_1,
                                INTERVAL_VALUE_2
                        ),
                        false)
        );
    }

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        downloadDatasetQuery = new DownloadDatasetQuery(application, DATATYPE_NAME);
    }

    @Test
    void build() {
        final OutPut outPut = buildOutput();
        downloadDatasetQuery.setOutPut(outPut);
        downloadDatasetQuery.setOffset(OFFSET);
        downloadDatasetQuery.setLimit(LIMIT);
        downloadDatasetQuery.setRowIds(ROW_IDS);
        downloadDatasetQuery.setHorizontalDisplay(true);
        downloadDatasetQuery.setNaturalKeys(NATURAL_KEYS);
        downloadDatasetQuery.setComponentSelects(COMPONENT_SELECT);
        final Set<ComponentFilters> componentFilters = buildComponentFilter();
        downloadDatasetQuery.setComponentFilters(componentFilters);
        final Set<ComponentOrderBy> componentOrderBy = buildComponentOrderBy();
        downloadDatasetQuery.setComponentOrderBy(componentOrderBy);
        final Set<AuthorizationDescription> authorizationDescriptions = buildAuthorizationDescriptions();
        downloadDatasetQuery.setAuthorizationDescriptions(authorizationDescriptions);
        downloadDatasetQuery.setHorizontalDisplay(true);
        StandardDataDescription dataDescription = Mockito.mock(StandardDataDescription.class);
        Mockito.doReturn(Optional.of(dataDescription)).when(application).findData(ArgumentMatchers.eq(DATATYPE_NAME));
        Mockito.doReturn(true).when(application).existsData(ArgumentMatchers.eq(DATATYPE_NAME));
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery buildedDownloadDatasetQuery = DownloadDatasetQuery.build(downloadDatasetQuery);
        Assertions.assertEquals(application, buildedDownloadDatasetQuery.application());
        Assertions.assertEquals(DATATYPE_NAME, buildedDownloadDatasetQuery.dataName());
        Assertions.assertEquals(outPut, buildedDownloadDatasetQuery.outPut());
        Assertions.assertEquals(COMPONENT_SELECT, buildedDownloadDatasetQuery.componentSelects());
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::componentKey).anyMatch("key1"::equals));
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::componentKey).anyMatch("key2"::equals));
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::order).anyMatch(DataRepository.Order.ASC::equals));
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::order).anyMatch(DataRepository.Order.DESC::equals));
        Assertions.assertEquals(NATURAL_KEYS, ((DownloadDatasetQueryByNaturalKey) buildedDownloadDatasetQuery).naturalOrHierarchicalKey());
        Assertions.assertTrue(buildedDownloadDatasetQuery.horizontalDisplay());
    }

    @Test
    void buildWithRowIds() {
        final OutPut outPut = buildOutput();
        downloadDatasetQuery.setRowIds(ROW_IDS);
        downloadDatasetQuery.setOutPut(outPut);
        final Set<ComponentOrderBy> componentOrderBy = buildComponentOrderBy();
        downloadDatasetQuery.setComponentOrderBy(componentOrderBy);
        StandardDataDescription dataDescription = Mockito.mock(StandardDataDescription.class);
        Mockito.doReturn(Optional.of(dataDescription)).when(application).findData(ArgumentMatchers.eq(DATATYPE_NAME));
        Mockito.doReturn(true).when(application).existsData(ArgumentMatchers.eq(DATATYPE_NAME));
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery buildedDownloadDatasetQuery = DownloadDatasetQuery.build(downloadDatasetQuery);
        Assertions.assertEquals(application, buildedDownloadDatasetQuery.application());
        Assertions.assertEquals(DATATYPE_NAME, buildedDownloadDatasetQuery.dataName());
        Assertions.assertEquals(new OutPut(Locale.FRENCH, 0L, null), buildedDownloadDatasetQuery.outPut());
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::componentKey).anyMatch("key1"::equals));
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::componentKey).anyMatch("key2"::equals));
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::order).anyMatch(DataRepository.Order.ASC::equals));
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::order).anyMatch(DataRepository.Order.DESC::equals));
        Assertions.assertEquals(DownloadDatasetQueryByRowId.class, buildedDownloadDatasetQuery.getClass());
        Assertions.assertTrue(((DownloadDatasetQueryByRowId) buildedDownloadDatasetQuery).rowIds().stream().map(dataRowIds -> dataRowIds.id()).map(UUID::toString).allMatch(ROW_IDS::contains));
        Assertions.assertFalse(buildedDownloadDatasetQuery.horizontalDisplay());
    }

    @Test
    void buildWithAdvancedSearch() {
        final OutPut outPut = buildOutput();
        final Set<ComponentFilters> componentFilters = buildComponentFilter();
        downloadDatasetQuery.setComponentFilters(componentFilters);
        downloadDatasetQuery.setOutPut(outPut);
        final Set<ComponentOrderBy> componentOrderBy = buildComponentOrderBy();
        downloadDatasetQuery.setComponentOrderBy(componentOrderBy);
        StandardDataDescription dataDescription = Mockito.mock(StandardDataDescription.class);
        Mockito.doReturn(Optional.of(dataDescription)).when(application).findData(ArgumentMatchers.eq(DATATYPE_NAME));
        Mockito.doReturn(true).when(application).existsData(ArgumentMatchers.eq(DATATYPE_NAME));
        Map<String, ComponentDescription> componentDescription = new HashMap<>();
        componentDescription.put("basic", BASIC_COMPONENT);
        componentDescription.put("computed", COMPUTED_COMPONENT);
        Mockito.doReturn(componentDescription).when(dataDescription).componentDescriptions();
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery buildedDownloadDatasetQuery = DownloadDatasetQuery.build(downloadDatasetQuery);
        Assertions.assertEquals(application, buildedDownloadDatasetQuery.application());
        Assertions.assertEquals(DATATYPE_NAME, buildedDownloadDatasetQuery.dataName());
        Assertions.assertEquals(new OutPut(Locale.FRENCH, 0L, null), buildedDownloadDatasetQuery.outPut());
        Assertions.assertNull(buildedDownloadDatasetQuery.componentSelects());
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::componentKey).anyMatch("key1"::equals));
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::componentKey).anyMatch("key2"::equals));
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::order).anyMatch(DataRepository.Order.ASC::equals));
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::order).anyMatch(DataRepository.Order.DESC::equals));
        Assertions.assertEquals(DownloadDatasetQueryAdvancedSearch.class, buildedDownloadDatasetQuery.getClass());
        Assertions.assertTrue(((DownloadDatasetQueryAdvancedSearch) buildedDownloadDatasetQuery).componentFilters().stream().map(fr.inra.oresing.domain.data.read.query.ComponentFilters::multiplicity).allMatch(Arrays.stream(Multiplicity.values()).toList()::contains));
        Assertions.assertFalse(buildedDownloadDatasetQuery.horizontalDisplay());
    }

    @Test
    void buildWithNoFilter() {
        StandardDataDescription dataDescription = Mockito.mock(StandardDataDescription.class);
        final Set<ComponentOrderBy> componentOrderBy = buildComponentOrderBy();
        downloadDatasetQuery.setComponentOrderBy(componentOrderBy);
        Mockito.doReturn(Optional.of(dataDescription)).when(application).findData(ArgumentMatchers.eq(DATATYPE_NAME));
        Mockito.doReturn(true).when(application).existsData(ArgumentMatchers.eq(DATATYPE_NAME));
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery buildedDownloadDatasetQuery = DownloadDatasetQuery.build(downloadDatasetQuery);
        Assertions.assertEquals(application, buildedDownloadDatasetQuery.application());
        Assertions.assertEquals(DATATYPE_NAME, buildedDownloadDatasetQuery.dataName());
        Assertions.assertEquals(new OutPut(Locale.FRENCH, 0L, null), buildedDownloadDatasetQuery.outPut());
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::componentKey).anyMatch("key1"::equals));
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::componentKey).anyMatch("key2"::equals));
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::order).anyMatch(DataRepository.Order.ASC::equals));
        Assertions.assertTrue(buildedDownloadDatasetQuery.componentOrderBy().stream().map(fr.inra.oresing.domain.data.read.query.ComponentOrderBy::order).anyMatch(DataRepository.Order.DESC::equals));
        Assertions.assertEquals(DownloadDatasetQueryNoFilter.class, buildedDownloadDatasetQuery.getClass());
        Assertions.assertFalse(buildedDownloadDatasetQuery.horizontalDisplay());
    }

    @Test
    void patternDefinitionCount() {
        Mockito.doReturn(4L).when(application).patternDefinitionCount(Mockito.eq(DATATYPE_NAME));
        final long patternDefinitionCount = downloadDatasetQuery.patternDefinitionCount();
        Assertions.assertEquals(4L, patternDefinitionCount);
    }

    @Test
    void getApplication() {
        Assert.assertEquals(application, downloadDatasetQuery.getApplication());
    }

    @Test
    void getDataName() {
        Assert.assertEquals(DATATYPE_NAME, downloadDatasetQuery.getDataName());
    }

    @Test
    void getOutPut() {
        final OutPut outPut = buildOutput();
        downloadDatasetQuery.setOutPut(outPut);
        Assert.assertEquals(outPut, downloadDatasetQuery.getOutPut());
    }

    @Test
    void getOffset() {
        downloadDatasetQuery.setOffset(OFFSET);
        Assert.assertEquals((Long) OFFSET, downloadDatasetQuery.getOffset());
    }

    @Test
    void getLimit() {
        downloadDatasetQuery.setLimit(LIMIT);
        Assert.assertEquals((Long) LIMIT, downloadDatasetQuery.getLimit());
    }

    @Test
    void getRowIds() {
        downloadDatasetQuery.setRowIds(ROW_IDS);
        Assertions.assertEquals(ROW_IDS, downloadDatasetQuery.getRowIds());
    }

    @Test
    void getNaturalKeys() {
        downloadDatasetQuery.setNaturalKeys(NATURAL_KEYS);
        Assert.assertEquals(NATURAL_KEYS, downloadDatasetQuery.getNaturalKeys());
    }

    @Test
    void getComponentSelects() {
        downloadDatasetQuery.setComponentSelects(COMPONENT_SELECT);
        Assert.assertEquals(COMPONENT_SELECT, downloadDatasetQuery.getComponentSelects());
    }

    @Test
    void getComponentFilters() {
        Set<ComponentFilters> componentFilters = buildComponentFilter();
        downloadDatasetQuery.setComponentFilters(componentFilters);
        Assert.assertEquals(componentFilters, downloadDatasetQuery.getComponentFilters());
    }

    @Test
    void getComponentOrderBy() {
        final Set<ComponentOrderBy> componentOrderBy = buildComponentOrderBy();
        downloadDatasetQuery.setComponentOrderBy(componentOrderBy);
        Assert.assertEquals(componentOrderBy, downloadDatasetQuery.getComponentOrderBy());
    }

    private Set<ComponentOrderBy> buildComponentOrderBy() {
        return Set.of(
                new ComponentOrderBy("key1", DataRepository.Order.ASC),
                new ComponentOrderBy("key2", DataRepository.Order.DESC)
        );
    }

    @Test
    void getAuthorizationDescriptions() {
        Set<AuthorizationDescription> authorizationDescriptions = buildAuthorizationDescriptions();
        downloadDatasetQuery.setAuthorizationDescriptions(authorizationDescriptions);
        Assertions.assertEquals(authorizationDescriptions, downloadDatasetQuery.getAuthorizationDescriptions());
    }

    private Set<AuthorizationDescription> buildAuthorizationDescriptions() {
        return Set.of(
                AUTHORIZATION_1,
                AUTHORIZATION_2
        );
    }

    @Test
    void isHorizontalDisplay() {
        Assert.assertFalse(downloadDatasetQuery.isHorizontalDisplay());
        downloadDatasetQuery.setHorizontalDisplay(true);
        Assert.assertTrue(downloadDatasetQuery.isHorizontalDisplay());
    }

    @Test
    void setApplication() {
        downloadDatasetQuery.setApplication(null);
        Assertions.assertNull(downloadDatasetQuery.getApplication());
        downloadDatasetQuery.setApplication(application);
        Assert.assertEquals(application, downloadDatasetQuery.getApplication());
    }

    @Test
    void setDataName() {
        downloadDatasetQuery.setDataName(null);
        Assertions.assertNull(downloadDatasetQuery.getDataName());
        downloadDatasetQuery.setDataName(DATATYPE_NAME);
        Assert.assertEquals(DATATYPE_NAME, downloadDatasetQuery.getDataName());
    }

    @Test
    void testConstructorEmpty() {
        downloadDatasetQuery = new DownloadDatasetQuery();
        Assertions.assertNull(downloadDatasetQuery.getApplication());
        Assertions.assertNull(downloadDatasetQuery.getDataName());
        Assertions.assertNull(downloadDatasetQuery.getOutPut());
        Assertions.assertNull(downloadDatasetQuery.getOffset());
        Assertions.assertNull(downloadDatasetQuery.getLimit());
        Assertions.assertNull(downloadDatasetQuery.getRowIds());
        Assertions.assertNull(downloadDatasetQuery.getNaturalKeys());
        Assertions.assertNull(downloadDatasetQuery.getComponentSelects());
        Assertions.assertNull(downloadDatasetQuery.getComponentFilters());
    }

    @Test
    void testConstructor1() {
        final Set<ComponentFilters> componentFilters = buildComponentFilter();
        final Set<ComponentOrderBy> componentOrderBy = buildComponentOrderBy();
        downloadDatasetQuery = new DownloadDatasetQuery(
                OFFSET,
                LIMIT,
                COMPONENT_SELECT,
                componentFilters,
                componentOrderBy
        );
        Assertions.assertNull(downloadDatasetQuery.getApplication());
        Assertions.assertNull(downloadDatasetQuery.getDataName());
        Assertions.assertNull(downloadDatasetQuery.getOutPut());
        Assertions.assertEquals((Long) OFFSET, downloadDatasetQuery.getOffset());
        Assertions.assertEquals((Long) LIMIT, downloadDatasetQuery.getLimit());
        Assertions.assertNull(downloadDatasetQuery.getRowIds());
        Assertions.assertNull(downloadDatasetQuery.getNaturalKeys());
        Assertions.assertEquals(COMPONENT_SELECT, downloadDatasetQuery.getComponentSelects());
        Assertions.assertEquals(componentFilters, downloadDatasetQuery.getComponentFilters());
        Assertions.assertEquals(componentOrderBy, downloadDatasetQuery.getComponentOrderBy());
        Assertions.assertNull(downloadDatasetQuery.getAuthorizationDescriptions());
        Assertions.assertFalse(downloadDatasetQuery.isHorizontalDisplay());
    }

    @Test
    void testConstructor2() {
        final Set<ComponentFilters> componentFilters = buildComponentFilter();
        final Set<ComponentOrderBy> componentOrderBy = buildComponentOrderBy();
        final Set<AuthorizationDescription> authorizationDescriptions = buildAuthorizationDescriptions();
        downloadDatasetQuery = new DownloadDatasetQuery(
                application,
                OFFSET,
                DATATYPE_NAME,
                COMPONENT_SELECT,
                componentFilters,
                componentOrderBy,
                authorizationDescriptions,
                LIMIT,
                ROW_IDS
        );
        Assertions.assertEquals(application, downloadDatasetQuery.getApplication());
        Assertions.assertEquals(DATATYPE_NAME, downloadDatasetQuery.getDataName());
        Assertions.assertNull(downloadDatasetQuery.getOutPut());
        Assertions.assertEquals((Long) OFFSET, downloadDatasetQuery.getOffset());
        Assertions.assertEquals((Long) LIMIT, downloadDatasetQuery.getLimit());
        Assertions.assertEquals(ROW_IDS, downloadDatasetQuery.getRowIds());
        Assertions.assertNull(downloadDatasetQuery.getNaturalKeys());
        Assertions.assertEquals(COMPONENT_SELECT, downloadDatasetQuery.getComponentSelects());
        Assertions.assertEquals(componentFilters, downloadDatasetQuery.getComponentFilters());
        Assertions.assertEquals(componentOrderBy, downloadDatasetQuery.getComponentOrderBy());
        Assertions.assertEquals(authorizationDescriptions, downloadDatasetQuery.getAuthorizationDescriptions());
        Assertions.assertFalse(downloadDatasetQuery.isHorizontalDisplay());
    }
}