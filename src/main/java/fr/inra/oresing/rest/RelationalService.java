package fr.inra.oresing.rest;

import com.google.common.base.Joiner;
import fr.inra.oresing.checker.Checker;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.ReferenceChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.persistence.AuthRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.util.Collections;
import java.util.LinkedHashMap;
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

        Map<String, String> sqlViewPerDatasetNames = getSqlViewPerDatasetNames(app);
        for (Map.Entry<String, String> entry : sqlViewPerDatasetNames.entrySet()) {
            String datasetName = entry.getKey();
            String viewSql = entry.getValue();
            String quotedDatasetName = quoteSqlIdentifier(datasetName);

            String createStatementSql = "CREATE VIEW " + quotedDatasetName + " AS (" + viewSql + ")";
            namedParameterJdbcTemplate.execute(createStatementSql, Collections.emptyMap(), PreparedStatement::execute);
        }
    }

    private String quoteSqlIdentifier(String sqlIdentifier) {
        if (sqlIdentifier.contains(" ")) {
            return "\"" + sqlIdentifier + "\"";
        } else {
            return sqlIdentifier;
        }
    }

    private Map<String, String> getSqlViewPerDatasetNames(Application app) {

        UUID appId = app.getId();
        Map<String, String> sqlViewPerReferenceType = new LinkedHashMap<>();
        Set<String> withClauseElements = new LinkedHashSet<>();

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
            String referenceView = "select referenceValue.id as " + quotedViewIdColumnName + ", " + quotedReferenceType + ".* "
                    + " from referenceValue, jsonb_to_record(referenceValue.refValues) as " + schemaDeclaration
                    + " where referenceType = '" + referenceType + "' and application = '" + appId + "'::uuid";

            if (log.isTraceEnabled()) {
                log.trace("pour le référentiel " + referenceType + ", la requête pour avoir un vue relationnelle des données JSON est " + referenceView);
            }

            sqlViewPerReferenceType.put(quotedReferenceType, referenceView);
            withClauseElements.add(quotedReferenceType + " AS (" + referenceView + ")");
        }

        String withClause = "WITH " + String.join(",\n", withClauseElements);

//            String referenceView = " select projet.* "
//                    + " from referencevalue, jsonb_to_record(referencevalue.refvalues) as projet(nom_en text, nom_fr text, nom_key text, definition_en text, definition_fr text)"
//                    + " where referencetype = 'projet' and application = '" + appId + "'::uuid";

        Map<String, String> sqlViewPerDatasetNames = new LinkedHashMap<>();

        for (Map.Entry<String, Configuration.DatasetDescription> entry : app.getConfiguration().getDataset().entrySet()) {
            Configuration.DatasetDescription datasetDescription = entry.getValue();

            Set<String> selectClauseElements = new LinkedHashSet<>();
            Set<String> fromClauseJoinElements = new LinkedHashSet<>();

            for (Map.Entry<String, Configuration.ColumnDescription> referenceEntry : datasetDescription.getReferences().entrySet()) {
                Configuration.ColumnDescription columnDescription = referenceEntry.getValue();
                Checker checker = checkerFactory.getChecker(columnDescription, app);
                if (checker instanceof ReferenceChecker) {
                    String referenceType = ((ReferenceChecker) checker).getRefType();  // especes
                    String quotedViewName = quoteSqlIdentifier(referenceType);

                    // left outer join "sites" on data.refsLinkedTo::uuid[] @> ARRAY["sites"."sites_id"::uuid]
                    String quotedViewIdColumnName = quoteSqlIdentifier(referenceType + "_id");
                    selectClauseElements.add(quotedViewName);
                    fromClauseJoinElements.add("left outer join " + quotedViewName + " on data.refsLinkedTo::uuid[] @> ARRAY[" + quotedViewName + "." + quotedViewIdColumnName + "::uuid]");
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

            String fromClause = "from data "
                    + Joiner.on(" ").join(fromClauseJoinElements)
                    + ", jsonb_to_record(data.dataValues) as " + schemaDeclaration;

            String whereClause = "where data.application = '" + appId + "'::uuid";

            String viewSql = String.join("\n", withClause, selectClause, fromClause, whereClause);

            sqlViewPerDatasetNames.put(datasetName, viewSql);
        }

        return sqlViewPerDatasetNames;
    }

    public List<Map<String, Object>> readView() {
        authRepository.setRoleForClient();
        return namedParameterJdbcTemplate.queryForList("select * from pem", Collections.emptyMap());
    }
}
