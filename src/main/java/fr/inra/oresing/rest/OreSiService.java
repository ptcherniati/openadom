package fr.inra.oresing.rest;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.common.io.Resources;
import fr.inra.oresing.client.Client;
import fr.inra.oresing.domain.*;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.additionalfiles.AdditionalFilesInfos;
import fr.inra.oresing.domain.application.ApplicationInformation;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.authorization.request.AuthorizationRequest;
import fr.inra.oresing.domain.chart.Chart;
import fr.inra.oresing.domain.chart.OreSiSynthesis;
import fr.inra.oresing.domain.checker.CheckerFactory;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.authentication.authentication.NotApplicationCreatorRightsException;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisation;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisationForBuildBundleReport;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisationForDownloadDatasetQuery;
import fr.inra.oresing.domain.groovy.GroovyContextHelper;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.domain.rightsrequest.RightsRequest;
import fr.inra.oresing.domain.repository.authorization.OperationType;
import fr.inra.oresing.domain.services.file.BinaryFileService;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;
import fr.inra.oresing.persistence.flyway.MigrateService;
import fr.inra.oresing.rest.application.ApplicationService;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.data.extraction.DataCsvBuilder;
import fr.inra.oresing.rest.filesenderclient.*;
import fr.inra.oresing.rest.model.additionalfiles.AdditionalBinaryFileResult;
import fr.inra.oresing.rest.model.additionalfiles.CreateAdditionalFileRequest;
import fr.inra.oresing.rest.model.additionalfiles.exception.AdditionalFileParamsParsingResult;
import fr.inra.oresing.rest.model.additionalfiles.exceptions.BadAdditionalFileParamsSearchException;
import fr.inra.oresing.rest.model.application.ApplicationLightResult;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.model.authorization.*;
import fr.inra.oresing.rest.model.data.DefaultLineCheckerResult;
import fr.inra.oresing.rest.model.data.LineCheckerResult;
import fr.inra.oresing.rest.model.rightsrequest.*;
import fr.inra.oresing.rest.reactive.ReactiveProgression;
import fr.inra.oresing.rest.reactive.ReactiveTypeInfo;
import fr.inra.oresing.rest.reactive.ReactiveTypeProgress;
import fr.inra.oresing.rest.reactive.ReactiveTypeResult;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomain.*;
import static fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomain.*;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
@Transactional(readOnly = true)
public class OreSiService {

    public static final String CHARTE = "__charte__";
    private final GroovyContextHelper groovyContextHelper = new GroovyContextHelper();
    @Value("classpath:charte/default_charte.pdf")
    Resource defaultCharte;
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private FileRepository fileRepository;
    @Autowired
    private JavaMailSender mailSender;
    @Autowired
    private OreSiRepository repository;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private AuthorizationService authorizationService;
    @Autowired
    private BinaryFileService binaryFileService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OreSiApiRequestContext request;
    @Autowired
    private RelationalService relationalService;
    @Autowired
    private DataService dataService;
    @Autowired
    private RightsRequestService rightsRequestService;
    @Autowired
    private BeanFactory beanFactory;
    @Autowired
    private AdditionalFileService additionalFileService;
    @Autowired
    private JsonRowMapper jsonRowMapper;

    @Transactional()
    public ReactiveProgression.CreateApplicationProgression createApplication(
            ReactiveProgression.CreateApplicationProgression progression,
            final String name,
            final MultipartFile configurationFile,
            final String comment) {
        authorizationService.getPrivilegeAssessorForSystem(SYSTEM_ADMINISTRATION)
                .forCreateApplication()
                .canCreateApplication(name);
        final ReactiveProgression.CreateApplicationProgressionMessagesLabel baseMessage = new ReactiveProgression.CreateApplicationProgressionMessagesLabel();
        progression.pushProgression();
        OreSiUser currentUser = getCurrentUser();

        final Application application = new Application();
        application.setName(name);
        ReactiveProgression.CreateApplicationProgression result = null;
        try {
            result = (ReactiveProgression.CreateApplicationProgression) changeApplicationConfiguration(
                    comment,
                    progression,
                    application,
                    configurationFile,
                    createOrModifySchema -> {
                        try {
                            return initApplication(createOrModifySchema);
                        } catch (SQLException e) {
                            throw new RuntimeException(e);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        } catch (final OreSiTechnicalException | IOException e) {
            if ("fr.inra.oresing.domain.authorization.privilegeassessor.exception"
                    .equals(e.getClass().getPackage().getName())) {
                progression.fluxSink().error(e);
                throw (OreSiTechnicalException) e;
            }
            progression.fluxSink().error(e);
            progression.fluxSink().complete();
            return null;
        }
        ReactiveProgression.CreateApplicationProgression progression1 = progression.withSubLabel("viewCreation");
        progression1.pushMessage("start", Map.of("applicationName", application.getName()));
        progression1.incrementAndPush(i -> ReactiveProgression.CreateApplicationProgression.PROGRESSION_FOR_READING_CONFIGURATION.progress());
        //TODO
        // relationalService.createViews(application.getName());
        progression1.pushMessage("end", Map.of("applicationName", application.getName()));
        progression1.pushResult(application.getId());
        progression1.incrementAndPush(i -> 1D);
        progression1.complete();
        return result;
    }


    public ApplicationResult buildOpenAdom(final Application application, final String[] filter) {
        final List<ApplicationInformation> filters = Arrays.stream(filter)
                .map(s -> ApplicationInformation.valueOf(s))
                .toList();
        final boolean withDatatypes = filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.DATATYPE);
        final boolean withReferenceType = filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.REFERENCETYPE);
        final boolean withConfiguration = filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.CONFIGURATION);
        final boolean withRightsRequest = filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.RIGHTSREQUEST);
        List<ApplicationResult.DataSynthesis> referenceSynthesis = withReferenceType ? dataService.getReferenceSynthesis(application) : List.of();
        final TreeMultimap<String, String> childrenPerReferences = TreeMultimap.create();
        ApplicationResult.RightsRequest rightsRequest = null;
        if (withRightsRequest) {
            RightRequestDescription rightsRequestDescription = application.findRightRequest()
                    .orElse(null);
            rightsRequest = new ApplicationResult.RightsRequest(rightsRequestDescription);
        }
        Map<String, ApplicationResult.AdditionalFile> additionalFilesWithFields = application.getConfiguration().additionalFiles().entrySet().stream()
                .collect(
                        Collectors.toMap(
                                Map.Entry::getKey,
                                k -> new ApplicationResult.AdditionalFile(k.getValue().formFields().keySet())
                        )
                );
        final Map<String, StandardDataDescription> referenceComponents = Maps.filterValues(application.getConfiguration().dataDescription(), cd -> {
            return cd.tags().contains(Tag.ReferenceTag.INSTANCE()) || !cd.tags().contains(Tag.DataTag.INSTANCE());
        });
        final Map<String, StandardDataDescription> datatypeComponents = Maps.filterValues(application.getConfiguration().dataDescription(), cd -> {
            return cd.tags().contains(Tag.DataTag.INSTANCE());
        });

        final Map<String, Node> referencesNodes = application.getConfiguration().hierarchicalNodes().stream()
                .filter(node -> referenceComponents.containsKey(node.nodeName()))
                .collect(Collectors.toMap(node -> node.nodeName(), Function.identity()));
        final Map<String, Node> datatypesNodes = application.getConfiguration().hierarchicalNodes().stream()
                .filter(node -> datatypeComponents.containsKey(node.nodeName()))
                .collect(Collectors.toMap(node -> node.nodeName(), Function.identity()));

        final String nameOrId = application.getId().toString();
        Map<String, Map<AuthorizationsForUserResult.Roles, Boolean>> authorizations = withDatatypes || withReferenceType ? getAuthorizationsDatatypesRights(nameOrId, datatypeComponents.keySet()) : new HashMap<>();
        final Configuration configuration = withConfiguration ? application.getConfiguration() : null;
        CurrentUserRoles currentUserRoles = authenticationService.getCurrentUserRoles();
        final ApplicationResult applicationResult = new ApplicationResult(
                application.getId().toString(),
                Optional.ofNullable(application).map(Application::getName).orElseThrow(IllegalArgumentException::new),
                application.findApplicationDescription()
                        .map(ApplicationDescription::comment)
                        .orElseGet(String::new),
                application.findApplicationDescription()
                        .map(ApplicationDescription::comment)
                        .orElse(""),
                application.findInternationalizations()
                        .orElseGet(Internationalizations::new),

                application.findData(),
                referencesNodes,
                authorizations,
                referenceSynthesis,//referenceSynthesis,
                datatypesNodes,
                additionalFilesWithFields,
                rightsRequest,
                configuration,
                CurrentApplicationUserRolesResult.of(currentUserRoles, application.getId()),
                application.findDependantNodesByDataName());
        return applicationResult;
    }

    private OreSiUser getCurrentUser() {
        return userRepository.findById(request.getRequestClient().id());
    }

    @Transactional
    public Application initApplication(final Application application) throws SQLException, IOException {
        MigrateService migrateService = beanFactory.getBean(MigrateService.class);
        migrateService.setApplication(application);
        authenticationService.resetRole();
        final OreSiUserRole creator = authenticationService.getUserRole(request.getRequestUserId());

        migrateService.runFlywayUpdate(creator);
        authenticationService.setRoleForClient();
        repository.application().store(application);
        return application;
    }

    public Application modifySchemaApplication(final Application application) {
        MigrateService migrateService = beanFactory.getBean(MigrateService.class);
        migrateService.setApplication(application);
        authenticationService.resetRole();
        migrateService.updateSchema();
        authenticationService.setRoleForClient();
        repository.application().store(application);
        authenticationService.setRoleAdmin();
        repository.application().updateAuthorizationIndexes(application);
        authenticationService.setRoleForClient();
        return application;
    }


    @Transactional()
    public UUID changeApplicationConfiguration(
            ReactiveProgression.ChangeApplicationProgression progression,
            final String nameOrId,
            final MultipartFile configurationFile,
            final String comment) {
        final Application application = applicationService.getApplication(nameOrId);

        authorizationService.getPrivilegeAssessorForApplication(APPLICATION_MANAGER, application)
                .forUpdateApplication()
                .canUpdateApplication();
        ReactiveProgression.ChangeApplicationProgression progression1 = progression;
        final ReactiveProgression.ChangeApplicationProgressionMessagesLabel baseMessage = new ReactiveProgression.ChangeApplicationProgressionMessagesLabel();
        progression1.pushProgression();
        relationalService.dropViews(nameOrId);
        authenticationService.setRoleForClient();
        final Configuration oldConfiguration = application.getConfiguration();
        final UUID oldConfigFileId = application.getConfigFile();
        try {
            progression1 = (ReactiveProgression.ChangeApplicationProgression) changeApplicationConfiguration(comment,
                    progression1,
                    application,
                    configurationFile,
                    this::modifySchemaApplication
            ).up().withSubLabel("migrate");
        } catch (final IOException e) {
            progression1.pushError(e);
        }
        final String applicationName = application.getName();
        final Configuration newConfiguration = applicationService.getApplication(applicationName).getConfiguration();
        //TODO test à faire entre version ancienne et nouvelle
        final Version oldVersion = oldConfiguration.applicationDescription().version();
        final Version newVersion = newConfiguration.applicationDescription().version();
        try {
            Preconditions.checkArgument(newVersion.compareTo(oldVersion) > 0, "l'application " + applicationName + " est déjà dans la version " + oldVersion);
        } catch (final IllegalArgumentException e) {
            progression1.pushError(e);
            progression1.pushMessage("start", Map.of("application", applicationName, "oldVersion", oldVersion.version(), "newVersion", newVersion.version()));
        }
        if (log.isInfoEnabled()) {
            log.info("va migrer les données de {} de la version actuelle {} à la nouvelle version {}", applicationName, oldVersion, newVersion);
        }
        final DataRepository dataRepository = repository.getRepository(application).data();
        //TODO migration
/*
        for (Map.Entry<String, Configuration.StandardDataComponent> dataTypeEntry : newConfiguration.componentDescription().entrySet()) {
            String dataName = dataTypeEntry.getKey();
            Configuration.StandardDataComponent dataTypeDescription = dataTypeEntry.getValue();
            ImmutableMap<ComponentKey, LineCheckerWarper> referenceLineCheckers = checkerFactory.getReferenceLineCheckers(application, dataName);
            progression.pushMessage("datatype", Map.of("application", application.getName(), "dataName", dataName, "oldVersion", Integer.toString(oldVersion), "newVersion", Integer.toString(newVersion)));
            if (log.isInfoEnabled()) {
                log.info("va migrer les données de {}, type de données, {} de la version actuelle {} à la nouvelle version {}", application.getName(), dataName, oldVersion, newVersion);
            }
            for (int migrationVersionToApply = firstMigrationToApply; migrationVersionToApply <= newVersion; migrationVersionToApply++) {
                List<Configuration.MigrationDescription> migrations = dataTypeDescription.migrations().get(migrationVersionToApply);
                if (migrations == null) {
                    progression.pushMessage("noMigration", Map.of("application", application.getName(), "migrationVersionToApply", Integer.toString(migrationVersionToApply)));
                    if (log.isInfoEnabled()) {
                        log.info("aucune migration déclarée pour migrer le type de données {} vers la version {}", dataName, migrationVersionToApply);
                    }
                } else {
                    progression.pushMessage("declaredMigration", Map.of("application", application.getName(), "migrationSize", Integer.toString(migrations.size()), "migrationVersionToApply", Integer.toString(migrationVersionToApply)));
                    if (log.isInfoEnabled()) {
                        log.info("{} migrations déclarée pour migrer vers la version {}", migrations.size(), migrationVersionToApply);
                    }
                    for (Configuration.MigrationDescription migration : migrations) {
                        //Preconditions.checkArgument(migration.strategy() == Configuration.MigrationStrategy.ADD_VARIABLE);
                        String dataGroup = migration.dataGroup();
                        Map<String, String> variableValue = new LinkedHashMap<>();
                        Map<String, Set<UUID>> refsLinkedToAddForVariable = new LinkedHashMap<>();
                        for (Map.Entry<String, Configuration.ComponentDescription> componentEntry : migration.components().entrySet()) {
                            String component = componentEntry.getKey();
                            String componentValue = Optional.ofNullable(componentEntry.getValue())
                                    .map(Configuration.ComponentDescription::defaultValue)
                                    .orElse("");
                            ComponentKey componentKey = new ComponentKey(variable, component);
                            if (referenceLineCheckers.containsKey(componentKey)) {
                                LineCheckerWarper ReferenceType = referenceLineCheckers.get(componentKey);
                                ReferenceValidationCheckResult referenceCheckResult = (ReferenceValidationCheckResult) ReferenceType.check(componentValue);
                                Preconditions.checkState(referenceCheckResult.isSuccess(), componentValue + " n'est pas une valeur par défaut acceptable pour " + componentKey);
                                Set<UUID> referenceId = referenceCheckResult.matchedReferenceId();
                                refsLinkedToAddForVariable.put(component, referenceId);
                            }
                            variableValue.put(component, componentValue);
                        }
                        Map<String, Map<String, String>> variablesToAdd = Map.of(variable, variableValue);
                        Map<String, Map<String, Set<UUID>>> refsLinkedToAdd = Map.of(variable, refsLinkedToAddForVariable);
                        int migratedCount = dataRepository.migrate(dataName, dataGroup, variablesToAdd, refsLinkedToAdd);
                        progression.pushMessage("linesMigrated", Map.of("application", application.getName(), "migratedCount", Integer.toString(migratedCount)));
                        if (log.isInfoEnabled()) {
                            log.info("{} lignes migrées", migratedCount);
                        }
                    }
                }
            }
            validateStoredData(new DownloadDatasetQueryNoFilter(application, dataName, new DownloadDatasetQuery.OutPut(Locale.FRANCE, 0L,null),null,null));
            return application.getId();
        }
*/

        // on supprime l'ancien fichier vu que tout c'est bien passé
        final boolean deleted = repository.getRepository(application).binaryFile().delete(oldConfigFileId);
        Preconditions.checkState(deleted);

        relationalService.createViews(nameOrId);
        return application.getId();
    }/*

    private void validateStoredData(final DownloadDatasetQuery downloadDatasetQuery) {
        final BrokenApplication application = downloadDatasetQuery.application();
        final String dataType = downloadDatasetQuery.dataName();
        final ImmutableSet<LineCheckerWarper> lineCheckers = checkerFactory.getLineCheckers(application, dataType);
        final Consumer<Datum> validateRow = line -> {
            lineCheckers.forEach(lineChecker -> {
                final ValidationCheckResult validationCheckResult = lineChecker.check(line);
                Preconditions.checkState(validationCheckResult.isSuccess(),
                        "erreur de validation d'une donnée stockée " + validationCheckResult);
            });
        };
        repository.getRepository(application).data()
                .findAllByDataTypeFlux(downloadDatasetQuery)
                .map(DataRow::getValues)
                .map(Datum::fromMapMapOfFieldType)
                .subscribe(validateRow);
    }*/

    private ReactiveProgression.ChangeOrCreateApplicationProgression changeApplicationConfiguration(
            String comment,
            final ReactiveProgression.ChangeOrCreateApplicationProgression progression,
            Application application,
            final MultipartFile configurationFile,
            final Function<Application, Application> createOrModifySchema) throws IOException {
        String applicationName = application.getName();
        OreSiUser currentUser = getCurrentUser();
        UUID oldApplicationId = application.getId();
        ReactiveProgression.ChangeOrCreateApplicationProgression progressionForConfiguration = (ReactiveProgression.ChangeOrCreateApplicationProgression) progression.withSubLabel("configuration");
        progressionForConfiguration.pushMessage("rights.checking", Map.of("applicationName", applicationName));
        progressionForConfiguration = (ReactiveProgression.ChangeOrCreateApplicationProgression) progressionForConfiguration.incrementAndPush(i -> i + .02);
        final ReactiveProgression.ChangeOrCreateApplicationProgression progressionForParsingConfiguration = (ReactiveProgression.ChangeOrCreateApplicationProgression) progressionForConfiguration.withSubLabel("parsingConfiguration");
        if (Objects.requireNonNull(configurationFile.getOriginalFilename()).matches(".*\\.zip")) {
            InputStream multiYAmlInput = new MultiYaml().parseConfigurationBytes(configurationFile);
            progressionForParsingConfiguration.pushMessage("forMulti", Map.of("applicationName", applicationName));
            application = ApplicationConfigurationService.parseConfigurationBytes(comment, progressionForConfiguration, FileBomResolver.of(multiYAmlInput));
        } else {
            progressionForParsingConfiguration.pushMessage("forSingle", Map.of("applicationName", applicationName));
            application = ApplicationConfigurationService.parseConfigurationBytes(comment, progressionForConfiguration, FileBomResolver.of(configurationFile.getInputStream()));
        }
        if (application == null) {
            return progression;
        }
        //BadApplicationConfigurationException.check(configurationParsingResult);
        progression.fluxSink().next(new ReactiveTypeInfo("application.configuration.create.register.start", Map.of("applicationName", applicationName)));

        final Configuration configuration = application.getConfiguration();
        application.setId(oldApplicationId);
        assert configuration != null;
        application.setData(new ArrayList<>(configuration.dataDescription().keySet()));
        application.setConfiguration(configuration);
        final Optional<Set<String>> additionalsFiles = Optional.ofNullable(configuration.additionalFiles())
                .map(Map::keySet);
        if (additionalsFiles.isPresent()) {
            application.setAdditionalFiles(new LinkedList<>(additionalsFiles.get()));
        } else {
            application.setAdditionalFiles(List.of());
        }
        progressionForParsingConfiguration.pushMessage("endparsing", Map.of("applicationName", applicationName));
        String comment1 = configuration.applicationDescription().comment();
        Optional.ofNullable(applicationName).ifPresent(application::setName);
        try {
            application = createOrModifySchema.apply(application);
            final UUID confId = binaryFileService.storeFile(application, configurationFile, comment1, null);
            application.setConfigFile(confId);
            Timestamp charteLastTimestamp = Optional.ofNullable(additionalFileService.findCharte(application))
                    .map(AdditionalBinaryFile::getUpdateDate)
                    .map(Timestamp::valueOf)
                    .orElse(Timestamp.from(Instant.MIN));
            application.setLastChartes(charteLastTimestamp);
            final UUID appId = repository.application().store(application);
            final ReactiveProgression.ChangeOrCreateApplicationProgression progressionRegister = (ReactiveProgression.ChangeOrCreateApplicationProgression) progressionForParsingConfiguration.up();
            progressionRegister.pushMessage("register", Map.of("applicationName", applicationName));
            //repository.application().updateAuthorizationIndexes(application);

            return progressionRegister;
        } catch (final BadSqlGrammarException bsge) {
            throw new NotApplicationCreatorRightsException(applicationName, currentUser.getAuthorizations());
        }/* catch (final IOException e) {
            throw new RuntimeException(e);
        }*/
    }

    public List<BinaryFile> getFilesOnRepository(final String nameOrId, final String datatype, final BinaryFileDataset fileDatasetID, final boolean overlap) {
        authenticationService.setRoleForClient();
        final Application app = applicationService.getApplication(nameOrId);
        return repository.getRepository(app).binaryFile().findByBinaryFileDataset(datatype, fileDatasetID, overlap);
    }

    public Mono<List<DownloadDatasetQueryByRowId>> getDownloadDatasetQueriesAsync(
            boolean hasPatternDefinition,
            Application application,
            Locale locale,
            DataRepository dataRepository,
            Set<UUID> uuidsFromData) {
        return Flux.fromStream(dataRepository.getLinkedReferenceValuesStream(uuidsFromData))
                .map(dataValuesByDataType -> {
                    String dataType = dataValuesByDataType.getDataType();
                    Set<DataRowIds> ids = dataValuesByDataType.getIds();
                    return new DownloadDatasetQueryByRowId(
                            hasPatternDefinition,
                            application,
                            dataType,
                            new OutPut(locale, 0L, null),
                            new HashSet<>(),
                            new HashSet<>(),
                            ids
                    );
                })
                .collectList();
    }

    @Transactional(readOnly = true)
    public void buildDataZip(
            ZipOutputStream zipOutputStream,
            DownloadDatasetQuery downloadDatasetQuery) {
        Application application = downloadDatasetQuery.application();
        DataRepository dataRepository = repository.getRepository(downloadDatasetQuery.application()).data();
        DataRepositoryWithBuffer dataRepositoryWithBuffer = new DataRepositoryWithBuffer(application, dataRepository);

        authenticationService.setRoleForClient();

        UUIDsfromData uuiDsfromData = addDatacsv(zipOutputStream, dataRepositoryWithBuffer, downloadDatasetQuery, "%s.csv");


        getDownloadDatasetQueriesAsync(
                downloadDatasetQuery.hasPatternDefinition(),
                application,
                downloadDatasetQuery.outPut().locale(),
                dataRepository,
                uuiDsfromData.uuidsfromData())
                .subscribe(downloadDatasetQueries -> {
                    for (DownloadDatasetQueryByRowId downloadDatasetQueryByRowId : downloadDatasetQueries) {
                        try {
                            addDatacsv(zipOutputStream, dataRepositoryWithBuffer, downloadDatasetQueryByRowId, "references/%s.csv");
                        } catch (Exception e) {
                            throw new SiOreIllegalArgumentException("IOException", Map.of("message", e.getLocalizedMessage()));
                        }
                    }
                    try {
                        zipOutputStream.close();
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
        //TODO add additionalFiles

        /*Flux.fromStream(dataRepository.getLinkedReferenceValuesStream(uuiDsfromData.uuidsfromData()))
                //.filter(dataValuesByDataType -> "tr_metadata_agri_magri".equals(dataValuesByDataType.getDataType()))
                //.take(10)
                .doOnNext(dataValuesByDataType -> {
                    try {
                        addReferenceEntry(
                                downloadDatasetQuery.application(),
                                language,
                                separator,
                                dataValuesByDataType,
                                dataRepositoryWithBuffer,
                                zipOutputStream);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                })
                .doOnError(e -> {
                    // Gestion des erreurs
                    throw new SiOreIllegalArgumentException("IOException", Map.of("message", e.getLocalizedMessage()));
                })
                .doOnComplete(() -> {
                    try {
                        zipOutputStream.close();
                    } catch (IOException e) {
                        throw new SiOreIllegalArgumentException("IOException", Map.of("message", e.getLocalizedMessage()));
                    }
                })
                .subscribe();*/

        // 3. Construire la liste des fichiers additionnels
        //AdditionalFileRepository additionalFileRepository = repository.getRepository(downloadDatasetQuery.application()).additionalBinaryFile();
        /*for (String additionalFileType : downloadDatasetQuery.application().getConfiguration().additionalFiles()) {
            if (!additionalFileType.isEmpty()) {
                additionalFileRepository.getAssociatedAdditionalFilesStream(uuiDsfromData.getDatasIds())
                        .forEach(additionalFile -> {
                            try {
                                new AdditionalFileSearchHelper().addAdditionalFilesToZip(additionalFile, zipOutputStream, "additionalFiles/");
                            } catch (final IOException e) {
                                throw new RuntimeException("Erreur lors de l'ajout des fichiers additionnels", e);
                            }
                        });
            }
        }*/
    }

    public UUIDsfromData addDatacsv(
            final ZipOutputStream zipOutputStream,
            DataRepositoryWithBuffer dataRepositoryWithBuffer,
            final DownloadDatasetQuery downloadDatasetQuery,
            String fileNamePattern) {
        final Flux<DataRow> datas = dataService.findDataFlux(downloadDatasetQuery);
        try {
            DataRepository dataRepository = repository.getRepository(downloadDatasetQuery.application()).data();
            AdditionalFileRepository additionalFileRepository = repository.getRepository(downloadDatasetQuery.application()).additionalBinaryFile();
            return DataCsvBuilder.getDataCsvBuilder((applicationNameOrId, referenceType) -> dataService.getDataImporterContext(downloadDatasetQuery.application(), referenceType, null))
                    .withDownloadDatasetQuery(downloadDatasetQuery)
                    .withReferenceService(dataService)
                    .withOutputStream(zipOutputStream)
                    .onRepositories(dataRepositoryWithBuffer, additionalFileRepository)
                    .addDatas(datas)
                    .build(fileNamePattern);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public Map<String, Map<String, LineCheckerResult>> getCheckedFormatComponents(final String nameOrId, final String dataName) {
        Application application = applicationService.getApplication(nameOrId);
        return new CheckerFactory(repository.getRepository(application).data()).getCheckers(application, dataName, new PublishContext.PublishContextBuilder(application, dataName, null, r -> List.of())).stream()
                .filter(c -> (c.underlyingType() instanceof DateType) || (c.underlyingType() instanceof IntegerType) || (c.underlyingType() instanceof FloatType) || (c.underlyingType() instanceof ReferenceType)).collect(Collectors
                        .groupingBy(
                                c -> c.underlyingType().getClass().getSimpleName(),
                                Collectors.toMap(c -> {
                                            final DataColumn dataColumn = (DataColumn) c.target();
                                            return dataColumn.toHumanReadableString();
                                        },
                                        c -> DefaultLineCheckerResult.fromLineChecker(c))
                        )
                );
    }


    @Transactional(readOnly = true)
    public Map<String, Map<String, LineChecker>> getFormatChecked(final String nameOrId, final String references) {
        final DataRepository dataRepository = repository.getRepository(applicationService.getApplication(nameOrId)).data();
        return new CheckerFactory(dataRepository)
                .getCheckers(
                        applicationService.getApplicationOrApplicationAccordingToRights(nameOrId),
                        references,
                        null
                ).stream()
                .filter(c -> (c.underlyingType() instanceof DateType) || (c.underlyingType() instanceof IntegerType) || (c.underlyingType() instanceof FloatType) || (c.underlyingType() instanceof ReferenceType)).collect(Collectors
                        .groupingBy(
                                c -> c.fieldTypeForOne().getClass().getSimpleName(),
                                Collectors.toMap(
                                        c -> {
                                            final DataColumn vc = (DataColumn) c.target();
                                            return vc.asString();
                                        },
                                        c -> c)
                        )
                );
    }

    public List<DataRow> findData(final DownloadDatasetQuery downloadDatasetQuery) {
        return dataService.findDataFlux(downloadDatasetQuery).collectList().block();
    }

/*
    public void writeData(FluxSink<ReactiveResult> fluxSink, DownloadDatasetQuery downloadDatasetQuery, String nameOrId, String dataName) {
        authenticationService.setRoleForClient();
        Application app = downloadDatasetQuery.applicationService.getApplication();
        if (Optional.of(app.getConfiguration())
                .map(Configuration::getDataTypes)
                .map(datatypes -> datatypes.get(dataName))
                .map(Configuration.DataTypeDescription::getTags)
                .map(tags -> tags.stream().anyMatch(tag -> Configuration.HIDDEN_TAG.equals(tag)))
                .orElse(true)) {
            return;
        }
        progression.fluxSink().next(new ReactiveTypeInfo("Ca commence ! "));
        progression.fluxSink().next(new ReactiveTypeProgress(0));
        AtomicLong counter = new AtomicLong(0);
        repo.getRepository(app).data().findAllByDataTypeStream(downloadDatasetQuery)
                .peek(dataRow -> {
                    if (counter.incrementAndGet() % 3 == 0) {
                        progression.fluxSink().next(new ReactiveTypeProgress(counter.get()));
                    }
                })
                .forEach(dataRow -> {
                    progression.fluxSink().next(new ReactiveTypeResult(dataRow));
                    //progression.fluxSink().next(dataRow);
                });
        progression.fluxSink().next(new ReactiveTypeInfo("C'est fini ! "));
        fluxSink.complete();
    }
*/

    @Transactional()
    public List<UUID> deleteData(final DownloadDatasetQuery downloadDatasetQuery) {
        authenticationService.setRoleForClient();
        final Application application = downloadDatasetQuery.application();
        final List<UUID> data = repository.getRepository(application).data().delete(downloadDatasetQuery);
        return data;
    }

    public void getApplications(ReactiveProgression.GetApplicationProgression progression, final List<ApplicationInformation> filters) {
        authenticationService.setRoleForClient();
        final List<Application> applicationForUser = repository.application().findAll();
        authenticationService.setRoleAdmin();
        final Stream<Application> applicationForAdmin = repository.application().findAllStream();
        final AtomicLong progres = new AtomicLong(0);
        progression.fluxSink().next(new ReactiveTypeProgress(progres.get()));
        CurrentUserRoles currentUserRoles = authenticationService.getCurrentUserRoles();
        applicationForAdmin
                .map(application -> applicationForUser.stream()
                        .filter(app -> app.getId().equals(application.getId()))
                        .findAny()
                        .orElse(application.applicationAccordingToRights())
                )
                .map(application -> application.filterFieldsAndHidden(filters))
                .map(application -> ApplicationLightResult.of(application, currentUserRoles))
                .forEach(application -> {
                    progression.fluxSink().next(new ReactiveTypeResult(application));
                    final double prog = progres.incrementAndGet() / ((double) applicationForUser.size());
                    progression.fluxSink().next(new ReactiveTypeProgress(prog));
                });
        progression.complete();
    }

    public Map<String, Map<AuthorizationsForUserResult.Roles, Boolean>> getAuthorizationsDatatypesRights(final String nameOrId, final Set<String> datatypes) {
        return datatypes.stream().map(dty -> getAuthorizationsDatatypesRights(nameOrId, dty, request.getRequestUserId().toString())).collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
    }

    private Map.Entry<String, Map<AuthorizationsForUserResult.Roles, Boolean>> getAuthorizationsDatatypesRights(
            final String nameOrId,
            final String datatype,
            final String userId) {
        AuthorizationsResult authorizationsForUser = authorizationService.getAuthorizationsForUserAndPublic(nameOrId, userId);
        final Map<AuthorizationsForUserResult.Roles, Boolean> roleForDatatype = new EnumMap<>(AuthorizationsForUserResult.Roles.class);

        Set<OperationType> rolesSetted = Optional.ofNullable(authorizationsForUser.userAuthorization())
                .map(map -> map.get(datatype))
                .map(authList -> authList.stream()
                        .flatMap(auth -> auth.operationTypes().stream())
                        .collect(Collectors.toSet()))
                .orElseGet(Set::of);

        Boolean isAdministrator = authorizationsForUser.applicationManager();
        roleForDatatype.put(AuthorizationsForUserResult.Roles.UPLOAD, isAdministrator || rolesSetted.contains(OperationType.depot) || rolesSetted.contains(OperationType.publication));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.DELETE, isAdministrator || rolesSetted.contains(OperationType.delete));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.DOWNLOAD, isAdministrator || rolesSetted.contains(OperationType.extraction) || rolesSetted.contains(OperationType.publication));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.READ, isAdministrator || rolesSetted.contains(OperationType.extraction) || rolesSetted.contains(OperationType.publication));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.PUBLICATION, isAdministrator || rolesSetted.contains(OperationType.publication));
        roleForDatatype.put(AuthorizationsForUserResult.Roles.ANY, isAdministrator || !rolesSetted.isEmpty());

        new AuthorizationsForUserResult(Map.of(datatype, roleForDatatype), nameOrId, isAdministrator, userId);
        return new AbstractMap.SimpleEntry<>(datatype, roleForDatatype);
    }

    public List<List<String>> getDataColumn(final Application application, final String refType, final String column) {
        List<List<String>> list = List.of();
        if (application.findData(refType)
                .map(StandardDataDescription::tags)
                .filter(Tag.HiddenTag.HAS_HIDDEN_TAG_PREDICATE)
                .isPresent()) {
            list = repository.getRepository(application).data().findDataColumn(refType, column);
        }
        return list;
    }

    public Optional<Application> tryFindApplication(final String nameOrId) {
        authenticationService.setRoleForClient();
        return repository.application().tryFindApplication(nameOrId);
    }

    public List<DataValue> findReference(final String nameOrId, final String refType, final MultiValueMap<String, String> params) {
        Application application = applicationService.getApplicationOrApplicationAccordingToRights(nameOrId);
        return dataService
                .findReferenceAccordingToRights(application, refType, params);
    }

    @Transactional()
    public List<UUID> deleteData(final String nameOrId, final String refType, final MultiValueMap<String, String> params) {
        Application applicationOrApplicationAccordingToRights = applicationService.getApplicationOrApplicationAccordingToRights(nameOrId);
        return dataService.deleteDataAccordingToRights(applicationOrApplicationAccordingToRights, refType, params);
    }

    public GetAdditionalFilesResult findAdditionalFile(final String nameOrId, final AdditionalFilesInfos additionalFilesInfos) {
        Application application = applicationService.getApplication(nameOrId);
        final AdditionalFileDescription description = Optional.ofNullable(application.getConfiguration().additionalFiles()).map(map -> map.get(additionalFilesInfos.getFiletype())).orElseGet(AdditionalFileDescription::EMPTY_INSTANCE);
        List<AdditionalBinaryFile> additionalFiles = additionalFileService.findAdditionalFile(application, additionalFilesInfos);
        List<AdditionalBinaryFileResult> additionalBinaryFileResults = additionalFiles.stream().map(af -> getAdditionalBinaryFileResult(af, application)).collect(Collectors.toList());
        ImmutableSortedSet<GetGrantableResult.User> grantableUsers = authorizationService.getGrantableUsers();
        List<String> fileNamesForFiletype = repository.getRepository(application).additionalBinaryFile().getFileNamesForFiletype(additionalFilesInfos.getFiletype());
        return new GetAdditionalFilesResult(grantableUsers, additionalFilesInfos.getFiletype(), additionalBinaryFileResults, description, fileNamesForFiletype);
    }

    public void getDataCsvStream(final OutputStream outputStream, final String applicationNameOrId, final String ReferenceType, Locale language) {
        dataService.getDataCsvStream(outputStream, applicationNameOrId, ReferenceType, language);
    }

    public Application validateConfiguration(final ReactiveProgression.CreateApplicationProgression fluxSink, final MultipartFile file) {
        try {
            final Application application;
            if (Objects.requireNonNull(file.getOriginalFilename()).matches(".zip")) {
                application = ApplicationConfigurationService.unzipConfiguration(file);
            } else {
                application = ApplicationConfigurationService.parseConfigurationBytes(null, fluxSink, FileBomResolver.of(file.getInputStream()));
            }
            return application;
        } catch (final IOException e) {
            fluxSink.pushError(e);
            return null;
        }
    }

    public int deleteSynthesis(final String nameOrId, final String dataType, final String variable) {
        final Application application = applicationService.getApplication(nameOrId);
        return repository.getRepository(application).synthesisRepository().removeSynthesisByApplicationDatatypeAndVariable(application.getId(), dataType, variable);
    }

    public int deleteSynthesis(final String nameOrId, final String dataType) {
        final Application application = applicationService.getApplication(nameOrId);
        return repository.getRepository(application).synthesisRepository().removeSynthesisByApplicationDatatype(application.getId(), dataType);
    }

    @Transactional()
    public Map<String, List<OreSiSynthesis>> buildSynthesis(final String nameOrId, final String dataType, final String variable) {
        final Application application = applicationService.getApplication(nameOrId);
        DataSynthesisRepository repo = repository.getRepository(application).synthesisRepository();
        if (variable == null) {
            repo.removeSynthesisByApplicationDatatype(application.getId(), dataType);
        } else {
            repo.removeSynthesisByApplicationDatatypeAndVariable(application.getId(), dataType, variable);
        }
        final boolean hasChartDescription = application.getConfiguration().dataDescription().get(dataType).componentDescriptions().entrySet().stream()
                .filter(entry -> Strings.isNullOrEmpty(variable) || entry.getKey().equals(variable))
                .anyMatch(entry -> entry.getValue().getChartDescription() != null);
        final String sql;
        if (hasChartDescription) {
            sql = application.getConfiguration().dataDescription().get(dataType).componentDescriptions().entrySet().stream()
                    .filter(entry -> Strings.isNullOrEmpty(variable) || entry.getKey().equals(variable))
                    .filter(entry -> entry.getValue().getChartDescription() != null)
                    .map(entry -> entry.getValue().getChartDescription().toSQL(entry.getKey(), dataType))
                    .collect(Collectors.joining(", \n"));
        } else {
            sql = Chart.toSQL(dataType);
        }
        final List<OreSiSynthesis> oreSiSynthesisList = new LinkedList<>();
        List<OreSiSynthesis> oreSiSynthesis = repo.buildSynthesis(sql, hasChartDescription);
        repo.storeAll(oreSiSynthesis.stream());

        return !hasChartDescription ? Map.of("__NO-CHART", oreSiSynthesis) : oreSiSynthesis.stream().collect(Collectors.groupingBy(OreSiSynthesis::getVariable));
    }

    public Map<String, List<OreSiSynthesis>> getSynthesis(final String nameOrId, final String dataType) {
        final Application application = applicationService.getApplicationOrApplicationAccordingToRights(nameOrId);
        if (Optional.of(application.getConfiguration())
                .map(Configuration::dataDescription)
                .map(datatypes -> datatypes.get(dataType))
                .map(StandardDataDescription::tags)
                .map(tags -> tags.stream().noneMatch(tag -> Tag.HiddenTag.INSTANCE().equals(tag)))
                .orElse(false)) {
            return repository.getRepository(application).synthesisRepository().selectSynthesisDatatype(application.getId(), dataType).stream()
                    .collect(Collectors.groupingBy(OreSiSynthesis::getVariable));
        }
        return null;
    }

    public Map<String, List<OreSiSynthesis>> getSynthesis(final String nameOrId, final String dataName, final String componentName) {
        final Application application = applicationService.getApplication(nameOrId);
        if (Optional.of(application.getConfiguration())
                .map(Configuration::dataDescription)
                .map(data -> data.get(dataName))
                .map(StandardDataDescription::componentDescriptions)
                .map(data -> data.get(componentName))
                .map(ComponentDescription::tags)
                .map(tags -> tags.stream().noneMatch(tag -> Tag.HiddenTag.INSTANCE() == tag))
                .orElse(false)) {
            return repository.getRepository(application).synthesisRepository().selectSynthesisDatatypeAndVariable(application.getId(), dataName, componentName).stream()
                    .collect(Collectors.groupingBy(OreSiSynthesis::getVariable));
        }
        return null;
    }

    public Map<Ltree, List<DataValue>> getReferenceDisplaysById(final Application application, final Set<String> listOfDataIds) {
        return repository.getRepository(application).data().getReferenceDisplaysById(listOfDataIds);
    }

    public GetRightsRequestResult findRightsRequest(final String nameOrId, final RightsRequestInfos rightsRequestInfos) {
        Application application = applicationService.getApplicationOrApplicationAccordingToRights(nameOrId);
        final RightRequestDescription description = application.getConfiguration().rightsRequest();
        List<RightsRequest> rightsRequests = rightsRequestService.findRightsRequests(application, rightsRequestInfos);
        List<RightsRequestResult> rightsRequestResult = rightsRequests.stream()
                .map(rightsRequest ->
                        getRightsRequestResult(rightsRequest, application)
                )
                .collect(Collectors.toList());
        ImmutableSortedSet<GetGrantableResult.User> grantableUsers = authorizationService.getGrantableUsers();
        return new GetRightsRequestResult(grantableUsers, rightsRequestResult, description);
    }

    private RightsRequestResult getRightsRequestResult(final RightsRequest rightsRequest, final Application application) {
        Map<String, List<AuthorizationParsed>> authorizationsParsed = new HashMap<>();
        AuthorizationService.authorizationsToParsedAuthorizations(
                List.of(rightsRequest.getRightsRequest()),
                authorizationsParsed);
        return new RightsRequestResult(
                rightsRequest,
                authorizationsParsed
        );
    }

    private AdditionalBinaryFileResult getAdditionalBinaryFileResult(
            final AdditionalBinaryFile additionalBinaryFile,
            final Application application) {
        Map<String, List<AuthorizationParsed>> authorizationsParsed = new HashMap<>();
        authorizationService.authorizationsToParsedAuthorizations(
                additionalBinaryFile.getAssociates(),
                authorizationsParsed);
        return new AdditionalBinaryFileResult(additionalBinaryFile, authorizationsParsed);
    }

    @Transactional()
    public UUID createOrUpdate(final CreateRightsRequestRequest createRightsRequestRequest, final String nameOrId) {
        authenticationService.setRoleForClient();
        final Application application = applicationService.getApplicationOrApplicationAccordingToRights(nameOrId);

        RightsRequest rightsRequest = Optional.of(createRightsRequestRequest)
                .map(CreateRightsRequestRequest::id)
                .map(id -> repository.getRepository(application).rightsRequestRepository().findById(id))
                .orElseGet(RightsRequest::new);
        rightsRequest.setRightsRequestForm(createRightsRequestRequest.fields());
        rightsRequest.setApplication(application.getId());
        rightsRequest.setComment(createRightsRequestRequest.comment());
        rightsRequest.setSetted(createRightsRequestRequest.setted());
        rightsRequest.setId(rightsRequest.getId() == null ? UUID.randomUUID() : rightsRequest.getId());
        OreSiAuthorization authorizations = Optional.ofNullable(createRightsRequestRequest)
                .map(CreateRightsRequestRequest::rightsRequest)
                .map(authorization -> {
                    List errors = new ArrayList<>();
                    AuthorizationRequest authorizationRequestToAuthorizationRequest = authorizationService.createAuthorizationRequestToAuthorizationRequest(
                            authorization,
                            application,
                            List.of(getCurrentUser().getId()),
                            List.of(),
                            errors
                    );
                    OreSiAuthorization oreSiAuthorization = new OreSiAuthorization();
                    oreSiAuthorization.setId(rightsRequest.getId());
                    oreSiAuthorization.setApplication(application.getId());
                    oreSiAuthorization.setAuthorizations(authorizationRequestToAuthorizationRequest.buildAuthorizationsByDataname());
                    return oreSiAuthorization;
                })
                .orElse(null);
        rightsRequest.setRightsRequest(authorizations);
        rightsRequest.setUser(rightsRequest.getUser() == null ? request.getRequestUserId() : rightsRequest.getUser());
        rightsRequest.getRightsRequest().setOreSiUsers(Set.of(rightsRequest.getUser()));
        authenticationService.setRoleForClient();
        UUID store = repository.getRepository(application).rightsRequestRepository().store(rightsRequest);
        return store;
    }

    public void getCharte(final OutputStream out, final HttpServletResponse response, final String nameOrId, final AdditionalFilesInfos additionalFilesInfos) throws IOException {
        AdditionalFileParamsParsingResult additionalFileParamsParsingResult = getAdditionalFileSearchHelper(nameOrId, additionalFilesInfos);
        final AdditionalFileSearchHelper additionalFileSearchHelper = additionalFileParamsParsingResult.getResult();

        final AdditionalFileRepository additionalFileRepository = repository.getRepository(nameOrId).additionalBinaryFile();
        authenticationService.setRoleForClient();
        final Stream<AdditionalBinaryFile> additionnalFilesStream = additionalFileRepository
                .findByCriteriaStream(additionalFileSearchHelper);
        final Mono<byte[]> mono = Mono.just(additionnalFilesStream)

                .map(Stream::findFirst)
                .map(o -> o.orElse(null))//orElseGet(() -> getDefaultCharte(additionalFilesInfos, nameOrId)))
                .map(additionalBinaryFile -> {
                    response.setHeader("Content-Disposition", "inline; filename=" + additionalBinaryFile.getFileName());
                    response.setHeader("Content-Length", Long.toString(additionalBinaryFile.getSize()));
                    return additionalBinaryFile;
                })
                .map(AdditionalBinaryFile::getData)
                .onErrorComplete();
        final byte[] block = mono.block();
        if (block != null && block.length > 0) {
            out.write(block);
            out.flush();
        } else {
            out.write(FileCopyUtils.copyToByteArray(defaultCharte.getInputStream()));
        }
    }

    public void getAdditionalFilesNamesZipStream(final ZipOutputStream zipOutputStream, final String nameOrId, final AdditionalFilesInfos additionalFilesInfos) {
        final Application application = applicationService.getApplication(nameOrId);
        AdditionalFileParamsParsingResult additionalFileParamsParsingResult = getAdditionalFileSearchHelper(nameOrId, additionalFilesInfos);
        BadAdditionalFileParamsSearchException.check(additionalFileParamsParsingResult);
        final AdditionalFileSearchHelper additionalFileSearchHelper = additionalFileParamsParsingResult.getResult();
        final AtomicLong counter = new AtomicLong(0);
        repository
                .getRepository(application).additionalBinaryFile()
                .findByCriteriaStream(additionalFileSearchHelper)
                .forEach(additionalBinaryFile -> {
                    try {
                        if (counter.incrementAndGet() % 1000 == 0) {
                            zipOutputStream.flush();
                        }
                        additionalFileSearchHelper.addAdditionalFilesToZip(additionalBinaryFile, zipOutputStream, "");
                    } catch (final IOException e) {
                        throw new RuntimeException(e);
                    }
                });

    }

    @Transactional()
    public List<UUID> deleteAdditionalFiles(final String nameOrId, final AdditionalFilesInfos additionalFilesInfos) {
        final Application application = applicationService.getApplication(nameOrId);
        AdditionalFileParamsParsingResult additionalFileParamsParsingResult = getAdditionalFileSearchHelper(nameOrId, additionalFilesInfos);
        BadAdditionalFileParamsSearchException.check(additionalFileParamsParsingResult);
        final AdditionalFileSearchHelper additionalFileSearchHelper = additionalFileParamsParsingResult.getResult();
        try {
            final List<UUID> deletedAdditionalBinaryFiles = repository
                    .getRepository(application).additionalBinaryFile()
                    .deleteByCriteria(additionalFileSearchHelper);
            return deletedAdditionalBinaryFiles;
        } catch (final DataIntegrityViolationException e) {
            return null;
        }
    }

    public AdditionalFileParamsParsingResult getAdditionalFileSearchHelper(final String nameOrId, final AdditionalFilesInfos additionalFilesInfos) {
        final Application application = "__charte__".equals(additionalFilesInfos.getFiletype()) ? applicationService.getApplicationOrApplicationAccordingToRights(nameOrId) : applicationService.getApplication(nameOrId);
        final AdditionalFileParamsParsingResult.Builder builder = AdditionalFileParamsParsingResult.builder();
        for (final Map.Entry<String, AdditionalFilesInfos.AdditionalFileInfos> entry : additionalFilesInfos.getAdditionalFilesInfos().entrySet()) {
            final String additionalFileName = entry.getKey();
            AdditionalFileDescription additionalFileDescription = application.getConfiguration().additionalFiles().get(additionalFileName);
            if (additionalFileDescription == null) {
                builder.unknownAdditionalFilename(additionalFileName, additionalFilesInfos.getAdditionalFilesInfos().keySet());
            } else {
                AdditionalFilesInfos.AdditionalFileInfos value = entry.getValue();
                if (value != null && !CollectionUtils.isEmpty(value.getFieldFilters())) {
                    for (final AdditionalFilesInfos.FieldFilters filter : value.getFieldFilters()) {
                        if (additionalFileDescription.formFields().get(filter.field) == null) {
                            builder.unknownFieldAdditionalFilename(additionalFileName, filter.field, additionalFileDescription.formFields().keySet());
                        }
                    }
                }
            }

        }
        AdditionalFileParamsParsingResult build = builder.build(application, additionalFilesInfos);
        return build;
    }

    @Transactional()
    public UUID createOrUpdate(final CreateAdditionalFileRequest createAdditionalFileRequest,
                               final String additionalFileName,
                               final String nameOrId,
                               final MultipartFile file) {
        authenticationService.setRoleForClient();
        final Application application = applicationService.getApplication(nameOrId);

        AdditionalBinaryFile additionalBinaryFile = Optional.of(createAdditionalFileRequest)
                .map(CreateAdditionalFileRequest::id)
                .map(id -> {
                    UUID id1 = id;
                    if (CHARTE.equals(additionalFileName)) {
                        id1 = application.getId();
                    }
                    AdditionalBinaryFile abf = repository.getRepository(application).additionalBinaryFile().findById(id1);
                    if (abf == null) {
                        abf = new AdditionalBinaryFile();
                        abf.setId(id1);
                        abf.setApplication(id1);
                        abf.setFileType(CHARTE);
                        abf.setForApplication(true);
                    }
                    return abf;
                })
                .orElseGet(AdditionalBinaryFile::new);
        additionalBinaryFile.setFileInfos(createAdditionalFileRequest.fields());
        additionalBinaryFile.setApplication(application.getId());
        additionalBinaryFile.setForApplication(
                Optional.ofNullable(createAdditionalFileRequest.forApplication())
                        .orElse(CHARTE.equals(additionalFileName)));
        if (file != null) {
            additionalBinaryFile.setSize(file.getSize());
            additionalBinaryFile.setFileName(file.getOriginalFilename());
            try {
                additionalBinaryFile.setData(file.getBytes());
            } catch (final IOException e) {
                throw new RuntimeException(e);
            }
        }
        additionalBinaryFile.setComment(createAdditionalFileRequest.comment());
        additionalBinaryFile.setFileType(createAdditionalFileRequest.fileType());
        additionalBinaryFile.setCreationUser(additionalBinaryFile.getCreationUser() == null ? getCurrentUser().getId() : additionalBinaryFile.getCreationUser());
        additionalBinaryFile.setUpdateUser(getCurrentUser().getId());
        additionalBinaryFile.setId(additionalBinaryFile.getId() == null ? UUID.randomUUID() : additionalBinaryFile.getId());
        OreSiAuthorization oreSiAuthorization = new OreSiAuthorization();
        oreSiAuthorization.setId(additionalBinaryFile.getId());
        oreSiAuthorization.setApplication(application.getId());

        /*TODO Optional.ofNullable(createAdditionalFileRequest)
                .map(CreateAdditionalFileRequest::associates)
                .ifPresent(associate -> oreSiAuthorization
                        .setAuthorizations(associate.authorizations())
                )
        ;*/
        List<OreSiAuthorization> authorizations = List.of(oreSiAuthorization);
        additionalBinaryFile.setAssociates(authorizations);
        final UUID store = repository.getRepository(application).additionalBinaryFile().store(additionalBinaryFile);
        if (CHARTE.equals(additionalBinaryFile.getFileType()) && store != null) {
            userRepository.invalidateCharte(store);
        }
        return store;
    }

    @Transactional(readOnly = true)
    public BuildBundleReport writeUploadBundle(String instanceUrl, String nameOrId, boolean withData, Locale locale, ZipOutputStream zipOutputStream) throws IOException {
        Application application = applicationService.getApplication(nameOrId);
        String applicationName = application.getName();
        List<String> referentielsAvecDonnees = new ArrayList<>();
        Map<String, List<String>> fichiersGeneres = new HashMap<>();
        List<String> referentielsAvecDonneesExemple = new ArrayList<>();
        List<String> referentielsEnErreur = new ArrayList<>();

        locale = Optional.of(locale)
                .orElseGet(application.getConfiguration().applicationDescription()::defaultLanguage);

        try {
            // Écrire le fichier Groovy
            String groovyScriptFileName = "OpenAdomClient.groovy";
            writeFileToZip(zipOutputStream, groovyScriptFileName, Resources.getResource(Client.class, groovyScriptFileName));
            fichiersGeneres.put("Scripts", List.of(groovyScriptFileName));

            // Écrire le fichier de configuration
            String configFileName = "openAdom-client-configuration.json";
            String configurationJson = """
                    {
                      "instanceUrl": "%s",
                      "applicationName": "%s"
                    }
                    """.formatted(instanceUrl, nameOrId);
            writeStringToZip(zipOutputStream, configFileName, configurationJson);
            fichiersGeneres.put("Configuration", List.of(configFileName));

            // Écrire le fichier README
            String readmeFileName = "LISEZ-MOI.txt";
            String readmeContent = """
                    Instructions :
                    
                    1. installer Groovy version 4 minimum https://groovy.apache.org/download.html#osinstall
                    
                    2. vérifier que Groovy fonctionne en lançant groovy --version
                    
                    Exemple de retour correct :
                    Groovy Version: 4.0.15 JVM: 17.0.8.1 Vendor: Private Build OS: Linux
                    
                    3. lancer le script d'import en masse
                    
                    groovy %s
                    """.formatted(groovyScriptFileName);
            writeStringToZip(zipOutputStream, readmeFileName, readmeContent);
            fichiersGeneres.put("Documentation", List.of(readmeFileName));

            // Traiter chaque référentiel
            for (String reference : application.getConfiguration().dataDescription().keySet()) {
                String fileName = application.getConfiguration().findData(reference)
                        .map(StandardDataDescription::submission)
                        .map(Submission::fileNameParsing)
                        .map(Submission.SubmissionFileNameParsing::createExampleSubmissionFileName)
                        .orElse("%s.csv".formatted(reference));
                String dataCsvFilePath = "%1$s/%2$s".formatted(reference, fileName);

                try {
                    if (withData && dataService.getDataFromStoredCsvStream(zipOutputStream, application.getName(), reference, application, locale)) {
                        referentielsAvecDonnees.add(reference);
                        fichiersGeneres.computeIfAbsent(reference, k -> new ArrayList<>()).add(dataCsvFilePath);
                    } else {
                        zipOutputStream.putNextEntry(new ZipEntry(dataCsvFilePath));
                        application.getConfiguration().dataDescription().get(reference).buildEmptyFile(zipOutputStream);
                        zipOutputStream.flush();
                        zipOutputStream.closeEntry();
                        referentielsAvecDonneesExemple.add(reference);
                        fichiersGeneres.computeIfAbsent(reference, k -> new ArrayList<>()).add(dataCsvFilePath);
                    }
                } catch (Exception e) {
                    log.error("Erreur lors du traitement du référentiel " + reference, e);
                    referentielsEnErreur.add(reference);
                }
            }
        } catch (Exception e) {
            log.error("Erreur générale lors de la création du bundle", e);
            referentielsEnErreur.add("ERREUR_GENERALE");
        }

        return new BuildBundleReport(application, referentielsAvecDonnees, fichiersGeneres, referentielsAvecDonneesExemple, referentielsEnErreur, locale);
    }

    private void writeFileToZip(ZipOutputStream zipOutputStream, String fileName, URL resourceUrl) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(fileName));
        byte[] fileBytes = Resources.toByteArray(resourceUrl);
        zipOutputStream.write(fileBytes);
        zipOutputStream.closeEntry();
    }

    private void writeStringToZip(ZipOutputStream zipOutputStream, String fileName, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(fileName));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
    }


    public String sendZipLinkByMail(Path filePath, MessageInformations messageInformations, OreSiUser currentUser) {
        return switch (messageInformations) {
            case DownloadDatasetQuery downloadDatasetQuery -> {
                try {
                    FileSenderInternationalisation fileSenderInternationalisation = new FileSenderInternationalisationForDownloadDatasetQuery(downloadDatasetQuery);
                    Locale locale = downloadDatasetQuery.outPut().locale();
                    String applicationName = Optional.ofNullable(
                                    fileSenderInternationalisation.getInternationnalizedApplication(locale)
                            )
                            .orElseGet(() -> Optional.ofNullable(fileSenderInternationalisation.getInternationnalizedApplication(fileSenderInternationalisation.getDefaultLanguage()))
                                    .orElse(downloadDatasetQuery.application().getName()));
                    String dataName = Optional.ofNullable(fileSenderInternationalisation.getInternationnalizedDataName(locale, downloadDatasetQuery.dataName()))
                            .orElseGet(() -> Optional.ofNullable(fileSenderInternationalisation.getInternationnalizedDataName(fileSenderInternationalisation.getDefaultLanguage(), downloadDatasetQuery.dataName()))
                                    .orElse(downloadDatasetQuery.dataName()));
                    ;
                    String subject = fileSenderInternationalisation.subjectPattern();
                    String message = fileSenderInternationalisation.messagePattern();
                    String internationnalizedDataName = fileSenderInternationalisation.getInternationnalizedDataName(
                            Locale.of(downloadDatasetQuery.getLanguage()),
                            dataName
                    );

                    String messageWithReport = fileSenderInternationalisation.
                            mailMessagefor(message.formatted(internationnalizedDataName),
                                    FileSenderRepository.DEFAULT_TRANSFER_DAYS_VALID);
                    FileInfos fileInfos = new FileInfos(
                            applicationName,
                            dataName,
                            filePath,
                            currentUser.getEmail(),
                            subject.formatted(applicationName),
                            messageWithReport);
                    String downloadUrl = fileRepository.postTransfer(fileInfos);
                    log.info("Adresse de téléchargement : %s".formatted(downloadUrl));
                    /*sendUploadZipEmail(
                            currentUser.getEmail(),
                            subject.formatted(applicationName),
                            message.formatted(dataName),
                            downloadUrl,
                            fileSenderInternationalisation,
                            internationnalizedDataName
                    );*/
                    yield downloadUrl;
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            case BuildBundleReport buildBundleReport -> {
                try {
                    FileSenderInternationalisation fileSenderInternationalisation = new FileSenderInternationalisationForBuildBundleReport(buildBundleReport);
                    Locale locale = buildBundleReport.locale();

                    String applicationName = Optional.ofNullable(
                            fileSenderInternationalisation.getInternationnalizedApplication(locale)
                    ).orElseGet(() -> Optional.ofNullable(
                            fileSenderInternationalisation.getInternationnalizedApplication(fileSenderInternationalisation.getDefaultLanguage())
                    ).orElse(buildBundleReport.applicationName().getName()));

                    String subject = fileSenderInternationalisation.subjectPattern().formatted(applicationName);
                    String message = fileSenderInternationalisation.messagePattern().formatted(applicationName);


                    String emailMessage = fileSenderInternationalisation.mailMessagefor(message, FileSenderRepository.DEFAULT_TRANSFER_DAYS_VALID);

                    /*sendUploadZipEmail(
                            currentUser.getEmail(),
                            subject,
                            emailMessage,
                            FileSenderRepository.DEFAULT_TRANSFER_DAYS_VALID,
                            fileSenderInternationalisation,
                            applicationName
                    );*/
                    FileInfos fileInfos = new FileInfos(
                            applicationName,
                            "BulkUploadZIP",
                            filePath,
                            currentUser.getEmail(),
                            subject,
                            message
                    );
                    String downloadUrl = fileRepository.postTransfer(fileInfos);
                    log.info("Adresse de téléchargement du ZIP pour dépôt en masse : %s".formatted(downloadUrl));

                    yield downloadUrl;
                } catch (Exception e) {
                    log.error("Erreur lors de la création ou de l'envoi du ZIP pour dépôt en masse", e);
                    throw new RuntimeException("Erreur lors de la création ou de l'envoi du ZIP pour dépôt en masse", e);
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + messageInformations);
        };
    }


    @Async
    public void sendUploadZipEmail(
            final String to,
            final String subject,
            final String message,
            final String downloadUrl,
            FileSenderInternationalisation fileSenderInternationalisation,
            String internationnalizedDataName) {
        final SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(to);
        mailMessage.setFrom("openadom@inrae.fr");
        mailMessage.setSubject(subject);
        mailMessage.setText(
                String.format(
                        fileSenderInternationalisation.mailMessagefor(message, FileSenderRepository.DEFAULT_TRANSFER_DAYS_VALID),
                        internationnalizedDataName
                )
        );
        mailSender.send(mailMessage);
    }

    public DataRepositoryWithBuffer getNewDataRepositoryWithBuffer(Application application) {
        return new DataRepositoryWithBuffer(application, repository.getRepository(application).data());
    }
}
