package fr.inra.oresing.rest.services;

import com.google.common.collect.Lists;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.rest.ViewStrategy;
import fr.inra.oresing.rest.exceptions.views.FieldNameTooLongForSqlFieldException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.logging.log4j.util.Strings;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.function.Predicate;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@Transactional()
public class RelationalService implements ServiceContainerBean, InitializingBean, DisposableBean {
    private static final String IDENTIFIER_PATTERN = "[a-z][a-z_0-9]{%d,%d}";
    @Autowired
    private SqlService db;
    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    @Autowired
    private OreSiRepository repository;
    @Value("${viewStrategy:DISABLED}")
    private ViewStrategy viewStrategy;

    public static Predicate<String> getIsValidIdentifierPattern(int min, int max) {
        int min1 = min > 0 ? min : 1;
        int max1 = max < 64 ? max : 63;
        return Pattern.compile(String.format(IDENTIFIER_PATTERN, min1 - 1, max1 - 1)).asMatchPredicate();
    }

    public void createViews(final String appName) {
        createViews(appName, viewStrategy);
    }


    public void createViews(final String appName, final ViewStrategy viewStrategy) {
        //TODO
        /*Application app = getApplication(appName);
        createViews(app, viewStrategy);*/
    }

    private Application getApplication(final String appName) {
        return repository.application().findApplication(appName);
    }

    void createViews(final UUID appId, final ViewStrategy viewStrategy) {
        final Application app = getApplication(appId.toString());
        createViews(app, viewStrategy);
    }

    public void createViews(final Application app, final ViewStrategy viewStrategy) {
       /* log.info("Création des vues : \u001B[32m{}\u001B[0m", app.getName());
        if (viewStrategy.isEnabled()) {
            authenticationService.resetRole();
            final SchemaCreationCommand schemaCreationCommand = getSchemaCreationCommand(app, viewStrategy);
            create(schemaCreationCommand);
        } else {
            if (log.isInfoEnabled()) {
                log.info("les vues relationnelles sont désactivées, on ne crée pas les vues pour {}", app.getName());
            }
        }*/
    }

    public void dropViews(final String appName) {
        dropViews(appName, viewStrategy);
    }

    void dropViews(final String appName, final ViewStrategy viewStrategy) {
        /*if (viewStrategy.isEnabled()) {
            authenticationService.resetRole();
            final Application app = getApplication(appName);
            final SchemaCreationCommand schemaCreationCommand = getSchemaCreationCommand(app, viewStrategy);
            drop(schemaCreationCommand);
        } else {
            if (log.isInfoEnabled()) {
                log.info("les vues relationnelles sont désactivées, on ne supprime pas les vues pour {}", appName);
            }
        }*/
    }

    @Transactional()
    public void create(final SchemaCreationCommand schemaCreationCommand) {
        final ViewStrategy viewStrategy = schemaCreationCommand.viewStrategy();
        Application application = schemaCreationCommand.application();
        final UUID appId = application.getId();
        final OreSiRightOnApplicationRole owner = OreSiRightOnApplicationRole.adminOn(application);
        db.createSchema(schemaCreationCommand.schema(), owner);

        for (final ViewCreationCommand viewCreationCommand : schemaCreationCommand.views()) {
            final SqlTable view = viewCreationCommand.view();
            final String viewSql = viewCreationCommand.sql();
            if (viewStrategy == ViewStrategy.VIEW) {
                db.createView(view, viewSql);
                db.setViewOwner(view, owner);
            } else if (viewStrategy == ViewStrategy.TABLE) {
                db.createTable(view, viewSql);
                db.enableRowLevelSecurity(view);
                db.setTableOwner(view, owner);
//                for (ApplicationRight applicationRight : ApplicationRight.values()) {
//                    OreSiRightOnApplicationRole roleThatCanReadViews = applicationRight.getRole(appId);
//                    namedParameterJdbcTemplate.execute("GRANT USAGE ON SCHEMA " + schemaName + " TO " + roleThatCanReadViews.getSqlIdentifier(), PreparedStatement::execute);
//                    namedParameterJdbcTemplate.execute("GRANT SELECT ON ALL TABLES IN SCHEMA " + schemaName + " TO " + roleThatCanReadViews.getSqlIdentifier(), PreparedStatement::execute);
//                }

                // TODO reste à poser des contraintes de clés étrangères et des indexes
            } else {
                throw ViewStrategy.getError(viewStrategy);
            }
        }
    }

    private void drop(final SchemaCreationCommand schemaCreationCommand) {
        final ViewStrategy viewStrategy = schemaCreationCommand.viewStrategy();
        final List<ViewCreationCommand> reverse = Lists.reverse(schemaCreationCommand.views());
        for (final ViewCreationCommand viewCreationCommand : reverse) {
            final SqlTable view = viewCreationCommand.view();
            if (viewStrategy == ViewStrategy.VIEW) {
                final String formatted = """
                        **************************************
                        *     Suppression de la vue %s       *
                        **************************************
                        """.formatted(view.name());
                log.info(formatted);
                db.dropView(view);
            } else if (viewStrategy == ViewStrategy.TABLE) {
                final String formatted = """
                        **************************************
                        *     Suppression de la table %s     *
                        **************************************
                        """.formatted(view.name());
                log.info(formatted);
                db.dropTable(view);
            } else {
                throw ViewStrategy.getError(viewStrategy);
            }
        }
        db.dropSchema(schemaCreationCommand.schema());
    }
/*
    public SchemaCreationCommand getSchemaCreationCommand(final BrokenApplication app, final ViewStrategy viewStrategy) {
        final SqlSchemaForRelationalViewsForApplication sqlSchema = SqlSchema.forRelationalViewsOf(app, viewStrategy);
        final List<ViewCreationCommand> views = new LinkedList<>();
        //views.addAll(getViewsForReferences(sqlSchema, app));
        return new SchemaCreationCommand(app, sqlSchema, views, viewStrategy);
    }*/

    /*

    private List<ViewCreationCommand> getViewsForReferences(final SqlSchemaForRelationalViewsForApplication
                                                                    sqlSchema, final Application app) {
        final UUID appId = app.getId();
        final List<ViewCreationCommand> views = new LinkedList<>();
        for (final Map.Entry<String, Configuration.ReferenceDescription> entry : app.getConfiguration().getReferences().entrySet()) {
            final String referenceColumnName = entry.getKey();
            log.info("Création du schéma du référentiel \u001B[32m{}\u001B[0m de l'application \u001B[32m{}\u001B[0m", referenceColumnName, app.getName());
            final Configuration.ReferenceDescription referenceDescription = entry.getValue();

            final ImmutableMap<DataColumn, SqlPrimitiveType> sqlTypePerColumns = checkerFactory.getReferenceValidationLineCheckers(app, referenceColumnName).stream()
                    .filter(lc -> lc.getUnderlyingType() instanceof ReferenceType)
                    .collect(ImmutableMap.toImmutableMap(rlc -> (DataColumn) rlc.getTarget(), LineCheckerWarper::getSqlType));

            final ImmutableMap<DataColumn, Multiplicity> declaredMultiplicityPerReferenceColumns = checkerFactory.getReferenceValidationLineCheckers(app, referenceColumnName).stream()
                    .filter(lc -> lc.getUnderlyingType() instanceof ReferenceType)
                    .collect(ImmutableMap.toImmutableMap(rlc -> (DataColumn) rlc.getTarget(), rt -> rt.getMultiplicity()));

            final ImmutableSetMultimap<Multiplicity, DataColumn> allReferenceColumnsPerMultiplicity = referenceDescription.doGetStaticColumns().stream()
                    .map(DataColumn::new)
                    .collect(ImmutableSetMultimap.toImmutableSetMultimap(referenceColumn -> declaredMultiplicityPerReferenceColumns.getOrDefault(referenceColumn, Multiplicity.ONE), Function.identity()));

            final String columnsAsSchema = allReferenceColumnsPerMultiplicity.values().stream()
                    .map(referenceColumn -> {
                        final String columnName = IdentifierTest.forReference(referenceColumn).testAndQuote();
                        final String columnDeclaration = String.format("%s %s", columnName, SqlPrimitiveType.TEXT);
                        return columnDeclaration;
                    })
                    .collect(Collectors.joining(", ", "(", ")"));
            final String quotedReferenceType = IdentifierTest.forStringIdentifier(referenceColumnName).testAndQuote();
            final String castedColumnSelect = allReferenceColumnsPerMultiplicity.values().stream()
                    .map(referenceColumn -> {
                        final String columnName = IdentifierTest.forReference(referenceColumn).testAndQuote();
                        final SqlPrimitiveType columnType = sqlTypePerColumns.getOrDefault(referenceColumn, SqlPrimitiveType.TEXT);
                        final Multiplicity multiplicity = declaredMultiplicityPerReferenceColumns.getOrDefault(referenceColumn, Multiplicity.ONE);
                        String columnDeclaration = null;
                        if (multiplicity == Multiplicity.ONE) {
                            columnDeclaration = String.format("%s.%s::%s", quotedReferenceType, columnName, columnType.getSql());
                        } else if (multiplicity == Multiplicity.MANY) {
                            columnDeclaration = String.format("ARRAY(SELECT JSONB_ARRAY_ELEMENTS_TEXT(%s.%s::JSONB))::%s[] AS %s", quotedReferenceType, columnName, columnType.getSql(), columnName);
                        } else {
                            //TODO throw Multiplicity.getError(multiplicity);
                        }
                        return columnDeclaration;
                    })
                    .collect(Collectors.joining(", "));

            // par example "projet"(nom_en text, nom_fr text, nom_key text, definition_en text, definition_fr text)
            final String schemaDeclaration = quotedReferenceType + columnsAsSchema;

            final String quotedViewIdColumnName = IdentifierTest.forStringIdentifier(referenceColumnName).forId().testAndQuote();
            final String quotedViewHierarchicalKeyColumnName = IdentifierTest.forStringIdentifier(referenceColumnName).forHierachicalKey().testAndQuote();
            final String quotedViewNaturalKeyColumnName = IdentifierTest.forStringIdentifier(referenceColumnName).forNaturalKey().testAndQuote();
            final String referenceValueTableName = SqlSchema.forApplication(app).referenceValue().getSqlIdentifier();
            final String whereClause = " referenceType = '" + referenceColumnName + "' and application = '" + appId + "'::uuid";
            final String referenceView = """
                    select
                        referenceValue.id as %s,
                        referenceValue.hierarchicalKey as %s,
                        referenceValue.naturalKey as %s, %s
                    from %s, jsonb_to_record(referenceValue.refValues) as %s
                    where %s
                    """
                    .formatted(quotedViewIdColumnName, quotedViewHierarchicalKeyColumnName, quotedViewNaturalKeyColumnName, castedColumnSelect, referenceValueTableName, schemaDeclaration, whereClause);

            if (log.isTraceEnabled()) {
                log.trace("pour le référentiel {}, la requête pour avoir un vue relationnelle des données JSON est {}", referenceColumnName, referenceView);
            }

            final SqlTable view = sqlSchema.forReferenceType(referenceColumnName);
            views.add(new ViewCreationCommand(view, referenceView));

            final Set<ViewCreationCommand> associationViews = allReferenceColumnsPerMultiplicity.get(Multiplicity.MANY).stream()
                    .map(referenceColumn -> {
                        final String columnNameForOneValueFromTheManyArray = IdentifierTest.forReference(referenceColumn).forOneValueFromTheManyArray().testAndQuote();
                        final String columnFromReferenceViewThatContainsTheForeignKeysArray = IdentifierTest.forReference(referenceColumn).testAndQuote();
                        final String associationViewPattern = String.join("\n"
                                , "SELECT %s, %s"
                                , "FROM %s"
                                , "JOIN LATERAL"
                                , "    UNNEST(%s) %s ON TRUE"
                        );
                        final String associationView = String.format(associationViewPattern
                                , quotedViewHierarchicalKeyColumnName
                                , columnNameForOneValueFromTheManyArray
                                , view.getSqlIdentifier()
                                , columnFromReferenceViewThatContainsTheForeignKeysArray
                                , columnNameForOneValueFromTheManyArray
                        );
                        return new ViewCreationCommand(sqlSchema.forAssociation(referenceColumnName, referenceColumn), associationView);
                    })
                    .collect(Collectors.toUnmodifiableSet());

            views.addAll(associationViews);

            final AtomicInteger dynamicColumnCount = new AtomicInteger(1);
            final Set<ViewCreationCommand> dynamicColumnViews = referenceDescription.getDynamicColumns().keySet().stream()
                    .map(dynamicColumn -> {
                        final String sqlPattern = String.join("\n"
                                , "SELECT"
                                , "    referenceValue.hierarchicalKey AS %s,"
                                , "    (jsonb_each_text(referenceValue.refValues->'%s')).key::LTREE AS %s,"
                                , "    (jsonb_each_text(referenceValue.refValues->'%s')).value"
                                , "   FROM %s"
                                , "  WHERE %s"
                        );
                        final String dynamicColumnsView = String.format(sqlPattern,
                                quotedViewHierarchicalKeyColumnName,
                                dynamicColumn,
                                IdentifierTest.forStringIdentifier(dynamicColumn).forDynamicReferenceHierachicakKey(dynamicColumnCount.getAndIncrement()).testAndQuote(),
                                dynamicColumn,
                                referenceValueTableName,
                                whereClause
                        );
                        return new ViewCreationCommand(sqlSchema.forAssociation(referenceColumnName, new DataColumn(dynamicColumn)), dynamicColumnsView);
                    })
                    .collect(Collectors.toUnmodifiableSet());

            views.addAll(dynamicColumnViews);
        }
        return views;
    }*/

    public List<Map<String, Object>> readView(final String appName, final String dataType, final ViewStrategy viewStrategy) {
//        authRepository.setRoleForClient();
        final Application application = getApplication(appName);
        final SqlTable view = SqlSchema.forRelationalViewsOf(application, viewStrategy).forDataType(dataType);
        return namedParameterJdbcTemplate.queryForList("select * from " + view.getSqlIdentifier(), Collections.emptyMap());
    }

    public void onDataUpdate(final String appName) {
        if (viewStrategy.isRecreationOnDataUpdateRequired()) {
            dropViews(appName);
            createViews(appName);
        }
    }

    @Override
    public void afterPropertiesSet() {
        dropSchemas();
        if (viewStrategy.isEnabled()) {
            if (log.isInfoEnabled()) {
                log.info("création des vues relationnelles pour les applications existantes");
            }
            //TODO philippe
            final List<Application> allApplications = repository.application().findAll();
            /*allApplications.stream()
                    .map(BrokenApplication::getName)
                    .forEach(this::createViews);*/
        }
    }

    @Override
    public void destroy() {
        log.info("""
                
                \u001B[32mextinction des feux good night\u001B[0m
                
                """);
        //dropsViews();
    }

    private void dropSchemas() {
        if (viewStrategy.isEnabled()) {
            if (log.isInfoEnabled()) {
                log.info(" suppression des vues relationnelles pour les application existantes");
            }
            // TODO philippe
            final List<Application> allApplications = repository.application().findAll();
            /*allApplications.stream()
                    .forEach(this::dropSchema);*/
        }
    }

    private void dropSchema(final Application application) {
        final SqlSchemaForRelationalViewsForApplication schema = SqlSchema.forRelationalViewsOf(application, viewStrategy);
        final String formatted = """
                \u001B[35m
                **************************************
                *     Suppression du schema %s       *
                **************************************\u001B[0m
                """.formatted(schema.getSqlIdentifier());
        log.info(formatted);
        db.dropSchema(schema);
    }

    @Override
    public void setServiceContainer(ServiceContainer serviceContainer) {

    }


    sealed interface SQLVariable permits SQLVariableForData, SQLVariableForRefsLinkedTo {
        String name();

        List<SQLComponent> sqlComponents();

        default String getType() {
            return switch (this) {
                case final SQLVariableForData sqlVariableForData -> "datavalues";
                case final SQLVariableForRefsLinkedTo sqlVariableForRefsLinkedTo -> "refslinkedto";
            };
        }

        default String getSelect(final SQLComponent component) {

            return switch (this) {
                case final SQLVariableForData sqlVariableForData -> """
                        "%1$s_datavalues_row"."%2$s"::%3$s "%4$s_%5$s\"""".formatted(
                        name().replace("'", "''"),
                        component.name(),
                        component.sqlType().cast(),
                        name().toLowerCase().replaceAll("[' ]", "_"),
                        component.name().toLowerCase().replaceAll("[' ]", "_")
                );
                case final SQLVariableForRefsLinkedTo sqlVariableForRefsLinkedTo -> "refslinkedto";
            };
        }

        default String getSelectForRef(final SQLComponent component) {
            return switch (this) {
                case final SQLVariableForData sqlVariableForData -> """
                        "%1$s_refslinkedto_row"."%2$s"::%3$s "%4$s_%5$s_technicalid\"""".formatted(
                        name().replace("'", "''"),
                        component.name(),
                        "UUID[]",
                        name().toLowerCase().replaceAll("[' ]", "_"),
                        component.name().toLowerCase().replaceAll("[' ]", "_")
                );
                case final SQLVariableForRefsLinkedTo sqlVariableForRefsLinkedTo -> "refslinkedto";
            };
        }

        default String buildLateralForWithVariable() {
            return """
                    LATERAL (
                                  SELECT "%1$s" as "%1$s_%2$s"
                                  FROM jsonb_to_record(%2$s) AS "%1$s"("%1$s" jsonb)
                                ) "%1$s_%2$s_row\""""
                    .formatted(
                            name().replace("'", "''"),
                            getType()
                    );
        }

        default String getSqlSelectForWithVariable() {
            return """
                    "%1$s_%2$s_row".*""".formatted(name().replace("'", "''"), getType());
        }

        default String buildLateralForSelect() {
            return """
                    LATERAL (
                           SELECT *
                           FROM jsonb_to_record("%1$s_%2$s")
                           AS "%1$s" %3$s
                          ) "%1$s_%2$s_row\""""
                    .formatted(
                            name().replace("'", "''"),
                            getType(),
                            sqlComponents()
                                    .stream()
                                    .map(sqlComponent -> switch (this) {
                                        case final SQLVariableForData sqlVariableForData ->
                                                sqlComponent.toRecordDefinition();
                                        case final SQLVariableForRefsLinkedTo sqlVariableForRefsLinkedTo ->
                                                sqlComponent.toRecordDefinitionForRef();
                                    })
                                    .collect(Collectors.joining(
                                                    """
                                                            ,\s
                                                                          \s""",
                                                    """
                                                            (
                                                                          \s""",
                                                    """
                                                            )"""
                                            )
                                    )
                    );
        }
    }

    private record SchemaCreationCommand(Application application, SqlSchemaForRelationalViewsForApplication schema,
                                         List<ViewCreationCommand> views, ViewStrategy viewStrategy) {

    }

    record ViewCreationCommand(SqlTable view, String sql) {

    }

    public static class IdentifierTest {
        private String identifier;

        private IdentifierTest(final String label) {
            super();
            identifier = Optional.ofNullable(label).orElse("");
            testlabelLength();
        }

        public static IdentifierTest forReference(final DataColumn reference) {
            return forStringIdentifier(reference.column());
        }

        public static boolean identifierForApplicationName(final String applicationName) {
            return getIsValidIdentifierPattern(2, 40).test(applicationName);
        }

        public static boolean identifierForObject(final String objectName) {
            return getIsValidIdentifierPattern(1, 50).test(objectName);
        }

        public static IdentifierTest forStringIdentifier(final String identifier) {
            Optional.ofNullable(identifier)
                    .map(IdentifierTest::new)
                    .orElseThrow(() -> new FieldNameTooLongForSqlFieldException(identifier));
            return new IdentifierTest(identifier);
        }/*

        public static List<IdentifierTest> getColumnNamesForReferenceDynamicColumns(final Configuration.ReferenceDescription referenceDescription) {
            final AtomicInteger count = new AtomicInteger(1);
            return referenceDescription.getDynamicColumns().keySet().stream()
                    .map(IdentifierTest::new)
                    .map(labelTest -> labelTest.forDynamicReferenceHierachicakKey(count.getAndIncrement()))
                    .toList();
        }*/

        @Deprecated
        private String toQuotedIdentifier() {
            return WithSqlIdentifier.escapeSqlIdentifier(identifier);
        }

        public IdentifierTest testlabelLength() {
            return Optional.of(identifier)
                    .filter(l -> l.length() <= 63)
                    .map(l -> this)
                    .orElseThrow(() -> new FieldNameTooLongForSqlFieldException(identifier));
        }

        public String testAndQuote() {
            final String quotedIdentifier = toQuotedIdentifier();
            return (quotedIdentifier.startsWith("\"") && quotedIdentifier.endsWith("\"")) ?
                    quotedIdentifier :
                    String.format("\"%s\"", quotedIdentifier);
        }

        public String testAndQuoteForRefsLinkedTo() {
            return testAndQuote().replaceAll("^\"", "\"refs_linked_to_");
        }

        public String testAndReturnIdentifier() {
            return identifier;
        }

        public IdentifierTest forHierachicalKey() {
            identifier = identifier + "_hierachicakkey";
            return testlabelLength();
        }

        public IdentifierTest forNaturalKey() {
            identifier = identifier + "_naturalkey";
            return testlabelLength();
        }

        public IdentifierTest forOneValueFromTheManyArray() {
            identifier = identifier + "_value";
            return testlabelLength();
        }

        public IdentifierTest forId() {
            identifier = identifier + "_id";
            return testlabelLength();
        }

        public IdentifierTest forDynamicReferenceHierachicakKey(final int count) {
            identifier = Pattern.compile(".{1," + 64 + "}")
                    .matcher(String.format("_%d%s_hierachicakKey", count, identifier))
                    .results()
                    .map(MatchResult::group)
                    .findFirst()
                    .orElse("");
            return this;
        }
    }

    record MultiplicitySelect(String select, String lateral) {
        public static final String SIMPLE_SELECT_PATTERN = """
                (%1$s.dataValues #>>  '{%2$s,%3$s}')::%4$s as %5$s""";
        public static final String SIMPLE_SELECT_PATTERN_WITH_NULL = """
                (NULLIF(%1$s.dataValues #>>  '{%2$s,%3$s}', ''))::%4$s as %5$s""";
        static String SELECT_PATTERN_MANY = """
                array_agg(%1$s.v) over(partition by my_data.rowid) %1$s""";
        static String SELECT_PATTERN_MANY_WITH_NULL = """
                array_agg(NULLIF(%1$s.v, '')) over(partition by my_data.rowid) %1$s""";
        static String SELECT_LATERAL_MANY = """ 
                LEFT JOIN LATERAL(select ((jsonb_array_elements(%1$s.dataValues #> '{%2$s, %3$s}')) #>> '{}')::%4$s as v, rowid ) as %5$s using(rowid)""";
        static String SELECT_PATTERN_ID = """ 
                %1$s.v::uuid %2$s""";
        static String SELECT_LATERAL_ID = """ 
                LEFT JOIN LATERAL (SELECT jsonb_array_elements_text(coalesce(%1$s.refsLinkedTo #> '{%2$s,%3$s}', '[null]'::jsonb)) as v, rowid) %4$s using(rowid)""";
    }

    record SQLComponent(String name, SqlViewPrimitiveType sqlType) {
        SQLComponent {
            if (!Strings.isNotEmpty(name)) {
                throw new IllegalArgumentException("name can't be null");
            }
            Objects.requireNonNull(sqlType, "sqlType can't be empty");
        }

        String toRecordDefinition() {
            final String cast = sqlType().cast().contains("DATE") ? "TEXT" : sqlType().cast();
            return """
                    "%1$s" %2$s%3$s"""
                    .formatted(
                            name().replace("[, ]", "''"),
                            cast,
                            sqlType().multiplicity()
                    );
        }

        String toRecordDefinitionForRef() {
            return """
                    "%1$s" UUID%2$s"""
                    .formatted(
                            name().replace("[, ]", "''"),
                            sqlType().multiplicity()
                    );
        }

        record SqlViewPrimitiveType(String cast, String multiplicity) {

            static final SqlViewPrimitiveType DEFAULT = new SqlViewPrimitiveType("TEXT", "");

            /*public static SqlViewPrimitiveType getSqlType(final LineCheckerWarper checkerWarper) {
                final String multiplicity = switch (checkerWarper) {
                    case final ManyCheckerWarper manyCheckerWarper -> "[]";
                    case final OneCheckerWarper oneCheckerWarper -> "";
                    case null, default -> "";
                };
                return switch (checkerWarper.getUnderlyingType()) {
                    case final ReferenceType referenceType ->
                            new SqlViewPrimitiveType("LTREE%1$s".formatted(multiplicity), multiplicity);
                    case final BooleanType booleanType ->
                            new SqlViewPrimitiveType("BOOLEAN%1$s".formatted(multiplicity), multiplicity);
                    case final DateType dateType ->
                            new SqlViewPrimitiveType("COMPOSITE_DATE%s::TIMESTAMP%1$s".formatted(multiplicity), multiplicity);
                    case final FloatType floatType ->
                            new SqlViewPrimitiveType("NUMERIC%1$s".formatted(multiplicity), multiplicity);
                    case final IntegerType integerType ->
                            new SqlViewPrimitiveType("INTEGER%1$s".formatted(multiplicity), multiplicity);
                    case null, default -> new SqlViewPrimitiveType("TEXT%1$s".formatted(multiplicity), multiplicity);
                };
            }*/
        }
    }

    record SQLVariableForData(String name, List<SQLComponent> sqlComponents) implements SQLVariable {
        SQLVariableForData {
            if (Strings.isEmpty(name)) {
                throw new IllegalArgumentException("name can't be null");
            }
            if (CollectionUtils.isEmpty(sqlComponents)) {
                throw new IllegalArgumentException("sqlComponents can't be empty");
            }
        }

        public List<String> getSqlSelectForWithVariableJoiningRefsLinkedTo(final List<SQLVariableForRefsLinkedTo> sqlVariablesForRefsLinkedTos) {
            return sqlComponents().stream()
                    .map(component -> {
                                final LinkedList<String> sqlVariables = new LinkedList<>();
                                sqlVariables.add(getSelect(component));
                                sqlVariables.addAll(sqlVariablesForRefsLinkedTos.stream()
                                        .filter(sqlVariableForRefsLinkedTo -> sqlVariableForRefsLinkedTo.name().equals(name()))
                                        .map(SQLVariableForRefsLinkedTo::sqlComponents)
                                        .map(sqlComponents -> sqlComponents.stream()
                                                .filter(componentRefs -> componentRefs.name().equals(component.name()))
                                                .map(this::getSelectForRef)
                                                .toList()
                                        )
                                        .flatMap(List::stream)
                                        .toList()
                                );
                                return sqlVariables;
                            }
                    )
                    .flatMap(List::stream)
                    .toList();
        }
    }

    record SQLVariableForRefsLinkedTo(String name, List<SQLComponent> sqlComponents) implements SQLVariable {
        SQLVariableForRefsLinkedTo {
            if (Strings.isEmpty(name)) {
                throw new IllegalArgumentException("name can't be null");
            }
            if (CollectionUtils.isEmpty(sqlComponents)) {
                throw new IllegalArgumentException("sqlComponents can't be empty");
            }
        }
    }
}