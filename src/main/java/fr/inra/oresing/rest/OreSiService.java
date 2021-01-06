package fr.inra.oresing.rest;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.base.MoreObjects;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import fr.inra.oresing.checker.Checker;
import fr.inra.oresing.checker.CheckerException;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.DateChecker;
import fr.inra.oresing.checker.ReferenceChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.model.LocalDateTimeRange;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.persistence.ApplicationRepository;
import fr.inra.oresing.persistence.AuthRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.SqlPolicy;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.SqlSchemaForApplication;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.PreparedStatement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@Transactional
public class OreSiService {

    @Autowired
    private OreSiRepository repo;

    @Autowired
    private AuthRepository authRepository;

    @Autowired
    private CheckerFactory checkerFactory;

    @Autowired
    private OreSiApiRequestContext request;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NamedParameterJdbcTemplate namedParameterJdbcTemplate;

    protected UUID storeFile(Application app, MultipartFile file) throws IOException {
        authRepository.setRoleForClient();
        // creation du fichier
        BinaryFile binaryFile = new BinaryFile();
        binaryFile.setApplication(app.getId());
        binaryFile.setName(file.getOriginalFilename());
        binaryFile.setSize(file.getSize());
        binaryFile.setData(file.getBytes());
        UUID result = repo.getRepository(app).store(binaryFile);
        return result;
    }

    public UUID createApplication(String name, MultipartFile configurationFile) throws IOException {

        Application app = new Application();
        app.setName(name);

        authRepository.resetRole();

        SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(app);
        org.flywaydb.core.api.configuration.ClassicConfiguration flywayConfiguration = new ClassicConfiguration();
        flywayConfiguration.setDataSource(dataSource);
        flywayConfiguration.setSchemas(sqlSchemaForApplication.getSqlIdentifier());
        flywayConfiguration.setLocations(new Location("classpath:migration/application"));
        flywayConfiguration.getPlaceholders().put("applicationSchema", sqlSchemaForApplication.getSqlIdentifier());
        Flyway flyway = new Flyway(flywayConfiguration);
        flyway.migrate();

        OreSiRightOnApplicationRole adminOnApplicationRole = OreSiRightOnApplicationRole.adminOn(app);
        OreSiRightOnApplicationRole readerOnApplicationRole = OreSiRightOnApplicationRole.readerOn(app);

        authRepository.createRole(adminOnApplicationRole);
        authRepository.createRole(readerOnApplicationRole);

        authRepository.createPolicy(new SqlPolicy(
                SqlSchema.main().application(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                SqlPolicy.Statement.ALL,
                adminOnApplicationRole,
                "name = '" + name + "'"
        ));

        authRepository.createPolicy(new SqlPolicy(
                SqlSchema.main().application(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                SqlPolicy.Statement.SELECT,
                readerOnApplicationRole,
                "name = '" + name + "'"
        ));

        namedParameterJdbcTemplate.execute("ALTER SCHEMA " + sqlSchemaForApplication.getSqlIdentifier() + " OWNER TO " + adminOnApplicationRole.getSqlIdentifier(), PreparedStatement::execute);
        namedParameterJdbcTemplate.execute("GRANT USAGE ON SCHEMA " + sqlSchemaForApplication.getSqlIdentifier() + " TO " + readerOnApplicationRole.getSqlIdentifier(), PreparedStatement::execute);

        namedParameterJdbcTemplate.execute("ALTER TABLE " + sqlSchemaForApplication.data().getSqlIdentifier() + " OWNER TO " + adminOnApplicationRole.getSqlIdentifier(), PreparedStatement::execute);
        namedParameterJdbcTemplate.execute("ALTER TABLE " + sqlSchemaForApplication.referenceValue().getSqlIdentifier() + " OWNER TO " + adminOnApplicationRole.getSqlIdentifier(), PreparedStatement::execute);
        namedParameterJdbcTemplate.execute("ALTER TABLE " + sqlSchemaForApplication.binaryFile().getSqlIdentifier() + " OWNER TO " + adminOnApplicationRole.getSqlIdentifier(), PreparedStatement::execute);

        OreSiUserRole creator = authRepository.getUserRole(request.getRequestClient().getId());
        authRepository.addUserInRole(creator, adminOnApplicationRole);

        authRepository.setRoleForClient();
        UUID result = repo.store(app);
        changeApplicationConfiguration(app, configurationFile);

        return result;
    }

    public UUID changeApplicationConfiguration(Application app, MultipartFile configurationFile) throws IOException {
        authRepository.setRoleForClient();
        // on essaie de parser le fichier, si tout ce passe bien, on remplace ou ajoute le fichier

        UUID oldConfigId = app.getConfigFile();
        UUID confId = storeFile(app, configurationFile);

        app.setConfigFile(confId);
        repo.store(app);

        // on supprime l'ancien fichier vu que tout c'est bien passé
        ApplicationRepository applicationRepository = repo.getRepository(app);
        applicationRepository.deleteBinaryFile(oldConfigId);

        Configuration conf = Configuration.read(configurationFile.getBytes());
        app.setReferenceType(new ArrayList<>(conf.getReferences().keySet()));
        app.setDataType(new ArrayList<>(conf.getDataset().keySet()));

        app.setConfiguration(conf);

        checkConfiguration(app);

        repo.store(app);

        return confId;
    }

    private void checkConfiguration(Application app) {
        Configuration conf = app.getConfiguration();
        for (Map.Entry<String, Configuration.DatasetDescription> entry : conf.getDataset().entrySet()) {
            String datasetName = entry.getKey();
            Configuration.DatasetDescription datasetDescription = entry.getValue();
            String timeScopeColumn = datasetDescription.getTimeScopeColumn();
            if (StringUtils.isBlank(timeScopeColumn)) {
                throw new IllegalArgumentException("il faut indiquer la colonne dans laquelle on recueille la période de temps à laquelle rattacher la donnée pour le gestion des droits jeu de données " + datasetName);
            }
            Set<String> knownColumns = Sets.union(datasetDescription.getData().keySet(), datasetDescription.getReferences().keySet()).immutableCopy();
            if (!knownColumns.contains(timeScopeColumn)) {
                throw new IllegalArgumentException(timeScopeColumn + " ne fait pas parti des colonnes connues " + datasetName);
            }
            Configuration.ColumnDescription timeScopeColumnDescription = MoreObjects.firstNonNull(
                    datasetDescription.getData().get(timeScopeColumn),
                    datasetDescription.getReferences().get(timeScopeColumn)
            );
            Checker timeScopeColumnChecker = checkerFactory.getChecker(timeScopeColumnDescription, app);
            if (timeScopeColumnChecker instanceof DateChecker) {
                String pattern = ((DateChecker) timeScopeColumnChecker).getPattern();
                if (!LocalDateTimeRange.getKnownPatterns().contains(pattern)) {
                    throw new IllegalArgumentException("ne sait pas traiter le format " + pattern + ". Les formats acceptés sont " + LocalDateTimeRange.getKnownPatterns());
                }
            }
        }
    }

    public UUID addReference(Application app, String refType, MultipartFile file) throws IOException {
        authRepository.setRoleForClient();
        UUID fileId = storeFile(app, file);

        Configuration conf = app.getConfiguration();
        Configuration.ReferenceDescription ref = conf.getReferences().get(refType);

        CsvSchema.Builder schemaBuilder = CsvSchema.builder();
        ref.getColumns().entrySet().forEach(e -> {
            schemaBuilder.addColumn(e.getKey());
        });
        CsvSchema schema = schemaBuilder.setColumnSeparator(ref.getSeparator()).build();
        ApplicationRepository applicationRepository = repo.getRepository(app);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(file.getBytes())))) {
            for (int i = 0; i < ref.getLineToSkip(); i++) {
                reader.readLine();
            }

            CsvMapper mapper = new CsvMapper();
            List<ReferenceValue> refs = mapper.readerFor(Map.class).with(schema).readValues(reader).readAll().stream()
                    .map(line -> {
                        ReferenceValue e = new ReferenceValue();
                        e.setBinaryFile(fileId);
                        e.setReferenceType(refType);
                        e.setApplication(app.getId());
                        e.setRefValues((Map<String, String>) line);
                        return e;
                    }).collect(Collectors.toList());

            refs.forEach(applicationRepository::store);
        }

        return fileId;
    }

    public UUID addData(Application app, String dataType, MultipartFile file) throws IOException, CheckerException {
        authRepository.setRoleForClient();
        UUID fileId = storeFile(app, file);

        // recuperation de la configuration pour ce type de donnees
        Configuration conf = app.getConfiguration();
        Configuration.DatasetDescription dataSet = conf.getDataset().get(dataType);

        // ajout des contraintes sur les champs de type referenciel
        Map<String, Checker> checkers = new HashMap<>();
        for (Map.Entry<String, Configuration.ColumnDescription> e : dataSet.getReferences().entrySet()) {
            checkers.put(e.getKey(), checkerFactory.getChecker(e.getValue(), app));
        }

        // ajout des contraintes sur les champs de data
        for (Map.Entry<String, Configuration.DataDescription> e : dataSet.getData().entrySet()) {
            checkers.put(e.getKey(), checkerFactory.getChecker(e.getValue(), app));
            if (e.getValue() != null) {
                // ajout de contraintes sur les champs de precisions
                for (Map.Entry<String, Configuration.ColumnDescription> a : e.getValue().getAccuracy().entrySet()) {
                    checkers.put(a.getKey(), checkerFactory.getChecker(a.getValue(), app));
                }
            }
        }

        CsvSchema.Builder schemaBuilder = CsvSchema.builder();
        schemaBuilder.setColumnSeparator(dataSet.getSeparator());
        schemaBuilder.setUseHeader(true);
        schemaBuilder.setReorderColumns(true);

        checkers.keySet().forEach(schemaBuilder::addColumn);
        CsvSchema schema = schemaBuilder.build();

        List<String> error = new LinkedList<>();

        DateChecker timeScopeColumnChecker = (DateChecker) checkers.get(dataSet.getTimeScopeColumn());
        String timeScopeColumnPattern = timeScopeColumnChecker.getPattern();

        ApplicationRepository applicationRepository = repo.getRepository(app);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(file.getBytes())))) {
            for (int i = 0; i < dataSet.getLineToSkip(); i++) {
                reader.readLine();
            }

            CsvMapper mapper = new CsvMapper();
            mapper.readerFor(Map.class).with(schema).readValues(reader).forEachRemaining(line -> {
                Map<String, String> values = (Map<String, String>) line;
                List<UUID> refsLinkedTo = new ArrayList<>();
                values.forEach((k, v) -> {
                    try {
                        Checker checker = checkers.get(k);
                        if (checker == null) {
                            throw new CheckerException(String.format("Unknown column: '%s'", k));
                        }
                        Object result = checker.check(v);
                        if (checker instanceof ReferenceChecker) {
                            refsLinkedTo.add((UUID) result);
                        }
                    } catch (CheckerException eee) {
                        log.debug("Validation problem", eee);
                        error.add(eee.getMessage());
                    }
                });

                String timeScopeValue = values.get(dataSet.getTimeScopeColumn());
                LocalDateTimeRange timeScope = LocalDateTimeRange.parse(timeScopeValue, timeScopeColumnPattern);

                // String rowId = Hashing.sha256().hashString(line.toString(), Charsets.UTF_8).toString();
                String rowId = UUID.randomUUID().toString();

                for (Map.Entry<String, Configuration.DataGroupDescription> entry : dataSet.getDataGroups().entrySet()) {
                    String dataGroup = entry.getKey();
                    Configuration.DataGroupDescription dataGroupDescription = entry.getValue();

                    Set<String> columnsIncludedInDataGroup = dataGroupDescription.getData().keySet();
                    Map<String, String> dataGroupValues = Maps.filterKeys(values, columnsIncludedInDataGroup::contains);

                    Data e = new Data();
                    e.setBinaryFile(fileId);
                    e.setDataType(dataType);
                    e.setRowId(rowId);
                    e.setDataGroup(dataGroup);
                    e.setApplication(app.getId());
                    e.setRefsLinkedTo(refsLinkedTo);
                    e.setDataValues(dataGroupValues);
                    e.setTimeScope(timeScope);
                    applicationRepository.store(e);
                }
            });
        }

        if (!error.isEmpty()) {
            throw new CheckerException("Parsing error:\n" + String.join("\n\t", error));
        }

        return fileId;
    }

    public List<Map<String, String>> findData(String applicationNameOrId, String dataType) {
        authRepository.setRoleForClient();
        Application app = getApplication(applicationNameOrId);
        ApplicationRepository applicationRepository = repo.getRepository(app);
        List<Map<String, String>> data = applicationRepository.findData(dataType);
        return data;
    }

    public List<Application> getApplications() {
        authRepository.setRoleForClient();
        List<Application> result = repo.findAll(Application.class);
        return result;
    }

    public Application getApplication(String nameOrId) {
        authRepository.setRoleForClient();
        return repo.findApplication(nameOrId);
    }

    public Optional<Application> tryFindApplication(String nameOrId) {
        authRepository.setRoleForClient();
        return repo.tryFindApplication(nameOrId);
    }
}