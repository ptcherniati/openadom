package fr.inra.oresing.rest.services;

import com.google.common.base.Preconditions;
import com.google.common.collect.Maps;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.additionalfiles.AdditionalBinaryFile;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.ApplicationInformation;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationMode;
import fr.inra.oresing.domain.application.configuration.migration.MigrationProperties;
import fr.inra.oresing.domain.application.configuration.migration.report.MigrationResult;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationCreatorRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationCreator;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationManager;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.application.NoSuchApplicationException;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.domain.repository.authorization.role.OreSiUserRole;
import fr.inra.oresing.persistence.ApplicationRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.flyway.MigrateService;
import fr.inra.oresing.rest.MultiYaml;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.authentication.OreSiAuthenticationToken;
import fr.inra.oresing.rest.model.application.ApplicationLightResult;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.model.authorization.AuthorizationsForUserResult;
import fr.inra.oresing.rest.model.authorization.CurrentApplicationUserRolesResult;
import fr.inra.oresing.rest.reactive.ReactiveEventHelper;
import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.reactive.ReactiveTypeProgress;
import fr.inra.oresing.rest.reactive.ReactiveTypeResult;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Timestamp;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Component
@Transactional(readOnly = true)
public class ApplicationService {
    public static final String APPLICATION_NAME = "applicationName";
    public static final String MIGRATION_REPORT = "migrationreport";
    public static final String START = "start";
    public static final String END = "end";
    private final OreSiRepository repository;
    private final MigrationService migrationService;
    private final MigrateService flywayMigrateService;
    private final MigrationProperties migrationProperties;
    @Setter
    private ServiceContainer serviceContainer;

    public ApplicationService(
            OreSiRepository repository,
            ServiceContainer serviceContainer,
            MigrationService migrationService,
            MigrateService flywayMigrateService,
            MigrationProperties migrationProperties) {
        this.repository = repository;
        this.serviceContainer = serviceContainer;
        this.migrationService = migrationService;
        this.flywayMigrateService = flywayMigrateService;
        this.migrationProperties = migrationProperties;
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
            Consumer<ReactiveResult> sink,
            final String name,
            final DataFile dataFile,
            final String comment) throws IOException {
        
        ReactiveEventHelper eventHelper = new ReactiveEventHelper(sink, "application.createConfiguration");
        
        Objects.requireNonNull(OreSiApiRequestContext.getAuthentication()
                        .map(OreSiAuthenticationToken::getSystemPersona)
                        .filter(ApplicationCreator.class::isInstance)
                        .map(ApplicationCreator.class::cast)
                        .orElse(null))
                .canCreateApplication(name);
        eventHelper.pushProgress(0D);

        final Application application = new Application();
        application.setName(name);
        try {
            changeApplicationConfiguration(
                    comment,
                    eventHelper,
                    application,
                    dataFile);
        } catch (final OreSiTechnicalException | IOException e) {
            if ("fr.inra.oresing.domain.authorization.privilegeassessor.exception"
                    .equals(e.getClass().getPackage().getName())) {
                eventHelper.pushError(e);
                assert e instanceof OreSiTechnicalException;
                throw (OreSiTechnicalException) e;
            }
            eventHelper.pushError(e);
            eventHelper.complete();
            return;
        }
        ReactiveEventHelper helperViewCreation = eventHelper.withSubLabel("viewCreation");
        helperViewCreation.pushMessage(START, Map.of(APPLICATION_NAME, application.getName()));
        helperViewCreation.incrementAndPush(i -> 0.5D); // Valeur arbitraire basée sur l'ancien code
        //TODO
        helperViewCreation.pushMessage(END, Map.of(APPLICATION_NAME, application.getName()));
        helperViewCreation.pushResult(application.getId());
        helperViewCreation.incrementAndPush(i -> 1D);
        helperViewCreation.complete();
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

    @Transactional()
    public UUID changeApplicationConfiguration(
            Consumer<ReactiveResult> sink,
            final String nameOrId,
            final DataFile dataFile,
            final String comment) {
        
        ReactiveEventHelper eventHelper = new ReactiveEventHelper(sink, "application.ChangeConfiguration");
        
        final Application application = getApplication(nameOrId);
        Objects.requireNonNull(OreSiApiRequestContext.getAuthentication()
                        .map(OreSiAuthenticationToken::getApplicationPersona)
                        .filter(ApplicationManager.class::isInstance)
                        .map(ApplicationManager.class::cast)
                        .orElse(null))
                .canUpdateApplication();
        
        eventHelper.pushProgress(0D);
        serviceContainer.relationalService().dropViews(nameOrId);
        serviceContainer.authenticationService().setRoleForClient();
        final Configuration oldConfiguration = application.getConfiguration();
        final UUID oldConfigFileId = application.getConfigFile();
        try {
            changeApplicationConfiguration(comment,
                    eventHelper,
                    application,
                    dataFile
            ).up().withSubLabel("migrate");
        } catch (final IOException e) {
            eventHelper.pushError(e);
        }
        final String applicationName = application.getName();
        final Configuration newConfiguration = serviceContainer.applicationService().getApplication(applicationName).getConfiguration();
        //TODO test à faire entre version ancienne et nouvelle
        final Version oldVersion = oldConfiguration.applicationDescription().version();
        final Version newVersion = newConfiguration.applicationDescription().version();
        final boolean bypass = migrationProperties.isBypassConfigurationCheck();
        try {
            if (!bypass) {
                // Mode sécurisé (openadom.migration.bypass-configuration-check=false) :
                // la version doit obligatoirement être incrémentée, sinon la mise à jour est bloquée.
                Preconditions.checkArgument(newVersion.compareTo(oldVersion) > 0,
                        "l'application " + applicationName + " est déjà dans la version " + oldVersion);
            } else if (newVersion.compareTo(oldVersion) <= 0) {
                // Bypass actif (valeur par défaut) : comportement historique —
                // avertissement non bloquant, la mise à jour se poursuit.
                if (log.isWarnEnabled()) {
                    log.warn("openadom.migration.bypass-configuration-check=true : " +
                            "version non incrémentée ({} → {}) pour '{}', mise à jour acceptée malgré tout",
                            oldVersion, newVersion, applicationName);
                }
                eventHelper.pushMessage(START, Map.of("application", applicationName,
                        "oldVersion", oldVersion.version(), "newVersion", newVersion.version()));
            }
        } catch (final IllegalArgumentException e) {
            eventHelper.pushError(e);
        }
        if (log.isInfoEnabled()) {
            log.info("va migrer les données de {} de la version actuelle {} à la nouvelle version {}", applicationName, oldVersion, newVersion);
        }

        final boolean deleted = repository.getRepository(application).binaryFile().delete(oldConfigFileId);
        Preconditions.checkState(deleted);

        serviceContainer.relationalService().createViews(nameOrId);
        return application.getId();
    }

    private ReactiveEventHelper changeApplicationConfiguration(
            String comment,
            final ReactiveEventHelper eventHelper,
            Application application,
            final DataFile configurationFile) throws IOException {
            Application oldApplication = application;
        String applicationName = oldApplication.getName();
        OreSiUser currentUser = serviceContainer.authenticationService().getCurrentUser();
        UUID oldApplicationId = oldApplication.getId();
        ReactiveEventHelper helperConfiguration = eventHelper.withSubLabel("configuration");
        helperConfiguration.pushMessage("rights.checking", Map.of(APPLICATION_NAME, applicationName));
        helperConfiguration.incrementAndPush(i -> i + .02);
        final ReactiveEventHelper helperParsingConfiguration = helperConfiguration.withSubLabel("parsingConfiguration");
        Application newApplication;
        if (Objects.requireNonNull(configurationFile.fileName()).matches(".*\\.zip")) {
            InputStream multiYAmlInput = MultiYaml.parseConfigurationBytes(configurationFile);
            helperParsingConfiguration.pushMessage("forMulti", Map.of(APPLICATION_NAME, applicationName));
            newApplication = ApplicationConfigurationService.parseConfigurationBytes(applicationName, comment, helperConfiguration, FileBomResolver.of(multiYAmlInput));
        } else {
            helperParsingConfiguration.pushMessage("forSingle", Map.of(APPLICATION_NAME, applicationName));
            newApplication = ApplicationConfigurationService.parseConfigurationBytes(applicationName, comment, helperConfiguration, FileBomResolver.of(configurationFile.inputStream()));
        }
        if (newApplication == null) {
            return eventHelper;
        }
        eventHelper.pushMessage("application.configuration.create.register.start", Map.of(APPLICATION_NAME, applicationName));

        final Configuration configuration = oldApplication.getConfiguration();
        // Pour la création, oldApplicationId est null → store() générera un UUID
        // Pour la mise à jour, on préserve l'ID existant pour l'upsert
        newApplication.setId(oldApplicationId);

        if (configuration != null) {
            // Cas mise à jour : on préserve la structure des données et des
            // fichiers additionnels de l'ancienne configuration ; ce contrat
            // existe pour les chemins en aval qui itèrent {@code Application#getData()}
            // ( ex. {@code AuthorizationService} ). La levée de cette préservation
            // pour les modifications structurelles ( renommage / suppression /
            // ajout de datatype , de composant , de naturalKey , de submission )
            // est traitée dans le chantier "datatype vide ⇒ tout autorisé"
            // ( use-case séparé ).
            newApplication.setData(new ArrayList<>(configuration.dataDescription().keySet()));
            // NOTE : la ligne précédente {@code newApplication.setConfiguration(configuration)}
            // a été supprimée volontairement. Elle écrasait la configuration
            // fraîchement parsée du YAML uploadé par celle de l'ancienne
            // application , empêchant {@code MigrationService.executeMigration}
            // de détecter un quelconque changement et bloquant l'application
            // des updates non structurelles ( {@code applicationDescription.comment} ,
            // {@code Internationalizations.tags} , {@code OA_i18n} ). Régression
            // introduite par le commit {@code 12ee48f} lors du renommage de
            // la variable {@code application => oldApplication} : avant ce
            // commit l'instruction était un self-assign inoffensif , après
            // elle est devenue un écrasement.
            final Optional<Set<String>> additionalsFiles = Optional.ofNullable(configuration.additionalFiles())
                    .map(Map::keySet);
            if (additionalsFiles.isPresent()) {
                newApplication.setAdditionalFiles(new LinkedList<>(additionalsFiles.get()));
            } else {
                newApplication.setAdditionalFiles(List.of());
            }
        }
        // Cas création : newApplication conserve sa propre configuration issue du YAML

        helperParsingConfiguration.pushMessage("endparsing", Map.of(APPLICATION_NAME, applicationName));
        // Pour la création, pas d'ancienne configuration → on utilise le commentaire passé en paramètre
        String comment1 = Optional.ofNullable(configuration)
                .map(Configuration::applicationDescription)
                .map(ApplicationDescription::comment)
                .orElse(comment);
        Optional.of(applicationName).ifPresent(newApplication::setName);
        try {
            if (configuration == null) {
                // Création: initialiser le schéma applicatif via Flyway.
                flywayMigrateService.setApplication(newApplication);
                serviceContainer.authenticationService().resetRole();
                final OreSiUserRole creator = serviceContainer.authenticationService().getUserRole(OreSiApiRequestContext.getRequestUserId());
                flywayMigrateService.runFlywayUpdate(creator);
                serviceContainer.authenticationService().setRoleForClient();
                // L'application doit exister dans la table application AVANT d'insérer dans binaryfile
                // (contrainte FK binaryfile_application_fkey). On la persiste ici sans configFile (null autorisé),
                // puis on la met à jour après l'enregistrement du fichier de configuration.
                repository.application().store(newApplication);
            } else {
                // Mise à jour: exécuter le pipeline de migration de configuration.
                migrationService.executeMigration(oldApplication, newApplication, Set.of(), MigrationMode.EXECUTE);
            }
            final UUID confId = serviceContainer.binaryFileService().storeFile(newApplication, configurationFile, comment1, null);
            newApplication.setConfigFile(confId);
            Timestamp charteLastTimestamp = Optional.ofNullable(serviceContainer.additionalFileService().findCharte(newApplication))
                    .map(AdditionalBinaryFile::getUpdateDate)
                    .map(Timestamp::valueOf)
                    .orElse((new Timestamp(Long.MIN_VALUE)));
            newApplication.setLastChartes(charteLastTimestamp);
            repository.application().store(newApplication);
            // Propager l'ID généré (création) ou existant (MAJ) vers oldApplication
            // afin que createApplication puisse pousser application.getId() comme résultat
            oldApplication.setId(newApplication.getId());
            final ReactiveEventHelper helperRegister = helperParsingConfiguration.up();
            helperRegister.pushMessage("register", Map.of(APPLICATION_NAME, applicationName));

            return helperRegister;
        } catch (final BadSqlGrammarException bsge) {
            log.error("""
                    ╔══════════════════════════════════════════════════════════════════╗
                    ║  BadSqlGrammarException lors de la création de l'application    ║
                    ╠══════════════════════════════════════════════════════════════════╣
                    ║  Application  : {}
                    ║  SQL échoué   : {}
                    ║  Cause racine : {}
                    ╚══════════════════════════════════════════════════════════════════╝
                    """,
                    applicationName,
                    bsge.getSql(),
                    bsge.getCause() != null ? bsge.getCause().getMessage() : bsge.getMessage(),
                    bsge);
            throw new NotApplicationCreatorRightsException(applicationName, currentUser.getAuthorizations());
        }
    }

    public Flux<ReactiveResult> getApplications(final List<ApplicationInformation> filters) {
        return Mono.fromCallable(() -> {
                    // Charger les applications
                    serviceContainer.authenticationService().setRoleForClient();
                    final List<Application> applicationForUser = repository.application().findAll();

                    serviceContainer.authenticationService().setRoleAdmin();
                    try (Stream<Application> stream = repository.application().findAllStream()) {
                        return stream
                                .map(application -> applicationForUser.stream()
                                        .filter(app -> app.getId().equals(application.getId()))
                                        .findAny()
                                        .orElse(application.applicationAccordingToRights())
                                )
                                .toList();
                    }
                })
                .flatMapMany(allApplications -> {
                    CurrentUserRoles currentUserRoles = serviceContainer.authenticationService().getCurrentUserRoles();
                    int total = allApplications.size();

                    return Flux.fromIterable(allApplications)
                            .index()
                            .flatMap(tuple -> {
                                long index = tuple.getT1();
                                Application app = tuple.getT2();

                                // Traiter l'application
                                Application filtered = app.filterFieldsAndHidden(filters);
                                List<ApplicationResult.DataSynthesis> synthesis = serviceContainer.dataService().getReferenceSynthesis(filtered);
                                ApplicationLightResult result = ApplicationLightResult.of(filtered, currentUserRoles, synthesis);

                                // Calculer la progression
                                double progress = (index + 1.0) / total;

                                // Émettre résultat + progression
                                return Flux.just(
                                        new ReactiveTypeResult<>(result),
                                        new ReactiveTypeProgress(progress)
                                );
                            });
                });
    }



    /**
     * Pure YAML validation, no DB writes. Runs OUTSIDE any transaction
     * (NOT_SUPPORTED) so that the inner getApplication() call - which throws
     * NoSuchApplicationException for new apps as part of the DRY_RUN migration
     * preview - cannot poison an enclosing read-only transaction with a
     * rollback-only marker. Without this, the silent catch (Exception) below
     * suppresses the exception but Spring's TransactionInterceptor still
     * marks the tx rollback-only -> UnexpectedRollbackException at commit ->
     * NDJSON stream cut mid-way without REACTIVE_RESULT.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public Application validateConfiguration(final Consumer<ReactiveResult> sink, final DataFile file) {
        ReactiveEventHelper eventHelper = new ReactiveEventHelper(sink, "application.createConfiguration");
        try {
            final Application application;
            if (Objects.requireNonNull(file.fileName()).matches(".*\\.zip")) {
                application = ApplicationConfigurationService.unzipConfiguration(file, eventHelper);
            } else {
                application = ApplicationConfigurationService.parseConfigurationBytes("", "", eventHelper, FileBomResolver.of(file.inputStream()));
            }
            Application oldApplication;
            try {
                oldApplication = serviceContainer.applicationService().getApplication(application.getName());
                final MigrationResult migrationResult = migrationService.executeMigration(oldApplication, application, new HashSet<>(), MigrationMode.DRY_RUN);
                eventHelper.pushMessage("migration",
                        Map.of(
                                APPLICATION_NAME, oldApplication.getName(),
                                MIGRATION_REPORT, new fr.inra.oresing.persistence.JsonRowMapper<>().toJson(migrationResult)
                        )
                );
            } catch (Exception e) {
                //nothing to do
            }
            return application;
        } catch (final IOException e) {
            eventHelper.pushError(e);
            return null;
        }
    }

}