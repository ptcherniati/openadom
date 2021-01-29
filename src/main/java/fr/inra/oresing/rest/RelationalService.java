package fr.inra.oresing.rest;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.ReferenceChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.persistence.AuthRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.SqlPolicy;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.SqlSchemaForRelationalViewsForApplication;
import fr.inra.oresing.persistence.SqlService;
import fr.inra.oresing.persistence.SqlTable;
import fr.inra.oresing.persistence.WithSqlIdentifier;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiRoleToAccessDatabase;
import fr.inra.oresing.rest.OreSiService;
import fr.inra.oresing.rest.ViewStrategy;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
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
public class RelationalService {

    @Autowired
    private SqlService db;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private OreSiService service;

    @Autowired
    private CheckerFactory checkerFactory;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    @Autowired
    private OreSiRepository repository;

    public void createViews(String appName, ViewStrategy viewStrategy) {
        Application app = service.getApplication(appName);
        createViews(app, viewStrategy);
    }

    public void createViews(UUID appId, ViewStrategy viewStrategy) {
        Application app = service.getApplication(appId.toString());
        createViews(app, viewStrategy);
    }

    private void createViews(Application app, ViewStrategy viewStrategy) {
        authRepository.resetRole();
        SchemaCreationCommand schemaCreationCommand = getSchemaCreationCommand(app, viewStrategy);
        create(schemaCreationCommand);
    }

    public void dropViews(String appName, ViewStrategy viewStrategy) {
        authRepository.resetRole();
        Application app = service.getApplication(appName);
        SchemaCreationCommand schemaCreationCommand = getSchemaCreationCommand(app, viewStrategy);
        drop(schemaCreationCommand);
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
        views.addAll(getViewsForDatasets(sqlSchema, app));
        if (viewStrategy == ViewStrategy.VIEW) {
            views.addAll(getDenormalizedViewsForDatasets(sqlSchema, app));
        }
        return new SchemaCreationCommand(app, sqlSchema, views, viewStrategy);
    }

    private List<ViewCreationCommand> getViewsForDatasets(SqlSchemaForRelationalViewsForApplication sqlSchema, Application application) {

        List<ViewCreationCommand> views = new LinkedList<>();

        UUID appId = application.getId();

        for (Map.Entry<String, Configuration.DatasetDescription> entry : application.getConfiguration().getDataset().entrySet()) {
            String datasetName = entry.getKey();
            Configuration.DatasetDescription datasetDescription = entry.getValue();
            Set<ReferenceChecker> referenceCheckers = checkerFactory.getReferenceCheckers(application, datasetName);

            Set<String> referenceColumnIds = new LinkedHashSet<>();
            Set<String> selectClauseReferenceElements = new LinkedHashSet<>();
            Set<String> fromClauseJoinElements = new LinkedHashSet<>();

            String dataTableName = "my_data";
            String dataAfterDataGroupsMergingQuery = repository.getRepository(application).getSqlToMergeData(datasetName);
            String withClause = "WITH " + dataTableName + " AS (" + dataAfterDataGroupsMergingQuery + ")";

            for (ReferenceChecker referenceChecker : referenceCheckers) {
                String referenceType = referenceChecker.getRefType();
                String quotedViewName = sqlSchema.forReferenceType(referenceType).getSqlIdentifier();

                String quotedViewIdColumnName = quoteSqlIdentifier(referenceType + "_id");
                selectClauseReferenceElements.add(quotedViewName + "." + quotedViewIdColumnName);
                referenceColumnIds.add(quotedViewIdColumnName);
                fromClauseJoinElements.add("left outer join " + quotedViewName + " on " + dataTableName + ".refsLinkedTo::uuid[] @> ARRAY[" + quotedViewName + "." + quotedViewIdColumnName + "::uuid]");
            }

            Set<String> selectClauseElements = new LinkedHashSet<>(selectClauseReferenceElements);
            String quotedDatasetName = quoteSqlIdentifier(datasetName);
            selectClauseElements.add(quotedDatasetName + ".*");
            String selectClause = "select " + String.join(", ", selectClauseElements);

            String dataColumnsAsSchema = datasetDescription.getData().keySet().stream()
                    .map(this::quoteSqlIdentifier)
                    .map(quotedColumnName -> quotedColumnName + " text")
                    .collect(Collectors.joining(", ", "(", ")"));

            String schemaDeclaration = quotedDatasetName + dataColumnsAsSchema;

            String fromClause = "from " + dataTableName + " "
                    + Joiner.on(" ").join(fromClauseJoinElements)
                    + ", jsonb_to_record(dataValues) as " + schemaDeclaration;

            String viewSql = String.join("\n", withClause, selectClause, fromClause);

            SqlTable view = sqlSchema.forDataset(datasetName);
            views.add(new ViewCreationCommand(view, viewSql, referenceColumnIds));
        }
        return views;
    }

    private List<ViewCreationCommand> getDenormalizedViewsForDatasets(SqlSchemaForRelationalViewsForApplication sqlSchema, Application application) {
        List<ViewCreationCommand> views = new LinkedList<>();
        for (Map.Entry<String, Configuration.DatasetDescription> entry : application.getConfiguration().getDataset().entrySet()) {
            String datasetName = entry.getKey();
            Configuration.DatasetDescription datasetDescription = entry.getValue();
            Set<ReferenceChecker> referenceCheckers = checkerFactory.getReferenceCheckers(application, datasetName);

            Set<String> selectClauseReferenceElements = new LinkedHashSet<>();
            Set<String> fromClauseJoinElements = new LinkedHashSet<>();

            String dataTableName = sqlSchema.forDataset(datasetName).getSqlIdentifier();

            for (ReferenceChecker referenceChecker : referenceCheckers) {
                String referenceType = referenceChecker.getRefType();  // especes
                String quotedViewName = sqlSchema.forReferenceType(referenceType).getSqlIdentifier();

                String quotedViewIdColumnName = quoteSqlIdentifier(referenceType + "_id");
                selectClauseReferenceElements.add(quotedViewName + ".*");
                fromClauseJoinElements.add("left outer join " + quotedViewName + " on " + dataTableName + "." + quotedViewIdColumnName + " = " + quotedViewName + "." + quotedViewIdColumnName);
            }

            Set<String> selectClauseElements = new LinkedHashSet<>(selectClauseReferenceElements);

            datasetDescription.getData().keySet().stream()
                    .map(column -> dataTableName + "." + quoteSqlIdentifier(column))
                    .forEach(selectClauseElements::add);

            String selectClause = "select " + String.join(", ", selectClauseElements);

            String fromClause = "from " + sqlSchema.forDataset(datasetName).getSqlIdentifier() + " "
                    + Joiner.on(" ").join(fromClauseJoinElements);

            String viewSql = String.join("\n", selectClause, fromClause);

            SqlTable view = sqlSchema.forDenormalizedDataset(datasetName);
            views.add(new ViewCreationCommand(view, viewSql, selectClauseReferenceElements));
        }
        return views;
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
            String referenceValueTableName = SqlSchema.forApplication(app).referenceValue().getSqlIdentifier();
            String referenceView = "select referenceValue.id as " + quotedViewIdColumnName + ", " + quotedReferenceType + ".* "
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

    public List<Map<String, Object>> readView(String appName, String dataset, ViewStrategy viewStrategy) {
        authRepository.setRoleForClient();
        Application application = service.getApplication(appName);
        SqlTable view = SqlSchema.forRelationalViewsOf(application, viewStrategy).forDataset(dataset);
        return namedParameterJdbcTemplate.queryForList("select * from " + view.getSqlIdentifier(), Collections.emptyMap());
    }

    public void addRestrictedUser(OreSiRoleToAccessDatabase role, Set<String> excludedReferenceIds, String appName, ViewStrategy viewStrategy) {
        authRepository.resetRole();
        Application app = service.getApplication(appName);
        SqlSchemaForRelationalViewsForApplication sqlSchema = SqlSchema.forRelationalViewsOf(app, viewStrategy);
        List<ViewCreationCommand> viewCreationCommands = new LinkedList<>();
        viewCreationCommands.addAll(getViewsForReferences(sqlSchema, app));
        viewCreationCommands.addAll(getViewsForDatasets(sqlSchema, app));
        for (ViewCreationCommand viewCreationCommand : viewCreationCommands) {
            Set<String> referenceIdColumns = viewCreationCommand.getReferenceIdColumns();
            String usingExpression = referenceIdColumns.stream().map(referenceIdColumn -> referenceIdColumn + "::uuid").collect(Collectors.joining(", ", "ARRAY[", "]"))
                                   + " && "
                                   + excludedReferenceIds.stream().map(excludedReferenceId -> "'" + excludedReferenceId + "'::uuid").collect(Collectors.joining(", ", "ARRAY[", "]"))
                                   ;
            SqlPolicy sqlPolicy = new SqlPolicy(viewCreationCommand.getView(), SqlPolicy.PermissiveOrRestrictive.PERMISSIVE, SqlPolicy.Statement.SELECT, role, "(" + usingExpression + ") is false");
            db.createPolicy(sqlPolicy);
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
