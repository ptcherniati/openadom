package fr.inra.oresing.rest;

import com.google.common.base.Joiner;
import fr.inra.oresing.checker.Checker;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.ReferenceChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ApplicationRight;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.persistence.AuthRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.SqlSchemaForRelationalViewsForApplication;
import fr.inra.oresing.persistence.SqlTable;
import fr.inra.oresing.persistence.WithSqlIdentifier;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.LinkedHashSet;
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

    public void createViews(String appName) {
        Application app = repo.findApplication(appName)
                .orElseThrow(() -> new IllegalArgumentException("il n'existe pas d'application " + appName));
        createViews(app);
    }

    public void createViews(UUID appId) {
        Application app = repo.findApplication(appId.toString())
                .orElseThrow(() -> new IllegalArgumentException("il n'existe pas d'application " + appId));
        createViews(app);
    }

    private void createViews(Application app) {
        authRepository.setRoleForClient();

        SqlSchemaForRelationalViewsForApplication sqlSchema = SqlSchema.forRelationalViewsOf(app);
        OreSiRightOnApplicationRole schemaOwner = ApplicationRight.ADMIN.getRole(app.getId());
        createSchema(sqlSchema, schemaOwner);
        createViewsForReferences(sqlSchema, app);
        createViewsForDatasets(sqlSchema, app);
    }

    private void createViewsForDatasets(SqlSchemaForRelationalViewsForApplication sqlSchema, Application application) {
        UUID appId = application.getId();

        for (Map.Entry<String, Configuration.DatasetDescription> entry : application.getConfiguration().getDataset().entrySet()) {
            Configuration.DatasetDescription datasetDescription = entry.getValue();

            Set<String> selectClauseElements = new LinkedHashSet<>();
            Set<String> fromClauseJoinElements = new LinkedHashSet<>();

            String dataTableName = SqlSchema.main().data().getSqlIdentifier();
            for (Map.Entry<String, Configuration.ColumnDescription> referenceEntry : datasetDescription.getReferences().entrySet()) {
                Configuration.ColumnDescription columnDescription = referenceEntry.getValue();
                Checker checker = checkerFactory.getChecker(columnDescription, application);
                if (checker instanceof ReferenceChecker) {
                    String referenceType = ((ReferenceChecker) checker).getRefType();  // especes
                    String quotedViewName = sqlSchema.forReferenceType(referenceType).getSqlIdentifier();

                    // left outer join "sites" on data.refsLinkedTo::uuid[] @> ARRAY["sites"."sites_id"::uuid]
                    String quotedViewIdColumnName = quoteSqlIdentifier(referenceType + "_id");
                    selectClauseElements.add(quotedViewName);
                    fromClauseJoinElements.add("left outer join " + quotedViewName + " on " + dataTableName + ".refsLinkedTo::uuid[] @> ARRAY[" + quotedViewName + "." + quotedViewIdColumnName + "::uuid]");
                }
            }

            String datasetName = entry.getKey();
            String quotedDatasetName = quoteSqlIdentifier(datasetName);
            selectClauseElements.add(quotedDatasetName);
            String selectClause = "select " + selectClauseElements.stream()
                    .map(quotedTableName -> quotedTableName + ".*")
                    .collect(Collectors.joining(", "));

            Set<String> dataColumns = new LinkedHashSet<>();
            dataColumns.addAll(datasetDescription.getData().keySet());
            dataColumns.addAll(datasetDescription.getReferences().keySet());
            String dataColumnsAsSchema = dataColumns.stream()
                    .map(this::quoteSqlIdentifier)
                    .map(quotedColumnName -> quotedColumnName + " text")
                    .collect(Collectors.joining(", ", "(", ")"));

            // exemple "pem"("date" text, "site" text, "espece" text, "projet" text, "plateforme" text, "Nombre d'individus" text, "Couleur des individus" text)
            String schemaDeclaration = quotedDatasetName + dataColumnsAsSchema;

            String fromClause = "from " + dataTableName + " "
                    + Joiner.on(" ").join(fromClauseJoinElements)
                    + ", jsonb_to_record(data.dataValues) as " + schemaDeclaration;

            String whereClause = "where data.application = '" + appId + "'::uuid";

            String viewSql = String.join("\n", selectClause, fromClause, whereClause);

            SqlTable view = sqlSchema.forDataset(datasetName);
            createView(view, viewSql);
        }
    }

    private void createView(SqlTable view, String viewSql) {
        String viewFqn = view.getSqlIdentifier();
        String createStatementSql = "CREATE VIEW " + viewFqn + " AS (" + viewSql + ")";
        namedParameterJdbcTemplate.execute(createStatementSql, Collections.emptyMap(), PreparedStatement::execute);
    }

    private void createSchema(SqlSchemaForRelationalViewsForApplication sqlSchema, OreSiRightOnApplicationRole ownerRole) {
        authRepository.resetRole();
        String schemaName = sqlSchema.getSqlIdentifier();
        String owner = ownerRole.getSqlIdentifier();
        namedParameterJdbcTemplate.execute("DROP SCHEMA " + schemaName, PreparedStatement::execute);
        namedParameterJdbcTemplate.execute("CREATE SCHEMA " + schemaName + " AUTHORIZATION " + owner, PreparedStatement::execute);
        authRepository.setRoleForClient();
    }

    private void createViewsForReferences(SqlSchemaForRelationalViewsForApplication sqlSchema, Application app) {
        UUID appId = app.getId();
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

            createView(sqlSchema.forReferenceType(referenceType), referenceView);
        }
    }

    @Deprecated
    private String quoteSqlIdentifier(String sqlIdentifier) {
        return WithSqlIdentifier.escapeSqlIdentifier(sqlIdentifier);
    }

    public List<Map<String, Object>> readView() {
        authRepository.setRoleForClient();
        return namedParameterJdbcTemplate.queryForList("select * from monsore.pem", Collections.emptyMap());
    }
}
