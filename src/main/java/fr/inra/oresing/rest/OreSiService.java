package fr.inra.oresing.rest;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.primitives.Ints;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.checker.InvalidDatasetContentException;
import fr.inra.oresing.checker.LineChecker;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.checker.ReferenceValidationCheckResult;
import fr.inra.oresing.groovy.CommonExpression;
import fr.inra.oresing.groovy.Expression;
import fr.inra.oresing.groovy.StringGroovyExpression;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.model.LocalDateTimeRange;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.ReferenceValueRepository;
import fr.inra.oresing.persistence.SqlPolicy;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.SqlSchemaForApplication;
import fr.inra.oresing.persistence.SqlService;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import lombok.Value;
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
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
    private static final String KEYCOLUMN_SEPARATOR = "__";

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
                String.join("_", adminOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.ALL.name()),
                SqlSchema.main().application(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                SqlPolicy.Statement.ALL,
                adminOnApplicationRole,
                "name = '" + name + "'"
        ));

        db.createPolicy(new SqlPolicy(
                String.join("_", readerOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.SELECT.name()),
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
            ImmutableMap<VariableComponentKey, ReferenceLineChecker> referenceLineCheckers = checkerFactory.getReferenceLineCheckers(app, dataType);
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
                        Map<String, UUID> refsLinkedToAddForVariable = new LinkedHashMap<>();
                        for (Map.Entry<String, Configuration.AddVariableMigrationDescription> componentEntry : migration.getComponents().entrySet()) {
                            String component = componentEntry.getKey();
                            String componentValue = Optional.ofNullable(componentEntry.getValue())
                                    .map(Configuration.AddVariableMigrationDescription::getDefaultValue)
                                    .orElse("");
                            VariableComponentKey variableComponentKey = new VariableComponentKey(variable, component);
                            if (referenceLineCheckers.containsKey(variableComponentKey)) {
                                ReferenceLineChecker referenceLineChecker = referenceLineCheckers.get(variableComponentKey);
                                ReferenceValidationCheckResult referenceCheckResult = referenceLineChecker.check(componentValue);
                                Preconditions.checkState(referenceCheckResult.isSuccess(), componentValue + " n'est pas une valeur par défaut acceptable pour " + variableComponentKey);
                                UUID referenceId = referenceCheckResult.getReferenceId();
                                refsLinkedToAddForVariable.put(component, referenceId);
                            }
                            variableValue.put(component, componentValue);
                        }
                        Map<String, Map<String, String>> variablesToAdd = Map.of(variable, variableValue);
                        Map<String, Map<String, UUID>> refsLinkedToAdd = Map.of(variable, refsLinkedToAddForVariable);
                        int migratedCount = dataRepository.migrate(dataType, dataGroup, variablesToAdd, refsLinkedToAdd);
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
        ImmutableSet<LineChecker> lineCheckers = checkerFactory.getLineCheckers(app, dataType);
        Consumer<ImmutableMap<VariableComponentKey, String>> validateRow = line -> {
            lineCheckers.forEach(lineChecker -> {
                ValidationCheckResult validationCheckResult = lineChecker.check(line);
                Preconditions.checkState(validationCheckResult.isSuccess(), "erreur de validation d'une donnée stockée " + validationCheckResult);
            });
        };
        repo.getRepository(app).data().findAllByDataType(dataType).stream()
                .map(this::valuesToIndexedPerReferenceMap)
                .forEach(validateRow);
    }

    private UUID changeApplicationConfiguration(Application app, MultipartFile configurationFile) throws IOException, BadApplicationConfigurationException {
        ConfigurationParsingResult configurationParsingResult = applicationConfigurationService.parseConfigurationBytes(configurationFile.getBytes());
        BadApplicationConfigurationException.check(configurationParsingResult);
        Configuration configuration = configurationParsingResult.getResult();
        app.setReferenceType(new ArrayList<>(configuration.getReferences().keySet()));
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

        Optional<Configuration.CompositeReferenceDescription> toUpdateCompositeReference = conf.getCompositeReferencesUsing(refType);
        String parentHierarchicalKeyColumn;
        BiFunction<String, Map<String, String>, String> getHierarchicalKeyFn;
        if (toUpdateCompositeReference.isPresent()) {
            Configuration.CompositeReferenceDescription compositeReferenceDescription = toUpdateCompositeReference.get();
            boolean root = Iterables.get(compositeReferenceDescription.getComponents(), 0).getReference().equals(refType);
            if (root) {
                getHierarchicalKeyFn = (naturalKey, referenceValues) -> naturalKey;
            } else {
                parentHierarchicalKeyColumn = compositeReferenceDescription.getComponents().stream()
                        .filter(compositeReferenceComponentDescription -> compositeReferenceComponentDescription.getReference().equals(refType))
                        .collect(MoreCollectors.onlyElement())
                        .getParentKeyColumn();
                getHierarchicalKeyFn = (naturalKey, referenceValues) -> {
                    String parentHierarchicalKey = referenceValues.get(parentHierarchicalKeyColumn);
                    return parentHierarchicalKey + LTREE_SEPARATOR + naturalKey;
                };
            }
        } else {
            getHierarchicalKeyFn = (naturalKey, referenceValues) -> naturalKey;
        }

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
                        String naturalKey;
                        String technicalId = e.getId().toString();
                        if (ref.getKeyColumns().isEmpty()) {
                            naturalKey = escapeKeyComponent(technicalId);
                        } else {
                            naturalKey = ref.getKeyColumns().stream()
                                    .map(kc -> escapeKeyComponent(refValues.get(kc)))
                                    .collect(Collectors.joining(KEYCOLUMN_SEPARATOR));
                        }
                        checkNaturalKeySyntax(naturalKey);
                        String hierarchicalKey = getHierarchicalKeyFn.apply(naturalKey, refValues);
                        checkHierarchicalKeySyntax(hierarchicalKey);
                        e.setBinaryFile(fileId);
                        e.setReferenceType(refType);
                        e.setHierarchicalKey(hierarchicalKey);
                        e.setNaturalKey(naturalKey);
                        e.setApplication(app.getId());
                        e.setRefValues(refValues);
                        return e;
                    });
            referenceValueRepository.storeAll(referenceValuesStream);
        }

        return fileId;
    }

    private String escapeKeyComponent(String key) {
        String toEscape = StringUtils.stripAccents(key.toLowerCase());
        String escaped = StringUtils.remove(StringUtils.replace(toEscape, " ", "_"), "-");
        checkNaturalKeySyntax(escaped);
        return escaped;
    }

    private void checkNaturalKeySyntax(String keyComponent) {
        Preconditions.checkState(keyComponent.matches("[a-z0-9_]+"), keyComponent + " n'est pas un élément valide pour une clé naturelle");
    }

    private void checkHierarchicalKeySyntax(String compositeKey) {
        Splitter.on(LTREE_SEPARATOR).split(compositeKey).forEach(this::checkNaturalKeySyntax);
    }

    @Value
    private static class RowWithData {
        int lineNumber;
        Map<VariableComponentKey, String> datum;
    }

    @Value
    private static class ParsedCsvRow {
        int lineNumber;
        List<Map.Entry<String, String>> columns;
    }


    /**
     * Insérer un jeu de données.
     */
    public UUID addData(String nameOrId, String dataType, MultipartFile file) throws IOException, InvalidDatasetContentException {
        List<CsvRowValidationCheckResult> errors= new LinkedList<>();
        authenticationService.setRoleForClient();

        Application app = getApplication(nameOrId);
        UUID fileId = storeFile(app, file);

        Configuration conf = app.getConfiguration();
        Configuration.DataTypeDescription dataTypeDescription = conf.getDataTypes().get(dataType);
        Configuration.FormatDescription formatDescription = dataTypeDescription.getFormat();

        try (InputStream csv = file.getInputStream()) {
            CSVFormat csvFormat = CSVFormat.DEFAULT
                    .withDelimiter(formatDescription.getSeparator())
                    .withSkipHeaderRecord();
            CSVParser csvParser = CSVParser.parse(csv, Charsets.UTF_8, csvFormat);
            Iterator<CSVRecord> linesIterator = csvParser.iterator();

            Map<VariableComponentKey, String> constantValues = new LinkedHashMap<>();
            ImmutableMap<VariableComponentKey, Expression<String>> defaultValueExpressions = getDefaultValueExpressions(dataTypeDescription);

            readPreHeader(formatDescription, constantValues, linesIterator);

            ImmutableList<String> columns = readHeaderRow(linesIterator);
            readPostHeader(formatDescription, linesIterator);

            Stream<Data> dataStream = Streams.stream(csvParser)
                    .map(buildCsvRecordToLineAsMapFn(columns))
                    .flatMap(lineAsMap -> buildLineAsMapToRecordsFn(formatDescription).apply(lineAsMap).stream())
                    .map(buildMergeLineValuesAndConstantValuesFn(constantValues))
                    .map(buildReplaceMissingValuesByDefaultValuesFn(defaultValueExpressions))
                    .flatMap(buildLineValuesToEntityStreamFn(app, dataType, fileId, errors));

            repo.getRepository(app).data().storeAll(dataStream);
        }
        InvalidDatasetContentException.checkErrorsIsEmpty(errors);
        relationalService.onDataUpdate(app.getName());
        return fileId;
    }

    /**
     * return a function that transform each RowWithData to a stream of data entities
     * @param app
     * @param dataType
     * @param fileId
     * @return
     */
    private Function<RowWithData, Stream<Data>> buildLineValuesToEntityStreamFn(Application app, String dataType, UUID fileId, List<CsvRowValidationCheckResult> errors){
        ImmutableSet<LineChecker> lineCheckers = checkerFactory.getLineCheckers(app, dataType);
        Configuration conf = app.getConfiguration();
        Configuration.DataTypeDescription dataTypeDescription = conf.getDataTypes().get(dataType);

        String timeScopeColumnPattern = lineCheckers.stream()
                .filter(lineChecker -> lineChecker instanceof DateLineChecker)
                .map(lineChecker -> (DateLineChecker) lineChecker)
                .filter(dateLineChecker -> dateLineChecker.getVariableComponentKey().equals(dataTypeDescription.getAuthorization().getTimeScope()))
                .collect(MoreCollectors.onlyElement())
                .getPattern();

        return buildRowWithDataStreamFunction(app, dataType, fileId, errors, lineCheckers, dataTypeDescription, timeScopeColumnPattern);
    }

    /**
     * build the function that transform each RowWithData to a stream of data entities
     * @param app
     * @param dataType
     * @param fileId
     * @param errors
     * @param lineCheckers
     * @param dataTypeDescription
     * @param timeScopeColumnPattern
     * @return
     */
    private Function<RowWithData, Stream<Data>> buildRowWithDataStreamFunction(Application app, String dataType, UUID fileId, List<CsvRowValidationCheckResult> errors, ImmutableSet<LineChecker> lineCheckers, Configuration.DataTypeDescription dataTypeDescription, String timeScopeColumnPattern) {
        return rowWithData -> {
            Map<VariableComponentKey, String> values = rowWithData.getDatum();
            Map<VariableComponentKey, UUID> refsLinkedTo = new LinkedHashMap<>();
            List<CsvRowValidationCheckResult> rowErrors = new LinkedList<>();

            lineCheckers.forEach(lineChecker -> {
                ValidationCheckResult validationCheckResult = lineChecker.check(values);
                if (validationCheckResult.isSuccess()) {
                    if (validationCheckResult instanceof ReferenceValidationCheckResult) {
                        ReferenceValidationCheckResult referenceValidationCheckResult = (ReferenceValidationCheckResult) validationCheckResult;
                        VariableComponentKey variableComponentKey = referenceValidationCheckResult.getVariableComponentKey();
                        UUID referenceId = referenceValidationCheckResult.getReferenceId();
                        refsLinkedTo.put(variableComponentKey, referenceId);
                    }
                } else {
                    rowErrors.add(new CsvRowValidationCheckResult(validationCheckResult, rowWithData.getLineNumber()));
                }
            });

            if (!rowErrors.isEmpty()) {
                errors.addAll(rowErrors);
                return Stream.empty();
            }

            String timeScopeValue = values.get(dataTypeDescription.getAuthorization().getTimeScope());
            LocalDateTimeRange timeScope = LocalDateTimeRange.parse(timeScopeValue, timeScopeColumnPattern);

            Map<String, String> requiredAuthorizations = new LinkedHashMap<>();
            dataTypeDescription.getAuthorization().getAuthorizationScopes().forEach((authorizationScope, variableComponentKey) -> {
                String requiredAuthorization = values.get(variableComponentKey);
                checkHierarchicalKeySyntax(requiredAuthorization);
                requiredAuthorizations.put(authorizationScope, requiredAuthorization);
            });

            // String rowId = Hashing.sha256().hashString(line.toString(), Charsets.UTF_8).toString();
            String rowId = UUID.randomUUID().toString();

            Stream<Data> dataStream = dataTypeDescription.getAuthorization().getDataGroups().entrySet().stream().map(entry -> {
                String dataGroup = entry.getKey();
                Configuration.DataGroupDescription dataGroupDescription = entry.getValue();

                Predicate<VariableComponentKey> includeInDataGroupPredicate = variableComponentKey -> dataGroupDescription.getData().contains(variableComponentKey.getVariable());
                Map<VariableComponentKey, String> dataGroupValues = Maps.filterKeys(values, includeInDataGroupPredicate);

                Map<String, Map<String, String>> toStore = new LinkedHashMap<>();
                Map<String, Map<String, UUID>> refsLinkedToToStore = new LinkedHashMap<>();
                for (Map.Entry<VariableComponentKey, String> entry2 : dataGroupValues.entrySet()) {
                    VariableComponentKey variableComponentKey = entry2.getKey();
                    String variable = variableComponentKey.getVariable();
                    String component = variableComponentKey.getComponent();
                    String value = entry2.getValue();
                    toStore.computeIfAbsent(variable, k -> new LinkedHashMap<>()).put(component, value);
                    refsLinkedToToStore.computeIfAbsent(variable, k -> new LinkedHashMap<>()).put(component, refsLinkedTo.get(variableComponentKey));
                }

                Data e = new Data();
                e.setBinaryFile(fileId);
                e.setDataType(dataType);
                e.setRowId(rowId);
                e.setDataGroup(dataGroup);
                e.setApplication(app.getId());
                e.setRefsLinkedTo(refsLinkedToToStore);
                e.setDataValues(toStore);
                e.setTimeScope(timeScope);
                e.setRequiredAuthorizations(requiredAuthorizations);
                return e;
            });

            return dataStream;
        };
    }

    /**
     * Une fonction qui ajoute à une donnée les valeurs par défaut.
     *
     * Si des valeurs par défaut ont été définies dans le YAML, la donnée doit les avoir.
     */
    private Function<RowWithData, RowWithData> buildReplaceMissingValuesByDefaultValuesFn(ImmutableMap<VariableComponentKey, Expression<String>> defaultValueExpressions) {
        return rowWithData -> {
            ImmutableMap<String, Object> evaluationContext = ImmutableMap.of("datum", rowWithData.getDatum());
            Map<VariableComponentKey, String> defaultValues = Maps.transformValues(defaultValueExpressions, expression -> expression.evaluate(evaluationContext));
            Map<VariableComponentKey, String> rowWithDefaults = new LinkedHashMap<>(defaultValues);
            rowWithDefaults.putAll(Maps.filterValues(rowWithData.getDatum(), StringUtils::isNotBlank));
            return new RowWithData(rowWithData.getLineNumber(), ImmutableMap.copyOf(rowWithDefaults));
        };
    }

    /**
     * Une fonction qui ajoute à une donnée les données constantes.
     *
     * Les constantes sont des variables/composants qui ont la même valeur pour toutes les lignes
     * d'un fichier de données qu'on importe. Ce sont les données qu'on trouve dans l'entête
     * du fichier.
     */
    private Function<RowWithData, RowWithData> buildMergeLineValuesAndConstantValuesFn(Map<VariableComponentKey, String> constantValues) {
        return rowWithData -> {
            ImmutableMap<VariableComponentKey, String> datum = ImmutableMap.<VariableComponentKey, String>builder()
                    .putAll(constantValues)
                    .putAll(rowWithData.getDatum())
                    .build();
            return new RowWithData(rowWithData.getLineNumber(), datum);
        };
    }

    /**
     * Build the function that Dispatch ParsedCsvRow into RowWithData when there are not repeatedColumns
     */
    private Function<ParsedCsvRow, ImmutableSet<RowWithData>> buildLineAsMapWhenNoRepeatedColumnsToRecordsFn(Configuration.FormatDescription formatDescription) {
        ImmutableSet<String> expectedHeaderColumns = formatDescription.getColumns().stream()
                .map(Configuration.ColumnBindingDescription::getHeader)
                .collect(ImmutableSet.toImmutableSet());
        int headerLine = formatDescription.getHeaderLine();
        ImmutableMap<String, Configuration.ColumnBindingDescription> bindingPerHeader = Maps.uniqueIndex(formatDescription.getColumns(), Configuration.ColumnBindingDescription::getHeader);
        Function<ParsedCsvRow, ImmutableSet<RowWithData>> lineAsMapToRecordsFn = parsedCsvRow -> {
            List<Map.Entry<String, String>> line = parsedCsvRow.getColumns();
            ImmutableMultiset<String> actualHeaderColumns = line.stream()
                    .map(Map.Entry::getKey)
                    .collect(ImmutableMultiset.toImmutableMultiset());
            InvalidDatasetContentException.checkHeader(expectedHeaderColumns, actualHeaderColumns, headerLine);
            Map<VariableComponentKey, String> record = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : line) {
                String header = entry.getKey();
                String value = entry.getValue();
                Configuration.ColumnBindingDescription bindingDescription = bindingPerHeader.get(header);
                record.put(bindingDescription.getBoundTo(), value);
            }
            return ImmutableSet.of(new RowWithData(parsedCsvRow.getLineNumber(), record));
        };
        return lineAsMapToRecordsFn;
    }

    /**
     * build the function that Dispatch ParsedCsvRow into RowWithData when there are repeatedColumns
     */
    private Function<ParsedCsvRow, ImmutableSet<RowWithData>> buildLineAsMapWhenRepeatedColumnsToRecordsFn(Configuration.FormatDescription formatDescription) {
        return  parsedCsvRow -> {
            List<Map.Entry<String, String>> line = parsedCsvRow.getColumns();
            LinkedList<Map.Entry<String, String>> lineCopy = new LinkedList<>(line);

            // d'abord, il s'agit de lire les colonnes fixes, non répétées. Les données
            // qui en sont tirées sont communes pour toute la ligne
            Map<VariableComponentKey, String> recordPrototype;
            {
                recordPrototype = new LinkedHashMap<>();
                for (Configuration.ColumnBindingDescription column : formatDescription.getColumns()) {
                    Map.Entry<String, String> poll = lineCopy.poll();
                    String header = poll.getKey();
                    String expected = column.getHeader();
                    if (!header.equals(expected)) {
                        throw InvalidDatasetContentException.forUnexpectedHeaderColumn(expected, header, formatDescription.getHeaderLine());
                    }
                    String value = poll.getValue();
                    recordPrototype.put(column.getBoundTo(), value);
                }
            }

            // ensuite, on traite les colonnes répétées
            Iterator<Map.Entry<String, String>> actualColumnsIterator = lineCopy.iterator();
            Iterator<Configuration.RepeatedColumnBindingDescription> expectedColumns = formatDescription.getRepeatedColumns().iterator();
            Set<RowWithData> records = new LinkedHashSet<>();

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
                    if (!matches) {
                        throw InvalidDatasetContentException.forHeaderColumnPatternNotMatching(headerPattern, actualHeader, formatDescription.getHeaderLine());
                    }
                    List<Configuration.HeaderPatternToken> tokens = expectedColumn.getTokens();
                    if (tokens != null) {
                        if (matcher.groupCount() != tokens.size()) {
                            throw InvalidDatasetContentException.forUnexpectedTokenCount(tokens.size(), actualHeader, matcher.groupCount(), formatDescription.getHeaderLine());
                        }
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
                    records.add(new RowWithData(parsedCsvRow.getLineNumber(), record));

                    // et on passe au groupe de colonnes répétées suivant
                    tokenValues.clear();
                    bodyValues.clear();
                    expectedColumns = formatDescription.getRepeatedColumns().iterator();
                }
            }
            return ImmutableSet.copyOf(records);
        };
    }


    /**
     * build the function Dispatch ParsedCsvRow into RowWithData
     *
     * @param formatDescription
     * @return
     */
    private Function<ParsedCsvRow, ImmutableSet<RowWithData>> buildLineAsMapToRecordsFn(Configuration.FormatDescription formatDescription) {
        if (formatDescription.getRepeatedColumns().isEmpty()) {
            return buildLineAsMapWhenNoRepeatedColumnsToRecordsFn(formatDescription);
        } else {
            return buildLineAsMapWhenRepeatedColumnsToRecordsFn(formatDescription);
        }
    }


    /**
     * build the function that diplay the line in a {@link ParsedCsvRow}
     *
     * @param columns
     * @return
     */
    private Function<CSVRecord, ParsedCsvRow> buildCsvRecordToLineAsMapFn(ImmutableList<String> columns) {
        return line -> {
            int lineNumber = Ints.checkedCast(line.getRecordNumber());
            Iterator<String> currentHeader = columns.iterator();
            List<Map.Entry<String, String>> record = new LinkedList<>();
            line.forEach(value -> {
                String header = currentHeader.next();
                record.add(Map.entry(header, value));
            });
            return new ParsedCsvRow(lineNumber, record);
        };
    }

    /**
     * read the header cartridge of the file to extract some constants values.
     *
     * @param formatDescription
     * @param constantValues
     * @param linesIterator
     */
    private void readPreHeader(Configuration.FormatDescription formatDescription, Map<VariableComponentKey, String> constantValues, Iterator<CSVRecord> linesIterator) {
        ImmutableSetMultimap<Integer, Configuration.HeaderConstantDescription> perRowNumberConstants =
                formatDescription.getConstants().stream()
                        .collect(ImmutableSetMultimap.toImmutableSetMultimap(Configuration.HeaderConstantDescription::getRowNumber, Function.identity()));

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
    }

    /**
     * read the header row and return the columns
     *
     * @param linesIterator
     * @return
     */
    private ImmutableList<String> readHeaderRow(Iterator<CSVRecord> linesIterator) {
        CSVRecord headerRow = linesIterator.next();
        return Streams.stream(headerRow).collect(ImmutableList.toImmutableList());
    }

    /**
     * read some post header as example line, units, min and max values for each columns
     *
     * @param formatDescription
     * @param linesIterator
     */
    private void readPostHeader(Configuration.FormatDescription formatDescription, Iterator<CSVRecord> linesIterator) {
        int lineToSkipAfterHeader = formatDescription.getFirstRowLine() - formatDescription.getHeaderLine() - 1;
        Iterators.advance(linesIterator, lineToSkipAfterHeader);
    }

    private ImmutableMap<VariableComponentKey, Expression<String>> getDefaultValueExpressions(Configuration.DataTypeDescription dataTypeDescription) {
        ImmutableMap.Builder<VariableComponentKey, Expression<String>> defaultValueExpressionsBuilder = ImmutableMap.builder();
        for (Map.Entry<String, Configuration.ColumnDescription> variableEntry : dataTypeDescription.getData().entrySet()) {
            String variable = variableEntry.getKey();
            Configuration.ColumnDescription variableDescription = variableEntry.getValue();
            for (Map.Entry<String, Configuration.VariableComponentDescription> componentEntry : variableDescription.getComponents().entrySet()) {
                String component = componentEntry.getKey();
                Configuration.VariableComponentDescription componentDescription = componentEntry.getValue();
                VariableComponentKey variableComponentKey = new VariableComponentKey(variable, component);
                Expression<String> defaultValueExpression;
                if (componentDescription == null) {
                    defaultValueExpression = CommonExpression.EMPTY_STRING;
                } else {
                    String defaultValue = componentDescription.getDefaultValue();
                    if (StringUtils.isEmpty(defaultValue)) {
                        defaultValueExpression = CommonExpression.EMPTY_STRING;
                    } else {
                        defaultValueExpression = StringGroovyExpression.forExpression(defaultValue);
                    }
                }
                defaultValueExpressionsBuilder.put(variableComponentKey, defaultValueExpression);
            }
        }
        ImmutableMap<VariableComponentKey, Expression<String>> defaultValueExpressions = defaultValueExpressionsBuilder.build();
        if (log.isDebugEnabled()) {
            log.debug("expressions des valeurs par défaut détectées pour " + dataTypeDescription + " = " + defaultValueExpressions);
        }
        return defaultValueExpressions;
    }

    public String getDataCsv(DownloadDatasetQuery downloadDatasetQuery) {
        String applicationNameOrId = downloadDatasetQuery.getApplicationNameOrId();
        String dataType = downloadDatasetQuery.getDataType();
        List<DataRow> list = findData(downloadDatasetQuery);
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
                for (DataRow dataRow : list) {
                    Map<String, Map<String, String>> record = dataRow.getValues();
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

    public List<DataRow> findData(DownloadDatasetQuery downloadDatasetQuery) {
        authenticationService.setRoleForClient();
        String applicationNameOrId = downloadDatasetQuery.getApplicationNameOrId();
        Application app = getApplication(applicationNameOrId);
        List<DataRow> data = repo.getRepository(app).data().findAllByDataType(downloadDatasetQuery.getDataType());
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

    private ImmutableMap<VariableComponentKey, String> valuesToIndexedPerReferenceMap(DataRow dataRow) {
        Map<String, Map<String, String>> line = dataRow.getValues();
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

    public String getReferenceValuesCsv(String applicationNameOrId, String referenceType, MultiValueMap<String, String> params) {
        Configuration.ReferenceDescription referenceDescription = getApplication(applicationNameOrId)
                .getConfiguration()
                .getReferences()
                .get(referenceType);
        ImmutableMap<String, Function<ReferenceValue, String>> model = referenceDescription.getColumns().keySet().stream()
                .collect(ImmutableMap.toImmutableMap(Function.identity(), column -> referenceValue -> referenceValue.getRefValues().get(column)));
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(referenceDescription.getSeparator())
                .withSkipHeaderRecord();
        StringWriter out = new StringWriter();
        try {
            CSVPrinter csvPrinter = new CSVPrinter(out, csvFormat);
            csvPrinter.printRecord(model.keySet());
            List<ReferenceValue> list = repo.getRepository(applicationNameOrId).referenceValue().findAllByReferenceType(referenceType, params);
            for (ReferenceValue referenceValue : list) {
                ImmutableList<String> rowAsRecord = model.values().stream()
                        .map(getCellContentFn -> getCellContentFn.apply(referenceValue))
                        .collect(ImmutableList.toImmutableList());
                csvPrinter.printRecord(rowAsRecord);
            }
        } catch (IOException e) {
            throw new OreSiTechnicalException("erreur lors de la génération du fichier CSV", e);
        }
        String csv = out.toString();
        return csv;
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