package fr.inra.oresing.rest;

import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Multiset;
import com.google.common.collect.Sets;
import com.google.common.collect.TreeMultiset;
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
import fr.inra.oresing.model.VariableComponentKey;
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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
        flywayConfiguration.setSchemas(sqlSchemaForApplication.getName());
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

    public UUID changeApplicationConfiguration(String nameOrId, MultipartFile configurationFile) throws IOException {
        authRepository.setRoleForClient();
        Application app = getApplication(nameOrId);
        Configuration oldConfiguration = app.getConfiguration();
        UUID oldConfigFileId = app.getConfigFile();
        UUID uuid = changeApplicationConfiguration(app, configurationFile);
        Configuration newConfiguration = app.getConfiguration();
        int oldVersion = oldConfiguration.getApplication().getVersion();
        int newVersion = newConfiguration.getApplication().getVersion();
        Preconditions.checkArgument(newVersion > oldVersion, "l'application " + app.getName() + " est déjà dans la version " + oldVersion);
        int firstMigrationToApply = oldVersion + 1;
        if (log.isInfoEnabled()) {
            log.info("va migrer les données de " + app.getName() + " de la version actuelle " + oldVersion + " à la nouvelle version " + newVersion);
        }
        ApplicationRepository applicationRepository = repo.getRepository(app);
        for (Map.Entry<String, Configuration.DatasetDescription> datasetEntry : newConfiguration.getDataset().entrySet()) {
            String dataType = datasetEntry.getKey();
            Configuration.DatasetDescription datasetDescription = datasetEntry.getValue();
            ImmutableMap<VariableComponentKey, Checker> checkers = checkerFactory.getCheckers(app, dataType);
            if (log.isInfoEnabled()) {
                log.info("va migrer les données de " + app.getName() + ", type de données, " + dataType + " de la version actuelle " + oldVersion + " à la nouvelle version " + newVersion);
            }
            for (int migrationVersionToApply = firstMigrationToApply; migrationVersionToApply <= newVersion; migrationVersionToApply++) {
                List<Configuration.MigrationDescription> migrations = datasetDescription.getMigrations().get(migrationVersionToApply);
                if (migrations == null) {
                    if (log.isInfoEnabled()) {
                        log.info("aucune migration déclarée pour migrer le type de données " + dataType + " vers la version " + migrationVersionToApply);
                    }
                } else {
                    if (log.isInfoEnabled()) {
                        log.info(migrations.size() + " migrations déclarée pour migrer vers la version " + migrationVersionToApply);
                    }
                    for (Configuration.MigrationDescription migration : migrations) {
                        Preconditions.checkArgument(migration.getStrategy() == Configuration.MigrationStrategy.ADD_VARIABLE);
                        String dataGroup = migration.getDataGroup();
                        String variable = migration.getVariable();
                        Map<String, String> variableValue = new LinkedHashMap<>();
                        Set<UUID> refsLinkedToToAdd = new LinkedHashSet<>();
                        for (Map.Entry<String, Configuration.AddVariableMigrationDescription> componentEntry : migration.getComponents().entrySet()) {
                            String component = componentEntry.getKey();
                            String componentValue = Optional.ofNullable(componentEntry.getValue())
                                    .map(Configuration.AddVariableMigrationDescription::getDefaultValue)
                                    .orElse("");
                            VariableComponentKey reference = new VariableComponentKey(variable, component);
                            Checker checker = checkers.get(reference);
                            if (checker instanceof ReferenceChecker) {
                                try {
                                    UUID referenceId = checker.check(componentValue);
                                    refsLinkedToToAdd.add(referenceId);
                                } catch (CheckerException e) {
                                    throw new IllegalStateException(componentValue + " n'est pas une valeur par défaut acceptable pour " + component);
                                }
                            }
                            variableValue.put(component, componentValue);
                        }
                        Map<String, Map<String, String>> variablesToAdd = Map.of(variable, variableValue);
                        int migratedCount = applicationRepository.migrateData(dataType, dataGroup, variablesToAdd, refsLinkedToToAdd);
                        if (log.isInfoEnabled()) {
                            log.info(migratedCount + " lignes migrées");
                        }
                    }
                }
            }

            validateStoredData(app, dataType);
        }

        // on supprime l'ancien fichier vu que tout c'est bien passé
        boolean deleted = applicationRepository.deleteBinaryFile(oldConfigFileId);
        Preconditions.checkState(deleted);

        return uuid;
    }

    private void validateStoredData(Application app, String dataType) {
        ApplicationRepository applicationRepository = repo.getRepository(app);
        ImmutableMap<VariableComponentKey, Checker> checkers = checkerFactory.getCheckers(app, dataType);
        Consumer<ImmutableMap<VariableComponentKey, String>> validateRow = line -> {
            for (Map.Entry<VariableComponentKey, String> entry : line.entrySet()) {
                VariableComponentKey reference = entry.getKey();
                Checker checker = checkers.get(reference);
                String value = entry.getValue();
                try {
                    checker.check(value);
                } catch (CheckerException e) {
                    throw new IllegalStateException("erreur de validation d'une donnée stockée", e);
                }
            }
        };
        applicationRepository.findData(dataType).stream()
                .map(this::valuesToIndexedPerReferenceMap)
                .forEach(validateRow);
    }

    private UUID changeApplicationConfiguration(Application app, MultipartFile configurationFile) throws IOException {
        UUID confId = storeFile(app, configurationFile);
        app.setConfigFile(confId);
        repo.store(app);
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
            VariableComponentKey timeScopeVariableComponentKey = datasetDescription.getAuthorization().getTimeScope();
            if (timeScopeVariableComponentKey == null) {
                throw new IllegalArgumentException("il faut indiquer la variable (et son composant) dans laquelle on recueille la période de temps à laquelle rattacher la donnée pour le gestion des droits jeu de données " + datasetName);
            }
            Set<String> variables = datasetDescription.getData().keySet();
            if (timeScopeVariableComponentKey.getVariable() == null) {
                throw new IllegalArgumentException("il faut indiquer la variable dans laquelle on recueille la période de temps à laquelle rattacher la donnée pour le gestion des droits jeu de données " + datasetName + ". Valeurs possibles " + variables);
            }
            if (!datasetDescription.getData().containsKey(timeScopeVariableComponentKey.getVariable())) {
                throw new IllegalArgumentException(timeScopeVariableComponentKey + " ne fait pas parti des colonnes connues " + variables);
            }
            if (timeScopeVariableComponentKey.getComponent() == null) {
                throw new IllegalArgumentException("il faut indiquer le composant de la variable " + timeScopeVariableComponentKey.getVariable() + " dans laquelle on recueille la période de temps à laquelle rattacher la donnée pour le gestion des droits jeu de données " + datasetName + ". Valeurs possibles " + datasetDescription.getData().get(timeScopeVariableComponentKey.getVariable()).getComponents().keySet());
            }
            if (!datasetDescription.getData().get(timeScopeVariableComponentKey.getVariable()).getComponents().containsKey(timeScopeVariableComponentKey.getComponent())) {
                throw new IllegalArgumentException(timeScopeVariableComponentKey + " ne fait pas parti des colonnes connues " + variables);
            }
            ImmutableMap<VariableComponentKey, Checker> checkers = checkerFactory.getCheckers(app, datasetName);
            Checker timeScopeColumnChecker = checkers.get(timeScopeVariableComponentKey);
            if (timeScopeColumnChecker instanceof DateChecker) {
                String pattern = ((DateChecker) timeScopeColumnChecker).getPattern();
                if (!LocalDateTimeRange.getKnownPatterns().contains(pattern)) {
                    throw new IllegalArgumentException("ne sait pas traiter le format " + pattern + ". Les formats acceptés sont " + LocalDateTimeRange.getKnownPatterns());
                }
            }

            Multiset<String> variableOccurrencesInDataGroups = TreeMultiset.create();
            for (Map.Entry<String, Configuration.DataGroupDescription> dataGroupEntry : datasetDescription.getAuthorization().getDataGroups().entrySet()) {
                String dataGroup = dataGroupEntry.getKey();
                Configuration.DataGroupDescription dataGroupDescription = dataGroupEntry.getValue();
                Set<String> dataGroupVariables = dataGroupDescription.getData();
                variableOccurrencesInDataGroups.addAll(dataGroupVariables);
                ImmutableSet<String> unknownVariables = Sets.difference(dataGroupVariables, variables).immutableCopy();
                if (!unknownVariables.isEmpty()) {
                    throw new IllegalArgumentException("le groupe de données " + dataGroup + " est contient des données qui ne sont pas déclarées " + unknownVariables + ". Données connues " + variables);
                }
            }

            variables.forEach(variable -> {
                int count = variableOccurrencesInDataGroups.count(variable);
                if (count == 0) {
                    throw new IllegalArgumentException("la variable " + variable + " n'est déclarée appartenir à aucun groupe de données, elle doit être présente dans un groupe");
                } else if (count > 1) {
                    throw new IllegalArgumentException("la variable " + variable + " est déclarée dans plusieurs groupes de données, elle ne doit être présente que dans un groupe");
                }
            });
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
            for (int lineNumber = 1; lineNumber < ref.getFirstRowLine(); lineNumber++) {
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

        ImmutableMap<VariableComponentKey, Checker> checkers = checkerFactory.getCheckers(app, dataType);

        List<String> error = new LinkedList<>();

        DateChecker timeScopeColumnChecker = (DateChecker) checkers.get(dataSet.getAuthorization().getTimeScope());
        String timeScopeColumnPattern = timeScopeColumnChecker.getPattern();

        ApplicationRepository applicationRepository = repo.getRepository(app);

        Consumer<Map<VariableComponentKey, String>> lineConsumer = line -> {
            Map<VariableComponentKey, String> values = line;
            List<UUID> refsLinkedTo = new ArrayList<>();
            values.forEach((variableComponentKey, value) -> {
                try {
                    Checker checker = checkers.get(variableComponentKey);
                    if (checker == null) {
                        throw new CheckerException("Unknown column: " + variableComponentKey);
                    }
                    Object result = checker.check(value);
                    if (checker instanceof ReferenceChecker) {
                        refsLinkedTo.add((UUID) result);
                    }
                } catch (CheckerException eee) {
                    log.debug("Validation problem", eee);
                    error.add(eee.getMessage());
                }
            });

            String timeScopeValue = values.get(dataSet.getAuthorization().getTimeScope());
            LocalDateTimeRange timeScope = LocalDateTimeRange.parse(timeScopeValue, timeScopeColumnPattern);

            // String rowId = Hashing.sha256().hashString(line.toString(), Charsets.UTF_8).toString();
            String rowId = UUID.randomUUID().toString();

            for (Map.Entry<String, Configuration.DataGroupDescription> entry : dataSet.getAuthorization().getDataGroups().entrySet()) {
                String dataGroup = entry.getKey();
                Configuration.DataGroupDescription dataGroupDescription = entry.getValue();

                Predicate<VariableComponentKey> includeInDataGroupPredicate = variableComponentKey -> dataGroupDescription.getData().contains(variableComponentKey.getVariable());
                Map<VariableComponentKey, String> dataGroupValues = Maps.filterKeys(values, includeInDataGroupPredicate);

                Map<String, Map<String, String>> toStore = new LinkedHashMap<>();
                for (Map.Entry<VariableComponentKey, String> entry2 : dataGroupValues.entrySet()) {
                    String variable = entry2.getKey().getVariable();
                    String component = entry2.getKey().getComponent();
                    String value = entry2.getValue();
                    toStore.computeIfAbsent(variable, k -> new LinkedHashMap<>()).put(component, value);
                }

                Data e = new Data();
                e.setBinaryFile(fileId);
                e.setDataType(dataType);
                e.setRowId(rowId);
                e.setDataGroup(dataGroup);
                e.setApplication(app.getId());
                e.setRefsLinkedTo(refsLinkedTo);
                e.setDataValues(toStore);
                e.setTimeScope(timeScope);
                applicationRepository.store(e);
            }
        };

        Configuration.FormatDescription formatDescription = dataSet.getFormat();

        Function<List<Map.Entry<String, String>>, ImmutableSet<Map<VariableComponentKey, String>>> lineAsMapToRecordsFn;
        if (formatDescription.getRepeatedColumns() == null || formatDescription.getRepeatedColumns().isEmpty()) {
            ImmutableSet<String> expectedColumns = formatDescription.getColumns().stream()
                    .map(Configuration.ColumnBindingDescription::getHeader)
                    .collect(ImmutableSet.toImmutableSet());
            ImmutableMap<String, Configuration.ColumnBindingDescription> bindingPerHeader = Maps.uniqueIndex(formatDescription.getColumns(), Configuration.ColumnBindingDescription::getHeader);
            lineAsMapToRecordsFn = line -> {
                ImmutableList<String> actualHeaders = line.stream().map(Map.Entry::getKey).collect(ImmutableList.toImmutableList());
                Preconditions.checkArgument(expectedColumns.containsAll(actualHeaders), "Fichier incorrect. Entêtes détectés " + actualHeaders + ". Entêtes attendus " + expectedColumns);
                Map<VariableComponentKey, String> record = new LinkedHashMap<>();
                for (Map.Entry<String, String> entry : line) {
                    String header = entry.getKey();
                    String value = entry.getValue();
                    Configuration.ColumnBindingDescription bindingDescription = bindingPerHeader.get(header);
                    record.put(bindingDescription.getBoundTo(), value);
                }
                return ImmutableSet.of(record);
            };
        } else {
            lineAsMapToRecordsFn = line -> {
                LinkedList<Map.Entry<String, String>> lineCopy = new LinkedList<>(line);
                Map<VariableComponentKey, String> recordPrototype = new LinkedHashMap<>();
                for (Configuration.ColumnBindingDescription column : formatDescription.getColumns()) {
                    Map.Entry<String, String> poll = lineCopy.poll();
                    String header = poll.getKey();
                    Preconditions.checkState(header.equals(column.getHeader()), "Entête inattendu " + header + ". Entête attendu " + column.getHeader());
                    String value = poll.getValue();
                    recordPrototype.put(column.getBoundTo(), value);
                }
                Iterator<Map.Entry<String, String>> actualColumnsIterator = lineCopy.iterator();
                Iterator<Configuration.RepeatedColumnBindingDescription> expectedColumns = formatDescription.getRepeatedColumns().iterator();
                Set<Map<VariableComponentKey, String>> records = new LinkedHashSet<>();

                Map<VariableComponentKey, String> tokenValues = new LinkedHashMap<>(recordPrototype);
                Map<VariableComponentKey, String> bodyValues = new LinkedHashMap<>(recordPrototype);
                while (actualColumnsIterator.hasNext()) {
                    Map.Entry<String, String> actualColumn = actualColumnsIterator.next();
                    Configuration.RepeatedColumnBindingDescription expectedColumn = expectedColumns.next();

                    String actualHeader = actualColumn.getKey();
                    String value = actualColumn.getValue();

                    String headerPattern = expectedColumn.getHeaderPattern();
                    Pattern pattern = Pattern.compile(headerPattern);
                    Matcher matcher = pattern.matcher(actualHeader);
                    boolean matches = matcher.matches();
                    Preconditions.checkState(matches, "Entête imprévu " + actualHeader + ". Entête attendu " + headerPattern);

                    List<Configuration.HeaderPatternToken> tokens = expectedColumn.getTokens();
                    if (tokens != null) {
                        Preconditions.checkState(matcher.groupCount() == tokens.size(), "On doit pouvoir repérer " + tokens.size() + " informations dans l'entête " + actualHeader + ", or seulement " + matcher.groupCount() + " détectés");
                        int groupIndex = 1;
                        for (Configuration.HeaderPatternToken token : tokens) {
                            tokenValues.put(token.getBoundTo(), matcher.group(groupIndex++));
                        }
                    }

                    bodyValues.put(expectedColumn.getBoundTo(), value);

                    if (!expectedColumns.hasNext()) {
                        Map<VariableComponentKey, String> record = new LinkedHashMap<>(recordPrototype);
                        record.putAll(tokenValues);
                        record.putAll(bodyValues);
                        records.add(record);
                        tokenValues.clear();
                        bodyValues.clear();
                        expectedColumns = formatDescription.getRepeatedColumns().iterator();
                    }
                }
                return ImmutableSet.copyOf(records);
            };
        }

        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(formatDescription.getSeparator())
                .withSkipHeaderRecord();
        try (InputStream csv = file.getInputStream()) {
            CSVParser csvParser = CSVParser.parse(csv, Charsets.UTF_8, csvFormat);
            Iterator<CSVRecord> linesIterator = csvParser.iterator();
            Iterators.advance(linesIterator, formatDescription.getLineToSkip());
            CSVRecord headerRow = linesIterator.next();
            ImmutableList<String> columns = Streams.stream(headerRow).collect(ImmutableList.toImmutableList());
            Iterators.advance(linesIterator, formatDescription.getLineToSkipAfterHeader());
            Function<CSVRecord, List<Map.Entry<String, String>>> csvRecordToLineAsMapFn = line -> {
                Iterator<String> currentHeader = columns.iterator();
                List<Map.Entry<String, String>> record = new LinkedList<>();
                line.forEach(value -> {
                    String header = currentHeader.next();
                    record.add(Map.entry(header, value));
                });
                return record;
            };
            Streams.stream(csvParser)
                    .map(csvRecordToLineAsMapFn)
                    .flatMap(lineAsMap -> lineAsMapToRecordsFn.apply(lineAsMap).stream())
                    .forEach(lineConsumer);
        }

        if (!error.isEmpty()) {
            throw new CheckerException("Parsing error:\n" + String.join("\n\t", error));
        }

        return fileId;
    }

    public String getDataCsv(DownloadDatasetQuery downloadDatasetQuery) {
        String applicationNameOrId = downloadDatasetQuery.getApplicationNameOrId();
        String dataType = downloadDatasetQuery.getDataType();
        List<Map<String, Map<String, String>>> list = findData(downloadDatasetQuery);
        Configuration.FormatDescription format = getApplication(applicationNameOrId)
                .getConfiguration()
                .getDataset()
                .get(dataType)
                .getFormat();
        ImmutableMap<String, VariableComponentKey> allColumns = getExportColumns(format);
        ImmutableMap<String, VariableComponentKey> columns;
        if (downloadDatasetQuery.getVariableComponentIds() == null) {
            columns = allColumns;
        } else {
            Map<String, VariableComponentKey> filter = Maps.filterValues(allColumns, variableComponent -> downloadDatasetQuery.getVariableComponentIds().contains(variableComponent.getId()));
            columns = ImmutableMap.copyOf(filter);
        }
        String result = "";
        if (list.size() > 0) {
            CSVFormat csvFormat = CSVFormat.DEFAULT
                    .withDelimiter(format.getSeparator())
                    .withSkipHeaderRecord();
            StringWriter out = new StringWriter();
            try {
                CSVPrinter csvPrinter = new CSVPrinter(out, csvFormat);
                csvPrinter.printRecord(columns.keySet());
                for (Map<String, Map<String, String>> record : list) {
                    ImmutableList<String> rowAsRecord = columns.values().stream()
                            .map(variableComponentKey -> {
                                Map<String, String> components = record.computeIfAbsent(variableComponentKey.getVariable(), k -> Collections.emptyMap());
                                return components.getOrDefault(variableComponentKey.getComponent(), "");
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

    private ImmutableMap<String, VariableComponentKey> getExportColumns(Configuration.FormatDescription format) {
        ImmutableMap<String, VariableComponentKey> valuesFromStaticColumns = format.getColumns().stream()
                .collect(ImmutableMap.toImmutableMap(Configuration.ColumnBindingDescription::getHeader, Configuration.ColumnBindingDescription::getBoundTo));
        ImmutableMap.Builder<String, VariableComponentKey> allColumnsBuilder = ImmutableMap.<String, VariableComponentKey>builder()
                .putAll(valuesFromStaticColumns);
        if (format.getRepeatedColumns() != null) {
            ImmutableMap<String, VariableComponentKey> valuesFromHeaderPatterns = format.getRepeatedColumns().stream()
                    .filter(repeatedColumnBindingDescription -> repeatedColumnBindingDescription.getTokens() != null)
                    .flatMap(repeatedColumnBindingDescription -> repeatedColumnBindingDescription.getTokens().stream())
                    .collect(ImmutableMap.toImmutableMap(Configuration.HeaderPatternToken::getExportHeader, Configuration.HeaderPatternToken::getBoundTo));
            ImmutableMap<String, VariableComponentKey> valuesFromRepeatedColumns = format.getRepeatedColumns().stream()
                    .collect(ImmutableMap.toImmutableMap(Configuration.RepeatedColumnBindingDescription::getExportHeader, Configuration.RepeatedColumnBindingDescription::getBoundTo));
            allColumnsBuilder.putAll(valuesFromHeaderPatterns)
                             .putAll(valuesFromRepeatedColumns)
                             ;
        }
        return allColumnsBuilder.build();
    }

    public List<Map<String, Map<String, String>>> findData(DownloadDatasetQuery downloadDatasetQuery) {
        authRepository.setRoleForClient();
        String applicationNameOrId = downloadDatasetQuery.getApplicationNameOrId();
        Application app = getApplication(applicationNameOrId);
        ApplicationRepository applicationRepository = repo.getRepository(app);
        List<Map<String, Map<String, String>>> data = applicationRepository.findData(downloadDatasetQuery.getDataType());
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

    private ImmutableMap<VariableComponentKey, String> valuesToIndexedPerReferenceMap(Map<String, Map<String, String>> line) {
        Map<VariableComponentKey, String> valuesPerReference = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, String>> variableEntry : line.entrySet()) {
            String variable = variableEntry.getKey();
            for (Map.Entry<String, String> componentEntry : variableEntry.getValue().entrySet()) {
                String component = componentEntry.getKey();
                VariableComponentKey reference = new VariableComponentKey(variable, component);
                valuesPerReference.put(reference, componentEntry.getValue());
            }
        }
        return ImmutableMap.copyOf(valuesPerReference);
    }
}