package fr.inra.oresing.rest;

import com.google.common.base.Charsets;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterators;
import com.google.common.collect.Maps;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Ordering;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.DateLineChecker;
import fr.inra.oresing.checker.FloatChecker;
import fr.inra.oresing.checker.IntegerChecker;
import fr.inra.oresing.checker.InvalidDatasetContentException;
import fr.inra.oresing.checker.LineChecker;
import fr.inra.oresing.checker.Multiplicity;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.checker.ReferenceLineCheckerConfiguration;
import fr.inra.oresing.groovy.CommonExpression;
import fr.inra.oresing.groovy.Expression;
import fr.inra.oresing.groovy.GroovyContextHelper;
import fr.inra.oresing.groovy.StringGroovyExpression;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Authorization;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.BinaryFileDataset;
import fr.inra.oresing.model.ColumnPresenceConstraint;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.Data;
import fr.inra.oresing.model.Datum;
import fr.inra.oresing.model.LocalDateTimeRange;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.ReferenceColumnSingleValue;
import fr.inra.oresing.model.ReferenceColumnValue;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.BinaryFileInfos;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.ReferenceValueRepository;
import fr.inra.oresing.persistence.SqlPolicy;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.SqlSchemaForApplication;
import fr.inra.oresing.persistence.SqlService;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import fr.inra.oresing.rest.validationcheckresults.DateValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.DefaultValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.ReferenceValidationCheckResult;
import fr.inra.oresing.transformer.TransformerFactory;
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
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Nullable;
import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd hh:mm:ss").withZone(ZoneOffset.UTC);
    public static final DateTimeFormatter DATE_FORMATTER_DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private OreSiRepository repo;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private TransformerFactory transformerFactory;

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

    @Autowired
    private GroovyContextHelper groovyContextHelper;

    /**
     * @deprecated utiliser directement {@link Ltree#escapeToLabel(String)}
     */
    @Deprecated
    public static String escapeKeyComponent(String key) {
        return Ltree.escapeToLabel(key);
    }

    protected UUID storeFile(Application app, MultipartFile file, String comment) throws IOException {
        authenticationService.setRoleForClient();
        // creation du fichier
        BinaryFile binaryFile = new BinaryFile();
        binaryFile.setComment(comment);
        binaryFile.setApplication(app.getId());
        binaryFile.setName(file.getOriginalFilename());
        binaryFile.setSize(file.getSize());
        binaryFile.setData(file.getBytes());
        BinaryFileInfos binaryFileInfos = new BinaryFileInfos();
        binaryFile.setParams(binaryFileInfos);
        binaryFile.getParams().createuser = request.getRequestClient().getId();
        binaryFile.getParams().createdate = LocalDateTime.now().toString();
        UUID result = repo.getRepository(app).binaryFile().store(binaryFile);
        return result;
    }

    public UUID createApplication(String name, MultipartFile configurationFile, String comment) throws IOException, BadApplicationConfigurationException {
        Application app = new Application();
        app.setName(name);
        app.setComment(comment);
        UUID result = changeApplicationConfiguration(app, configurationFile, this::initApplication);
        relationalService.createViews(app.getName());

        return result;
    }

    public Application initApplication(Application app) {
        authenticationService.resetRole();
        SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(app);
        org.flywaydb.core.api.configuration.ClassicConfiguration flywayConfiguration = new ClassicConfiguration();
        flywayConfiguration.setDataSource(dataSource);
        flywayConfiguration.setSchemas(sqlSchemaForApplication.getName());
        flywayConfiguration.setLocations(new Location("classpath:migration/application"));
        flywayConfiguration.getPlaceholders().put("applicationSchema", sqlSchemaForApplication.getSqlIdentifier());
        flywayConfiguration.getPlaceholders().put("requiredauthorizations", sqlSchemaForApplication.getRequiredauthorizationsAttributes(app));
        flywayConfiguration.getPlaceholders().put("requiredauthorizationscomparing", sqlSchemaForApplication.getRequiredauthorizationsAttributesComparing(app));
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
                "name = '" + app.getName() + "'"
        ));

        db.createPolicy(new SqlPolicy(
                String.join("_", readerOnApplicationRole.getAsSqlRole(), SqlPolicy.Statement.SELECT.name()),
                SqlSchema.main().application(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                SqlPolicy.Statement.SELECT,
                readerOnApplicationRole,
                "name = '" + app.getName() + "'"
        ));

        db.setSchemaOwner(sqlSchemaForApplication, adminOnApplicationRole);
        db.grantUsage(sqlSchemaForApplication, readerOnApplicationRole);

        db.setTableOwner(sqlSchemaForApplication.data(), adminOnApplicationRole);
        db.setTableOwner(sqlSchemaForApplication.referenceValue(), adminOnApplicationRole);
        db.setTableOwner(sqlSchemaForApplication.binaryFile(), adminOnApplicationRole);

        OreSiUserRole creator = authenticationService.getUserRole(request.getRequestClient().getId());
        db.addUserInRole(creator, adminOnApplicationRole);

        authenticationService.setRoleForClient();
        repo.application().store(app);
        return app;
    }

    public UUID changeApplicationConfiguration(String nameOrId, MultipartFile configurationFile, String comment) throws IOException, BadApplicationConfigurationException {
        relationalService.dropViews(nameOrId);
        authenticationService.setRoleForClient();
        Application app = getApplication(nameOrId);
        app.setComment(comment);
        Configuration oldConfiguration = app.getConfiguration();
        UUID oldConfigFileId = app.getConfigFile();
        Application application = getApplication(nameOrId);
        UUID uuid = changeApplicationConfiguration(app, configurationFile, Function.identity());
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
                                UUID referenceId = referenceCheckResult.getMatchedReferenceId();
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

            validateStoredData(new DownloadDatasetQuery(nameOrId, dataType, null, null, null, null, null, app));
        }

        // on supprime l'ancien fichier vu que tout c'est bien passé
        boolean deleted = repo.getRepository(app).binaryFile().delete(oldConfigFileId);
        Preconditions.checkState(deleted);

        relationalService.createViews(nameOrId);

        return uuid;
    }

    /*private void validateStoredReference(Application app, String reference) {
        ImmutableSet<LineChecker> lineCheckers = checkerFactory.getReferenceValidationLineCheckers(app, reference);
        Consumer<ImmutableMap<String, String>> validateRow = line -> {
            lineCheckers.forEach(lineChecker -> {
                ValidationCheckResult validationCheckResult = lineChecker.checkReference(line);
                Preconditions.checkState(validationCheckResult.isSuccess(), "erreur de validation d'une donnée stockée " + validationCheckResult);
            });
        };
        repo.getRepository(app).referenceValue().findAllByReferenceType(reference).stream()
                .map(this::valuesToIndexedPerReferenceMap)
                .forEach(validateRow);
    }*/

    private void validateStoredData(DownloadDatasetQuery downloadDatasetQuery) {
        Application application = downloadDatasetQuery.getApplication();
        String dataType = downloadDatasetQuery.getDataType();
        ImmutableSet<LineChecker> lineCheckers = checkerFactory.getLineCheckers(application, dataType);
        Consumer<Datum> validateRow = line -> {
            lineCheckers.forEach(lineChecker -> {
                ValidationCheckResult validationCheckResult = lineChecker.check(line);
                Preconditions.checkState(validationCheckResult.isSuccess(), "erreur de validation d'une donnée stockée " + validationCheckResult);
            });
        };
        repo.getRepository(application).data().findAllByDataType(downloadDatasetQuery).stream()
                .map(DataRow::getValues)
                .map(Datum::fromMapMap)
                .forEach(validateRow);
    }

    private UUID changeApplicationConfiguration(Application app, MultipartFile configurationFile, Function<Application, Application> initApplication) throws IOException, BadApplicationConfigurationException {

        ConfigurationParsingResult configurationParsingResult;
        if (configurationFile.getOriginalFilename().matches(".*\\.zip")) {
            final byte[] bytes = new MultiYaml().parseConfigurationBytes(configurationFile);
            configurationParsingResult = applicationConfigurationService.parseConfigurationBytes(bytes);
        } else {
            configurationParsingResult = applicationConfigurationService.parseConfigurationBytes(configurationFile.getBytes());
        }
        BadApplicationConfigurationException.check(configurationParsingResult);
        Configuration configuration = configurationParsingResult.getResult();
        app.setReferenceType(new ArrayList<>(configuration.getReferences().keySet()));
        app.setDataType(new ArrayList<>(configuration.getDataTypes().keySet()));
        app.setConfiguration(configuration);
        app = initApplication.apply(app);
        UUID confId = storeFile(app, configurationFile, app.getComment());
        app.setConfigFile(confId);
        UUID appId = repo.application().store(app);
        return appId;
    }

    public UUID addReference(Application app, String refType, MultipartFile file) throws IOException {
        ReferenceValueRepository referenceValueRepository = repo.getRepository(app).referenceValue();
        authenticationService.setRoleForClient();
        UUID fileId = storeFile(app, file, "");
        final ReferenceImporterContext referenceImporterContext = getReferenceImporterContext(app, refType);
        ReferenceImporter referenceImporter = new ReferenceImporter(referenceImporterContext) {
            @Override
            void storeAll(Stream<ReferenceValue> stream) {
                referenceValueRepository.storeAll(stream);
            }
        };
        referenceImporter.doImport(file, fileId);
        return fileId;
    }

    private ReferenceImporterContext getReferenceImporterContext(Application app, String refType) {
        ReferenceValueRepository referenceValueRepository = repo.getRepository(app).referenceValue();
        Configuration conf = app.getConfiguration();
        ImmutableSet<LineChecker> lineCheckers = checkerFactory.getReferenceValidationLineCheckers(app, refType);
        final ImmutableMap<Ltree, UUID> storedReferences = referenceValueRepository.getReferenceIdPerKeys(refType);

        ImmutableMap<ReferenceColumn, Multiplicity> multiplicityPerColumns = lineCheckers.stream()
                .filter(lineChecker -> lineChecker instanceof ReferenceLineChecker)
                .map(lineChecker -> (ReferenceLineChecker) lineChecker)
                .collect(ImmutableMap.toImmutableMap(referenceLineChecker -> (ReferenceColumn) referenceLineChecker.getTarget().getTarget(), referenceLineChecker -> referenceLineChecker.getConfiguration().getMultiplicity()));

        Configuration.ReferenceDescription referenceDescription = conf.getReferences().get(refType);

        Stream<ReferenceImporterContext.Column> staticColumns = referenceDescription.getColumns().entrySet().stream()
                .map(entry -> {
                    ReferenceColumn referenceColumn = new ReferenceColumn(entry.getKey());
                    Multiplicity multiplicity = multiplicityPerColumns.getOrDefault(referenceColumn, Multiplicity.ONE);
                    ColumnPresenceConstraint presenceConstraint = MoreObjects.firstNonNull(entry.getValue(), new Configuration.ReferenceColumnDescription()).getPresenceConstraint();
                    ReferenceImporterContext.Column column;
                    if (multiplicity == Multiplicity.ONE) {
                        column = new ReferenceImporterContext.OneValueStaticColumn(referenceColumn, presenceConstraint);
                    } else if (multiplicity == Multiplicity.MANY) {
                        column = new ReferenceImporterContext.ManyValuesStaticColumn(referenceColumn, presenceConstraint);
                    } else {
                        throw new IllegalStateException("multiplicity = " + multiplicity);
                    }
                    return column;
                });

        Stream<ReferenceImporterContext.Column> dynamicColumns = referenceDescription.getDynamicColumns().entrySet().stream()
                .flatMap(entry -> {
                    ReferenceColumn referenceColumn = new ReferenceColumn(entry.getKey());
                    Configuration.ReferenceDynamicColumnDescription value = entry.getValue();
                    String reference = value.getReference();
                    ReferenceColumn referenceColumnToLookForHeader = new ReferenceColumn(value.getReferenceColumnToLookForHeader());
                    List<ReferenceValue> allByReferenceType = referenceValueRepository.findAllByReferenceType(reference);
                    Stream<ReferenceImporterContext.Column> valuedDynamicColumns = allByReferenceType.stream()
                            .map(referenceValue -> {
                                ReferenceDatum referenceDatum = referenceValue.getRefValues();
                                Ltree hierarchicalKey = referenceValue.getHierarchicalKey();
                                ReferenceColumnSingleValue referenceColumnValue = (ReferenceColumnSingleValue) referenceDatum.get(referenceColumnToLookForHeader);
                                String header = referenceColumnValue.getValue();
                                String fullHeader = value.getHeaderPrefix() + header;
                                ColumnPresenceConstraint presenceConstraint = value.getPresenceConstraint();
                                return new ReferenceImporterContext.DynamicColumn(
                                        referenceColumn,
                                        fullHeader,
                                        presenceConstraint,
                                        hierarchicalKey,
                                        Map.entry(reference, referenceValue.getId())
                                );
                            });
                    return valuedDynamicColumns;
                });

        ImmutableMap<String, ReferenceImporterContext.Column> columns =
                Stream.concat(staticColumns, dynamicColumns)
                        .collect(ImmutableMap.toImmutableMap(
                                ReferenceImporterContext.Column::getExpectedHeader,
                                Function.identity()
                        ));

        final ReferenceImporterContext referenceImporterContext =
                new ReferenceImporterContext(
                        app.getId(),
                        conf,
                        refType,
                        lineCheckers,
                        storedReferences,
                        columns
                );
        return referenceImporterContext;
    }

    HierarchicalReferenceAsTree getHierarchicalReferenceAsTree(Application application, String lowestLevelReference) {
        ReferenceValueRepository referenceValueRepository = repo.getRepository(application).referenceValue();
        Configuration.CompositeReferenceDescription compositeReferenceDescription = application
                .getConfiguration()
                .getCompositeReferencesUsing(lowestLevelReference)
                .orElseThrow();
        BiMap<Ltree, ReferenceValue> indexedByHierarchicalKeyReferenceValues = HashBiMap.create();
        Map<ReferenceValue, Ltree> parentHierarchicalKeys = new LinkedHashMap<>();
        ImmutableList<String> referenceTypes = compositeReferenceDescription.getComponents().stream()
                .map(Configuration.CompositeReferenceComponentDescription::getReference)
                .collect(ImmutableList.toImmutableList());
        ImmutableSortedSet<String> sortedReferenceTypes = ImmutableSortedSet.copyOf(Ordering.explicit(referenceTypes), referenceTypes);
        ImmutableSortedSet<String> includedReferences = sortedReferenceTypes.headSet(lowestLevelReference, true);
        compositeReferenceDescription.getComponents().stream()
                .filter(compositeReferenceComponentDescription -> includedReferences.contains(compositeReferenceComponentDescription.getReference()))
                .forEach(compositeReferenceComponentDescription -> {
                    String reference = compositeReferenceComponentDescription.getReference();
                    Optional<ReferenceColumn> parentKeyColumn = Optional.ofNullable(compositeReferenceComponentDescription.getParentKeyColumn()).map(ReferenceColumn::new);
                    referenceValueRepository.findAllByReferenceType(reference).forEach(referenceValue -> {
                        indexedByHierarchicalKeyReferenceValues.put(referenceValue.getHierarchicalKey(), referenceValue);
                        parentKeyColumn.ifPresent(presentParentKeyColumn -> {
                            ReferenceDatum referenceDatum = referenceValue.getRefValues();
                            ReferenceColumnValue referenceColumnValue = referenceDatum.get(presentParentKeyColumn);
                            Preconditions.checkState(referenceColumnValue instanceof ReferenceColumnSingleValue);
                            String parentHierarchicalKeyAsString = ((ReferenceColumnSingleValue) referenceColumnValue).getValue();
                            Ltree parentHierarchicalKey = Ltree.fromSql(parentHierarchicalKeyAsString);
                            parentHierarchicalKeys.put(referenceValue, parentHierarchicalKey);
                        });
                    });
                });
        Map<ReferenceValue, ReferenceValue> childToParents = Maps.transformValues(parentHierarchicalKeys, indexedByHierarchicalKeyReferenceValues::get);
        SetMultimap<ReferenceValue, ReferenceValue> tree = HashMultimap.create();
        childToParents.forEach((child, parent) -> tree.put(parent, child));
        ImmutableSet<ReferenceValue> roots = Sets.difference(indexedByHierarchicalKeyReferenceValues.values(), parentHierarchicalKeys.keySet()).immutableCopy();
        return new HierarchicalReferenceAsTree(ImmutableSetMultimap.copyOf(tree), roots);
    }

    public List<BinaryFile> getFilesOnRepository(String nameOrId, String datatype, BinaryFileDataset fileDatasetID, boolean overlap) {
        authenticationService.setRoleForClient();
        Application app = getApplication(nameOrId);
        return repo.getRepository(app).binaryFile().findByBinaryFileDataset(datatype, fileDatasetID, overlap);
    }

    /**
     * Insérer un jeu de données.
     */
    public UUID addData(String nameOrId, String dataType, MultipartFile file, FileOrUUID params) throws IOException, InvalidDatasetContentException {
        List<CsvRowValidationCheckResult> errors = new LinkedList<>();
        authenticationService.setRoleForClient();
        log.debug(request.getRequestClient().getId().toString());
        Application app = getApplication(nameOrId);
        Set<BinaryFile> filesToStore = new HashSet<>();
        Optional.ofNullable(params)
                .map(par -> par.getBinaryfiledataset())
                .ifPresent(binaryFileDataset -> binaryFileDataset.setDatatype(dataType));
        BinaryFile storedFile = loadOrCreateFile(file, params, app);
        if (params != null && !params.topublish) {
            if (storedFile.getParams() != null && storedFile.getParams().published) {
                storedFile.getParams().published = false;
                filesToStore.add(storedFile);
                unPublishVersions(app, filesToStore);
            }
            return storedFile.getId();
        }
        Configuration conf = app.getConfiguration();
        Configuration.DataTypeDescription dataTypeDescription = conf.getDataTypes().get(dataType);
        Configuration.FormatDescription formatDescription = dataTypeDescription.getFormat();
        InvalidDatasetContentException.checkErrorsIsEmpty(findPublishedVersion(nameOrId, dataType, params, filesToStore, true));
        publishVersion(dataType, errors, app, storedFile, dataTypeDescription, formatDescription, params == null ? null : params.binaryfiledataset);
        InvalidDatasetContentException.checkErrorsIsEmpty(errors);
        relationalService.onDataUpdate(app.getName());
        unPublishVersions(app, filesToStore);
        storePublishedVersion(app, filesToStore, storedFile);
        filesToStore.stream()
                .forEach(repo.getRepository(app.getId()).binaryFile()::store);
        return storedFile.getId();
    }

    private void storePublishedVersion(Application app, Set<BinaryFile> filesToStore, BinaryFile storedFile) {
        if (storedFile != null) {
            if (storedFile.getParams() == null) {
                storedFile.setParams(BinaryFileInfos.EMPTY_INSTANCE());
            }
            storedFile.getParams().published = true;
            storedFile.getParams().publisheduser = request.getRequestClient().getId();
            storedFile.getParams().publisheddate = LocalDateTime.now().toString();
            repo.getRepository(app).binaryFile().store(storedFile);
            filesToStore.add(storedFile);
        }
    }

    private void unPublishVersions(Application app, Set<BinaryFile> filesToStore) {
        filesToStore.stream()
                .forEach(f -> {
                    repo.getRepository(app).data().removeByFileId(f.getId());
                    f.getParams().published = false;
                    repo.getRepository(app).binaryFile().store(f);
                });
    }

    private void publishVersion(String dataType,
                                List<CsvRowValidationCheckResult> errors,
                                Application app,
                                BinaryFile storedFile,
                                Configuration.DataTypeDescription dataTypeDescription,
                                Configuration.FormatDescription formatDescription,
                                BinaryFileDataset binaryFileDataset) throws IOException {
        try (InputStream csv = new ByteArrayInputStream(storedFile.getData())) {
            CSVFormat csvFormat = CSVFormat.DEFAULT
                    .withDelimiter(formatDescription.getSeparator())
                    .withSkipHeaderRecord();
            CSVParser csvParser = CSVParser.parse(csv, Charsets.UTF_8, csvFormat);
            Iterator<CSVRecord> linesIterator = csvParser.iterator();

            Datum constantValues = new Datum();
            readPreHeader(formatDescription, constantValues, linesIterator);

            ImmutableList<String> columns = readHeaderRow(linesIterator);
            readPostHeader(formatDescription, linesIterator);

            Stream<Data> dataStream = Streams.stream(csvParser)
                    .map(buildCsvRecordToLineAsMapFn(columns))
                    .flatMap(lineAsMap -> buildLineAsMapToRecordsFn(formatDescription).apply(lineAsMap).stream())
                    .map(buildMergeLineValuesAndConstantValuesFn(constantValues))
                    .map(buildReplaceMissingValuesByDefaultValuesFn(app, dataType, binaryFileDataset == null ? null : binaryFileDataset.getRequiredauthorizations()))
                    .flatMap(buildLineValuesToEntityStreamFn(app, dataType, storedFile.getId(), errors, binaryFileDataset));

            repo.getRepository(app).data().storeAll(dataStream);
        }
    }

    private List<CsvRowValidationCheckResult> findPublishedVersion(String nameOrId, String dataType, FileOrUUID params, Set<BinaryFile> filesToStore, boolean searchOverlaps) {
        if (params != null) {
            if (searchOverlaps) {
                List<BinaryFile> overlapingFiles = getFilesOnRepository(nameOrId, dataType, params.binaryfiledataset, true);
                if (!overlapingFiles.isEmpty()) {
                    return List.of(new CsvRowValidationCheckResult(DefaultValidationCheckResult.error(
                            "overlappingpublishedversion",
                            ImmutableMap.of("fileOrUUID", params, "files",
                                    overlapingFiles.stream()
                                            .map(f -> f.getParams().binaryFiledataset.toString())
                                            .collect(Collectors.toSet())
                            )), -1));
                }
            }
            getFilesOnRepository(nameOrId, dataType, params.binaryfiledataset, false)
                    .stream()
                    .filter(f -> f.getParams().published)
                    .forEach(f -> {
                        f.getParams().published = false;
                        f.getParams().publisheduser = null;
                        f.getParams().publisheddate = null;
                        filesToStore.add(f);
                    });
        }
        return new LinkedList<>();
    }

    @Nullable
    private BinaryFile loadOrCreateFile(MultipartFile file, FileOrUUID params, Application app) {
        BinaryFile storedFile = Optional.ofNullable(params)
                .map(param -> param.getFileid())
                .map(uuid -> repo.getRepository(app).binaryFile().tryFindByIdWithData(uuid).orElse(null))
                .orElseGet(() -> {
                    UUID fileId = null;
                    try {
                        fileId = storeFile(app, file, "");
                    } catch (IOException e) {
                        return null;
                    }
                    BinaryFile binaryFile = repo.getRepository(app).binaryFile().tryFindByIdWithData(fileId).orElse(null);
                    if (binaryFile == null) {
                        return null;
                    }
                    if (params != null) {
                        binaryFile.getParams().binaryFiledataset = params.binaryfiledataset;
                    }
                    fileId = repo.getRepository(app).binaryFile().store(binaryFile);
                    return repo.getRepository(app).binaryFile().tryFindByIdWithData(fileId).orElse(null);
                });
        return storedFile;
    }

    /**
     * return a function that transform each RowWithData to a stream of data entities
     *
     * @param app
     * @param dataType
     * @param fileId
     * @return
     */
    private Function<RowWithData, Stream<Data>> buildLineValuesToEntityStreamFn(Application app, String dataType, UUID fileId, List<CsvRowValidationCheckResult> errors, BinaryFileDataset binaryFileDataset) {
        ImmutableSet<LineChecker> lineCheckers = checkerFactory.getLineCheckers(app, dataType);
        Configuration conf = app.getConfiguration();
        Configuration.DataTypeDescription dataTypeDescription = conf.getDataTypes().get(dataType);

        return buildRowWithDataStreamFunction(app, dataType, fileId, errors, lineCheckers, dataTypeDescription, binaryFileDataset);
    }

    /**
     * build the function that transform each RowWithData to a stream of data entities
     *
     * @param app
     * @param dataType
     * @param fileId
     * @param errors
     * @param lineCheckers
     * @param dataTypeDescription
     * @param binaryFileDataset
     * @return
     */
    private Function<RowWithData, Stream<Data>> buildRowWithDataStreamFunction(Application app,
                                                                               String dataType,
                                                                               UUID fileId,
                                                                               List<CsvRowValidationCheckResult> errors,
                                                                               ImmutableSet<LineChecker> lineCheckers,
                                                                               Configuration.DataTypeDescription dataTypeDescription,
                                                                               BinaryFileDataset binaryFileDataset) {
        DateLineChecker timeScopeDateLineChecker = lineCheckers.stream()
                .filter(lineChecker -> lineChecker instanceof DateLineChecker)
                .map(lineChecker -> (DateLineChecker) lineChecker)
                .filter(dateLineChecker -> dateLineChecker.getTarget().getTarget().equals(dataTypeDescription.getAuthorization().getTimeScope()))
                .collect(MoreCollectors.onlyElement());


        return rowWithData -> {
            Datum datum = Datum.copyOf(rowWithData.getDatum());
            Map<VariableComponentKey, UUID> refsLinkedTo = new LinkedHashMap<>();
            Map<VariableComponentKey, DateValidationCheckResult> dateValidationCheckResultImmutableMap = new HashMap<>();
            List<CsvRowValidationCheckResult> rowErrors = new LinkedList<>();

            lineCheckers.forEach(lineChecker -> {
                ValidationCheckResult validationCheckResult = lineChecker.check(datum);
                if (validationCheckResult.isSuccess()) {
                    if (validationCheckResult instanceof DateValidationCheckResult) {
                        VariableComponentKey variableComponentKey = (VariableComponentKey) ((DateValidationCheckResult) validationCheckResult).getTarget().getTarget();
                        dateValidationCheckResultImmutableMap.put(variableComponentKey, (DateValidationCheckResult) validationCheckResult);
                    }
                    if (validationCheckResult instanceof ReferenceValidationCheckResult) {
                        ReferenceLineCheckerConfiguration configuration = (ReferenceLineCheckerConfiguration) lineChecker.getConfiguration();
                        if (configuration.getGroovy() != null) {
                            datum.put((VariableComponentKey) ((ReferenceValidationCheckResult) validationCheckResult).getTarget().getTarget(), ((ReferenceValidationCheckResult) validationCheckResult).getMatchedReferenceHierarchicalKey().getSql());
                        }
                        ReferenceValidationCheckResult referenceValidationCheckResult = (ReferenceValidationCheckResult) validationCheckResult;
                        VariableComponentKey variableComponentKey = (VariableComponentKey) referenceValidationCheckResult.getTarget().getTarget();
                        UUID referenceId = referenceValidationCheckResult.getMatchedReferenceId();
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

            String timeScopeValue = datum.get(dataTypeDescription.getAuthorization().getTimeScope());
            LocalDateTimeRange timeScope = LocalDateTimeRange.parse(timeScopeValue, timeScopeDateLineChecker);

            Map<String, String> requiredAuthorizations = new LinkedHashMap<>();
            dataTypeDescription.getAuthorization().getAuthorizationScopes().forEach((authorizationScope, authorizationScopeDescription) -> {
                VariableComponentKey variableComponentKey = authorizationScopeDescription.getVariableComponentKey();
                String requiredAuthorization = datum.get(variableComponentKey);
                Ltree.checkSyntax(requiredAuthorization);
                requiredAuthorizations.put(authorizationScope, requiredAuthorization);
            });
            checkTimescopRangeInDatasetRange(timeScope, errors, binaryFileDataset, rowWithData.getLineNumber());
            checkRequiredAuthorizationInDatasetRange(requiredAuthorizations, errors, binaryFileDataset, rowWithData.getLineNumber());
            // String rowId = Hashing.sha256().hashString(line.toString(), Charsets.UTF_8).toString();
            String rowId = UUID.randomUUID().toString();

            Stream<Data> dataStream = dataTypeDescription.getAuthorization().getDataGroups().entrySet().stream().map(entry -> {
                String dataGroup = entry.getKey();
                Configuration.DataGroupDescription dataGroupDescription = entry.getValue();

                Predicate<VariableComponentKey> includeInDataGroupPredicate = variableComponentKey -> dataGroupDescription.getData().contains(variableComponentKey.getVariable());
                Datum dataGroupValues = datum.filterOnVariable(includeInDataGroupPredicate);

                Map<String, Map<String, String>> toStore = new LinkedHashMap<>();
                Map<String, Map<String, UUID>> refsLinkedToToStore = new LinkedHashMap<>();
                for (Map.Entry<VariableComponentKey, String> entry2 : dataGroupValues.asMap().entrySet()) {
                    VariableComponentKey variableComponentKey = entry2.getKey();
                    String variable = variableComponentKey.getVariable();
                    String component = variableComponentKey.getComponent();
                    String value = entry2.getValue();
                    if (dateValidationCheckResultImmutableMap.containsKey(entry2.getKey())) {
                        value = String.format("date:%s:%s", dateValidationCheckResultImmutableMap.get(variableComponentKey).getLocalDateTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), value);
                    }
                    toStore.computeIfAbsent(variable, k -> new LinkedHashMap<>()).put(component, value);
                    refsLinkedToToStore.computeIfAbsent(variable, k -> new LinkedHashMap<>()).put(component, refsLinkedTo.get(variableComponentKey));
                }

                Data e = new Data();
                e.setBinaryFile(fileId);
                e.setDataType(dataType);
                e.setRowId(rowId);
                e.setAuthorization(new Authorization(List.of(dataGroup), requiredAuthorizations, timeScope));
                e.setApplication(app.getId());
                e.setRefsLinkedTo(refsLinkedToToStore);
                e.setDataValues(toStore);
                return e;
            });

            return dataStream;
        };
    }

    private void checkTimescopRangeInDatasetRange(LocalDateTimeRange timeScope,
                                                  List<CsvRowValidationCheckResult> errors,
                                                  BinaryFileDataset binaryFileDataset,
                                                  int rowNumber) {
        if (binaryFileDataset == null) {
            return;
        }
        LocalDateTime from = LocalDate.from(DATE_TIME_FORMATTER.parse(binaryFileDataset.getFrom())).atStartOfDay();
        LocalDateTime to = LocalDate.from(DATE_TIME_FORMATTER.parse(binaryFileDataset.getTo()))
                .plus(1, ChronoUnit.DAYS).atStartOfDay();
        if (!LocalDateTimeRange.between(from, to).getRange().encloses(timeScope.getRange())) {
            errors.add(
                    new CsvRowValidationCheckResult(DefaultValidationCheckResult.error(
                            "timerangeoutofinterval",
                            ImmutableMap.of(
                                    "from", DATE_FORMATTER_DDMMYYYY.format(from),
                                    "to", DATE_TIME_FORMATTER.format(to),
                                    "value", DATE_FORMATTER_DDMMYYYY.format(timeScope.getRange().lowerEndpoint())
                            )
                    ),
                            rowNumber)
            );
        }

    }


    private void checkRequiredAuthorizationInDatasetRange(Map<String, String> requiredAuthorizations,
                                                          List<CsvRowValidationCheckResult> errors,
                                                          BinaryFileDataset binaryFileDataset,
                                                          int rowNumber) {
        if (binaryFileDataset == null) {
            return;
        }
        binaryFileDataset.getRequiredauthorizations().entrySet()
                .forEach(entry -> {
                    if (!requiredAuthorizations.get(entry.getKey()).equals(entry.getValue())) {
                        errors.add(
                                new CsvRowValidationCheckResult(
                                        DefaultValidationCheckResult.error(
                                                "badauthorizationscopeforrepository",
                                                ImmutableMap.of(
                                                        "authorization", entry.getKey(),
                                                        "expectedValue", entry.getValue(),
                                                        "givenValue", requiredAuthorizations.get(entry.getKey())
                                                )
                                        ),
                                        rowNumber)
                        );
                    }
                });

    }

    /**
     * Une fonction qui ajoute à une donnée les valeurs par défaut.
     * <p>
     * Si des valeurs par défaut ont été définies dans le YAML, la donnée doit les avoir.
     */
    private Function<RowWithData, RowWithData> buildReplaceMissingValuesByDefaultValuesFn(Application app, String dataType, Map<String, String> requiredAuthorizations) {
        ReferenceValueRepository referenceValueRepository = repo.getRepository(app).referenceValue();
        Configuration.DataTypeDescription dataTypeDescription = app.getConfiguration().getDataTypes().get(dataType);
        ImmutableMap<VariableComponentKey, Expression<String>> defaultValueExpressions = getDefaultValueExpressions(dataTypeDescription, requiredAuthorizations);
        Map<String, Configuration.ColumnDescription> data = dataTypeDescription.getData();
        Map<VariableComponentKey, Function<Datum, String>> defaultValueFns = new LinkedHashMap<>();
        Set<VariableComponentKey> replaceEnabled = new LinkedHashSet<>();
        for (Map.Entry<VariableComponentKey, Expression<String>> entry : defaultValueExpressions.entrySet()) {
            VariableComponentKey variableComponentKey = entry.getKey();
            Expression<String> expression = entry.getValue();
            Configuration.VariableComponentDescriptionConfiguration params = Optional.ofNullable(data)
                    .map(columnDescriptionLinkedHashMap -> columnDescriptionLinkedHashMap.get(variableComponentKey.getVariable()))
                    .map(columnDescription -> columnDescription.getComponents())
                    .map(variableComponentDescriptionLinkedHashMap -> variableComponentDescriptionLinkedHashMap.get(variableComponentKey.getComponent()))
                    .map(variableComponentDescription -> variableComponentDescription.getParams())
                    .orElseGet(Configuration.VariableComponentDescriptionConfiguration::new);
            Set<String> configurationReferences = params.getReferences();
            ImmutableMap<String, Object> contextForExpression = groovyContextHelper.getGroovyContextForReferences(referenceValueRepository, configurationReferences);
            Preconditions.checkState(params.getDatatypes().isEmpty(), "à ce stade, on ne gère pas la chargement de données");
            Function<Datum, String> computeDefaultValueFn = datum -> {
                ImmutableMap<String, Object> evaluationContext = ImmutableMap.<String, Object>builder()
                        .putAll(contextForExpression)
                        .putAll(datum.getEvaluationContext())
                        .build();
                String evaluate = expression.evaluate(evaluationContext);
                return evaluate;
            };
            defaultValueFns.put(variableComponentKey, computeDefaultValueFn);
            if (params.isReplace()) {
                replaceEnabled.add(variableComponentKey);
            }
        }
        return rowWithData -> {
            Map<VariableComponentKey, String> rowWithDefaults = new LinkedHashMap<>();
            Map<VariableComponentKey, String> rowWithValues = new LinkedHashMap<>(rowWithData.getDatum().asMap());
            defaultValueFns.entrySet().stream()
                    .forEach(variableComponentKeyExpressionEntry -> {
                        VariableComponentKey variableComponentKey = variableComponentKeyExpressionEntry.getKey();
                        Function<Datum, String> computeDefaultValueFn = variableComponentKeyExpressionEntry.getValue();
                        String evaluate = computeDefaultValueFn.apply(rowWithData.getDatum());
                        if (StringUtils.isNotBlank(evaluate)) {
                            if (replaceEnabled.contains(variableComponentKey)) {
                                rowWithValues.put(variableComponentKey, evaluate);
                            } else {
                                rowWithDefaults.put(variableComponentKey, evaluate);
                            }
                        }
                    });
            rowWithDefaults.putAll(rowWithValues);
            return new RowWithData(rowWithData.getLineNumber(), new Datum(ImmutableMap.copyOf(rowWithDefaults)));
        };
    }

    /**
     * Une fonction qui ajoute à une donnée les données constantes.
     * <p>
     * Les constantes sont des variables/composants qui ont la même valeur pour toutes les lignes
     * d'un fichier de données qu'on importe. Ce sont les données qu'on trouve dans l'entête
     * du fichier.
     */
    private Function<RowWithData, RowWithData> buildMergeLineValuesAndConstantValuesFn(Datum constantValues) {
        return rowWithData -> {
            final ImmutableMap<VariableComponentKey, String> values = ImmutableMap.<VariableComponentKey, String>builder()
                    .putAll(constantValues.asMap())
                    .putAll(rowWithData.getDatum().asMap())
                    .build();
            Datum datum = new Datum(values);
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
            InvalidDatasetContentException.checkHeader(expectedHeaderColumns, expectedHeaderColumns, actualHeaderColumns, headerLine);
            Map<VariableComponentKey, String> record = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : line) {
                String header = entry.getKey();
                String value = entry.getValue();
                Configuration.ColumnBindingDescription bindingDescription = bindingPerHeader.get(header);
                record.put(bindingDescription.getBoundTo(), value);
            }
            return ImmutableSet.of(new RowWithData(parsedCsvRow.getLineNumber(), new Datum(record)));
        };
        return lineAsMapToRecordsFn;
    }

    /**
     * build the function that Dispatch ParsedCsvRow into RowWithData when there are repeatedColumns
     */
    private Function<ParsedCsvRow, ImmutableSet<RowWithData>> buildLineAsMapWhenRepeatedColumnsToRecordsFn(Configuration.FormatDescription formatDescription) {
        return parsedCsvRow -> {
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
                    Datum datum = new Datum(record);
                    records.add(new RowWithData(parsedCsvRow.getLineNumber(), datum));

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
                record.add(Map.entry(header.strip(), value));
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
    private void readPreHeader(Configuration.FormatDescription formatDescription, Datum constantValues, Iterator<CSVRecord> linesIterator) {
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

    private ImmutableMap<VariableComponentKey, Expression<String>> getDefaultValueExpressions(Configuration.DataTypeDescription dataTypeDescription, Map<String, String> requiredAuthorizations) {
        ImmutableMap.Builder<VariableComponentKey, Expression<String>> defaultValueExpressionsBuilder = ImmutableMap.builder();

        List<String> variableComponentsFromRepository = new LinkedList<>();
        if (requiredAuthorizations != null) {
            for (Map.Entry<String, String> entry : requiredAuthorizations.entrySet()) {
                Configuration.AuthorizationScopeDescription authorizationScopeDescription = dataTypeDescription.getAuthorization().getAuthorizationScopes().get(entry.getKey());
                VariableComponentKey variableComponentKey = authorizationScopeDescription.getVariableComponentKey();
                String value = entry.getValue();
                defaultValueExpressionsBuilder.put(variableComponentKey, StringGroovyExpression.forExpression("\"" + value + "\""));
                variableComponentsFromRepository.add(variableComponentKey.getId());
            }
        }
        for (Map.Entry<String, Configuration.ColumnDescription> variableEntry : dataTypeDescription.getData().entrySet()) {
            String variable = variableEntry.getKey();
            Configuration.ColumnDescription variableDescription = variableEntry.getValue();
            for (Map.Entry<String, Configuration.VariableComponentDescription> componentEntry : variableDescription.getComponents().entrySet()) {
                String component = componentEntry.getKey();
                Configuration.VariableComponentDescription componentDescription = componentEntry.getValue();
                VariableComponentKey variableComponentKey = new VariableComponentKey(variable, component);
                if (variableComponentsFromRepository.contains(variableComponentKey.getId())) {
                    continue;
                }
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
            //log.debug("expressions des valeurs par défaut détectées pour " + dataTypeDescription + " = " + defaultValueExpressions);
        }
        return defaultValueExpressions;
    }

    public String getDataCsv(DownloadDatasetQuery downloadDatasetQuery, String nameOrId, String dataType) {
        DownloadDatasetQuery downloadDatasetQueryCopy = DownloadDatasetQuery.buildDownloadDatasetQuery(downloadDatasetQuery, nameOrId, dataType, getApplication(nameOrId));
        List<DataRow> list = findData(downloadDatasetQueryCopy, nameOrId, dataType);
        Configuration.FormatDescription format = downloadDatasetQueryCopy.getApplication()
                .getConfiguration()
                .getDataTypes()
                .get(dataType)
                .getFormat();
        ImmutableMap<String, DownloadDatasetQuery.VariableComponentOrderBy> allColumns = ImmutableMap.copyOf(getExportColumns(format).entrySet().stream()
                .collect(Collectors.toMap(
                        e -> e.getKey(),
                        e -> new DownloadDatasetQuery.VariableComponentOrderBy(e.getValue(), DownloadDatasetQuery.Order.ASC)
                )));
        ImmutableMap<String, DownloadDatasetQuery.VariableComponentOrderBy> columns;
        List<String> dateLineCheckerVariableComponentKeyIdList = checkerFactory.getLineCheckers(getApplication(nameOrId), dataType).stream()
                .filter(ch -> ch instanceof DateLineChecker)
                .map(ch -> (DateLineChecker) ch)
                .map(ch -> ((VariableComponentKey) ch.getTarget().getTarget()).getId())
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(downloadDatasetQueryCopy.getVariableComponentOrderBy())) {
            columns = allColumns;
        } else {
            columns = ImmutableMap.copyOf(downloadDatasetQueryCopy.getVariableComponentOrderBy().stream()
                    .collect(Collectors.toMap(DownloadDatasetQuery.VariableComponentOrderBy::getId, k -> k)));
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
                            .map(variableComponentSelect -> {
                                Map<String, String> components = record.computeIfAbsent(variableComponentSelect.getVariable(), k -> Collections.emptyMap());
                                String value = components.getOrDefault(variableComponentSelect.getComponent(), "");
                                if (dateLineCheckerVariableComponentKeyIdList.contains(variableComponentSelect.variableComponentKey.getId())) {
                                    value = DateLineChecker.sortableDateToFormattedDate(value);
                                }
                                return value;
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

    public Map<String, Map<String, LineChecker>> getcheckedFormatVariableComponents(String nameOrId, String dataType, Locale locale) {
        return checkerFactory.getLineCheckers(getApplication(nameOrId), dataType, locale)
                .stream()
                .filter(c -> (c instanceof DateLineChecker) || (c instanceof IntegerChecker) || (c instanceof FloatChecker) || (c instanceof ReferenceLineChecker))
                .collect(
                        Collectors.groupingBy(
                                c -> c.getClass().getSimpleName(),
                                Collectors.toMap(
                                        c -> {
                                            VariableComponentKey vc;
                                            if (c instanceof DateLineChecker) {
                                                vc = (VariableComponentKey) ((DateLineChecker) c).getTarget().getTarget();
                                            } else if (c instanceof IntegerChecker) {
                                                vc = (VariableComponentKey) ((IntegerChecker) c).getTarget().getTarget();
                                            } else if (c instanceof FloatChecker) {
                                                vc = (VariableComponentKey) ((FloatChecker) c).getTarget().getTarget();
                                            } else {
                                                vc = (VariableComponentKey) ((ReferenceLineChecker) c).getTarget().getTarget();
                                            }
                                            return vc.getId();
                                        },
                                        c -> c
                                )
                        )
                );
    }

    public List<DataRow> findData(DownloadDatasetQuery downloadDatasetQuery, String nameOrId, String dataType) {
        downloadDatasetQuery = DownloadDatasetQuery.buildDownloadDatasetQuery(downloadDatasetQuery, nameOrId, dataType, getApplication(nameOrId));
        authenticationService.setRoleForClient();
        String applicationNameOrId = downloadDatasetQuery.getApplicationNameOrId();
        Application app = getApplication(applicationNameOrId);
        List<DataRow> data = repo.getRepository(app).data().findAllByDataType(downloadDatasetQuery);
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

    /**
     * @param nameOrId l'id de l'application
     * @param refType  le type du referenciel
     * @param params   les parametres query de la requete http. 'ANY' est utiliser pour dire n'importe quelle colonne
     * @return la liste qui satisfont aux criteres
     */
    public List<ReferenceValue> findReference(String nameOrId, String refType, MultiValueMap<String, String> params) {
        authenticationService.setRoleForClient();
        List<ReferenceValue> list = repo.getRepository(nameOrId).referenceValue().findAllByReferenceType(refType, params);
        return list;
    }

    public String getReferenceValuesCsv(String applicationNameOrId, String referenceType, MultiValueMap<String, String> params) {
        Application application = getApplication(applicationNameOrId);
        ReferenceImporterContext referenceImporterContext = getReferenceImporterContext(application, referenceType);
        ReferenceValueRepository referenceValueRepository = repo.getRepository(applicationNameOrId).referenceValue();
        Stream<ImmutableList<String>> recordsStream = referenceValueRepository.findAllByReferenceType(referenceType, params).stream()
                .map(ReferenceValue::getRefValues)
                .map(referenceDatum -> {
                    ImmutableList<String> rowAsRecord = referenceImporterContext.getExpectedHeaders().stream()
                            .map(header -> referenceImporterContext.getCsvCellContent(referenceDatum, header))
                            .collect(ImmutableList.toImmutableList());
                    return rowAsRecord;
                });
        ImmutableSet<String> headers = referenceImporterContext.getExpectedHeaders();
        CSVFormat csvFormat = CSVFormat.DEFAULT
                .withDelimiter(referenceImporterContext.getCsvSeparator())
                .withSkipHeaderRecord();
        StringWriter out = new StringWriter();
        try {
            CSVPrinter csvPrinter = new CSVPrinter(out, csvFormat);
            csvPrinter.printRecord(headers);
            recordsStream.forEach(record -> {
                try {
                    csvPrinter.printRecord(record);
                } catch (IOException e) {
                    throw new OreSiTechnicalException("erreur lors de la génération du fichier CSV", e);
                }
            });
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

    public boolean removeFile(String name, UUID id) {
        authenticationService.setRoleForClient();
        BinaryFile binaryFile = repo.getRepository(name).binaryFile().findById(id);
        if (binaryFile.getParams() != null && binaryFile.getParams().published) {
            Application app = getApplication(binaryFile.getApplication().toString());
            unPublishVersions(
                    app,
                    Set.of(binaryFile)
            );
        }
        boolean deleted = repo.getRepository(name).binaryFile().delete(id);
        return deleted;
    }

    public ConfigurationParsingResult validateConfiguration(MultipartFile file) throws IOException {
        authenticationService.setRoleForClient();
        if (file.getOriginalFilename().matches(".zip")) {
            return applicationConfigurationService.unzipConfiguration(file);
        }
        return applicationConfigurationService.parseConfigurationBytes(file.getBytes());
    }

    public Map<String, Map<String, Map<String, String>>> getEntitiesTranslation(String nameOrId, Locale locale, String datatype, Map<String, Map<String, LineChecker>> checkedFormatVariableComponents) {
        Application application = getApplication(nameOrId);
        return Optional.ofNullable(application)
                .map(a -> a.getConfiguration())
                .map(c -> c.getInternationalization())
                .map(i -> i.getReferences())
                .map(r -> {
                    Map<String, Map<String, String>> internationalizedReferences = r.entrySet()
                            .stream()
                            .filter(e -> e.getValue().getInternationalizedColumns() != null)
                            .collect(Collectors.toMap(
                                    e -> e.getKey(),
                                    e -> e.getValue().getInternationalizedColumns().entrySet()
                                            .stream()
                                            .collect(Collectors.toMap(i -> i.getKey(), i -> i.getValue().get(locale)))
                            ));
                    List<String> neddedReferenceTranslation = checkedFormatVariableComponents.entrySet().stream()
                            .filter(e -> "ReferenceLineChecker".equals(e.getKey()))
                            .map(e -> e.getValue().values()
                                    .stream()
                                    .map(l -> ((ReferenceLineChecker) l).getRefType())
                                    .collect(Collectors.toList())
                            )
                            .flatMap(List::stream)
                            .collect(Collectors.toList());
                    Map<String, Map<String, Map<String, String>>> collect = internationalizedReferences.entrySet()
                            .stream()
                            .filter(e -> neddedReferenceTranslation.contains(e.getKey()))
                            .collect(Collectors.toMap(
                                            e -> e.getKey(),
                                            e -> {
                                                String collectingKeys = e.getValue().entrySet()
                                                        .stream()
                                                        .map(i -> String.format("%s,%s", i.getKey(), i.getValue()))
                                                        .collect(Collectors.joining(",")
                                                        );
                                                List<List<String>> referenceTranslations = repo.getRepository(application).referenceValue().findReferenceValue(
                                                        e.getKey(),
                                                        collectingKeys
                                                );
                                                Map<String, Map<String, String>> translationsMap = new HashMap();
                                                referenceTranslations.stream()
                                                        .forEach(list -> {
                                                            String[] collectingKey = collectingKeys.split(",");
                                                            for (int i = 0; i < collectingKey.length; i = i + 2) {
                                                                if (list.size() > i && list.get(i) != null && list.get(i + 1) != null) {
                                                                    translationsMap.
                                                                            computeIfAbsent(collectingKey[i], k -> new HashMap<>())
                                                                            .put(list.get(i), list.get(i + 1));
                                                                }
                                                            }
                                                        });
                                                return translationsMap;
                                            }
                                    )
                            );
                    return collect;

                })
                .orElseGet(HashMap::new);
    }

    @Value
    private static class RowWithData {
        int lineNumber;
        Datum datum;
    }

    @Value
    private static class ParsedCsvRow {
        int lineNumber;
        List<Map.Entry<String, String>> columns;
    }
}
