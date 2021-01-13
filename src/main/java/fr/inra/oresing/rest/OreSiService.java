package fr.inra.oresing.rest;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import fr.inra.oresing.OreSiTechnicalException;
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
import fr.inra.oresing.persistence.SqlService;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.CSVRecord;
import org.assertj.core.util.Streams;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private SqlService db;

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

        db.createRole(adminOnApplicationRole);
        db.createRole(readerOnApplicationRole);

        db.createPolicy(new SqlPolicy(
                SqlSchema.main().application(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                SqlPolicy.Statement.ALL,
                adminOnApplicationRole,
                "name = '" + name + "'"
        ));

        db.createPolicy(new SqlPolicy(
                SqlSchema.main().application(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                SqlPolicy.Statement.SELECT,
                readerOnApplicationRole,
                "name = '" + name + "'"
        ));

        db.setSchemaOwner(sqlSchemaForApplication, adminOnApplicationRole);
        db.grantUsage(sqlSchemaForApplication, readerOnApplicationRole);

        db.setTableOwner(sqlSchemaForApplication.data(), adminOnApplicationRole);
        db.setTableOwner(sqlSchemaForApplication.referenceValue(), adminOnApplicationRole);
        db.setTableOwner(sqlSchemaForApplication.binaryFile(), adminOnApplicationRole);

        OreSiUserRole creator = authRepository.getUserRole(request.getRequestClient().getId());
        db.addUserInRole(creator, adminOnApplicationRole);

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
        if (conf.getReferences() == null) {
            app.setReferenceType(Collections.emptyList());
        } else {
            app.setReferenceType(new ArrayList<>(conf.getReferences().keySet()));
        }
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
            Configuration.VariableComponentReference timeScopeColumn = datasetDescription.getTimeScopeColumn();
            if (timeScopeColumn == null) {
                throw new IllegalArgumentException("il faut indiquer la variable (et son composant) dans laquelle on recueille la période de temps à laquelle rattacher la donnée pour le gestion des droits jeu de données " + datasetName);
            }
            if (timeScopeColumn.getVariable() == null) {
                throw new IllegalArgumentException("il faut indiquer la variable dans laquelle on recueille la période de temps à laquelle rattacher la donnée pour le gestion des droits jeu de données " + datasetName + ". Valeurs possibles " + datasetDescription.getData().keySet());
            }
            if (!datasetDescription.getData().containsKey(timeScopeColumn.getVariable())) {
                throw new IllegalArgumentException(timeScopeColumn + " ne fait pas parti des colonnes connues " + datasetDescription.getData().keySet());
            }
            if (timeScopeColumn.getComponent() == null) {
                throw new IllegalArgumentException("il faut indiquer le composant de la variable " + timeScopeColumn.getVariable() + " dans laquelle on recueille la période de temps à laquelle rattacher la donnée pour le gestion des droits jeu de données " + datasetName + ". Valeurs possibles " + datasetDescription.getData().get(timeScopeColumn.getVariable()).getComponents().keySet());
            }
            if (!datasetDescription.getData().get(timeScopeColumn.getVariable()).getComponents().containsKey(timeScopeColumn.getComponent())) {
                throw new IllegalArgumentException(timeScopeColumn + " ne fait pas parti des colonnes connues " + datasetDescription.getData().keySet());
            }
            Configuration.ColumnDescription timeScopeColumnDescription = datasetDescription.getData().get(timeScopeColumn.getVariable());
            Checker timeScopeColumnChecker = checkerFactory.getChecker(timeScopeColumnDescription, app, timeScopeColumn.getComponent());
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

        Map<String, Map<String, Checker>> checkers = new LinkedHashMap<>();
        for (Map.Entry<String, Configuration.ColumnDescription> variableEntry : dataSet.getData().entrySet()) {
            String variable = variableEntry.getKey();
            Configuration.ColumnDescription variableDescription = variableEntry.getValue();
            checkers.put(variable, new LinkedHashMap<>());
            for (Map.Entry<String, Configuration.VariableComponentDescription> componentEntry : variableDescription.getComponents().entrySet()) {
                String component = componentEntry.getKey();
                checkers.get(variable).put(component, checkerFactory.getChecker(variableDescription, app, component));
            }
        }

        List<String> error = new LinkedList<>();

        DateChecker timeScopeColumnChecker = (DateChecker) checkers.get(dataSet.getTimeScopeColumn().getVariable()).get(dataSet.getTimeScopeColumn().getComponent());
        String timeScopeColumnPattern = timeScopeColumnChecker.getPattern();

        ApplicationRepository applicationRepository = repo.getRepository(app);

        Consumer<Map<String, Map<String, String>>> lineConsumer = line -> {
            Map<String, Map<String, String>> values = line;
            List<UUID> refsLinkedTo = new ArrayList<>();
            values.forEach((compositeVariableName, compositeVariableValue) -> {
                compositeVariableValue.forEach((componentName, componentValue) -> {
                    try {
                        Checker checker = checkers.get(compositeVariableName).get(componentName);
                        if (checker == null) {
                            throw new CheckerException(String.format("Unknown column: '%s'", compositeVariableName));
                        }
                        Object result = checker.check(componentValue);
                        if (checker instanceof ReferenceChecker) {
                            refsLinkedTo.add((UUID) result);
                        }
                    } catch (CheckerException eee) {
                        log.debug("Validation problem", eee);
                        error.add(eee.getMessage());
                    }
                });
            });

            String timeScopeValue = values.get(dataSet.getTimeScopeColumn().getVariable()).get(dataSet.getTimeScopeColumn().getComponent());
            LocalDateTimeRange timeScope = LocalDateTimeRange.parse(timeScopeValue, timeScopeColumnPattern);

            // String rowId = Hashing.sha256().hashString(line.toString(), Charsets.UTF_8).toString();
            String rowId = UUID.randomUUID().toString();

            for (Map.Entry<String, Configuration.DataGroupDescription> entry : dataSet.getDataGroups().entrySet()) {
                String dataGroup = entry.getKey();
                Configuration.DataGroupDescription dataGroupDescription = entry.getValue();

                Set<String> columnsIncludedInDataGroup = dataGroupDescription.getData().keySet();
                Map<String, Map<String, String>> dataGroupValues = Maps.filterKeys(values, columnsIncludedInDataGroup::contains);

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
        };

        Configuration.FormatDescription formatDescription = dataSet.getFormat();
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(formatDescription.getSeparator())
                .withSkipHeaderRecord();
        try (InputStream csv = file.getInputStream()) {
            CSVParser csvParser = CSVParser.parse(csv, Charsets.UTF_8, csvFormat);
            Iterator<CSVRecord> linesIterator = csvParser.iterator();
            Iterators.advance(linesIterator, formatDescription.getLineToSkip());
            CSVRecord headerRow = linesIterator.next();
            ImmutableList<String> columns = Streams.stream(headerRow).collect(ImmutableList.toImmutableList());
            ImmutableList<String> expectedColumns = formatDescription.getColumns().stream()
                    .map(Configuration.ColumnBindingDescription::getHeader)
                    .collect(ImmutableList.toImmutableList());
            Preconditions.checkArgument(expectedColumns.equals(columns), "Fichier incorrect. Entêtes détectés " + columns + ". Entêtes attendus " + expectedColumns);
            Iterators.advance(linesIterator, formatDescription.getLineToSkipAfterHeader());
            Stream<Map<String, Map<String, String>>> lines = Streams.stream(csvParser).map(line -> {
                Iterator<String> currentHeader = columns.iterator();
                Map<String, Map<String, String>> record = new LinkedHashMap<>();
                ImmutableMap<String, Configuration.ColumnBindingDescription> bindingPerHeader = Maps.uniqueIndex(formatDescription.getColumns(), Configuration.ColumnBindingDescription::getHeader);
                line.forEach(column -> {
                    String header = currentHeader.next();
                    Configuration.ColumnBindingDescription bindingDescription = bindingPerHeader.get(header);
                    String variable = bindingDescription.getReference().getVariable();
                    String component = bindingDescription.getReference().getComponent();
                    record.computeIfAbsent(variable, whatever -> new LinkedHashMap<>()).put(component, column);
                });
                return record;
            });
            lines.forEach(lineConsumer);
        }

        if (!error.isEmpty()) {
            throw new CheckerException("Parsing error:\n" + String.join("\n\t", error));
        }

        return fileId;
    }

    public String getDataCsv(String nameOrId, String dataType) {
        List<Map<String, Map<String, String>>> list = findData(nameOrId, dataType);
        Configuration.FormatDescription format = getApplication(nameOrId)
                .getConfiguration()
                .getDataset()
                .get(dataType)
                .getFormat();
        String result = "";
        if (list.size() > 0) {
            CSVFormat csvFormat = CSVFormat.DEFAULT
                    .withDelimiter(format.getSeparator())
                    .withSkipHeaderRecord();
            StringWriter out = new StringWriter();
            try {
                CSVPrinter csvPrinter = new CSVPrinter(out, csvFormat);
                ImmutableList<String> headers = format.getColumns().stream()
                        .map(Configuration.ColumnBindingDescription::getHeader)
                        .collect(ImmutableList.toImmutableList());
                csvPrinter.printRecord(headers);
                for (Map<String, Map<String, String>> record : list) {
                    ImmutableList<String> rowAsRecord = format.getColumns().stream()
                            .map(Configuration.ColumnBindingDescription::getReference)
                            .map(reference -> {
                                Map<String, String> components = record.computeIfAbsent(reference.getVariable(), k -> Collections.emptyMap());
                                return components.getOrDefault(reference.getComponent(), "");
                            })
                            .collect(ImmutableList.toImmutableList());
                    csvPrinter.printRecord(rowAsRecord);
                }
            } catch (IOException e) {
                throw new OreSiTechnicalException("erreur lors de la génération du fichier CSV", e);
            }
            result = out.toString();
        }
        return result;
    }

    public List<Map<String, Map<String, String>>> findData(String applicationNameOrId, String dataType) {
        authRepository.setRoleForClient();
        Application app = getApplication(applicationNameOrId);
        ApplicationRepository applicationRepository = repo.getRepository(app);
        List<Map<String, Map<String, String>>> data = applicationRepository.findData(dataType);
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