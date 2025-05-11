package fr.inra.oresing.rest.services;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.ApplicationInformation;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCreatorRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationCreator;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationManager;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.application.NoSuchApplicationException;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.persistence.ApplicationRepository;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.flyway.MigrateService;
import fr.inra.oresing.rest.MultiYaml;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.model.application.ApplicationLightResult;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.model.authorization.AuthorizationsForUserResult;
import fr.inra.oresing.rest.model.authorization.CurrentApplicationUserRolesResult;
import fr.inra.oresing.rest.reactive.ReactiveProgression;
import fr.inra.oresing.rest.reactive.ReactiveTypeInfo;
import fr.inra.oresing.rest.reactive.ReactiveTypeProgress;
import fr.inra.oresing.rest.reactive.ReactiveTypeResult;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@Transactional(readOnly = true)
public class ApplicationService implements ServiceContainerBean {
    public static final String APPLICATION_NAME = "applicationName";
    public static final String START = "start";
    public static final String END = "end";
    @Setter
    private ServiceContainer serviceContainer;
    private final OreSiRepository repository;
    private final BeanFactory beanFactory;
    private final OreSiApiRequestContext request;

    public ApplicationService(OreSiRepository repository, BeanFactory beanFactory, OreSiApiRequestContext request) {
        this.repository = repository;
        this.beanFactory = beanFactory;
        this.request = request;
    }

    public Application getApplication(final String nameOrId) {
        // TODO filtre tag hidden boucle sur les reference et les datatypes
        serviceContainer.authenticationService().setRoleForClient();
        return getApplicationRepository().findApplication(nameOrId);
    }

    public Application getApplicationOrApplicationAccordingToRights(final String nameOrId) {
        serviceContainer.authenticationService().setRoleForClient();
        try {
            return getApplicationRepository().findApplication(nameOrId);
        } catch (final NoSuchApplicationException e) {
            serviceContainer.authenticationService().setRoleAdmin();
            return getApplicationRepository()
                    .findApplication(nameOrId)
                    .applicationAccordingToRights();
        }
    }

    private ApplicationRepository getApplicationRepository() {
        return repository.application();
    }

    @Transactional()
    public void createApplication(
            ReactiveProgression.CreateApplicationProgression progression,
            final String name,
            final MultipartFile configurationFile,
            final String comment) {
        Objects.requireNonNull(OreSiApiRequestContext.getAuthentication()
                        .map(OreSiAuthenticationToken::getSystemPersona)
                        .filter(ApplicationCreator.class::isInstance)
                        .map(ApplicationCreator.class::cast)
                        .orElse(null))
                .canCreateApplication(name);
        progression.pushProgression();

        final Application application = new Application();
        application.setName(name);
        try {
            changeApplicationConfiguration(
                    comment,
                    progression,
                    application,
                    configurationFile,
                    this::initApplication);
        } catch (final OreSiTechnicalException | IOException e) {
            if ("fr.inra.oresing.domain.authorization.privilegeassessor.exception"
                    .equals(e.getClass().getPackage().getName())) {
                progression.fluxSink().error(e);
                assert e instanceof OreSiTechnicalException;
                throw (OreSiTechnicalException) e;
            }
            progression.fluxSink().error(e);
            progression.fluxSink().complete();
            return;
        }
        ReactiveProgression.CreateApplicationProgression progression1 = progression.withSubLabel("viewCreation");
        progression1.pushMessage(START, Map.of(APPLICATION_NAME, application.getName()));
        progression1.incrementAndPush(i -> ReactiveProgression.CreateApplicationProgression.PROGRESSION_FOR_READING_CONFIGURATION.progress());
        //TODO
        progression1.pushMessage(END, Map.of(APPLICATION_NAME, application.getName()));
        progression1.pushResult(application.getId());
        progression1.incrementAndPush(i -> 1D);
        progression1.complete();
    }

    public ApplicationResult buildOpenAdom(final Application application, final String[] filter) {
        final List<ApplicationInformation> filters = Arrays.stream(filter)
                .map(ApplicationInformation::valueOf)
                .toList();
        final boolean withDatatypes = filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.DATATYPE);
        final boolean withReferenceType = filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.REFERENCETYPE);
        final boolean withConfiguration = filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.CONFIGURATION);
        final boolean withRightsRequest = filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.RIGHTSREQUEST);
        List<ApplicationResult.DataSynthesis> referenceSynthesis = withReferenceType ? serviceContainer.dataService().getReferenceSynthesis(application) : List.of();
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
        final Map<String, StandardDataDescription> referenceComponents = Maps.filterValues(application.getConfiguration().dataDescription(), cd -> cd.tags().contains(Tag.ReferenceTag.instance()) || !cd.tags().contains(Tag.DataTag.instance()));
        final Map<String, StandardDataDescription> datatypeComponents = Maps.filterValues(application.getConfiguration().dataDescription(), cd -> cd.tags().contains(Tag.DataTag.instance()));

        final Map<String, Node> referencesNodes = application.getConfiguration().hierarchicalNodes().stream()
                .filter(node -> referenceComponents.containsKey(node.nodeName()))
                .collect(Collectors.toMap(Node::nodeName, Function.identity()));
        final Map<String, Node> datatypesNodes = application.getConfiguration().hierarchicalNodes().stream()
                .filter(node -> datatypeComponents.containsKey(node.nodeName()))
                .collect(Collectors.toMap(Node::nodeName, Function.identity()));

        HashSet<String> dataNames = new HashSet<>(datatypeComponents.keySet());
        dataNames.addAll(referenceComponents.keySet());
        Map<String, Map<AuthorizationsForUserResult.Roles, Boolean>> authorizations = withDatatypes || withReferenceType ? serviceContainer.authorizationService().getAuthorizationsDataRights(application, dataNames) : new HashMap<>();
        final Configuration configuration = withConfiguration ? application.getConfiguration() : null;
        CurrentUserRoles currentUserRoles = serviceContainer.authenticationService().getCurrentUserRoles();
        //referenceSynthesis,
        return new ApplicationResult(
                application.getId().toString(),
                Optional.of(application).map(Application::getName).orElseThrow(IllegalArgumentException::new),
                application.findApplicationDescription()
                        .map(ApplicationDescription::comment)
                        .orElseGet(String::new),
                application.findApplicationDescription()
                        .map(ApplicationDescription::comment)
                        .orElse(""),
                application.getConfigFile(),
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
    }

    @Transactional
    public Application initApplication(final Application application) {
        MigrateService migrateService = beanFactory.getBean(MigrateService.class);
        migrateService.setApplication(application);
        serviceContainer.authenticationService().resetRole();
        final OreSiUserRole creator = serviceContainer.authenticationService().getUserRole(request.getRequestUserId());

        migrateService.runFlywayUpdate(creator);
        serviceContainer.authenticationService().setRoleForClient();
        repository.application().store(application);
        return application;
    }

    public Application modifySchemaApplication(final Application application) {
        MigrateService migrateService = beanFactory.getBean(MigrateService.class);
        migrateService.setApplication(application);
        serviceContainer.authenticationService().resetRole();
        migrateService.updateSchema();
        serviceContainer.authenticationService().setRoleForClient();
        repository.application().store(application);
        serviceContainer.authenticationService().setRoleAdmin();
        repository.application().updateAuthorizationIndexes(application);
        serviceContainer.authenticationService().setRoleForClient();
        return application;
    }

    @Transactional()
    public UUID changeApplicationConfiguration(
            ReactiveProgression.ChangeApplicationProgression progression,
            final String nameOrId,
            final MultipartFile configurationFile,
            final String comment) {
        final Application application = getApplication(nameOrId);
        Objects.requireNonNull(OreSiApiRequestContext.getAuthentication()
                        .map(OreSiAuthenticationToken::getApplicationPersona)
                        .filter(ApplicationManager.class::isInstance)
                        .map(ApplicationManager.class::cast)
                        .orElse(null))
                .canUpdateApplication();
        ReactiveProgression.ChangeApplicationProgression progression1 = progression;
        final ReactiveProgression.ChangeApplicationProgressionMessagesLabel baseMessage = new ReactiveProgression.ChangeApplicationProgressionMessagesLabel();
        progression1.pushProgression();
        serviceContainer.relationalService().dropViews(nameOrId);
        serviceContainer.authenticationService().setRoleForClient();
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
        final Configuration newConfiguration = serviceContainer.applicationService().getApplication(applicationName).getConfiguration();
        //TODO test à faire entre version ancienne et nouvelle
        final Version oldVersion = oldConfiguration.applicationDescription().version();
        final Version newVersion = newConfiguration.applicationDescription().version();
        try {
            Preconditions.checkArgument(newVersion.compareTo(oldVersion) > 0, "l'application " + applicationName + " est déjà dans la version " + oldVersion);
        } catch (final IllegalArgumentException e) {
            progression1.pushError(e);
            progression1.pushMessage(START, Map.of("application", applicationName, "oldVersion", oldVersion.version(), "newVersion", newVersion.version()));
        }
        if (log.isInfoEnabled()) {
            log.info("va migrer les données de {} de la version actuelle {} à la nouvelle version {}", applicationName, oldVersion, newVersion);
        }
        final DataRepository dataRepository = repository.getRepository(application).data();
        //TODO migration

        // on supprime l'ancien fichier vu que tout c'est bien passé
        final boolean deleted = repository.getRepository(application).binaryFile().delete(oldConfigFileId);
        Preconditions.checkState(deleted);

        serviceContainer.relationalService().createViews(nameOrId);
        return application.getId();
    }

    private ReactiveProgression.ChangeOrCreateApplicationProgression changeApplicationConfiguration(
            String comment,
            final ReactiveProgression.ChangeOrCreateApplicationProgression progression,
            Application application,
            final MultipartFile configurationFile,
            final UnaryOperator<Application> createOrModifySchema) throws IOException {
        String applicationName = application.getName();
        OreSiUser currentUser = serviceContainer.authenticationService().getCurrentUser();
        UUID oldApplicationId = application.getId();
        ReactiveProgression.ChangeOrCreateApplicationProgression progressionForConfiguration = (ReactiveProgression.ChangeOrCreateApplicationProgression) progression.withSubLabel("configuration");
        progressionForConfiguration.pushMessage("rights.checking", Map.of(APPLICATION_NAME, applicationName));
        progressionForConfiguration = (ReactiveProgression.ChangeOrCreateApplicationProgression) progressionForConfiguration.incrementAndPush(i -> i + .02);
        final ReactiveProgression.ChangeOrCreateApplicationProgression progressionForParsingConfiguration = (ReactiveProgression.ChangeOrCreateApplicationProgression) progressionForConfiguration.withSubLabel("parsingConfiguration");
        if (Objects.requireNonNull(configurationFile.getOriginalFilename()).matches(".*\\.zip")) {
            InputStream multiYAmlInput = MultiYaml.parseConfigurationBytes(configurationFile);
            progressionForParsingConfiguration.pushMessage("forMulti", Map.of(APPLICATION_NAME, applicationName));
            application = ApplicationConfigurationService.parseConfigurationBytes(comment, progressionForConfiguration, FileBomResolver.of(multiYAmlInput));
        } else {
            progressionForParsingConfiguration.pushMessage("forSingle", Map.of(APPLICATION_NAME, applicationName));
            application = ApplicationConfigurationService.parseConfigurationBytes(comment, progressionForConfiguration, FileBomResolver.of(configurationFile.getInputStream()));
        }
        if (application == null) {
            return progression;
        }
        progression.fluxSink().next(new ReactiveTypeInfo<>("application.configuration.create.register.start", Map.of(APPLICATION_NAME, applicationName)));

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
        progressionForParsingConfiguration.pushMessage("endparsing", Map.of(APPLICATION_NAME, applicationName));
        String comment1 = configuration.applicationDescription().comment();
        Optional.of(applicationName).ifPresent(application::setName);
        try {
            application = createOrModifySchema.apply(application);
            final UUID confId = serviceContainer.binaryFileService().storeFile(application, configurationFile, comment1, null);
            application.setConfigFile(confId);
            Timestamp charteLastTimestamp = Optional.ofNullable(serviceContainer.additionalFileService().findCharte(application))
                    .map(AdditionalBinaryFile::getUpdateDate)
                    .map(Timestamp::valueOf)
                    .orElse((new Timestamp(Long.MIN_VALUE)));
            application.setLastChartes(charteLastTimestamp);
            repository.application().store(application);
            final ReactiveProgression.ChangeOrCreateApplicationProgression progressionRegister = (ReactiveProgression.ChangeOrCreateApplicationProgression) progressionForParsingConfiguration.up();
            progressionRegister.pushMessage("register", Map.of(APPLICATION_NAME, applicationName));

            return progressionRegister;
        } catch (final BadSqlGrammarException bsge) {
            throw new NotApplicationCreatorRightsException(applicationName, currentUser.getAuthorizations());
        }
    }

    public void getApplications(ReactiveProgression.GetApplicationProgression progression, final List<ApplicationInformation> filters) {
        serviceContainer.authenticationService().setRoleForClient();
        final List<Application> applicationForUser = repository.application().findAll();
        serviceContainer.authenticationService().setRoleAdmin();
        final Stream<Application> applicationForAdmin = repository.application().findAllStream();
        final AtomicLong progres = new AtomicLong(0);
        progression.fluxSink().next(new ReactiveTypeProgress(progres.get()));
        CurrentUserRoles currentUserRoles = serviceContainer.authenticationService().getCurrentUserRoles();
        Function<Application, List<ApplicationResult.DataSynthesis>> getDatynthesis = application ->
                serviceContainer.dataService().getReferenceSynthesis(application);

        applicationForAdmin
                .map(application -> applicationForUser.stream()
                        .filter(app -> app.getId().equals(application.getId()))
                        .findAny()
                        .orElse(application.applicationAccordingToRights())
                )
                .map(application -> application.filterFieldsAndHidden(filters))
                .map(application -> ApplicationLightResult.of(application, currentUserRoles, getDatynthesis.apply(application)))
                .forEach(application -> {
                    progression.fluxSink().next(new ReactiveTypeResult(application));
                    final double prog = progres.incrementAndGet() / ((double) applicationForUser.size());
                    progression.fluxSink().next(new ReactiveTypeProgress(prog));
                });
        progression.complete();
    }

    public Application validateConfiguration(final ReactiveProgression.CreateApplicationProgression fluxSink, final MultipartFile file) {
        try {
            final Application application;
            if (Objects.requireNonNull(file.getOriginalFilename()).matches(".*\\.zip")) {
                application = ApplicationConfigurationService.unzipConfiguration(file, fluxSink);
            } else {
                application = ApplicationConfigurationService.parseConfigurationBytes(null, fluxSink, FileBomResolver.of(file.getInputStream()));
            }
            return application;
        } catch (final IOException e) {
            fluxSink.pushError(e);
            return null;
        }
    }

}