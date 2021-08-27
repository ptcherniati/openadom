package fr.inra.oresing.rest;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.OreSiRepository;
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
            ImmutableMap<VariableComponentKey, ReferenceLineChecker> referenceCheckers = checkerFactory.getReferenceLineCheckers(application, dataType);

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
                String selectClauseElement = String.format(
                        "jsonb_extract_path_text(%s.dataValues, '%s', '%s') %s",
                        dataTableName,
                        escapedVariableName,
                        escapedComponentName,
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
            views.add(new ViewCreationCommand(view, viewSql, referenceColumnIds));
        }
        return views;
    }

    private ImmutableSet<VariableComponentKey> getVariableComponentKeys(Configuration.DataTypeDescription dataTypeDescription) {
        Set<VariableComponentKey> references = new LinkedHashSet<>();
        for (Map.Entry<String, Configuration.ColumnDescription> variableEntry : dataTypeDescription.getData().entrySet()) {
            String variable = variableEntry.getKey();
            for (String component : variableEntry.getValue().getComponents().keySet()) {
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
                VariableComponentKey variableComponentKey = referenceChecker.getVariableComponentKey();
                String quotedViewName = sqlSchema.forReferenceType(referenceType).getSqlIdentifier();

                String foreignKeyColumnName = getTechnicalIdColumnName(variableComponentKey);
                String alias = getAliasForColumnName(variableComponentKey);

                application.getConfiguration().getReferences().get(referenceType).getColumns().keySet().stream()
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
            views.add(new ViewCreationCommand(view, viewSql, selectClauseReferenceElements));
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
            Set<String> columns = entry.getValue().getColumns().keySet();
            String columnsAsSchema = columns.stream()
                    .map(this::quoteSqlIdentifier)
                    .map(quotedColumnName -> quotedColumnName + " text")
                    .collect(Collectors.joining(", ", "(", ")"));
            String quotedReferenceType = quoteSqlIdentifier(referenceType);

            // par exemple "projet"(nom_en text, nom_fr text, nom_key text, definition_en text, definition_fr text)
            String schemaDeclaration = quotedReferenceType + columnsAsSchema;

            String quotedViewIdColumnName = quoteSqlIdentifier(referenceType + "_id");
            String quotedViewHierarchicalKeyColumnName = quoteSqlIdentifier(referenceType + "_hierarchicalKey");
            String quotedViewNaturalKeyColumnName = quoteSqlIdentifier(referenceType + "_naturalKey");
            String referenceValueTableName = SqlSchema.forApplication(app).referenceValue().getSqlIdentifier();
            String referenceView = "select referenceValue.id as " + quotedViewIdColumnName + ", referenceValue.hierarchicalKey as " + quotedViewHierarchicalKeyColumnName + ", referenceValue.naturalKey as " + quotedViewNaturalKeyColumnName + ", " + quotedReferenceType + ".* "
                    + " from " + referenceValueTableName + ", jsonb_to_record(referenceValue.refValues) as " + schemaDeclaration
                    + " where referenceType = '" + referenceType + "' and application = '" + appId + "'::uuid";

            if (log.isTraceEnabled()) {
                log.trace("pour le référentiel " + referenceType + ", la requête pour avoir un vue relationnelle des données JSON est " + referenceView);
            }

            views.add(new ViewCreationCommand(sqlSchema.forReferenceType(referenceType), referenceView, Set.of(quotedViewIdColumnName)));
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

        Set<String> referenceIdColumns;
    }
}
