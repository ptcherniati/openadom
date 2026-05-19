package fr.inra.oresing.rest.users;

import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.persistence.AuthenticationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Bean dedie a l'ecriture des rows d'audit GRANT / REVOKE dans
 * {@code oa_audit.role_grant_audit} . Extrait de {@link UserRolesService}
 * pour respecter le Single Responsibility Principle :
 * <ul>
 *   <li>{@link UserRolesService} : list / detail / grant / revoke metier ;</li>
 *   <li>{@link UserRolesAuditLogger} : ecriture log audit + resolution
 *       du caller UUID via {@link AuthenticationService} .</li>
 * </ul>
 *
 * <p>Best-effort : un echec d'INSERT dans {@code oa_audit} ne bloque pas
 * l'operation metier ( log warn + continue ) . Pattern identique a
 * {@code WorkflowLogWriter} .
 */
@Component
@Slf4j
public class UserRolesAuditLogger {

    private final RoleGrantAuditRepository repository;
    private final AuthenticationService    authenticationService;

    @Autowired
    public UserRolesAuditLogger(RoleGrantAuditRepository repository,
                                AuthenticationService authenticationService) {
        this.repository            = repository;
        this.authenticationService = authenticationService;
    }

    /**
     * Log une attribution de role . Recupere le caller UUID depuis le
     * contexte d'authentification courant ( {@code null} si introuvable ,
     * ex contexte system ) .
     */
    public void logGrant(UUID userId, String roleName, UUID applicationId) {
        logAction(userId, roleName, applicationId, RoleGrantAudit.ACTION_GRANT);
    }

    /** Log une revocation de role . Symetrique a {@link #logGrant} . */
    public void logRevoke(UUID userId, String roleName, UUID applicationId) {
        logAction(userId, roleName, applicationId, RoleGrantAudit.ACTION_REVOKE);
    }

    private void logAction(UUID userId, String roleName, UUID applicationId, String action) {
        UUID callerUuid = resolveCallerUuid();
        try {
            repository.logAction(userId, roleName, applicationId, action, callerUuid);
        } catch (RuntimeException ex) {
            log.warn("role_grant_audit insert failed for user {} role {} app {} action {} : {}",
                    userId, roleName, applicationId, action, ex.getMessage());
        }
    }

    /**
     * Recupere l'UUID du caller courant ( admin / applicationManager ) via
     * le {@link AuthenticationService} . Retourne {@code null} si aucun
     * user authentifie ( ex pre-flight system , tests sans security
     * context ) plutot que de lever - l'audit reste best-effort .
     */
    private UUID resolveCallerUuid() {
        try {
            CurrentUserRoles caller = authenticationService.getCurrentUserRoles();
            return caller == null ? null : caller.userId();
        } catch (RuntimeException ex) {
            log.debug("resolveCallerUuid failed : {}", ex.getMessage());
            return null;
        }
    }
}
