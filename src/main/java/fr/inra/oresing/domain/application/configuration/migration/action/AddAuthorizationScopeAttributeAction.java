package fr.inra.oresing.domain.application.configuration.migration.action;

import fr.inra.oresing.domain.application.configuration.migration.context.MigrationContext;
import fr.inra.oresing.domain.application.configuration.migration.plan.ActionPhase;
import fr.inra.oresing.domain.exceptions.application.SiOreConfigurationFormatException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import lombok.extern.java.Log;

import java.util.Map;
import java.util.Set;

@Log
public record AddAuthorizationScopeAttributeAction(String id, String dataName) implements MigrationAction {
    @Override
    public ActionPhase phase() {
        return ActionPhase.POST;
    }

    @Override
    public boolean requiresUserConfirmation() {
        return false;
    }

    @Override
    public String description() {
        return "Ajouter l'attribut " + dataName + " au type requiredAuthorizations";
    }

    @Override
    public void execute(MigrationContext context) {
        try {
            context.authenticationPort().resetRole();
            boolean added = context.migrationApplicationPort()
                    .addReferenceToAuthorizationScope(context.applicationName(), Set.of(dataName()));
            log.info("""
                    %4$s : 
                    Modification du schéma %1$s pour ajout d'identificateur de requiredAuthorizationScope.
                    Ajout des identificateurs %2$s.
                    Resultat : %3$s identificateurs ajoutés.
                    """
                    .formatted(id(), context.applicationName(), dataName(), added));
        } catch (Exception e) {
            throw new SiOreConfigurationFormatException(
                    ConfigurationException.ADDING_AUTHORIZATION_SCOPE_ATTRIBUTES_ERROR,
                    Map.of("newIdentifiers", dataName(),
                            "message", e.getMessage())
            );
        } finally {
            context.authenticationPort().setRoleForClient();
        }
    }
}