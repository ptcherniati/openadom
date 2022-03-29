package fr.inra.oresing.rest;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.CheckerOnOneVariableComponentLineChecker;
import fr.inra.oresing.checker.Multiplicity;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.SqlPrimitiveType;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.SqlSchemaForRelationalViewsForApplication;
import fr.inra.oresing.persistence.SqlService;
import fr.inra.oresing.persistence.SqlTable;
import fr.inra.oresing.persistence.WithSqlIdentifier;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Strings;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Component
@Transactional
public class RelationalService implements InitializingBean, DisposableBean {

    @Autowired
    private SqlService db;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private CheckerFactory checkerFactory;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private OreSiRepository repository;

    @org.springframework.beans.factory.annotation.Value("${viewStrategy:DISABLED}")
    private ViewStrategy viewStrategy;

    public void createViews(String appName) {
        createViews(appName, viewStrategy);
    }

    void createViews(String appName, ViewStrategy viewStrategy) {
        Application app = getApplication(appName);
        createViews(app, viewStrategy);
    }

    private Application getApplication(String appName) {
        return repository.application().findApplication(appName);
    }

    void createViews(UUID appId, ViewStrategy viewStrategy) {
        Application app = getApplication(appId.toString());
        createViews(app, viewStrategy);
    }

    private void createViews(Application app, ViewStrategy viewStrategy) {
        if (viewStrategy.isEnabled()) {
            authenticationService.resetRole();
            SchemaCreationCommand schemaCreationCommand = getSchemaCreationCommand(app, viewStrategy);
            create(schemaCreationCommand);
        } else {
            if (log.isInfoEnabled()) {
                log.info("les vues relationnelles sont désactivées, on ne crée pas les vues pour " + app.getName());
            }
        }
    }

    public void dropViews(String appName) {
        dropViews(appName, viewStrategy);
    }

    void dropViews(String appName, ViewStrategy viewStrategy) {
        if (viewStrategy.isEnabled()) {
            authenticationService.resetRole();
            Application app = getApplication(appName);
            SchemaCreationCommand schemaCreationCommand = getSchemaCreationCommand(app, viewStrategy);
            drop(schemaCreationCommand);
        } else {
            if (log.isInfoEnabled()) {
                log.info("les vues relationnelles sont désactivées, on ne supprime pas les vues pour " + appName);
            }
        }
    }

    private void create(SchemaCreationCommand schemaCreationCommand) {
        ViewStrategy viewStrategy = schemaCreationCommand.getViewStrategy();
        UUID appId = schemaCreationCommand.getApplication().getId();
        OreSiRightOnApplicationRole owner = OreSiRightOnApplicationRole.adminOn(schemaCreationCommand.getApplication());
        db.createSchema(schemaCreationCommand.getSchema(), owner);
        for (ViewCreationCommand viewCreationCommand : schemaCreationCommand.getViews()) {
            SqlTable view = viewCreationCommand.getView();
            String viewSql = viewCreationCommand.getSql();
            if (viewStrategy == ViewStrategy.VIEW) {
                db.createView(view, viewSql);
                db.setViewOwner(view, owner);
//                for (ApplicationRight applicationRight : ApplicationRight.values()) {
//                    OreSiRightOnApplicationRole roleThatCanReadViews = applicationRight.getRole(appId);
//                    namedParameterJdbcTemplate.execute("GRANT USAGE ON SCHEMA " + schemaName + " TO " + roleThatCanReadViews.getSqlIdentifier(), PreparedStatement::execute);
//                    namedParameterJdbcTemplate.execute("GRANT SELECT ON ALL TABLES IN SCHEMA " + schemaName + " TO " + roleThatCanReadViews.getSqlIdentifier(), PreparedStatement::execute);
//                }
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
                throw new IllegalArgumentException("stratégie " + viewStrategy);
            }
        }
    }

    private void drop(SchemaCreationCommand schemaCreationCommand) {
        ViewStrategy viewStrategy = schemaCreationCommand.getViewStrategy();
        List<ViewCreationCommand> reverse = Lists.reverse(schemaCreationCommand.getViews());
        for (ViewCreationCommand viewCreationCommand : reverse) {
            SqlTable view = viewCreationCommand.getView();
            if (viewStrategy == ViewStrategy.VIEW) {
                db.dropView(view);
            } else if (viewStrategy == ViewStrategy.TABLE) {
                db.dropTable(view);
            } else {
                throw new IllegalArgumentException("stratégie " + viewStrategy);
            }
        }
        db.dropSchema(schemaCreationCommand.getSchema());
    }

    private SchemaCreationCommand getSchemaCreationCommand(Application app, ViewStrategy viewStrategy) {
        SqlSchemaForRelationalViewsForApplication sqlSchema = SqlSchema.forRelationalViewsOf(app, viewStrategy);
        List<ViewCreationCommand> views = new LinkedList<>();
        views.addAll(getViewsForReferences(sqlSchema, app));
        views.addAll(getViewsForDataTypes(sqlSchema, app));
        if (viewStrategy == ViewStrategy.VIEW) {
            views.addAll(getDenormalizedViewsForDataTypes(sqlSchema, app));
        }
        return new SchemaCreationCommand(app, sqlSchema, views, viewStrategy);
    }

    private List<ViewCreationCommand> getViewsForDataTypes(SqlSchemaForRelationalViewsForApplication sqlSchema, Application application) {

        List<ViewCreationCommand> views = new LinkedList<>();

        UUID appId = application.getId();

        for (Map.Entry<String, Configuration.DataTypeDescription> entry : application.getConfiguration().getDataTypes().entrySet()) {
            String dataType = entry.getKey();
            Configuration.DataTypeDescription dataTypeDescription = entry.getValue();
            ImmutableMap<VariableComponentKey, CheckerOnOneVariableComponentLineChecker> checkerPerVariableComponentKeys = checkerFactory.getLineCheckers(application, dataType).stream()
                    .filter(lineChecker -> lineChecker instanceof CheckerOnOneVariableComponentLineChecker)
                    .map(lineChecker -> (CheckerOnOneVariableComponentLineChecker) lineChecker)
                    .collect(ImmutableMap.toImmutableMap(rlc -> (VariableComponentKey) rlc.getTarget(), Function.identity()));
            Map<VariableComponentKey, ReferenceLineChecker> referenceCheckers =
                    Maps.transformValues(
                            Maps.filterValues(checkerPerVariableComponentKeys, checker -> checker instanceof ReferenceLineChecker),
                            checker -> (ReferenceLineChecker) checker
                    );
            Map<VariableComponentKey, SqlPrimitiveType> sqlTypesPerVariableComponentKey =
                    Maps.transformValues(checkerPerVariableComponentKeys, CheckerOnOneVariableComponentLineChecker::getSqlType);

            Set<String> referenceColumnIds = new LinkedHashSet<>();
            Set<String> selectClauseElements = new LinkedHashSet<>();
            Set<String> fromClauseJoinElements = new LinkedHashSet<>();

            String dataTableName = "my_data";
            String dataAfterDataGroupsMergingQuery = repository.getRepository(application).data().getSqlToMergeData(DownloadDatasetQuery.buildDownloadDatasetQuery(null, appId.toString(), dataType, application));
            String withClause = "WITH " + dataTableName + " AS (" + dataAfterDataGroupsMergingQuery + ")";

            for (VariableComponentKey variableComponentKey : getVariableComponentKeys(dataTypeDescription)) {
                String variable = variableComponentKey.getVariable();
                String component = variableComponentKey.getComponent();
                String escapedVariableName = StringUtils.replace(variable, "'", "''");
                String escapedComponentName = StringUtils.replace(component, "'", "''");
                SqlPrimitiveType sqlType = sqlTypesPerVariableComponentKey.getOrDefault(variableComponentKey, SqlPrimitiveType.TEXT);
                String selectClausePattern;
                if (sqlType.isEmptyStringValidValue()) {
                    selectClausePattern = "jsonb_extract_path_text(%s.dataValues, '%s', '%s')::%s %s";
                } else {
                    selectClausePattern = "nullif(jsonb_extract_path_text(%s.dataValues, '%s', '%s'), '')::%s %s";
                }
                String selectClauseElement = String.format(
                        selectClausePattern,
                        dataTableName,
                        escapedVariableName,
                        escapedComponentName,
                        sqlType.getSql(),
                        getColumnName(variableComponentKey)
                );
                selectClauseElements.add(selectClauseElement);

                if (referenceCheckers.containsKey(variableComponentKey)) {
                    String selectTechnicalIdClauseElement = String.format(
                            "jsonb_extract_path_text(%s.refsLinkedTo, '%s', '%s') %s",
                            dataTableName,
                            escapedVariableName,
                            escapedComponentName,
                            getTechnicalIdColumnName(variableComponentKey)
                    );
                    selectClauseElements.add(selectTechnicalIdClauseElement);
                }
            }

            String selectClause = "SELECT " + String.join(", ", selectClauseElements);

            String fromClause = "FROM " + dataTableName + " "
                              + Joiner.on(" ").join(fromClauseJoinElements);

            String viewSql = String.join("\n", withClause, selectClause, fromClause);

            SqlTable view = sqlSchema.forDataType(dataType);
            views.add(new ViewCreationCommand(view, viewSql));
        }
        return views;
    }

    private ImmutableSet<VariableComponentKey> getVariableComponentKeys(Configuration.DataTypeDescription dataTypeDescription) {
        Set<VariableComponentKey> references = new LinkedHashSet<>();
        for (Map.Entry<String, Configuration.ColumnDescription> variableEntry : dataTypeDescription.getData().entrySet()) {
            String variable = variableEntry.getKey();
            for (String component : variableEntry.getValue().doGetAllComponents()) {
                references.add(new VariableComponentKey(variable, component));
            }
        }
        return ImmutableSet.copyOf(references);
    }

    private List<ViewCreationCommand> getDenormalizedViewsForDataTypes(SqlSchemaForRelationalViewsForApplication sqlSchema, Application application) {
        List<ViewCreationCommand> views = new LinkedList<>();
        for (Map.Entry<String, Configuration.DataTypeDescription> entry : application.getConfiguration().getDataTypes().entrySet()) {
            String dataType = entry.getKey();
            Configuration.DataTypeDescription dataTypeDescription = entry.getValue();
            ImmutableMap<VariableComponentKey, ReferenceLineChecker> referenceCheckers = checkerFactory.getReferenceLineCheckers(application, dataType);

            Set<String> selectClauseReferenceElements = new LinkedHashSet<>();
            Set<String> fromClauseJoinElements = new LinkedHashSet<>();

            String dataTableName = sqlSchema.forDataType(dataType).getSqlIdentifier();

            for (ReferenceLineChecker referenceChecker : referenceCheckers.values()) {
                String referenceType = referenceChecker.getRefType();  // especes
                VariableComponentKey variableComponentKey = (VariableComponentKey) referenceChecker.getTarget();
                String quotedViewName = sqlSchema.forReferenceType(referenceType).getSqlIdentifier();

                String foreignKeyColumnName = getTechnicalIdColumnName(variableComponentKey);
                String alias = getAliasForColumnName(variableComponentKey);

                application.getConfiguration().getReferences().get(referenceType).doGetStaticColumns().stream()
                        .map(referenceColumn -> alias + "." + quoteSqlIdentifier(referenceColumn) + " as " + getColumnName("ref", variableComponentKey.getVariable(), variableComponentKey.getComponent(), referenceColumn))
                        .forEach(selectClauseReferenceElements::add);
                fromClauseJoinElements.add("left outer join " + quotedViewName + " " + alias + " on " + dataTableName + "." + foreignKeyColumnName + "::uuid = " + alias + "." + quoteSqlIdentifier(referenceType + "_id") + "::uuid");
            }

            Set<String> selectClauseElements = new LinkedHashSet<>(selectClauseReferenceElements);

            getVariableComponentKeys(dataTypeDescription).stream()
                    .map(this::getColumnName)
                    .map(columnName -> dataTableName + "." + columnName)
                    .forEach(selectClauseElements::add);

            String selectClause = "select " + String.join(", ", selectClauseElements);

            String fromClause = "from " + sqlSchema.forDataType(dataType).getSqlIdentifier() + " "
                    + Joiner.on(" ").join(fromClauseJoinElements);

            String viewSql = String.join("\n", selectClause, fromClause);

            SqlTable view = sqlSchema.forDenormalizedDataType(dataType);
            views.add(new ViewCreationCommand(view, viewSql));
        }
        return views;
    }

    private String getTechnicalIdColumnName(VariableComponentKey variableComponentKey) {
        return getColumnName(variableComponentKey.getVariable(), variableComponentKey.getComponent(), "technicalId");
    }

    private String getAliasForColumnName(VariableComponentKey variableComponentKey) {
        return getColumnName("ref", variableComponentKey.getVariable(), variableComponentKey.getComponent());
    }

    private String getColumnName(VariableComponentKey variableComponentKey) {
        return getColumnName(variableComponentKey.getVariable(), variableComponentKey.getComponent());
    }

    private String getColumnName(String firstElement, String... otherElements) {
        String joined = Strings.join(Lists.asList(firstElement, otherElements)).with("_");
        String escaped = StringUtils.replace(StringUtils.replace(joined, " ", "_"), "'", "_");
        String quoted = "\"" + escaped + "\"";
        return quoted;
    }

    private List<ViewCreationCommand> getViewsForReferences(SqlSchemaForRelationalViewsForApplication sqlSchema, Application app) {
        UUID appId = app.getId();
        List<ViewCreationCommand> views = new LinkedList<>();
        for (Map.Entry<String, Configuration.ReferenceDescription> entry : app.getConfiguration().getReferences().entrySet()) {
            String referenceType = entry.getKey();
            Configuration.ReferenceDescription referenceDescription = entry.getValue();

            ImmutableMap<ReferenceColumn, SqlPrimitiveType> sqlTypePerColumns = checkerFactory.getReferenceValidationLineCheckers(app, referenceType).stream()
                    .filter(CheckerOnOneVariableComponentLineChecker.class::isInstance)
                    .map(CheckerOnOneVariableComponentLineChecker.class::cast)
                    .collect(ImmutableMap.toImmutableMap(rlc -> (ReferenceColumn) rlc.getTarget(), CheckerOnOneVariableComponentLineChecker::getSqlType));

            ImmutableMap<ReferenceColumn, Multiplicity> declaredMultiplicityPerReferenceColumns = checkerFactory.getReferenceValidationLineCheckers(app, referenceType).stream()
                    .filter(ReferenceLineChecker.class::isInstance)
                    .map(ReferenceLineChecker.class::cast)
                    .collect(ImmutableMap.toImmutableMap(rlc -> (ReferenceColumn) rlc.getTarget(), referenceLineChecker -> referenceLineChecker.getConfiguration().getMultiplicity()));

            ImmutableSetMultimap<Multiplicity, ReferenceColumn> allReferenceColumnsPerMultiplicity = referenceDescription.doGetStaticColumns().stream()
                    .map(ReferenceColumn::new)
                    .collect(ImmutableSetMultimap.toImmutableSetMultimap(referenceColumn -> declaredMultiplicityPerReferenceColumns.getOrDefault(referenceColumn, Multiplicity.ONE), Function.identity()));

            String columnsAsSchema = allReferenceColumnsPerMultiplicity.values().stream()
                    .map(referenceColumn -> {
                        String columnName = quoteSqlIdentifier(referenceColumn.getColumn());
                        String columnDeclaration = String.format("%s %s", columnName, SqlPrimitiveType.TEXT);
                        return columnDeclaration;
                    })
                    .collect(Collectors.joining(", ", "(", ")"));
            String quotedReferenceType = quoteSqlIdentifier(referenceType);
            String castedColumnSelect = allReferenceColumnsPerMultiplicity.values().stream()
                    .map(referenceColumn -> {
                        String columnName = quoteSqlIdentifier(referenceColumn.getColumn());
                        SqlPrimitiveType columnType = sqlTypePerColumns.getOrDefault(referenceColumn, SqlPrimitiveType.TEXT);
                        Multiplicity multiplicity = declaredMultiplicityPerReferenceColumns.getOrDefault(referenceColumn, Multiplicity.ONE);
                        String columnDeclaration;
                        if (multiplicity == Multiplicity.ONE) {
                            columnDeclaration = String.format("%s.%s::%s", quotedReferenceType,columnName, columnType.getSql());
                        } else if (multiplicity == Multiplicity.MANY) {
                            columnDeclaration = String.format("ARRAY(SELECT JSONB_ARRAY_ELEMENTS_TEXT(%s.%s::JSONB))::%s[] AS %s", quotedReferenceType, columnName, columnType.getSql(), columnName);
                        } else {
                            throw new IllegalStateException("multiplicy = " + multiplicity);
                        }
                        return columnDeclaration;
                    })
                    .collect(Collectors.joining(", "));

            // par exemple "projet"(nom_en text, nom_fr text, nom_key text, definition_en text, definition_fr text)
            String schemaDeclaration = quotedReferenceType + columnsAsSchema;

            String quotedViewIdColumnName = quoteSqlIdentifier(referenceType + "_id");
            String quotedViewHierarchicalKeyColumnName = quoteSqlIdentifier(referenceType + "_hierarchicalKey");
            String quotedViewNaturalKeyColumnName = quoteSqlIdentifier(referenceType + "_naturalKey");
            String referenceValueTableName = SqlSchema.forApplication(app).referenceValue().getSqlIdentifier();
            String whereClause = " referenceType = '" + referenceType + "' and application = '" + appId + "'::uuid";
            String referenceView = "select referenceValue.id as " + quotedViewIdColumnName + ", referenceValue.hierarchicalKey as " + quotedViewHierarchicalKeyColumnName + ", referenceValue.naturalKey as " + quotedViewNaturalKeyColumnName + ", "
                    + castedColumnSelect
                    + " from " + referenceValueTableName + ", " +
                    "jsonb_to_record(referenceValue.refValues) as " + schemaDeclaration
                    + " where " + whereClause;

            if (log.isTraceEnabled()) {
                log.trace("pour le référentiel " + referenceType + ", la requête pour avoir un vue relationnelle des données JSON est " + referenceView);
            }

            SqlTable view = sqlSchema.forReferenceType(referenceType);
            views.add(new ViewCreationCommand(view, referenceView));

            Set<ViewCreationCommand> associationViews = allReferenceColumnsPerMultiplicity.get(Multiplicity.MANY).stream()
                    .map(referenceColumn -> {
                        String columnNameForOneValueFromTheManyArray = quoteSqlIdentifier(referenceColumn.getColumn() + "_value");
                        String columnFromReferenceViewThatContainsTheForeignKeysArray = quoteSqlIdentifier(referenceColumn.getColumn());
                        String associationViewPattern = String.join("\n"
                                , "SELECT %s, %s"
                                , "FROM %s"
                                , "JOIN LATERAL"
                                , "    UNNEST(%s) %s ON TRUE"
                        );
                        String associationView = String.format(associationViewPattern
                                , quotedViewHierarchicalKeyColumnName
                                , columnNameForOneValueFromTheManyArray
                                , view.getSqlIdentifier()
                                , columnFromReferenceViewThatContainsTheForeignKeysArray
                                , columnNameForOneValueFromTheManyArray
                        );
                        return new ViewCreationCommand(sqlSchema.forAssociation(referenceType, referenceColumn), associationView);
                    })
                    .collect(Collectors.toUnmodifiableSet());

            views.addAll(associationViews);

            Set<ViewCreationCommand> dynamicColumnViews = referenceDescription.getDynamicColumns().keySet().stream()
                    .map(dynamicColumn -> {
                        String sqlPattern = String.join("\n"
                                , "SELECT"
                                , "    referenceValue.hierarchicalKey AS %s,"
                                , "    (jsonb_each_text(referenceValue.refValues->'%s')).key::LTREE AS %s,"
                                , "    (jsonb_each_text(referenceValue.refValues->'%s')).value"
                                , "   FROM %s"
                                , "  WHERE %s"
                        );
                        String dynamicColumnsView = String.format(sqlPattern,
                                quotedViewHierarchicalKeyColumnName,
                                dynamicColumn,
                                quoteSqlIdentifier(dynamicColumn + "_hierarchicalKey"),
                                dynamicColumn,
                                referenceValueTableName,
                                whereClause
                        );
                        return new ViewCreationCommand(sqlSchema.forAssociation(referenceType, new ReferenceColumn(dynamicColumn)), dynamicColumnsView);
                    })
                    .collect(Collectors.toUnmodifiableSet());

            views.addAll(dynamicColumnViews);
        }
        return views;
    }

    @Deprecated
    private String quoteSqlIdentifier(String sqlIdentifier) {
        return WithSqlIdentifier.escapeSqlIdentifier(sqlIdentifier);
    }

    public List<Map<String, Object>> readView(String appName, String dataType, ViewStrategy viewStrategy) {
//        authRepository.setRoleForClient();
        Application application = getApplication(appName);
        SqlTable view = SqlSchema.forRelationalViewsOf(application, viewStrategy).forDataType(dataType);
        return namedParameterJdbcTemplate.queryForList("select * from " + view.getSqlIdentifier(), Collections.emptyMap());
    }

    public void onDataUpdate(String appName) {
        if (viewStrategy.isRecreationOnDataUpdateRequired()) {
            dropViews(appName);
            createViews(appName);
        }
    }

    @Override
    public void afterPropertiesSet() {
        if (viewStrategy.isEnabled()) {
            if (log.isInfoEnabled()) {
                log.info("création des vues relationnelles pour les application existantes");
            }
            List<Application> allApplications = repository.application().findAll();
            allApplications.stream()
                    .map(Application::getName)
                    .forEach(this::createViews);
        }
    }

    @Override
    public void destroy() {
        if (viewStrategy.isEnabled()) {
            if (log.isInfoEnabled()) {
                log.info("suppression des vues relationnelles pour les application existantes");
            }
            List<Application> allApplications = repository.application().findAll();
            allApplications.stream()
                    .map(Application::getName)
                    .forEach(this::dropViews);
        }
    }

    @Value
    private static class SchemaCreationCommand {

        Application application;

        SqlSchemaForRelationalViewsForApplication schema;

        List<ViewCreationCommand> views;

        ViewStrategy viewStrategy;
    }

    @Value
    private static class ViewCreationCommand {

        SqlTable view;

        String sql;
    }
}