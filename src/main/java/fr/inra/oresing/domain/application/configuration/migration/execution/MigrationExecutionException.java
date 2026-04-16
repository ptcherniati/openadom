package fr.inra.oresing.domain.application.configuration.migration.execution;

import fr.inra.oresing.domain.application.configuration.migration.action.MigrationAction;

public class MigrationExecutionException extends RuntimeException {

    public MigrationExecutionException(String message, MigrationAction action, Throwable cause) {
        super("Action [%s] : %s".formatted(action.id(), message), cause);
    }
}