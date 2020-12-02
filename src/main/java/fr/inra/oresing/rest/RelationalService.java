package fr.inra.oresing.rest;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.ReferenceChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ApplicationRight;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.persistence.AuthRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.SqlPolicy;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.SqlSchemaForRelationalViewsForApplication;
import fr.inra.oresing.persistence.SqlTable;
import fr.inra.oresing.persistence.WithSqlIdentifier;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiRoleToAccessDatabase;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
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
    private AuthRepository authRepository;

    @Autowired
    private OreSiRepository repo;

    @Autowired
    private CheckerFactory checkerFactory;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    public void createViews(String appName, ViewStrategy viewStrategy) {
        Application app = repo.findApplication(appName)
                .orElseThrow(() -> new IllegalArgumentException("il n'existe pas d'application " + appName));
        createViews(app, viewStrategy);
    }

    public void createViews(UUID appId, ViewStrategy viewStrategy) {
        Application app = repo.findApplication(appId.toString())
                .orElseThrow(() -> new IllegalArgumentException("il n'existe pas d'application " + appId));
        createViews(app, viewStrategy);
    }

    private void createViews(Application app, ViewStrategy viewStrategy) {
        authRepository.resetRole();
        SchemaCreationCommand schemaCreationCommand = getSchemaCreationCommand(app, viewStrategy);
        create(schemaCreationCommand);
    }

    public void dropViews(String appName, ViewStrategy viewStrategy) {
        authRepository.resetRole();
        Application app = repo.findApplication(appName)
                .orElseThrow(() -> new IllegalArgumentException("il n'existe pas d'application " + appName));
        SchemaCreationCommand schemaCreationCommand = getSchemaCreationCommand(app, viewStrategy);
        drop(schemaCreationCommand);
    }

    private void create(SchemaCreationCommand schemaCreationCommand) {
        ViewStrategy viewStrategy = schemaCreationCommand.getViewStrategy();
        String schemaName = schemaCreationCommand.getSchema().getSqlIdentifier();
        UUID appId = schemaCreationCommand.getApplication().getId();
        OreSiRightOnApplicationRole owner = ApplicationRight.ADMIN.getRole(appId);
        namedParameterJdbcTemplate.execute("CREATE SCHEMA " + schemaName + " AUTHORIZATION " + owner.getSqlIdentifier(), PreparedStatement::execute);
        for (ViewCreationCommand viewCreationCommand : schemaCreationCommand.getViews()) {
            String viewFqn = viewCreationCommand.getView().getSqlIdentifier();
            String viewSql = viewCreationCommand.getSql();
            if (viewStrategy == ViewStrategy.VIEW) {
                namedParameterJdbcTemplate.execute("CREATE VIEW " + viewFqn + " AS (" + viewSql + ")", PreparedStatement::execute);
                namedParameterJdbcTemplate.execute("ALTER VIEW " + viewFqn + " OWNER TO " + owner.getSqlIdentifier(), PreparedStatement::execute);
                for (ApplicationRight applicationRight : ApplicationRight.values()) {
                    OreSiRightOnApplicationRole roleThatCanReadViews = applicationRight.getRole(appId);
                    namedParameterJdbcTemplate.execute("GRANT USAGE ON SCHEMA " + schemaName + " TO " + roleThatCanReadViews.getSqlIdentifier(), PreparedStatement::execute);
                    namedParameterJdbcTemplate.execute("GRANT SELECT ON ALL TABLES IN SCHEMA " + schemaName + " TO " + roleThatCanReadViews.getSqlIdentifier(), PreparedStatement::execute);
                }
            } else if (viewStrategy == ViewStrategy.TABLE) {
                namedParameterJdbcTemplate.execute("CREATE TABLE " + viewFqn + " AS (" + viewSql + ")", PreparedStatement::execute);
                namedParameterJdbcTemplate.execute("ALTER TABLE " + viewFqn + " ENABLE ROW LEVEL SECURITY", PreparedStatement::execute);
                namedParameterJdbcTemplate.execute("ALTER TABLE " + viewFqn + " OWNER TO " + owner.getSqlIdentifier(), PreparedStatement::execute);
                for (ApplicationRight applicationRight : ApplicationRight.values()) {
                    OreSiRightOnApplicationRole roleThatCanReadViews = applicationRight.getRole(appId);
                    namedParameterJdbcTemplate.execute("GRANT USAGE ON SCHEMA " + schemaName + " TO " + roleThatCanReadViews.getSqlIdentifier(), PreparedStatement::execute);
                    namedParameterJdbcTemplate.execute("GRANT SELECT ON ALL TABLES IN SCHEMA " + schemaName + " TO " + roleThatCanReadViews.getSqlIdentifier(), PreparedStatement::execute);
                }
            } else {
                throw new IllegalArgumentException("stratégie " + viewStrategy);
            }
        }
    }

    private void drop(SchemaCreationCommand schemaCreationCommand) {
        ViewStrategy viewStrategy = schemaCreationCommand.getViewStrategy();
        List<ViewCreationCommand> reverse = Lists.reverse(schemaCreationCommand.getViews());
        for (ViewCreationCommand viewCreationCommand : reverse) {
            if (viewStrategy == ViewStrategy.VIEW) {
                namedParameterJdbcTemplate.execute("DROP VIEW " + viewCreationCommand.getView().getSqlIdentifier(), PreparedStatement::execute);
            } else if (viewStrategy == ViewStrategy.TABLE) {
                namedParameterJdbcTemplate.execute("DROP TABLE " + viewCreationCommand.getView().getSqlIdentifier(), PreparedStatement::execute);
            } else {
                throw new IllegalArgumentException("stratégie " + viewStrategy);
            }
        }
        String schemaName = schemaCreationCommand.getSchema().getSqlIdentifier();
        namedParameterJdbcTemplate.execute("DROP SCHEMA " + schemaName, PreparedStatement::execute);
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
            Set<ReferenceChecker> referenceCheckers = checkerFactory.getReferenceCheckers(application, datasetDescription);

            Set<String> referenceColumnIds = new LinkedHashSet<>();
            Set<String> selectClauseReferenceElements = new LinkedHashSet<>();
            Set<String> fromClauseJoinElements = new LinkedHashSet<>();

            String dataTableName = SqlSchema.main().data().getSqlIdentifier();

            for (ReferenceChecker referenceChecker : referenceCheckers) {
                String referenceType = referenceChecker.getRefType();  // especes
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
                    + ", jsonb_to_record(data.dataValues) as " + schemaDeclaration;

            String whereClause = "where data.application = '" + appId + "'::uuid and data.dataType = '" + datasetName + "'";

            String viewSql = String.join("\n", selectClause, fromClause, whereClause);

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
            Set<ReferenceChecker> referenceCheckers = checkerFactory.getReferenceCheckers(application, datasetDescription);

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
            String referenceValueTableName = SqlSchema.main().referenceValue().getSqlIdentifier();
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
        Application application = repo.findApplication(appName)
                .orElseThrow(() -> new IllegalArgumentException("il n'existe pas d'application " + appName));
        SqlTable view = SqlSchema.forRelationalViewsOf(application, viewStrategy).forDataset(dataset);
        return namedParameterJdbcTemplate.queryForList("select * from " + view.getSqlIdentifier(), Collections.emptyMap());
    }

    public void addRestrictedUser(OreSiRoleToAccessDatabase role, Set<String> excludedReferenceIds, String appName, ViewStrategy viewStrategy) {
        authRepository.resetRole();
        Application app = repo.findApplication(appName)
                .orElseThrow(() -> new IllegalArgumentException("il n'existe pas d'application " + appName));
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
            authRepository.createPolicy(sqlPolicy);
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
