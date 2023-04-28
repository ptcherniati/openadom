package fr.inra.oresing.rest;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.common.primitives.Ints;
import fr.inra.oresing.OreSiTechnicalException;
import fr.inra.oresing.checker.*;
import fr.inra.oresing.groovy.Expression;
import fr.inra.oresing.groovy.GroovyContextHelper;
import fr.inra.oresing.groovy.StringGroovyExpression;
import fr.inra.oresing.model.*;
import fr.inra.oresing.model.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.model.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.model.chart.OreSiSynthesis;
import fr.inra.oresing.model.rightsrequest.RightsRequest;
import fr.inra.oresing.model.rightsrequest.RightsRequestInfos;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.persistence.flyway.MigrateService;
import fr.inra.oresing.persistence.roles.CurrentUserRoles;
import fr.inra.oresing.persistence.roles.OreSiRole;
import fr.inra.oresing.rest.exceptions.additionalfiles.BadAdditionalFileParamsSearchException;
import fr.inra.oresing.rest.exceptions.application.NoSuchApplicationException;
import fr.inra.oresing.rest.exceptions.authentication.NotApplicationCanDeleteRightsException;
import fr.inra.oresing.rest.exceptions.authentication.NotApplicationCreatorRightsException;
import fr.inra.oresing.rest.exceptions.configuration.BadApplicationConfigurationException;
import fr.inra.oresing.rest.validationcheckresults.DateValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.DefaultValidationCheckResult;
import fr.inra.oresing.rest.validationcheckresults.ReferenceValidationCheckResult;
import lombok.Getter;
import lombok.Setter;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.commons.lang3.StringUtils;
import org.assertj.core.util.Streams;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
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
import java.time.Duration;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
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

    public static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);
    public static final DateTimeFormatter DATE_FORMATTER_DDMMYYYY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    @Autowired
    private OreSiRepository repo;

    @Autowired
    private AuthenticationService authenticationService;


    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private UserRepository userRepository;

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

    @Autowired
    private ReferenceService referenceService;

    @Autowired
    private RightsRequestService rightsRequestService;

    @Autowired
    private BeanFactory beanFactory;

    @Autowired
    private AdditionalFileService additionalFileService;

    /**
     * @deprecated utiliser directement {@link Ltree#escapeToLabel(String)}
     */
    @Deprecated
    public static String escapeKeyComponent(String key) {
        return Ltree.escapeToLabel(key);
    }

    public UUID storeFile(Application app, MultipartFile file, String comment, BinaryFileDataset binaryFileDataset) throws IOException {
        authenticationService.setRoleForClient();
        // creation du fichier
        BinaryFile binaryFile = new BinaryFile();
        binaryFile.setComment(comment);
        binaryFile.setApplication(app.getId());
        binaryFile.setName(file.getOriginalFilename());
        binaryFile.setSize(file.getSize());
        binaryFile.setData(file.getBytes());
        BinaryFileInfos binaryFileInfos = new BinaryFileInfos();
        binaryFileInfos.binaryFiledataset = binaryFileDataset;
        binaryFile.setParams(binaryFileInfos);
        binaryFile.getParams().createuser = request.getRequestUserId();
        binaryFile.getParams().createdate = LocalDateTime.now().toString();
        UUID result = repo.getRepository(app).binaryFile().store(binaryFile);
        return result;
    }

    public UUID createApplication(String name, MultipartFile configurationFile, String comment) throws IOException, BadApplicationConfigurationException {
        final OreSiUser currentUser = getCurrentUser();
        final boolean canCreateApplication = currentUser.getAuthorizations().stream()
                .anyMatch(s -> name.matches(s));
        Application app = new Application();
        app.setName(name);
        app.setComment(comment);
        UUID result = changeApplicationConfiguration(app, configurationFile, this::initApplication);
        relationalService.createViews(app.getName());

        return result;
    }

    private OreSiUser getCurrentUser() {
        return userRepository.findById(request.getRequestClient().getId());
    }

    public Application initApplication(Application application) {
        final MigrateService migrateService = beanFactory.getBean(MigrateService.class);
        migrateService.setApplication(application);
        authenticationService.resetRole();
        migrateService.runFlywayUpdate();
        /*flyway.migrate();
        migrateService.completeMigration();*/
        authenticationService.setRoleForClient();
        repo.application().store(application);
        return application;

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
                        Map<String, Set<UUID>> refsLinkedToAddForVariable = new LinkedHashMap<>();
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
                                Set<UUID> referenceId = referenceCheckResult.getMatchedReferenceId();
                                refsLinkedToAddForVariable.put(component, referenceId);
                            }
                            variableValue.put(component, componentValue);
                        }
                        Map<String, Map<String, String>> variablesToAdd = Map.of(variable, variableValue);
                        Map<String, Map<String, Set<UUID>>> refsLinkedToAdd = Map.of(variable, refsLinkedToAddForVariable);
                        int migratedCount = dataRepository.migrate(dataType, dataGroup, variablesToAdd, refsLinkedToAdd);
                        if (log.isInfoEnabled()) {
                            log.info(migratedCount + " lignes migrées");
                        }
                    }
                }
            }

            validateStoredData(new DownloadDatasetQuery(nameOrId, dataType, null, null, null, null, null, null, app));
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
        final String applicationName = app.getName();
        final OreSiUser currentUser = getCurrentUser();
        authenticationService.setRoleForClient();
        final boolean canCreateApplication = authenticationService.hasRole(OreSiRole.applicationCreator()) && currentUser.getAuthorizations().stream()
                .anyMatch(s -> applicationName.matches(s));
        final boolean isSuperAdmin = authenticationService.isSuperAdmin();
        if (!(isSuperAdmin || canCreateApplication)) {
            throw new NotApplicationCreatorRightsException(applicationName, currentUser.getAuthorizations());
        } else if (!isSuperAdmin) {
            currentUser.getAuthorizations().stream()
                    .filter(s -> applicationName.matches(s))
                    .findAny()
                    .orElseThrow(() -> new NotApplicationCreatorRightsException(applicationName));
        }
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
        app.setAdditionalFile(new ArrayList<>(configuration.getAdditionalFiles().keySet()));
        app.setConfiguration(configuration);
        try {
            app = initApplication.apply(app);
            UUID confId = storeFile(app, configurationFile, app.getComment(), null);
            app.setConfigFile(confId);
            UUID appId = repo.application().store(app);
            return appId;
        } catch (BadSqlGrammarException bsge) {
            throw new NotApplicationCreatorRightsException(applicationName, currentUser.getAuthorizations());
        }
    }

    public UUID addReference(Application app, String refType, MultipartFile file) throws IOException {
        authenticationService.setRoleForClient();
        UUID fileId = storeFile(app, file, "", null);
        referenceService.addReference(app, refType, file, fileId);
        return fileId;
    }

    HierarchicalReferenceAsTree getHierarchicalReferenceAsTree(Application application, String lowestLevelReference) {
        ReferenceValueRepository referenceValueRepository = repo.getRepository(application).referenceValue();
        Configuration.CompositeReferenceDescription compositeReferenceDescription = application
                .getConfiguration()
                .getCompositeReferencesUsing(lowestLevelReference)
                .orElseThrow(() -> new OreSiTechnicalException("Can't find "));
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
        Application app = getApplication(nameOrId);
        Set<BinaryFile> filesToStore = new HashSet<>();
        final AuthorizationPublicationHelper authorizationPublicationHelper = new AuthorizationPublicationHelper(repo.getRepository(app.getId()), authorizationService, this);
        authorizationPublicationHelper.init(app, userRepository, dataType, params);

        BinaryFile storedFile = authorizationPublicationHelper.loadOrCreateFile(file, params, app, dataType);
        if (authorizationPublicationHelper.isRepository()) {
            if (params != null && !params.topublish) {
                if (storedFile.getParams() != null && storedFile.getParams().published) {
                    storedFile.getParams().published = false;
                    filesToStore.add(storedFile);
                    assert authorizationPublicationHelper.hasRightForPublishOrUnPublish(dataType);
                    unPublishVersions(app, filesToStore, dataType);
                }
                return storedFile.getId();
            } else if (params != null && params.getBinaryfiledataset() != null) {
                BinaryFile publishedVersion = getPublishedVersion(params, app);
                if (publishedVersion != null && publishedVersion.getParams().published) {
                    filesToStore.add(publishedVersion);
                    assert authorizationPublicationHelper.hasRightForPublishOrUnPublish(dataType);
                    unPublishVersions(app, filesToStore, dataType);
                }
            }
        }
        Configuration conf = app.getConfiguration();
        Configuration.DataTypeDescription dataTypeDescription = conf.getDataTypes().get(dataType);
        Configuration.FormatDescription formatDescription = dataTypeDescription.getFormat();
        InvalidDatasetContentException.checkErrorsIsEmpty(findPublishedVersion(nameOrId, dataType, params, filesToStore, true));
        final boolean isApplicationCreator = Optional.ofNullable(userRepository.getRolesForCurrentUser())
                .map(CurrentUserRoles::getMemberOf)
                .map(roles -> roles.stream().anyMatch(role -> OreSiRole.applicationCreator().getAsSqlRole().equals(role)))
                .orElse(false);
        final AuthorizationsResult authorizationsForUser = authorizationService.getAuthorizationsForUser(app.getName(), getCurrentUser().getLogin());

        assert authorizationPublicationHelper.hasRightForPublishOrUnPublish(dataType);
        publishVersion(dataType, authorizationPublicationHelper, errors, app, storedFile, dataTypeDescription, formatDescription, params == null ? null : params.binaryfiledataset);
        InvalidDatasetContentException.checkErrorsIsEmpty(errors);
        relationalService.onDataUpdate(app.getName());
        unPublishVersions(app, filesToStore, dataType);
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
            storedFile.getParams().publisheduser = request.getRequestUserId();
            storedFile.getParams().publisheddate = LocalDateTime.now().toString();
            repo.getRepository(app).binaryFile().store(storedFile);
            filesToStore.add(storedFile);
        }
    }

    private void unPublishVersions(Application app, Set<BinaryFile> filesToStore, String dataType) {
        filesToStore.stream()
                .forEach(f -> {
                    repo.getRepository(app).data().removeByFileId(f.getId());
                    f.getParams().published = false;
                    repo.getRepository(app).binaryFile().store(f);
                    buildSynthesis(app.getName(), dataType);
                });
        if (dataType != null) {
            buildSynthesis(app.getName(), dataType);
        }
    }

    private void publishVersion(String dataType,
                                AuthorizationPublicationHelper authorizationPublicationHelper, List<CsvRowValidationCheckResult> errors,
                                Application app,
                                BinaryFile storedFile,
                                Configuration.DataTypeDescription dataTypeDescription,
                                Configuration.FormatDescription formatDescription,
                                BinaryFileDataset binaryFileDataset) throws IOException {
        try (InputStream csv = new ByteArrayInputStream(storedFile.getData())) {
            PublishContext publishContext = new PublishContext(binaryFileDataset);
            CSVFormat csvFormat = CSVFormat.DEFAULT
                    .withDelimiter(formatDescription.getSeparator())
                    .withSkipHeaderRecord();
            CSVParser csvParser = CSVParser.parse(csv, Charsets.UTF_8, csvFormat);
            Iterator<CSVRecord> linesIterator = csvParser.iterator();

            Datum constantValues = new Datum();
            readPreHeader(formatDescription, constantValues, linesIterator, publishContext);

            ImmutableList<String> columns = readHeaderRow(linesIterator, publishContext);
            readPostHeader(formatDescription, columns, constantValues, linesIterator, publishContext);
            UniquenessBuilder uniquenessBuilder = new UniquenessBuilder(app, dataType);

            Stream<Data> dataStream = Streams.stream(csvParser)
                    .filter(row -> errors.size() < 50)
                    .map(buildCsvRecordToLineAsMapFn(columns, publishContext))
                    .flatMap(lineAsMap -> buildLineAsMapToRecordsFn(formatDescription).apply(lineAsMap).stream())
                    .map(buildMergeLineValuesAndConstantValuesFn(constantValues))
                    .map(buildReplaceMissingValuesByDefaultValuesFn(app, dataType))
                    .flatMap(buildLineValuesToEntityStreamFn(app, authorizationPublicationHelper, dataType, storedFile.getId(), uniquenessBuilder, errors, publishContext));
            AtomicLong lines = new AtomicLong();
            final Instant debut = Instant.now();
            final DataRepository dataRepository = repo.getRepository(app).data();
            final List<UUID> uuids = dataRepository
                    .storeAll(
                            dataStream
                                    .filter(Objects::nonNull)
                                    .peek(k -> {
                                        if (lines.incrementAndGet() % 1000 == 0) {
                                            log.debug(String.format("%d %d", lines.get(), Duration.between(debut, Instant.now()).getSeconds()));
                                        }
                                    })
                    );
            dataRepository.updateConstraintForeignData(uuids);
            errors.addAll(uniquenessBuilder.getErrors());
            InvalidDatasetContentException.checkErrorsIsEmpty(errors);
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
    private BinaryFile getPublishedVersion(FileOrUUID params, Application app) {
        return repo.getRepository(app).binaryFile().findPublishedVersions(params.getBinaryfiledataset()).orElse(null);
    }

    @Nullable
    private BinaryFile loadOrCreateFile(MultipartFile file, FileOrUUID params, Application app) {
        BinaryFile storedFile = Optional.ofNullable(params)
                .map(param -> param.getFileid())
                .map(uuid -> repo.getRepository(app).binaryFile().tryFindByIdWithData(uuid).orElse(null))
                .orElseGet(() -> {
                    UUID fileId = null;
                    try {
                        fileId = storeFile(app, file, "", null);
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
     * @param authorizationPublicationHelper
     * @param dataType
     * @param fileId
     * @param uniquenessBuilder
     * @return
     */
    private Function<RowWithData, Stream<Data>> buildLineValuesToEntityStreamFn(Application app, AuthorizationPublicationHelper authorizationPublicationHelper, String dataType, UUID fileId, UniquenessBuilder uniquenessBuilder, List<CsvRowValidationCheckResult> errors, PublishContext publishContext) {
        ImmutableSet<LineChecker> lineCheckers = checkerFactory.getLineCheckers(app, dataType);
        Configuration conf = app.getConfiguration();
        Configuration.DataTypeDescription dataTypeDescription = conf.getDataTypes().get(dataType);

        return buildRowWithDataStreamFunction(app, dataType, fileId, uniquenessBuilder, authorizationPublicationHelper, errors, lineCheckers, dataTypeDescription, publishContext);
    }

    /**
     * @param app
     * @param dataType
     * @param fileId
     * @param uniquenessBuilder
     * @param authorizationPublicationHelper
     * @param errors
     * @param lineCheckers
     * @param dataTypeDescription
     * @param publishContext
     * @return
     */
    private Function<RowWithData, Stream<Data>> buildRowWithDataStreamFunction(Application app,
                                                                               String dataType,
                                                                               UUID fileId,
                                                                               UniquenessBuilder uniquenessBuilder, AuthorizationPublicationHelper authorizationPublicationHelper, List<CsvRowValidationCheckResult> errors,
                                                                               ImmutableSet<LineChecker> lineCheckers,
                                                                               Configuration.DataTypeDescription dataTypeDescription,
                                                                               PublishContext publishContext) {
        final Configuration.AuthorizationDescription authorization = dataTypeDescription.getAuthorization();
        final boolean haveAuthorizationsDescription = authorization != null;

        final DateLineChecker timeScopeDateLineChecker = haveAuthorizationsDescription && authorization.getTimeScope() != null ?
                lineCheckers.stream()
                        .filter(lineChecker -> lineChecker instanceof DateLineChecker)
                        .map(lineChecker -> (DateLineChecker) lineChecker)
                        .filter(dateLineChecker -> dateLineChecker.getTarget().equals(authorization.getTimeScope()))
                        .collect(MoreCollectors.onlyElement())
                : null;

        return rowWithData -> {
            Datum datum = Datum.copyOf(rowWithData.getDatum());
            Map<VariableComponentKey, Set<UUID>> refsLinkedTo = new LinkedHashMap<>();
            Map<VariableComponentKey, DateValidationCheckResult> dateValidationCheckResultImmutableMap = new HashMap<>();
            List<CsvRowValidationCheckResult> rowErrors = new LinkedList<>();

            lineCheckers
                    .stream()
                    .filter(d -> rowErrors.size() < 50)
                    .forEach(lineChecker -> {
                        ValidationCheckResult validationCheckResult = lineChecker.check(datum);
                        if (validationCheckResult.isSuccess()) {
                            if (validationCheckResult instanceof DateValidationCheckResult) {
                                VariableComponentKey variableComponentKey = (VariableComponentKey) ((DateValidationCheckResult) validationCheckResult).getTarget();
                                dateValidationCheckResultImmutableMap.put(variableComponentKey, (DateValidationCheckResult) validationCheckResult);
                            }
                            if (validationCheckResult instanceof ReferenceValidationCheckResult) {
                                ReferenceLineCheckerConfiguration configuration = (ReferenceLineCheckerConfiguration) lineChecker.getConfiguration();
                                if (configuration.getTransformation().getGroovy() != null) {
                                    final Set<Ltree> matchedReferenceHierarchicalKeys = ((ReferenceValidationCheckResult) validationCheckResult).getMatchedReferenceHierarchicalKey();
                                    datum.put(
                                            (VariableComponentKey) ((ReferenceValidationCheckResult) validationCheckResult).getTarget(),
                                            matchedReferenceHierarchicalKeys
                                                    .stream()
                                                    .map(Ltree::getSql)
                                                    .collect(Collectors.joining(
                                                            ",",
                                                            matchedReferenceHierarchicalKeys.size() > 1 ? "[" : "",
                                                            matchedReferenceHierarchicalKeys.size() > 1 ? "]" : "")
                                                    )
                                    );
                                }
                                ReferenceValidationCheckResult referenceValidationCheckResult = (ReferenceValidationCheckResult) validationCheckResult;
                                VariableComponentKey variableComponentKey = (VariableComponentKey) referenceValidationCheckResult.getTarget();
                                Set<UUID> referenceId = referenceValidationCheckResult.getMatchedReferenceId();
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
            LocalDateTimeRange timeScope;
            if (timeScopeDateLineChecker != null) {
                String timeScopeValue = datum.get(authorization.getTimeScope());
                timeScope = LocalDateTimeRange.parse(timeScopeValue, timeScopeDateLineChecker);
            } else {
                timeScope = LocalDateTimeRange.always();
            }

            Map<String, Ltree> requiredAuthorizations = new LinkedHashMap<>();
            if (haveAuthorizationsDescription) {
                authorization.getAuthorizationScopes().forEach((authorizationScope, authorizationScopeDescription) -> {
                    VariableComponentKey variableComponentKey = authorizationScopeDescription.getVariableComponentKey();
                    String requiredAuthorizationsFromDatum = datum.get(variableComponentKey);
                    Ltree.checkSyntax(requiredAuthorizationsFromDatum);
                    requiredAuthorizations.put(authorizationScope, Ltree.fromSql(requiredAuthorizationsFromDatum));
                });
            }
            BinaryFileDataset binaryFileDataset = Optional.ofNullable(publishContext).map(PublishContext::getBinaryFileDataset).orElse(null);
            checkTimescopRangeInDatasetRange(timeScope, errors, binaryFileDataset, rowWithData.getLineNumber());
            checkrequiredAuthorizationsInDatasetRange(requiredAuthorizations, errors, binaryFileDataset, rowWithData.getLineNumber());
            // String rowId = Hashing.sha256().hashString(line.toString(), Charsets.UTF_8).toString();
            String rowId = UUID.randomUUID().toString();
            final List<String> uniquenessValues = uniquenessBuilder.test(datum, rowWithData.getLineNumber());
            if (uniquenessValues == null) {
                return Stream.of((Data) null);
            }
            LinkedHashMap<String, Configuration.DataGroupDescription> dataGroups;
            if (!haveAuthorizationsDescription) {
                dataGroups = new LinkedHashMap<>();
                final Configuration.DataGroupDescription dataGroupDescription = new Configuration.DataGroupDescription();
                dataGroupDescription.setData(dataTypeDescription.getData().keySet());
                dataGroups.put("_default_", dataGroupDescription);
            } else {
                dataGroups = authorization.getDataGroups();
            }

            Stream<Data> dataStream = dataGroups.entrySet().stream().map(entry -> {
                String dataGroup = entry.getKey();
                Configuration.DataGroupDescription dataGroupDescription = entry.getValue();

                Predicate<VariableComponentKey> includeInDataGroupPredicate = variableComponentKey -> dataGroupDescription.getData().contains(variableComponentKey.getVariable());
                Datum dataGroupValues = datum.filterOnVariable(includeInDataGroupPredicate);

                Map<String, Map<String, String>> toStore = new LinkedHashMap<>();
                Map<String, Map<String, Set<UUID>>> refsLinkedToToStore = new LinkedHashMap<>();
                for (Map.Entry<VariableComponentKey, String> entry2 : dataGroupValues.asMap().entrySet()) {
                    VariableComponentKey variableComponentKey = entry2.getKey();
                    String variable = variableComponentKey.getVariable();
                    String component = variableComponentKey.getComponent();
                    String value = entry2.getValue();
                    if (dateValidationCheckResultImmutableMap.containsKey(entry2.getKey())) {
                        final boolean isMany = dateValidationCheckResultImmutableMap.get(variableComponentKey).getLocalDateTime().size() > 1;
                        final String finalValue = value;
                        value = dateValidationCheckResultImmutableMap.get(variableComponentKey).getLocalDateTime().stream()
                                .map(date -> String.format("date:%s:%s", date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME), finalValue))
                                .collect(Collectors.joining(",", isMany ? "[" : "", isMany ? "]" : ""));
                    }
                    toStore.computeIfAbsent(variable, k -> new LinkedHashMap<>()).put(component, value);
                    refsLinkedToToStore.computeIfAbsent(variable, k -> new LinkedHashMap<>()).put(component, refsLinkedTo.get(variableComponentKey));
                }
                final Authorization authorization1 = new Authorization(List.of(dataGroup), requiredAuthorizations, timeScope);
                if (!authorizationPublicationHelper.isRepository() && !authorizationPublicationHelper.isApplicationCreator()) {
                    if (!authorizationPublicationHelper.hasRightForPublishOrUnPublish(authorization1)) {
                        errors.add(
                                new CsvRowValidationCheckResult(DefaultValidationCheckResult.error(
                                        "norightforpublish",
                                        ImmutableMap.of(
                                                "application", app.getName(),
                                                "dataType", dataType,
                                                "lineNumber", rowWithData.getLineNumber()
                                        )
                                ),
                                        rowWithData.getLineNumber())
                        );
                    }
                }
                Data e = new Data();
                e.setBinaryFile(fileId);
                e.setDataType(dataType);
                e.setRowId(rowId);
                e.setAuthorization(authorization1);
                e.setApplication(app.getId());
                e.setRefsLinkedTo(refsLinkedToToStore);
                e.setUniqueness(uniquenessValues);
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
                                    "to", DATE_FORMATTER_DDMMYYYY.format(to),
                                    "value", DATE_FORMATTER_DDMMYYYY.format(timeScope.getRange().lowerEndpoint())
                            )
                    ),
                            rowNumber)
            );
        }

    }


    private void checkrequiredAuthorizationsInDatasetRange(Map<String, Ltree> requiredAuthorizations,
                                                           List<CsvRowValidationCheckResult> errors,
                                                           BinaryFileDataset binaryFileDataset,
                                                           int rowNumber) {
        if (binaryFileDataset == null) {
            return;
        }
        binaryFileDataset.getRequiredAuthorizations().entrySet()
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
    private Function<RowWithData, RowWithData> buildReplaceMissingValuesByDefaultValuesFn(Application app, String dataType) {
        return rowWithData -> {
            ComputedValuesContext computedValuesContext = getComputedValuesContext(app, dataType, rowWithData.publishContext);
            ImmutableMap<VariableComponentKey, Function<Datum, String>> defaultValueFns = computedValuesContext.getDefaultValueFns();
            ImmutableSet<VariableComponentKey> replaceEnabled = computedValuesContext.getReplaceEnabled();
            Map<VariableComponentKey, String> rowWithDefaults = new LinkedHashMap<>();
            Map<VariableComponentKey, String> rowWithValues = new LinkedHashMap<>(rowWithData.getDatum().asMap());
            defaultValueFns.entrySet()
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
            return new RowWithData(rowWithData.getLineNumber(), new Datum(ImmutableMap.copyOf(rowWithDefaults)), rowWithData.publishContext);
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
            return new RowWithData(rowWithData.getLineNumber(), datum, rowWithData.publishContext);
        };
    }

    /**
     * Build the function that Dispatch ParsedCsvRow into RowWithData when there are not repeatedColumns
     */
    private Function<ParsedCsvRow, ImmutableSet<RowWithData>> buildLineAsMapWhenNoRepeatedColumnsToRecordsFn(Configuration.FormatDescription formatDescription) {
        ImmutableSet<String> expectedHeaderColumns = formatDescription.getColumns().stream()
                .map(Configuration.ColumnBindingDescription::getHeader)
                .collect(ImmutableSet.toImmutableSet());
        ImmutableSet<String> mandatoryHeaderColumns = formatDescription.getColumns().stream()
                .filter(columnBindingDescription -> ColumnPresenceConstraint.MANDATORY.equals(columnBindingDescription.getPresenceConstraint()))
                .map(Configuration.ColumnBindingDescription::getHeader)
                .collect(ImmutableSet.toImmutableSet());
        boolean allowUnexpectedColumns = formatDescription.isAllowUnexpectedColumns();
        int headerLine = formatDescription.getHeaderLine();
        ImmutableMap<String, Configuration.ColumnBindingDescription> bindingPerHeader = Maps.uniqueIndex(formatDescription.getColumns(), Configuration.ColumnBindingDescription::getHeader);
        Function<ParsedCsvRow, ImmutableSet<RowWithData>> lineAsMapToRecordsFn = parsedCsvRow -> {
            List<Map.Entry<String, String>> line = parsedCsvRow.getColumns();
            ImmutableMultiset<String> actualHeaderColumns = line.stream()
                    .map(Map.Entry::getKey)
                    .collect(ImmutableMultiset.toImmutableMultiset());
            InvalidDatasetContentException.checkHeader(expectedHeaderColumns, mandatoryHeaderColumns, actualHeaderColumns, headerLine, allowUnexpectedColumns);
            Map<VariableComponentKey, String> record = new LinkedHashMap<>();
            for (Map.Entry<String, String> entry : line) {
                String header = entry.getKey();
                String value = entry.getValue();
                Optional.ofNullable(bindingPerHeader.get(header))
                        .map(Configuration.ColumnBindingDescription::getBoundTo)
                        .ifPresent(boundTo -> record.put(boundTo, value));
            }
            return ImmutableSet.of(new RowWithData(parsedCsvRow.getLineNumber(), new Datum(record), parsedCsvRow.publishContext));
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
                    records.add(new RowWithData(parsedCsvRow.getLineNumber(), datum, parsedCsvRow.publishContext));

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
     * @param publishContext
     * @return
     */
    private Function<CSVRecord, ParsedCsvRow> buildCsvRecordToLineAsMapFn(ImmutableList<String> columns, PublishContext publishContext) {
        return line -> {
            int lineNumber = Ints.checkedCast(line.getRecordNumber());
            publishContext.setCurrentRowNumber(lineNumber);
            Iterator<String> currentHeader = columns.iterator();
            List<Map.Entry<String, String>> record = new LinkedList<>();
            List<String> currentRow = new LinkedList<>();
            line.forEach(value -> {
                value = value.trim();
                currentRow.add(value);
                String header = currentHeader.next();
                record.add(Map.entry(header.strip(), value));
            });
            publishContext.setCurrentRow(currentRow);
            return new ParsedCsvRow(lineNumber, record, publishContext);
        };
    }

    /**
     * read the header cartridge of the file to extract some constants values.
     *
     * @param formatDescription
     * @param constantValues
     * @param linesIterator
     */
    private void readPreHeader(Configuration.FormatDescription formatDescription, Datum constantValues, Iterator<CSVRecord> linesIterator, PublishContext publishContext) {
        ImmutableSetMultimap<Integer, Configuration.HeaderConstantDescription> perRowNumberConstants =
                formatDescription.getConstants().stream()
                        .collect(ImmutableSetMultimap.toImmutableSetMultimap(Configuration.HeaderConstantDescription::getRowNumber, Function.identity()));

        for (int lineNumber = 1; lineNumber < formatDescription.getHeaderLine(); lineNumber++) {
            CSVRecord row = linesIterator.next();
            List preHeaderLine = new LinkedList<>();
            ImmutableSet<Configuration.HeaderConstantDescription> constantDescriptions = perRowNumberConstants.get(lineNumber);
            constantDescriptions.forEach(constant -> {
                int columnNumber = constant.getColumnNumber();
                String value = (row.size() >= columnNumber ? row.get(columnNumber - 1) : "".trim());
                preHeaderLine.add(value);
                VariableComponentKey boundTo = constant.getBoundTo();
                constantValues.put(boundTo, value);
            });
            publishContext.getPreHeaderRow().add(preHeaderLine);
        }
    }

    /**
     * read the header row and return the columns
     *
     * @param linesIterator
     * @param publishContext
     * @return
     */
    private ImmutableList<String> readHeaderRow(Iterator<CSVRecord> linesIterator, PublishContext publishContext) {
        CSVRecord headerRow = linesIterator.next();
        final ImmutableList<String> headers = Streams.stream(headerRow)
                .map(String::trim)
                .collect(ImmutableList.toImmutableList());
        publishContext.setHeaderRow(headers);
        return headers;
    }

    /**
     * read some post header as example line, units, min and max values for each columns
     *
     * @param formatDescription
     * @param linesIterator
     * @param publishContext
     */
    private void readPostHeader(Configuration.FormatDescription formatDescription, ImmutableList<String> headerRow, Datum constantValues, Iterator<CSVRecord> linesIterator, PublishContext publishContext) {
        ImmutableSetMultimap<Integer, Configuration.HeaderConstantDescription> perRowNumberConstants =
                formatDescription.getConstants().stream()
                        .collect(
                                ImmutableSetMultimap.toImmutableSetMultimap(
                                        Configuration.HeaderConstantDescription::getRowNumber,
                                        Function.identity()
                                )
                        );
        for (int lineNumber = formatDescription.getHeaderLine() + 1; lineNumber < formatDescription.getFirstRowLine(); lineNumber++) {
            CSVRecord row = linesIterator.next();
            List postHeaderLine = new LinkedList<>();
            publishContext.getPostHeaderRow().add(row.getParser().getHeaderNames());
            ImmutableSet<Configuration.HeaderConstantDescription> constantDescriptions = perRowNumberConstants.get(lineNumber);
            constantDescriptions.forEach(constant -> {
                int columnNumber = constant.getColumnNumber(headerRow);
                String value = (row.size() >= columnNumber ? row.get(columnNumber - 1) : "").trim();
                postHeaderLine.add(value);
                VariableComponentKey boundTo = constant.getBoundTo();
                constantValues.put(boundTo, value);
            });
            publishContext.getPostHeaderRow().add(postHeaderLine);
        }
    }

    private ComputedValuesContext getComputedValuesContext(Application app, String dataType, PublishContext publishContext) {
        Configuration.DataTypeDescription dataTypeDescription = app.getConfiguration().getDataTypes().get(dataType);
        ImmutableMap.Builder<VariableComponentKey, Function<Datum, String>> defaultValueFnsBuilder = ImmutableMap.builder();
        ImmutableSet.Builder<VariableComponentKey> replaceEnabledBuilder = ImmutableSet.builder();
        Set<VariableComponentKey> variableComponentsFromRepository = new LinkedHashSet<>();
        final Map<String, Ltree> requiredAuthorizations = Optional.ofNullable(publishContext).map(PublishContext::getBinaryFileDataset).map(BinaryFileDataset::getRequiredAuthorizations).orElse(null);
        if (requiredAuthorizations != null) {
            for (Map.Entry<String, Ltree> entry : requiredAuthorizations.entrySet()) {
                Configuration.AuthorizationScopeDescription authorizationScopeDescription = dataTypeDescription.getAuthorization().getAuthorizationScopes().get(entry.getKey());
                VariableComponentKey variableComponentKey = authorizationScopeDescription.getVariableComponentKey();
                Ltree value = entry.getValue();
                defaultValueFnsBuilder.put(variableComponentKey, datum -> value.getSql());
                variableComponentsFromRepository.add(variableComponentKey);
            }
        }
        for (Map.Entry<String, Configuration.VariableDescription> variableEntry : dataTypeDescription.getData().entrySet()) {
            String variable = variableEntry.getKey();
            Configuration.VariableDescription variableDescription = variableEntry.getValue();
            for (Map.Entry<String, Configuration.VariableComponentWithDefaultValueDescription> componentEntry : variableDescription.getComponents().entrySet()) {
                String component = componentEntry.getKey();
                Configuration.VariableComponentWithDefaultValueDescription componentDescription = componentEntry.getValue();
                VariableComponentKey variableComponentKey = new VariableComponentKey(variable, component);
                if (variableComponentsFromRepository.contains(variableComponentKey)) {
                    continue;
                }
                Optional.ofNullable(componentDescription)
                        .map(Configuration.VariableComponentWithDefaultValueDescription::getDefaultValue)
                        .map(defaultValueConfiguration -> getEvaluateGroovyWithContextFunction(app, defaultValueConfiguration, publishContext))
                        .ifPresent(computeDefaultValueFn -> defaultValueFnsBuilder.put(variableComponentKey, computeDefaultValueFn));
            }
            for (Map.Entry<String, Configuration.ComputedVariableComponentDescription> computedComponentEntry : variableDescription.getComputedComponents().entrySet()) {
                String component = computedComponentEntry.getKey();
                Configuration.ComputedVariableComponentDescription componentDescription = computedComponentEntry.getValue();
                VariableComponentKey variableComponentKey = new VariableComponentKey(variable, component);
                if (variableComponentsFromRepository.contains(variableComponentKey)) {
                    continue;
                }
                Configuration.GroovyConfiguration computation = Optional.ofNullable(componentDescription)
                        .map(Configuration.ComputedVariableComponentDescription::getComputation)
                        .orElseThrow();
                Function<Datum, String> computeDefaultValueFn = getEvaluateGroovyWithContextFunction(app, computation, publishContext);
                defaultValueFnsBuilder.put(variableComponentKey, computeDefaultValueFn);
                replaceEnabledBuilder.add(variableComponentKey);
            }
        }
        return new ComputedValuesContext(defaultValueFnsBuilder.build(), replaceEnabledBuilder.build());
    }

    private Function<Datum, String> getEvaluateGroovyWithContextFunction(Application app, Configuration.GroovyConfiguration computation, PublishContext publishContext) {
        ReferenceValueRepository referenceValueRepository = repo.getRepository(app).referenceValue();
        Expression<String> defaultValueExpression = StringGroovyExpression.forExpression(computation.getExpression());
        Set<String> configurationReferences = computation.getReferences();
        ImmutableMap<String, Object> contextForExpression = groovyContextHelper.getGroovyContextForReferences(referenceValueRepository, configurationReferences, publishContext);
        Preconditions.checkState(computation.getDatatypes().isEmpty(), "à ce stade, on ne gère pas la chargement de données");
        Function<Datum, String> computeDefaultValueFn = datum -> {
            ImmutableMap<String, Object> evaluationContext = ImmutableMap.<String, Object>builder()
                    .putAll(contextForExpression)
                    .putAll(datum.getEvaluationContext())
                    .build();
            String evaluate = defaultValueExpression.evaluate(evaluationContext);
            return evaluate;
        };
        return computeDefaultValueFn;
    }

    public byte[] getDataCsv(DownloadDatasetQuery downloadDatasetQuery, String nameOrId, String dataType, String locale) throws IOException {
        final Application application = getApplication(nameOrId);
        DownloadDatasetQuery downloadDatasetQueryCopy = DownloadDatasetQuery.buildDownloadDatasetQuery(downloadDatasetQuery, nameOrId, dataType, application);
        List<DataRow> datas = findData(downloadDatasetQueryCopy, nameOrId, dataType);
        return DataCsvBuilder.getDataCsvBuilder()
                .withApplication(application)
                .withDatatype(dataType)
                .withDownloadDatasetQuery(downloadDatasetQueryCopy)
                .withCheckerFactory(checkerFactory)
                .withReferenceService(referenceService)
                .onRepositories(repo)
                .addDatas(datas)
                .build();
    }

    public Map<String, Map<String, LineChecker>> getFormatChecked(String nameOrId, String references) {
        return checkerFactory.getLineCheckersReferences(getApplication(nameOrId), references)
                .stream().filter(c -> (c instanceof DateLineChecker) || (c instanceof IntegerChecker) || (c instanceof FloatChecker) || (c instanceof ReferenceLineChecker))
                .collect(
                        Collectors.groupingBy(
                                c -> c.getClass().getSimpleName(),
                                Collectors.toMap(
                                        c -> {
                                            ReferenceColumn vc;
                                            if (c instanceof DateLineChecker) {
                                                vc = (ReferenceColumn) ((DateLineChecker) c).getTarget();
                                            } else if (c instanceof IntegerChecker) {
                                                vc = (ReferenceColumn) ((IntegerChecker) c).getTarget();
                                            } else if (c instanceof FloatChecker) {
                                                vc = (ReferenceColumn) ((FloatChecker) c).getTarget();
                                            } else {
                                                vc = (ReferenceColumn) ((ReferenceLineChecker) c).getTarget();
                                                //System.out.println(vc);
                                            }
                                            return vc.asString();
                                        },
                                        c -> c
                                )
                        )
                );
    }

    public Map<String, Map<String, LineChecker>> getcheckedFormatVariableComponents(String nameOrId, String dataType) {
        return checkerFactory.getLineCheckers(getApplication(nameOrId), dataType)
                .stream()
                .filter(c -> (c instanceof DateLineChecker) || (c instanceof IntegerChecker) || (c instanceof FloatChecker) || (c instanceof ReferenceLineChecker))
                .collect(
                        Collectors.groupingBy(
                                c -> c.getClass().getSimpleName(),
                                Collectors.toMap(
                                        c -> {
                                            VariableComponentKey vc;
                                            if (c instanceof DateLineChecker) {
                                                vc = (VariableComponentKey) ((DateLineChecker) c).getTarget();
                                            } else if (c instanceof IntegerChecker) {
                                                vc = (VariableComponentKey) ((IntegerChecker) c).getTarget();
                                            } else if (c instanceof FloatChecker) {
                                                vc = (VariableComponentKey) ((FloatChecker) c).getTarget();
                                            } else {
                                                vc = (VariableComponentKey) ((ReferenceLineChecker) c).getTarget();
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

    public List<UUID> deleteData(DownloadDatasetQuery downloadDatasetQuery, String nameOrId, String dataType) {
        downloadDatasetQuery = DownloadDatasetQuery.buildDownloadDatasetQuery(downloadDatasetQuery, nameOrId, dataType, getApplication(nameOrId));
        authenticationService.setRoleForClient();
        String applicationNameOrId = downloadDatasetQuery.getApplicationNameOrId();
        Application app = getApplication(applicationNameOrId);
        List<UUID> data = repo.getRepository(app).data().deleteDataType(downloadDatasetQuery);
        return data;
    }

    public List<Application> getApplications(List<ApplicationInformation> filters) {
        authenticationService.setRoleForClient();
        List<Application> applicationForUser = repo.application().findAll();
        authenticationService.setRoleAdmin();
        List<Application> applicationForAdmin = repo.application().findAll();
        return applicationForAdmin.stream()
                .map(application -> applicationForUser.stream()
                        .filter(app -> app.getId().equals(application.getId()))
                        .findAny()
                        .orElse(application.applicationAccordingToRights()))
                .collect(Collectors.toList());
    }

    public Application getApplication(String nameOrId) {
        authenticationService.setRoleForClient();
        return repo.application().findApplication(nameOrId);
    }

    public Application getApplicationOrApplicationAccordingToRights(String nameOrId) {
        authenticationService.setRoleForClient();
        try {
            return repo.application().findApplication(nameOrId);
        } catch (NoSuchApplicationException e) {
            authenticationService.setRoleAdmin();
            return repo.application().findApplication(nameOrId).applicationAccordingToRights();

        }
    }

    public AuthorizationsForUserResult getAuthorizationsReferencesRights(String nameOrId, String userID, Set<String> references) {
        final AuthorizationsReferencesResult referencesAuthorizationsForUser = authorizationService.getReferencesAuthorizationsForUser(nameOrId, userID);
        Map<String, Map<AuthorizationsForUserResult.Roles, Boolean>> authorizations = new HashMap<>();
        final Map<AuthorizationsForUserResult.Roles, Boolean> roles = AuthorizationsForUserResult.DEFAULT_REFERENCE_ROLES;
        references.stream().forEach(ref -> {
            final HashMap<AuthorizationsForUserResult.Roles, Boolean> r = new HashMap<>(roles);
            if (referencesAuthorizationsForUser.getIsAdministrator()) {
                r.put(AuthorizationsForUserResult.Roles.ADMIN, true);
            }
            authorizations.put(
                    ref,
                    r);
        });
        referencesAuthorizationsForUser.getAuthorizationResults().entrySet().stream()
                .forEach(entry -> {
                    final List<String> referencesList = entry.getValue();
                    switch (entry.getKey()) {
                        case manage:
                            referencesList.stream().forEach(ref -> {
                                final Map<AuthorizationsForUserResult.Roles, Boolean> roleForRef = authorizations.get(ref);
                                roleForRef.put(AuthorizationsForUserResult.Roles.UPLOAD, true);
                                roleForRef.put(AuthorizationsForUserResult.Roles.DOWNLOAD, true);
                                roleForRef.put(AuthorizationsForUserResult.Roles.DELETE, true);
                                authorizations.put(ref, roleForRef);
                            });
                            break;
                        case admin:
                            referencesList.stream().forEach(ref -> {
                                final Map<AuthorizationsForUserResult.Roles, Boolean> roleForRef = authorizations.get(ref);
                                roleForRef.put(AuthorizationsForUserResult.Roles.UPLOAD, true);
                                roleForRef.put(AuthorizationsForUserResult.Roles.DOWNLOAD, true);
                                roleForRef.put(AuthorizationsForUserResult.Roles.DELETE, true);
                                roleForRef.put(AuthorizationsForUserResult.Roles.ADMIN, true);
                                authorizations.put(ref, roleForRef);
                            });
                            break;
                    }
                });
        return new AuthorizationsForUserResult(authorizations, nameOrId, referencesAuthorizationsForUser.getIsAdministrator(), userID);
    }

    public Map<String, Map<AuthorizationsForUserResult.Roles, Boolean>> getAuthorizationsDatatypesRights(String nameOrId, Set<String> datatypes) {
        return datatypes.stream()
                .map(dty -> getAuthorizationsDatatypesRights(nameOrId, dty, request.getRequestUserId().toString()))
                .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
    }

    private Map.Entry<String, Map<AuthorizationsForUserResult.Roles, Boolean>> getAuthorizationsDatatypesRights(String nameOrId, String datatype, String userId) {
        final AuthorizationsResult authorizationsForUser = authorizationService.getAuthorizationsForUser(nameOrId, userId);
        Map<AuthorizationsForUserResult.Roles, Boolean> roleForDatatype = new HashMap<>();
        final Set<OperationType> rolesSetted = authorizationsForUser.getAuthorizationResults().getOrDefault(datatype, new HashMap<>()).keySet();
        final boolean isAdmin = authorizationsForUser.getIsAdministrator() || rolesSetted.contains(OperationType.admin);
        roleForDatatype.put(AuthorizationsForUserResult.Roles.ADMIN, isAdmin);
        roleForDatatype.put(AuthorizationsForUserResult.Roles.UPLOAD, isAdmin || rolesSetted.contains(OperationType.depot) || rolesSetted.contains(OperationType.publication));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.DELETE, authorizationsForUser.getIsAdministrator() || rolesSetted.contains(OperationType.delete));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.DOWNLOAD, authorizationsForUser.getIsAdministrator() || rolesSetted.contains(OperationType.extraction) || rolesSetted.contains(OperationType.publication));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.READ, authorizationsForUser.getIsAdministrator() || rolesSetted.contains(OperationType.extraction) || rolesSetted.contains(OperationType.publication));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.PUBLICATION, authorizationsForUser.getIsAdministrator() || rolesSetted.contains(OperationType.publication));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.ANY, authorizationsForUser.getIsAdministrator() || !rolesSetted.isEmpty());
        new AuthorizationsForUserResult(Map.of(datatype, roleForDatatype), nameOrId, authorizationsForUser.getIsAdministrator(), userId);

        return new AbstractMap.SimpleEntry<String, Map<AuthorizationsForUserResult.Roles, Boolean>>(datatype, roleForDatatype);
    }

    public Optional<Application> tryFindApplication(String nameOrId) {
        authenticationService.setRoleForClient();
        return repo.application().tryFindApplication(nameOrId);
    }

    public List<ReferenceValue> findReference(String nameOrId, String refType, MultiValueMap<String, String> params) {
        final Application applicationOrApplicationAccordingToRights = getApplicationOrApplicationAccordingToRights(nameOrId);
        return referenceService.findReferenceAccordingToRights(applicationOrApplicationAccordingToRights, refType, params);
    }

    public List<UUID> deleteReference(String nameOrId, String refType, MultiValueMap<String, String> params) {
        final Application applicationOrApplicationAccordingToRights = getApplicationOrApplicationAccordingToRights(nameOrId);
        return referenceService.deleteReferenceAccordingToRights(applicationOrApplicationAccordingToRights, refType, params);
    }

    public GetAdditionalFilesResult findAdditionalFile(String nameOrId, AdditionalFilesInfos additionalFilesInfos) {
        final Application application = getApplication(nameOrId);
        Configuration.AdditionalFileDescription description = Optional.ofNullable(application.getConfiguration().getAdditionalFiles())
                .map(map -> map.get(additionalFilesInfos.getFiletype()))
                .orElseGet(Configuration.AdditionalFileDescription::new);
        final List<AdditionalBinaryFile> additionalFiles = additionalFileService.findAdditionalFile(application, additionalFilesInfos);
        final List<AdditionalBinaryFileResult> additionalBinaryFileResults = additionalFiles.stream()
                .map(af -> getAdditionalBinaryFileResult(af, application))
                .collect(Collectors.toList());
        final ImmutableSortedSet<GetGrantableResult.User> grantableUsers = authorizationService.getGrantableUsers();
        final List<String> fileNamesForFiletype = repo.getRepository(application).additionalBinaryFile().getFileNamesForFiletype(additionalFilesInfos.getFiletype());
        return new GetAdditionalFilesResult(grantableUsers, additionalFilesInfos.getFiletype(), additionalBinaryFileResults, description, fileNamesForFiletype);
    }

    public byte[] getReferenceValuesCsv(String applicationNameOrId, String referenceType, MultiValueMap<String, String> params) {
        return referenceService.getReferenceValuesCsv(applicationNameOrId, referenceType, params);
    }

    public Optional<BinaryFile> getFile(String name, UUID id) {
        authenticationService.setRoleForClient();
        Optional<BinaryFile> optionalBinaryFile = repo.getRepository(name).binaryFile().tryFindById(id);
        return optionalBinaryFile;
    }

    public Optional<BinaryFile> getFileWithData(String name, UUID id) {
        authenticationService.setRoleForClient();
        Optional<BinaryFile> optionalBinaryFile = repo.getRepository(name).binaryFile().tryFindByIdWithData(id);
        return optionalBinaryFile;
    }

    public boolean removeFile(String name, UUID id) {
        authenticationService.setRoleForClient();
        BinaryFile binaryFile = repo.getRepository(name).binaryFile().findById(id);
        if (binaryFile.getParams() != null && binaryFile.getParams().published) {
            Application app = getApplication(binaryFile.getApplication().toString());
            unPublishVersions(
                    app,
                    Set.of(binaryFile),
                    Optional.of(binaryFile)
                            .map(BinaryFile::getParams)
                            .map(BinaryFileInfos::getBinaryFiledataset)
                            .map(BinaryFileDataset::getDatatype)
                            .orElse(null)
            );
            try {
                boolean deleted = repo.getRepository(name).binaryFile().delete(id);
            } catch (DataIntegrityViolationException dive) {
                throw new NotApplicationCanDeleteRightsException(app.getName(), binaryFile.getParams().getBinaryFiledataset().getDatatype());
            }
        }
        return repo.getRepository(name).binaryFile().delete(id);
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

    public int deleteSynthesis(String nameOrId, String dataType, String variable) {
        Application application = getApplication(nameOrId);
        return repo.getRepository(application).synthesisRepository().removeSynthesisByApplicationDatatypeAndVariable(application.getId(), dataType, variable);
    }

    public int deleteSynthesis(String nameOrId, String dataType) {
        Application application = getApplication(nameOrId);
        return repo.getRepository(application).synthesisRepository().removeSynthesisByApplicationDatatype(application.getId(), dataType);
    }

    public Map<String, List<OreSiSynthesis>> buildSynthesis(String nameOrId, String dataType) {
        return buildSynthesis(nameOrId, dataType, null);
    }

    public Map<String, List<OreSiSynthesis>> buildSynthesis(String nameOrId, String dataType, String variable) {
        Application application = getApplication(nameOrId);
        final DataSynthesisRepository repo = this.repo.getRepository(application).synthesisRepository();
        if (variable == null) {
            repo.removeSynthesisByApplicationDatatype(application.getId(), dataType);
        } else {
            repo.removeSynthesisByApplicationDatatypeAndVariable(application.getId(), dataType, variable);
        }
        boolean hasChartDescription = application.getConfiguration().getDataTypes().get(dataType).getData().entrySet().stream()
                .filter(entry -> Strings.isNullOrEmpty(variable) || entry.getKey().equals(variable))
                .anyMatch(entry -> entry.getValue().getChartDescription() != null);
        String sql;
        if (hasChartDescription) {
            sql = application.getConfiguration().getDataTypes().get(dataType).getData().entrySet().stream()
                    .filter(entry -> Strings.isNullOrEmpty(variable) || entry.getKey().equals(variable))
                    .filter(entry -> entry.getValue().getChartDescription() != null)
                    .map(entry -> entry.getValue().getChartDescription().toSQL(entry.getKey(), dataType))
                    .collect(Collectors.joining(", \n"));
        } else {
            sql = Configuration.Chart.toSQL(dataType);
        }
        List<OreSiSynthesis> oreSiSynthesisList = new LinkedList<>();
        final List<OreSiSynthesis> oreSiSynthesis = repo.buildSynthesis(sql, hasChartDescription);
        repo.storeAll(oreSiSynthesis.stream());

        return !hasChartDescription ? Map.of("__NO-CHART", oreSiSynthesis) : oreSiSynthesis.stream().collect(Collectors.groupingBy(OreSiSynthesis::getVariable));
    }

    public Map<String, List<OreSiSynthesis>> getSynthesis(String nameOrId, String dataType) {
        Application application = getApplication(nameOrId);
        return repo.getRepository(application).synthesisRepository().selectSynthesisDatatype(application.getId(), dataType).stream()
                .collect(Collectors.groupingBy(OreSiSynthesis::getVariable));
    }

    public Map<String, List<OreSiSynthesis>> getSynthesis(String nameOrId, String dataType, String variable) {
        Application application = getApplication(nameOrId);
        return repo.getRepository(application).synthesisRepository().selectSynthesisDatatypeAndVariable(application.getId(), dataType, variable).stream()
                .collect(Collectors.groupingBy(OreSiSynthesis::getVariable));
    }

    public List<ApplicationResult.ReferenceSynthesis> getReferenceSynthesis(Application application) {
        return repo.getRepository(application).referenceValue().buildReferenceSynthesis();
    }

    public Map<Ltree, List<ReferenceValue>> getReferenceDisplaysById(Application application, Set<String> listOfDataIds) {
        return repo.getRepository(application).referenceValue().getReferenceDisplaysById(listOfDataIds);
    }

    public GetRightsRequestResult findRightsRequest(String nameOrId, RightsRequestInfos rightsRequestInfos) {
        final Application application = getApplicationOrApplicationAccordingToRights(nameOrId);
        Configuration.RightsRequestDescription description = application.getConfiguration().getRightsRequest();
        final List<RightsRequest> rightsRequests = rightsRequestService.findRightsRequests(application, rightsRequestInfos);
        final List<RightsRequestResult> rightsRequestResult = rightsRequests.stream()
                .map(rightsRequest -> getRightsRequestResult(rightsRequest, application))
                .collect(Collectors.toList());
        final ImmutableSortedSet<GetGrantableResult.User> grantableUsers = authorizationService.getGrantableUsers();
        return new GetRightsRequestResult(grantableUsers, rightsRequestResult, description);


    }

    private RightsRequestResult getRightsRequestResult(RightsRequest rightsRequest, Application application) {
        List<String> attributes = Optional.ofNullable(application.getConfiguration())
                .map(Configuration::getRequiredAuthorizationsAttributes)
                .map(rca -> rca.stream().collect(Collectors.toList()))
                .orElseGet(List::of);
        final Map<String, Map<OperationType, Map<String, List<AuthorizationParsed>>>> authorizationByDatatypeAndPath = new HashMap<>();
        final Map<String, Map<OperationType, List<AuthorizationParsed>>> authorizationsparsed = new HashMap<>();
        authorizationService.authorizationsToParsedAuthorizations(List.of(rightsRequest.getRightsRequest()), authorizationsparsed, authorizationByDatatypeAndPath, attributes);
        return new RightsRequestResult(rightsRequest, authorizationsparsed, authorizationByDatatypeAndPath);
    }

    private AdditionalBinaryFileResult getAdditionalBinaryFileResult(AdditionalBinaryFile additionalBinaryFile, Application application) {
        List<String> attributes = Optional.ofNullable(application.getConfiguration())
                .map(Configuration::getRequiredAuthorizationsAttributes)
                .map(rca -> rca.stream().collect(Collectors.toList()))
                .orElseGet(List::of);
        final Map<String, Map<OperationType, Map<String, List<AuthorizationParsed>>>> authorizationByDatatypeAndPath = new HashMap<>();
        final Map<String, Map<OperationType, List<AuthorizationParsed>>> authorizationsparsed = new HashMap<>();
        authorizationService.authorizationsToParsedAuthorizations(additionalBinaryFile.getAssociates(), authorizationsparsed, authorizationByDatatypeAndPath, attributes);
        return new AdditionalBinaryFileResult(additionalBinaryFile, authorizationsparsed, authorizationByDatatypeAndPath);
    }

    public UUID createOrUpdate(CreateRightsRequestRequest createRightsRequestRequest, String nameOrId) {
        authenticationService.setRoleForClient();
        Application application = getApplicationOrApplicationAccordingToRights(nameOrId);

        final RightsRequest rightsRequest = Optional.of(createRightsRequestRequest)
                .map(CreateRightsRequestRequest::getId)
                .map(id -> repo.getRepository(application).rightsRequestRepository().findById(id))
                .orElseGet(RightsRequest::new);
        rightsRequest.setRightsRequestForm(createRightsRequestRequest.getFields());
        rightsRequest.setApplication(application.getId());
        rightsRequest.setComment(createRightsRequestRequest.getComment());
        rightsRequest.setSetted(createRightsRequestRequest.isSetted());
        rightsRequest.setId(rightsRequest.getId() == null ? UUID.randomUUID() : rightsRequest.getId());
        final OreSiAuthorization authorizations = Optional.ofNullable(createRightsRequestRequest)
                .map(CreateRightsRequestRequest::getRightsRequest)
                .map(authorization -> {
                    final OreSiAuthorization oreSiAuthorization = new OreSiAuthorization();
                    oreSiAuthorization.setId(rightsRequest.getId());
                    oreSiAuthorization.setApplication(application.getId());
                    oreSiAuthorization.setAuthorizations(authorization.getAuthorizations());
                    return oreSiAuthorization;
                })
                .orElse(null);
        rightsRequest.setRightsRequest(authorizations);
        rightsRequest.setUser(rightsRequest.getUser() == null ? request.getRequestUserId() : rightsRequest.getUser());
        rightsRequest.getRightsRequest().setOreSiUsers(Set.of(rightsRequest.getUser()));
        authenticationService.setRoleForClient();
        final UUID store = repo.getRepository(application).rightsRequestRepository().store(rightsRequest);
        return store;
    }

    public Boolean isAdmnistrator(Application application) {
        final UUID requestUserId = request.getRequestUserId();
        return authorizationService.isAdministratorForUser(application, requestUserId);
    }

    public byte[] getAdditionalFilesNamesZip(String nameOrId, AdditionalFilesInfos additionalFilesInfos) throws IOException {
        Application application = getApplication(nameOrId);
        final AdditionalFileParamsParsingResult additionalFileParamsParsingResult = getAdditionalFileSearchHelper(nameOrId, additionalFilesInfos);
        BadAdditionalFileParamsSearchException.check(additionalFileParamsParsingResult);
        AdditionalFileSearchHelper additionalFileSearchHelper = additionalFileParamsParsingResult.getResult();
        try {
            List<AdditionalBinaryFile> additionalBinaryFiles = repo
                    .getRepository(application).additionalBinaryFile()
                    .findByCriteria(additionalFileSearchHelper);
            return additionalFileSearchHelper.zip(additionalBinaryFiles);
        } catch (DataIntegrityViolationException e) {
            return new byte[0];
        }
    }

    public List<UUID> deleteAdditionalFiles(String nameOrId, AdditionalFilesInfos additionalFilesInfos) throws IOException {
        Application application = getApplication(nameOrId);
        final AdditionalFileParamsParsingResult additionalFileParamsParsingResult = getAdditionalFileSearchHelper(nameOrId, additionalFilesInfos);
        BadAdditionalFileParamsSearchException.check(additionalFileParamsParsingResult);
        AdditionalFileSearchHelper additionalFileSearchHelper = additionalFileParamsParsingResult.getResult();
        try {
            List<UUID> deletedAdditionalBinaryFiles = repo
                    .getRepository(application).additionalBinaryFile()
                    .deleteByCriteria(additionalFileSearchHelper);
            return deletedAdditionalBinaryFiles;
        } catch (DataIntegrityViolationException e) {
            return null;
        }
    }

    public AdditionalFileParamsParsingResult getAdditionalFileSearchHelper(String nameOrId, AdditionalFilesInfos additionalFilesInfos) {
        Application application = getApplication(nameOrId);
        AdditionalFileParamsParsingResult.Builder builder = AdditionalFileParamsParsingResult.builder();
        for (Map.Entry<String, AdditionalFilesInfos.AdditionalFileInfos> entry : additionalFilesInfos.getAdditionalFilesInfos().entrySet()) {
            String additionalFileName = entry.getKey();
            final Configuration.AdditionalFileDescription additionalFileDescription = application.getConfiguration().getAdditionalFiles().get(additionalFileName);
            if (additionalFileDescription == null) {
                builder.unknownAdditionalFilename(additionalFileName, additionalFilesInfos.getAdditionalFilesInfos().keySet());
            } else {
                final AdditionalFilesInfos.AdditionalFileInfos value = entry.getValue();
                if (value != null && !CollectionUtils.isEmpty(value.getFieldFilters())) {
                    for (AdditionalFilesInfos.FieldFilters filter : value.getFieldFilters()) {
                        if (additionalFileDescription.getFormat().get(filter.field) == null) {
                            builder.unknownFieldAdditionalFilename(additionalFileName, filter.field, additionalFileDescription.getFormat().keySet());
                        }
                    }
                }
            }

        }
        final AdditionalFileParamsParsingResult build = builder.build(application, additionalFilesInfos);
        return build;
    }

    public UUID createOrUpdate(CreateAdditionalFileRequest createAdditionalFileRequest, String nameOrId, MultipartFile file) {
        authenticationService.setRoleForClient();
        Application application = getApplication(nameOrId);

        final AdditionalBinaryFile additionalBinaryFile = Optional.of(createAdditionalFileRequest)
                .map(CreateAdditionalFileRequest::getId)
                .map(id -> repo.getRepository(application).additionalBinaryFile().findById(id))
                .orElseGet(AdditionalBinaryFile::new);
        additionalBinaryFile.setFileInfos(createAdditionalFileRequest.getFields());
        additionalBinaryFile.setApplication(application.getId());
        additionalBinaryFile.setForApplication(Optional.ofNullable(createAdditionalFileRequest.getForApplication()).orElse( Boolean.FALSE));
        if (file != null) {
            additionalBinaryFile.setSize(file.getSize());
            additionalBinaryFile.setFileName(file.getOriginalFilename());
            try {
                additionalBinaryFile.setData(file.getBytes());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        additionalBinaryFile.setFileType(createAdditionalFileRequest.getFileType());
        additionalBinaryFile.setCreationUser(additionalBinaryFile.getCreationUser() == null ? getCurrentUser().getId() : additionalBinaryFile.getCreationUser());
        additionalBinaryFile.setUpdateUser(getCurrentUser().getId());
        additionalBinaryFile.setComment("un commentaire");
        additionalBinaryFile.setId(additionalBinaryFile.getId() == null ? UUID.randomUUID() : additionalBinaryFile.getId());
        final OreSiAuthorization oreSiAuthorization = new OreSiAuthorization();
        oreSiAuthorization.setId(additionalBinaryFile.getId());
        oreSiAuthorization.setApplication(application.getId());
        oreSiAuthorization.setAuthorizations(createAdditionalFileRequest.getAssociates().getAuthorizations());
        final List<OreSiAuthorization> authorizations = List.of(oreSiAuthorization);
        additionalBinaryFile.setAssociates(authorizations);
        return repo.getRepository(application).additionalBinaryFile().store(additionalBinaryFile);
    }


    @Value
    private static class ComputedValuesContext {
        ImmutableMap<VariableComponentKey, Function<Datum, String>> defaultValueFns;
        ImmutableSet<VariableComponentKey> replaceEnabled;
    }

    @Value
    private static class RowWithData {
        int lineNumber;
        Datum datum;
        PublishContext publishContext;
    }

    @Value
    private static class ParsedCsvRow {
        int lineNumber;
        List<Map.Entry<String, String>> columns;
        PublishContext publishContext;
    }

    protected class UniquenessBuilder {
        final List<VariableComponentKey> uniquenessDescription;
        final Map<UniquenessKeys, List<Integer>> uniquenessInFile = new TreeMap<>();
        private String dataType;

        public UniquenessBuilder(Application application, String dataType) {
            this.uniquenessDescription = getUniquenessDescription(application, dataType);
            this.dataType = dataType;
        }

        private List<VariableComponentKey> getUniquenessDescription(Application application, String dataType) {
            final List<VariableComponentKey> uniqueness = application.getConfiguration().getDataTypes().get(dataType).getUniqueness();
            if (uniqueness.isEmpty()) {
                return application.getConfiguration().getDataTypes().get(dataType).getData().entrySet().stream()
                        .flatMap(this::getVariableComponentKeys)
                        .collect(Collectors.toList());
            } else {
                return uniqueness;
            }
        }

        private Stream<VariableComponentKey> getVariableComponentKeys(Map.Entry<String, Configuration.VariableDescription> entry) {
            return entry.getValue().doGetAllComponents().stream()
                    .map(componentName -> new VariableComponentKey(entry.getKey(), componentName));
        }

        public List<String> test(Datum datum, int lineNumber) {
            UniquenessKeys uniquenessKeys = new UniquenessKeys(datum, uniquenessDescription);
            uniquenessInFile.compute(uniquenessKeys, (k, v) -> v == null ? new LinkedList<>() : v)
                    .add(lineNumber);
            boolean isInError = uniquenessInFile.get(uniquenessKeys).size() > 1;
            return isInError ? null : uniquenessKeys.getValues();
        }

        private CsvRowValidationCheckResult getErrorForEntry(Map.Entry<UniquenessKeys, List<Integer>> entry) {
            return new CsvRowValidationCheckResult(
                    DefaultValidationCheckResult.error(
                            "duplicatedLineInDatatype",
                            ImmutableMap.of(
                                    "file", this.dataType,
                                    "duplicatedRows", entry.getValue(),
                                    "uniquenessKey", getUniquenessKey(entry.getKey())
                            )
                    ), entry.getValue().get(0));
        }

        public List<CsvRowValidationCheckResult> getErrors() {
            return uniquenessInFile.entrySet().stream()
                    .filter(entry -> entry.getValue().size() > 1)
                    .map(this::getErrorForEntry)
                    .collect(Collectors.toList());
        }

        public Map<String, String> getUniquenessKey(UniquenessKeys uniquenessKeys) {
            Map<String, String> uniquenessKeyMap = new HashMap<>();
            for (int i = 0; i < uniquenessDescription.size(); i++) {
                uniquenessKeyMap.put(uniquenessDescription.get(i).getId(), uniquenessKeys.getValues().get(i));
            }
            return uniquenessKeyMap;
        }
    }

    class UniquenessKeys implements Comparable<UniquenessKeys> {
        List<String> values = new LinkedList<>();
        List<VariableComponentKey> uniquenessDescription;

        public UniquenessKeys(Datum datum, List<VariableComponentKey> uniquenessDescription) {
            this.uniquenessDescription = uniquenessDescription;
            this.values = uniquenessDescription.stream()
                    .map(variableComponentKey -> datum.get(variableComponentKey))
                    .collect(Collectors.toList());
        }

        public List<String> getValues() {
            return values;
        }

        public String getKey() {
            return values.stream().collect(Collectors.joining());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UniquenessKeys that = (UniquenessKeys) o;
            return Objects.equals(getKey(), that.getKey());
        }

        @Override
        public int hashCode() {
            return Objects.hash(values);
        }

        @Override
        public int compareTo(UniquenessKeys uniquenessKeys) {
            return this.getKey().compareTo(uniquenessKeys.getKey());
        }
    }

    @Getter
    @Setter
    public static class PublishContext {
        BinaryFileDataset binaryFileDataset;
        List<List<String>> preHeaderRow = new LinkedList<>();
        List<List<String>> postHeaderRow = new LinkedList<>();
        List<String> headerRow = new LinkedList<>();
        List<String> currentRow;
        long currentRowNumber;

        public PublishContext(BinaryFileDataset binaryFileDataset) {
            this.binaryFileDataset = binaryFileDataset;
        }
    }
}