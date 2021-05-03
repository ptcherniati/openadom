package fr.inra.oresing.rest;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.Ordering;
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
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.ReferenceValueRepository;
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
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Streams;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.Location;
import org.flywaydb.core.api.configuration.ClassicConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
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
import java.util.stream.Stream;

@Slf4j
@Component
@Transactional
public class OreSiService {

    /**
     * Déliminateur entre les différents niveaux d'un ltree postgresql.
     *
     * https://www.postgresql.org/docs/current/ltree.html
     */
    private static final String LTREE_SEPARATOR = ".";

    @Autowired
    private OreSiRepository repo;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private CheckerFactory checkerFactory;

    @Autowired
    private ApplicationConfigurationService applicationConfigurationService;

    @Autowired
    private OreSiApiRequestContext request;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private SqlService db;

    @Autowired
    private RelationalService relationalService;

    protected UUID storeFile(Application app, MultipartFile file) throws IOException {
        authenticationService.setRoleForClient();
        // creation du fichier
        BinaryFile binaryFile = new BinaryFile();
        binaryFile.setApplication(app.getId());
        binaryFile.setName(file.getOriginalFilename());
        binaryFile.setSize(file.getSize());
        binaryFile.setData(file.getBytes());
        UUID result = repo.getRepository(app).binaryFile().store(binaryFile);
        return result;
    }

    public UUID createApplication(String name, MultipartFile configurationFile) throws IOException, BadApplicationConfigurationException {

        Application app = new Application();
        app.setName(name);

        authenticationService.resetRole();

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

        OreSiUserRole creator = authenticationService.getUserRole(request.getRequestClient().getId());
        db.addUserInRole(creator, adminOnApplicationRole);

        authenticationService.setRoleForClient();
        UUID result = repo.application().store(app);
        changeApplicationConfiguration(app, configurationFile);

        relationalService.createViews(app.getName());

        return result;
    }

    public UUID changeApplicationConfiguration(String nameOrId, MultipartFile configurationFile) throws IOException, BadApplicationConfigurationException {
        relationalService.dropViews(nameOrId);
        authenticationService.setRoleForClient();
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
        DataRepository dataRepository = repo.getRepository(app).data();
        for (Map.Entry<String, Configuration.DataTypeDescription> dataTypeEntry : newConfiguration.getDataTypes().entrySet()) {
            String dataType = dataTypeEntry.getKey();
            Configuration.DataTypeDescription dataTypeDescription = dataTypeEntry.getValue();
            ImmutableMap<VariableComponentKey, Checker> checkers = checkerFactory.getCheckers(app, dataType);
            if (log.isInfoEnabled()) {
                log.info("va migrer les données de " + app.getName() + ", type de données, " + dataType + " de la version actuelle " + oldVersion + " à la nouvelle version " + newVersion);
            }
            for (int migrationVersionToApply = firstMigrationToApply; migrationVersionToApply <= newVersion; migrationVersionToApply++) {
                List<Configuration.MigrationDescription> migrations = dataTypeDescription.getMigrations().get(migrationVersionToApply);
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
                        int migratedCount = dataRepository.migrate(dataType, dataGroup, variablesToAdd, refsLinkedToToAdd);
                        if (log.isInfoEnabled()) {
                            log.info(migratedCount + " lignes migrées");
                        }
                    }
                }
            }

            validateStoredData(app, dataType);
        }

        // on supprime l'ancien fichier vu que tout c'est bien passé
        boolean deleted = repo.getRepository(app).binaryFile().delete(oldConfigFileId);
        Preconditions.checkState(deleted);

        relationalService.createViews(nameOrId);

        return uuid;
    }

    private void validateStoredData(Application app, String dataType) {
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
        repo.getRepository(app).data().findAllByDataType(dataType).stream()
                .map(this::valuesToIndexedPerReferenceMap)
                .forEach(validateRow);
    }

    private UUID changeApplicationConfiguration(Application app, MultipartFile configurationFile) throws IOException, BadApplicationConfigurationException {
        ConfigurationParsingResult configurationParsingResult = applicationConfigurationService.parseConfigurationBytes(configurationFile.getBytes());
        BadApplicationConfigurationException.check(configurationParsingResult);
        Configuration configuration = configurationParsingResult.getResult();
        if (configuration.getReferences() == null) {
            app.setReferenceType(Collections.emptyList());
        } else {
            app.setReferenceType(new ArrayList<>(configuration.getReferences().keySet()));
        }
        app.setDataType(new ArrayList<>(configuration.getDataTypes().keySet()));
        app.setConfiguration(configuration);
        UUID confId = storeFile(app, configurationFile);
        app.setConfigFile(confId);
        repo.application().store(app);
        return confId;
    }

    public UUID addReference(Application app, String refType, MultipartFile file) throws IOException {
        authenticationService.setRoleForClient();
        UUID fileId = storeFile(app, file);

        Configuration conf = app.getConfiguration();
        Configuration.ReferenceDescription ref = conf.getReferences().get(refType);

        ReferenceValueRepository referenceValueRepository = repo.getRepository(app).referenceValue();

        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(ref.getSeparator())
                .withSkipHeaderRecord();
        try (InputStream csv = file.getInputStream()) {
            CSVParser csvParser = CSVParser.parse(csv, Charsets.UTF_8, csvFormat);
            Iterator<CSVRecord> linesIterator = csvParser.iterator();
            CSVRecord headerRow = linesIterator.next();
            ImmutableList<String> columns = Streams.stream(headerRow).collect(ImmutableList.toImmutableList());
            Function<CSVRecord, Map<String, String>> csvRecordToLineAsMapFn = line -> {
                Iterator<String> currentHeader = columns.iterator();
                Map<String, String> recordAsMap = new LinkedHashMap<>();
                line.forEach(value -> {
                    String header = currentHeader.next();
                    recordAsMap.put(header, value);
                });
                return recordAsMap;
            };
            Stream<ReferenceValue> referenceValuesStream = Streams.stream(csvParser)
                    .map(csvRecordToLineAsMapFn)
                    .map(refValues -> {
                        ReferenceValue e = new ReferenceValue();
                        String key;
                        if (ref.getKeyColumn() == null) {
                            key = escapeKeyComponent(e.getId().toString());
                        } else {
                            key = escapeKeyComponent(refValues.get(ref.getKeyColumn()));
                        }
                        checkCompositeKey(key);
                        e.setBinaryFile(fileId);
                        e.setReferenceType(refType);
                        e.setCompositeKey(key);
                        e.setApplication(app.getId());
                        e.setRefValues(refValues);
                        return e;
                    });
            referenceValueRepository.storeAll(referenceValuesStream);
        }

        ImmutableSet<String> toUpdateCompositeReferences = conf.getCompositeReferencesUsing(refType);
        for (String toUpdateCompositeReference : toUpdateCompositeReferences) {
            updateCompositeReference(app, toUpdateCompositeReference);
        }

        return fileId;
    }

    private void updateCompositeReference(Application application, String compositeReference) {
        ReferenceValueRepository referenceValueRepository = repo.getRepository(application).referenceValue();
        Configuration.CompositeReferenceDescription compositeReferenceDescription = application.getConfiguration().getCompositeReferences().get(compositeReference);

        ImmutableList<String> referenceTypes = compositeReferenceDescription.getComponents().stream()
                .map(Configuration.CompositeReferenceComponentDescription::getReference)
                .collect(ImmutableList.toImmutableList());
        ImmutableSortedSet<String> strings = ImmutableSortedSet.copyOf(Ordering.explicit(referenceTypes), referenceTypes);

        for (Configuration.CompositeReferenceComponentDescription compositeReferenceComponentDescription : compositeReferenceDescription.getComponents()) {
            String referenceType = compositeReferenceComponentDescription.getReference();
            String keyColumn = application.getConfiguration().getReferences().get(referenceType).getKeyColumn();
            String parentReferenceType = strings.lower(referenceType);
            boolean root = parentReferenceType == null;
            List<ReferenceValue> references = referenceValueRepository.findAllByReferenceType(referenceType);
            for (ReferenceValue reference : references) {
                String keyElement = reference.getRefValues().get(keyColumn);
                String escapedKeyElement = escapeKeyComponent(keyElement);
                String compositeKey;
                if (root) {
                    compositeKey = escapedKeyElement;
                } else {
                    String parentKeyColumn = compositeReferenceComponentDescription.getParentKeyColumn();
                    String parentCompositeKey = reference.getRefValues().get(parentKeyColumn);
                    compositeKey = parentCompositeKey + LTREE_SEPARATOR + escapedKeyElement;
                }
                checkCompositeKey(compositeKey);
                reference.setCompositeKey(compositeKey);
                referenceValueRepository.store(reference);
            }
        }
    }

    private String escapeKeyComponent(String key) {
        String toEscape = StringUtils.stripAccents(key.toLowerCase());
        String escaped = StringUtils.remove(StringUtils.replace(toEscape, " ", "_"), "-");
        checkCompositeKey(escaped);
        return escaped;
    }

    private void checkKeyComponent(String keyComponent) {
        Preconditions.checkState(keyComponent.matches("[a-z0-9_]+"), keyComponent + " n'est pas un élément valide pour une clé composite");
    }

    private void checkCompositeKey(String compositeKey) {
        Splitter.on(LTREE_SEPARATOR).split(compositeKey).forEach(this::checkKeyComponent);
    }

    public UUID addData(Application app, String dataType, MultipartFile file) throws IOException, CheckerException {
        authenticationService.setRoleForClient();
        UUID fileId = storeFile(app, file);

        // recuperation de la configuration pour ce type de donnees
        Configuration conf = app.getConfiguration();
        Configuration.DataTypeDescription dataTypeDescription = conf.getDataTypes().get(dataType);

        ImmutableMap<VariableComponentKey, Checker> checkers = checkerFactory.getCheckers(app, dataType);

        List<String> error = new LinkedList<>();

        DateChecker timeScopeColumnChecker = (DateChecker) checkers.get(dataTypeDescription.getAuthorization().getTimeScope());
        String timeScopeColumnPattern = timeScopeColumnChecker.getPattern();

        Function<Map<VariableComponentKey, String>, Stream<Data>> lineValuesToEntityStreamFn = line -> {
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

            String timeScopeValue = values.get(dataTypeDescription.getAuthorization().getTimeScope());
            LocalDateTimeRange timeScope = LocalDateTimeRange.parse(timeScopeValue, timeScopeColumnPattern);

            String localizationScope = values.get(dataTypeDescription.getAuthorization().getLocalizationScope());

            // String rowId = Hashing.sha256().hashString(line.toString(), Charsets.UTF_8).toString();
            String rowId = UUID.randomUUID().toString();

            Stream<Data> dataStream = dataTypeDescription.getAuthorization().getDataGroups().entrySet().stream().map(entry -> {
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
                e.setLocalizationScope(localizationScope);
                return e;
            });

            return dataStream;
        };

        Configuration.FormatDescription formatDescription = dataTypeDescription.getFormat();

        Function<List<Map.Entry<String, String>>, ImmutableSet<Map<VariableComponentKey, String>>> lineAsMapToRecordsFn;
        if (formatDescription.getRepeatedColumns().isEmpty()) {
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

                // d'abord, il s'agit de lire les colonnes fixes, non répétées. Les données
                // qui en sont tirées sont communes pour toute la ligne
                Map<VariableComponentKey, String> recordPrototype; {
                    recordPrototype = new LinkedHashMap<>();
                    for (Configuration.ColumnBindingDescription column : formatDescription.getColumns()) {
                        Map.Entry<String, String> poll = lineCopy.poll();
                        String header = poll.getKey();
                        Preconditions.checkState(header.equals(column.getHeader()), "Entête inattendu " + header + ". Entête attendu " + column.getHeader());
                        String value = poll.getValue();
                        recordPrototype.put(column.getBoundTo(), value);
                    }
                }

                // ensuite, on traite les colonnes répétées
                Iterator<Map.Entry<String, String>> actualColumnsIterator = lineCopy.iterator();
                Iterator<Configuration.RepeatedColumnBindingDescription> expectedColumns = formatDescription.getRepeatedColumns().iterator();
                Set<Map<VariableComponentKey, String>> records = new LinkedHashSet<>();

                // les données tirées de l'entête de la colonne répétée
                Map<VariableComponentKey, String> tokenValues = new LinkedHashMap<>();

                // les données tirées du contenu de la cellule d'une colonne répétée
                Map<VariableComponentKey, String> bodyValues = new LinkedHashMap<>();

                // pour lire toute la ligne, on doit lire X groupes qui sont Y groupes de N colonnes
                while (actualColumnsIterator.hasNext()) {
                    Map.Entry<String, String> actualColumn = actualColumnsIterator.next();
                    Configuration.RepeatedColumnBindingDescription expectedColumn = expectedColumns.next();

                    // on lit les informations dans l'entête
                    {
                        String actualHeader = actualColumn.getKey();

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
                    }

                    // on lit l'information dans le contenu de la cellule
                    String value = actualColumn.getValue();
                    bodyValues.put(expectedColumn.getBoundTo(), value);

                    if (!expectedColumns.hasNext()) {
                        // on a lu un groupe de colonne entier

                        // pour les données de ce groupe de colonne répétées, on ajoute une donnée
                        Map<VariableComponentKey, String> record = ImmutableMap.<VariableComponentKey, String>builder()
                                .putAll(recordPrototype)
                                .putAll(tokenValues)
                                .putAll(bodyValues)
                                .build();
                        records.add(record);

                        // et on passe au groupe de colonnes répétées suivant
                        tokenValues.clear();
                        bodyValues.clear();
                        expectedColumns = formatDescription.getRepeatedColumns().iterator();
                    }
                }
                return ImmutableSet.copyOf(records);
            };
        }

        ImmutableSetMultimap<Integer, Configuration.HeaderConstantDescription> perRowNumberConstants =
                formatDescription.getConstants().stream()
                .collect(ImmutableSetMultimap.toImmutableSetMultimap(Configuration.HeaderConstantDescription::getRowNumber, Function.identity()));
        Map<VariableComponentKey, String> constantValues = new LinkedHashMap<>();
        Function<Map<VariableComponentKey, String>, Map<VariableComponentKey, String>> mergeLineValuesAndConstantValuesFn =
                lineAsMap -> ImmutableMap.<VariableComponentKey, String>builder()
                        .putAll(constantValues)
                        .putAll(lineAsMap)
                        .build();

        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(formatDescription.getSeparator())
                .withSkipHeaderRecord();
        try (InputStream csv = file.getInputStream()) {
            CSVParser csvParser = CSVParser.parse(csv, Charsets.UTF_8, csvFormat);
            Iterator<CSVRecord> linesIterator = csvParser.iterator();

            for (int lineNumber = 1; lineNumber < formatDescription.getHeaderLine(); lineNumber++) {
                CSVRecord row = linesIterator.next();
                ImmutableSet<Configuration.HeaderConstantDescription> constantDescriptions = perRowNumberConstants.get(lineNumber);
                constantDescriptions.forEach(constant -> {
                    int columnNumber = constant.getColumnNumber();
                    String value = row.get(columnNumber - 1);
                    VariableComponentKey boundTo = constant.getBoundTo();
                    constantValues.put(boundTo, value);
                });
            }

            CSVRecord headerRow = linesIterator.next();
            ImmutableList<String> columns = Streams.stream(headerRow).collect(ImmutableList.toImmutableList());
            int lineToSkipAfterHeader = formatDescription.getFirstRowLine() - formatDescription.getHeaderLine() - 1;
            Iterators.advance(linesIterator, lineToSkipAfterHeader);
            Function<CSVRecord, List<Map.Entry<String, String>>> csvRecordToLineAsMapFn = line -> {
                Iterator<String> currentHeader = columns.iterator();
                List<Map.Entry<String, String>> record = new LinkedList<>();
                line.forEach(value -> {
                    String header = currentHeader.next();
                    record.add(Map.entry(header, value));
                });
                return record;
            };

            Stream<Data> dataStream = Streams.stream(csvParser)
                    .map(csvRecordToLineAsMapFn)
                    .flatMap(lineAsMap -> lineAsMapToRecordsFn.apply(lineAsMap).stream())
                    .map(mergeLineValuesAndConstantValuesFn)
                    .flatMap(lineValuesToEntityStreamFn);

            repo.getRepository(app).data().storeAll(dataStream);
        }

        if (!error.isEmpty()) {
            throw new CheckerException("Parsing error:\n" + String.join("\n\t", error));
        }

        relationalService.onDataUpdate(app.getName());

        return fileId;
    }

    public String getDataCsv(DownloadDatasetQuery downloadDatasetQuery) {
        String applicationNameOrId = downloadDatasetQuery.getApplicationNameOrId();
        String dataType = downloadDatasetQuery.getDataType();
        List<Map<String, Map<String, String>>> list = findData(downloadDatasetQuery);
        Configuration.FormatDescription format = getApplication(applicationNameOrId)
                .getConfiguration()
                .getDataTypes()
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
        ImmutableMap<String, VariableComponentKey> valuesFromConstants = format.getConstants().stream()
                .collect(ImmutableMap.toImmutableMap(Configuration.HeaderConstantDescription::getExportHeader, Configuration.HeaderConstantDescription::getBoundTo));
        ImmutableMap<String, VariableComponentKey> valuesFromHeaderPatterns = format.getRepeatedColumns().stream()
                .flatMap(repeatedColumnBindingDescription -> repeatedColumnBindingDescription.getTokens().stream())
                .collect(ImmutableMap.toImmutableMap(Configuration.HeaderPatternToken::getExportHeader, Configuration.HeaderPatternToken::getBoundTo));
        ImmutableMap<String, VariableComponentKey> valuesFromRepeatedColumns = format.getRepeatedColumns().stream()
                .collect(ImmutableMap.toImmutableMap(Configuration.RepeatedColumnBindingDescription::getExportHeader, Configuration.RepeatedColumnBindingDescription::getBoundTo));
        return ImmutableMap.<String, VariableComponentKey>builder()
                .putAll(valuesFromStaticColumns)
                .putAll(valuesFromConstants)
                .putAll(valuesFromHeaderPatterns)
                .putAll(valuesFromRepeatedColumns)
                .build();
    }

    public List<Map<String, Map<String, String>>> findData(DownloadDatasetQuery downloadDatasetQuery) {
        authenticationService.setRoleForClient();
        String applicationNameOrId = downloadDatasetQuery.getApplicationNameOrId();
        Application app = getApplication(applicationNameOrId);
        List<Map<String, Map<String, String>>> data = repo.getRepository(app).data().findAllByDataType(downloadDatasetQuery.getDataType());
        return data;
    }

    public List<Application> getApplications() {
        authenticationService.setRoleForClient();
        List<Application> result = repo.application().findAll();
        return result;
    }

    public Application getApplication(String nameOrId) {
        authenticationService.setRoleForClient();
        return repo.application().findApplication(nameOrId);
    }

    public Optional<Application> tryFindApplication(String nameOrId) {
        authenticationService.setRoleForClient();
        return repo.application().tryFindApplication(nameOrId);
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

    /**
     *
     * @param nameOrId l'id de l'application
     * @param refType le type du referenciel
     * @param params les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
     * @return la liste qui satisfont aux criteres
     */
    public List<ReferenceValue> findReference(String nameOrId, String refType, MultiValueMap<String, String> params) {
        authenticationService.setRoleForClient();
        List<ReferenceValue> list = repo.getRepository(nameOrId).referenceValue().findAllByReferenceType(refType, params);
        return list;
    }

    public Optional<BinaryFile> getFile(String name, UUID id) {
        authenticationService.setRoleForClient();
        Optional<BinaryFile> optionalBinaryFile = repo.getRepository(name).binaryFile().tryFindById(id);
        return optionalBinaryFile;
    }

    public ConfigurationParsingResult validateConfiguration(MultipartFile file) throws IOException {
        authenticationService.setRoleForClient();
        return applicationConfigurationService.parseConfigurationBytes(file.getBytes());
    }
}