package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.migration.change.ConfigurationChange;
import fr.inra.oresing.domain.application.configuration.migration.change.DataAdded;
import fr.inra.oresing.domain.application.configuration.migration.change.IgnorableChange;
import fr.inra.oresing.domain.application.configuration.migration.change.UnresolvableChange;
import fr.inra.oresing.domain.application.configuration.migration.context.DataInfo;
import fr.inra.oresing.domain.application.configuration.migration.context.FactKeys;
import fr.inra.oresing.domain.application.configuration.migration.context.MigrationContext;
import fr.inra.oresing.domain.application.configuration.migration.context.SchemaInfo;
import fr.inra.oresing.domain.application.configuration.migration.execution.MigrationExecutor;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationMode;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationPlan;
import fr.inra.oresing.domain.application.configuration.migration.plan.MigrationStatus;
import fr.inra.oresing.domain.application.configuration.migration.report.MigrationResult;
import fr.inra.oresing.domain.port.AuthenticationPort;
import fr.inra.oresing.domain.port.MigrationApplicationPort;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.config.MigrationProperties;
import fr.inra.oresing.rest.data.migration.MigrationConfiguration;
import org.apache.commons.collections4.CollectionUtils;
import org.javers.core.Javers;
import org.javers.core.diff.Diff;
import org.javers.core.diff.changetype.map.EntryAdded;
import org.javers.core.diff.changetype.map.EntryRemoved;
import org.javers.core.diff.changetype.map.MapChange;
import org.javers.core.metamodel.object.ValueObjectId;
import org.jeasy.rules.api.Facts;
import org.jeasy.rules.api.Rules;
import org.jeasy.rules.api.RulesEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Service
public class MigrationService {

    private static final Logger log = LoggerFactory.getLogger(MigrationService.class);

    private final ServiceContainer serviceContainer;
    private final Javers javers;
    private final MigrationConfiguration.MigrationRepositories migrationRepositories;
    private final RulesEngine rulesEngine;
    private final Rules migrationRules;
    private final JsonRowMapper jsonRowMapper;
    private final MigrationExecutor executor;
    private final MigrationProperties migrationProperties;

    public MigrationService(ServiceContainer serviceContainer, Javers javers,
                            MigrationConfiguration.MigrationRepositories migrationRepositories,
                            RulesEngine rulesEngine, Rules migrationRules,
                            JsonRowMapper jsonRowMapper, MigrationExecutor executor,
                            MigrationProperties migrationProperties) {
        this.serviceContainer = serviceContainer;
        this.javers = javers;
        this.migrationRepositories = migrationRepositories;
        this.rulesEngine = rulesEngine;
        this.migrationRules = migrationRules;
        this.jsonRowMapper = jsonRowMapper;
        this.executor = executor;
        this.migrationProperties = migrationProperties;
    }

    private List<ConfigurationChange> toConfigurationChange(
            MapChange change,
            Configuration from,
            Configuration to, List<MapChange> mapChanges) {
        return switch (change.getChangeType()) {
            case PROPERTY_VALUE_CHANGED -> {
                final List<EntryAdded> entryAddedChanges = change.getEntryAddedChanges();
                final List<EntryRemoved> entryRemovedChanges = change.getEntryRemovedChanges();
                yield switch (change.getPropertyName()) {
                    case "dataDescription" -> {
                        if (!entryAddedChanges.isEmpty()) {
                            yield entryAddedChanges.stream()
                                    .map(entryAdded -> {
                                        final String key = (String) entryAdded.getKey();
                                        final Object valueObj = entryAdded.getValue();
                                        if (valueObj instanceof ValueObjectId valueObjectId) {
                                            StandardDataDescription realObject =  to.dataDescription().get(key);
                                            return new DataAdded(key, realObject);
                                        }
                                        return unresolvableChange(change);
                                    })
                                    .toList();
                        }
                        if (!entryRemovedChanges.isEmpty()) {
                            yield entryRemovedChanges.stream()
                                    .map(entryremoved -> {
                                        final String key = (String) entryremoved.getKey();
                                        final Object valueObj = entryremoved.getValue();
                                        return unresolvableChange(change);
                                    })
                                    .toList();
                        } else {
                            yield List.of(unresolvableChange(change));
                        }
                    }
                    case "data" ->{
                        if( "fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations".equals(change.getAffectedGlobalId().getTypeName())){
                            if (!entryAddedChanges.isEmpty()) {
                                yield entryAddedChanges.stream()
                                        .map(entryAdded -> {
                                            final String key = (String) entryAdded.getKey();
                                            final boolean canBeIgnored = mapChanges.stream()
                                                    .map(MapChange::getEntryAddedChanges)
                                                    .flatMap(List<EntryAdded>::stream)
                                                    .map(EntryAdded::getValue)
                                                    .filter(ValueObjectId.class::isInstance)
                                                    .map(ValueObjectId.class::cast)
                                                    .map(ValueObjectId::getFragment)
                                                    .anyMatch("dataDescription/%s".formatted(key)::equals);
                                            return canBeIgnored?new IgnorableChange():unresolvableChange(change);
                                        })
                                        .toList();
                            }
                            if (!entryRemovedChanges.isEmpty()) {
                                yield entryRemovedChanges.stream()
                                        .map(entryRemoved -> {
                                            final String key = (String) entryRemoved.getKey();
                                            final boolean canBeIgnored = mapChanges.stream()
                                                    .map(MapChange::getEntryRemovedChanges)
                                                    .flatMap(List<EntryRemoved>::stream)
                                                    .map(EntryRemoved::getValue)
                                                    .filter(ValueObjectId.class::isInstance)
                                                    .map(ValueObjectId.class::cast)
                                                    .map(ValueObjectId::getFragment)
                                                    .anyMatch("dataDescription/%s".formatted(key)::equals);
                                            return canBeIgnored?new IgnorableChange():unresolvableChange(change);
                                        })
                                        .toList();
                            } else {
                                yield List.of(unresolvableChange(change));
                            }
                        }
                        yield List.of(unresolvableChange(change));
                    }
                    default -> List.of(unresolvableChange(change));
                };
            }
            default -> List.of(unresolvableChange(change));
        };
    }

    private ConfigurationChange unresolvableChange(MapChange change) {
        String leftValue = jsonRowMapper.toJson(change.getLeft());
        String rightValue = jsonRowMapper.toJson(change.getRight());
        return new UnresolvableChange(change.getPropertyName(), change.getChangeType().name(), leftValue, rightValue);
    }

    private void evaluateChange(
            ConfigurationChange change,
            MigrationPlan plan,
            MigrationContext context
    ) {
        Facts facts = FactKeys.buildFacts(change, plan,context);
        rulesEngine.fire(migrationRules, facts);
    }

    /**
     * Exécute ou simule la migration d'une configuration OpenADOM.
     *
     * <p>Lorsque {@code openadom.migration.bypass-configuration-check=true} (valeur par défaut),
     * les vérifications de compatibilité du schéma sont ignorées : tout changement de
     * configuration est accepté sans contrainte, quelle que soit la nature du changement.
     *
     * <p>Lorsque {@code openadom.migration.bypass-configuration-check=false} (mode sécurisé),
     * les changements incompatibles avec le schéma existant (changements non résolus,
     * actions nécessitant une confirmation) bloquent la mise à jour et retournent un résultat
     * avec statut {@link MigrationStatus#REQUIRES_CONFIRMATION} ou {@link MigrationStatus#FAILED}.
     *
     * @param oldApplication    ancienne configuration de l'application
     * @param newApplication    nouvelle configuration à appliquer
     * @param acceptedWarnings  identifiants des avertissements explicitement acceptés par l'utilisateur
     * @param migrationMode     {@link MigrationMode#DRY_RUN} pour simuler, {@link MigrationMode#EXECUTE} pour appliquer
     * @return le résultat de la migration
     */
    public MigrationResult executeMigration(
            Application oldApplication,
            Application newApplication,
            Set<String> acceptedWarnings,
            MigrationMode migrationMode
    ) {
        final boolean bypass = migrationProperties.isBypassConfigurationCheck();
        if (bypass && log.isDebugEnabled()) {
            log.debug("openadom.migration.bypass-configuration-check=true : " +
                    "vérifications de compatibilité désactivées — la mise à jour s'effectue sans contrainte.");
        }

        List<ConfigurationChange> changes = new LinkedList<>();
        Configuration oldConfig = oldApplication.getConfiguration();
        Configuration newConfig = newApplication.getConfiguration();
        List<String> oldData = oldApplication.getData();
        List<String> newData = newApplication.getData();
        final Collection<String> addedData = CollectionUtils.subtract(newData, oldData);
        final Collection<String> removedData = CollectionUtils.subtract(oldData, newData);
        addedData.forEach(newRef -> {
            final StandardDataDescription newDataDescription = newApplication.getConfiguration().dataDescription().get(newRef);
            changes.add(new DataAdded(newRef, newDataDescription));
            oldApplication.getConfiguration().dataDescription().put(newRef, newDataDescription);
            oldApplication.getConfiguration().i18n().getData().put(newRef, newApplication.getConfiguration().i18n().getData().get(newRef));
        });

        final List<ConfigurationChange> remainingChanges = detectChanges(oldConfig, newConfig);
        if (!remainingChanges.isEmpty()) {
            if (bypass) {
                // Mode permissif : on logue les changements non résolus mais on ne bloque pas
                if (log.isWarnEnabled()) {
                    log.warn("Changements de configuration non résolus détectés pour l'application '{}' " +
                                    "(bypass activé — mise à jour acceptée malgré tout) : {}",
                            newConfig.applicationDescription().name(),
                            remainingChanges.stream()
                                    .map(Object::toString)
                                    .reduce((a, b) -> a + ", " + b)
                                    .orElse("(aucun détail disponible)"));
                }
                // On continue avec les seuls changements résolus (addedData)
            } else {
                // Mode sécurisé : on bloque la mise à jour
                return MigrationResult.onError(remainingChanges);
            }
        }

        if (changes.isEmpty()) {
            return MigrationResult.noChanges();
        }

        String applicationName = newConfig.applicationDescription().name();
        MigrationContext context = buildContext(applicationName, oldApplication, newApplication);
        MigrationPlan plan = new MigrationPlan(
                migrationMode,
                acceptedWarnings,
                MigrationStatus.PENDING
        );

        for (ConfigurationChange change : changes) {
            evaluateChange(change, plan, context);
        }

        if (!bypass && plan.status().get() == MigrationStatus.REQUIRES_CONFIRMATION) {
            // Mode sécurisé : on bloque si une confirmation est requise
            return MigrationResult.blocked(plan.warnings());
        }
        if (bypass && plan.status().get() == MigrationStatus.REQUIRES_CONFIRMATION && log.isWarnEnabled()) {
            // Mode permissif : on logue l'avertissement mais on continue l'exécution
            log.warn("Des avertissements bloquants ont été détectés pour l'application '{}' " +
                            "(bypass activé — exécution forcée) : {}",
                    applicationName,
                    plan.warnings());
        }

        return executor.execute(plan, context);
    }

    private MigrationContext buildContext(
            String applicationName,
            Application oldApplication,
            Application newApplication
    ) {
        SchemaInfo schemaInfo = migrationRepositories.repository().getRepository(applicationName).getSchemaInfo();
        DataInfo dataInfo = migrationRepositories.repository().getRepository(applicationName).getDataInfo();

        AuthenticationPort authenticationPort = serviceContainer.authenticationService();

        MigrationApplicationPort migrationApplicationPort = new MigrationApplicationPort() {
            @Override
            public void storeApplication(Application application) {
                migrationRepositories.repository().application().store(application);
            }

            @Override
            public boolean addReferenceToAuthorizationScope(String appName, java.util.Collection<String> identifiers) {
                return migrationRepositories.repository().application().addReferenceToAuthorizationScope(appName, identifiers);
            }

            @Override
            public void updateAuthorizationIndexes(Application application) {
                migrationRepositories.repository().application().updateAuthorizationIndexes(application);
            }
        };

        return new MigrationContext(
                migrationApplicationPort,
                authenticationPort,
                applicationName,
                oldApplication,
                newApplication,
                schemaInfo,
                dataInfo
        );
    }

    private List<ConfigurationChange> detectChanges(Configuration from, Configuration to) {
        final Diff javersCompare = javers.compare(from, to);
        final List<MapChange> mapChanges = javersCompare.getChangesByType(MapChange.class);
        return mapChanges.stream()
                .map(mapChange -> toConfigurationChange(mapChange, from, to, mapChanges))
                .flatMap(List::stream)
                .toList();
    }
}