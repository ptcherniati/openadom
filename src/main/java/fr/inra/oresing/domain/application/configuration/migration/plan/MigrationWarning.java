package fr.inra.oresing.domain.application.configuration.migration.plan;

/**
 * Avertissement de migration
 */
public record MigrationWarning(
        String id,
        String message,
        String impact,
        Severity severity,
        boolean blocking
) {

    public enum Severity {
        INFO,
        WARNING,
        CRITICAL
    }

    public static MigrationWarning critical(String id, String message, String impact) {
        return new MigrationWarning(id, message, impact, Severity.CRITICAL, true);
    }

    public static MigrationWarning warning(String id, String message, String impact) {
        return new MigrationWarning(id, message, impact, Severity.WARNING, false);
    }

    public static MigrationWarning info(String id, String message) {
        return new MigrationWarning(id, message, null, Severity.INFO, false);
    }
}