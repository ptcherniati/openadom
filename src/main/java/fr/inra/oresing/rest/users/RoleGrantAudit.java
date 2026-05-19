package fr.inra.oresing.rest.users;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * 1 row de {@code oa_audit.role_grant_audit} mappee en memoire .
 *
 * <p>{@code applicationId} nullable = role global ( ex {@code openAdomAdmin} ) .
 * {@code grantedBy} nullable tolerance pour les backfills futurs ou les
 * operations system ; toujours rempli par l'UI admin .
 *
 * <p>{@code action} = {@code "GRANT"} ou {@code "REVOKE"} ( verifie par
 * le CHECK constraint en BDD ) .
 */
public record RoleGrantAudit(
        UUID            id,
        UUID            userId,
        String          roleName,
        UUID            applicationId,
        String          action,
        OffsetDateTime  grantedAt,
        UUID            grantedBy
) {
    public static final String ACTION_GRANT  = "GRANT";
    public static final String ACTION_REVOKE = "REVOKE";

    public boolean isGrant()  { return ACTION_GRANT.equals(action);  }
    public boolean isRevoke() { return ACTION_REVOKE.equals(action); }

    public boolean isGlobal() { return applicationId == null; }
}
