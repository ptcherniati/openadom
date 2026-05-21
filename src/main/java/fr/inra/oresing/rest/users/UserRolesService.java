package fr.inra.oresing.rest.users;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.persistence.ApplicationRepository;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Domain service backing the oa-live "Users" admin tab.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>List users with in-memory filtering (login, app, role, account state);</li>
 *   <li>Build a {@link UserDTO.UserDetail} aggregating applications and global roles,
 *       chaque role enrichi de sa trace d'attribution ( date + auteur ) lue depuis
 *       {@code oa_audit.role_grant_audit} ;</li>
 *   <li>Grant roles by delegating to {@link AuthenticationService} (which keeps PG roles,
 *       authorizations Set and RLS policies in sync), puis logger un GRANT dans
 *       {@code oa_audit.role_grant_audit} ;</li>
 *   <li>Revoke roles by reusing the {@code deleteUserRight*} methods for known scopes
 *       and falling back to a raw {@code REVOKE} for {@code reader}/{@code writer} roles,
 *       puis logger un REVOKE .</li>
 * </ul>
 *
 * <p>L'audit est best-effort : un echec d'INSERT dans oa_audit ne bloque pas le metier
 * ( log warn + continue ) , symetrique au pattern workflow_log .
 *
 * <p>Authorization: every entry point requires either {@code openAdomAdmin} (global
 * scope) or being {@code applicationManager_X} for the application of the modified
 * role. The service performs the check itself; controllers only need to ensure the
 * caller is authenticated.
 */
@Service
@Slf4j
public class UserRolesService {

    /** Regex matching a PG role of the form {@code <appUUID>_<roleType>}. */
    private static final Pattern APP_ROLE_PATTERN =
            Pattern.compile("(.*)_(applicationManager|userManager|reader|writer)");

    private static final String ROLE_APPLICATION_MANAGER = "applicationManager";
    private static final String ROLE_USER_MANAGER = "userManager";
    private static final String ROLE_READER = "reader";
    private static final String ROLE_WRITER = "writer";
    private static final String ROLE_OPEN_ADOM_ADMIN = "openAdomAdmin";
    private static final String ERR_UNKNOWN_GLOBAL_ROLE = "Unknown global role: ";
    private static final String ERR_UNKNOWN_APPLICATION_ROLE = "Unknown application role: ";

    /** Application-scoped role names allowed by the grant/revoke endpoints. */
    private static final Set<String> APP_SCOPED_ROLES =
            Set.of(ROLE_APPLICATION_MANAGER, ROLE_USER_MANAGER, ROLE_READER, ROLE_WRITER);

    /** Global role names allowed by the grant/revoke endpoints. */
    private static final Set<String> GLOBAL_ROLES =
            Set.of(ROLE_OPEN_ADOM_ADMIN, ROLE_USER_MANAGER);

    private final UserRepository userRepository;
    private final ApplicationRepository applicationRepository;
    private final AuthenticationService authenticationService;
    private final JdbcTemplate jdbcTemplate;
    private final RoleGrantAuditRepository auditRepository;
    private final UserRolesAuditLogger auditLogger;

    @Autowired
    public UserRolesService(UserRepository userRepository,
                                 ApplicationRepository applicationRepository,
                                 AuthenticationService authenticationService,
                                 JdbcTemplate jdbcTemplate,
                                 RoleGrantAuditRepository auditRepository,
                                 UserRolesAuditLogger auditLogger) {
        this.userRepository = userRepository;
        this.applicationRepository = applicationRepository;
        this.authenticationService = authenticationService;
        this.jdbcTemplate = jdbcTemplate;
        this.auditRepository = auditRepository;
        this.auditLogger = auditLogger;
    }

    // ---------------------------------------------------------------- //
    //  list                                                            //
    // ---------------------------------------------------------------- //

    /**
     * Lists users visible to the current caller, applying optional filters.
     *
     * @param loginFilter   case-insensitive substring filter on login / email (nullable)
     * @param appIdFilter   UUID of an application; user must have at least one role on it (nullable)
     * @param roleFilter    role name (one of {@link #APP_SCOPED_ROLES} or {@link #GLOBAL_ROLES}); nullable
     * @param stateFilter   account state filter (nullable; "all" or blank to disable)
     * @return matching summaries, sorted by login ASC
     */
    public List<UserDTO.UserSummary> listUsers(String loginFilter,
                                                    UUID appIdFilter,
                                                    String roleFilter,
                                                    String stateFilter) {
        CurrentUserRoles caller = requireAdminOrAnyManager();
        boolean isAdmin = caller.isOpenAdomAdmin();

        String normalizedLogin = loginFilter == null ? null : loginFilter.trim().toLowerCase(Locale.ROOT);
        String normalizedState = (stateFilter == null || stateFilter.isBlank()
                || "all".equalsIgnoreCase(stateFilter)) ? null : stateFilter;
        String normalizedRole  = (roleFilter == null || roleFilter.isBlank()) ? null : roleFilter;

        return userRepository.findAll().stream()
                .filter(u -> matchesLogin(u, normalizedLogin))
                .filter(u -> matchesState(u, normalizedState))
                .map(u -> {
                    CurrentUserRoles userRoles = loadUserRoles(u);
                    Map<String, List<String>> apps = userRoles.applicationRoles();
                    int appCount = apps.size();
                    int globalCount = countGlobalRoles(userRoles);
                    return new UserSummaryWithRoles(u, userRoles, apps, appCount, globalCount);
                })
                .filter(s -> matchesAppFilter(s.apps, appIdFilter))
                .filter(s -> matchesRoleFilter(s, normalizedRole, appIdFilter))
                .filter(s -> isAdmin || isVisibleToManager(s, caller))
                .sorted((a, b) -> compareLogins(a.user, b.user))
                .map(s -> UserDTO.UserSummary.of(s.user, s.appCount, s.globalCount))
                .toList();
    }

    // ---------------------------------------------------------------- //
    //  detail                                                          //
    // ---------------------------------------------------------------- //

    /**
     * Returns the full view (applications + global roles) of a single user, or
     * an empty Optional if the user does not exist or is not visible to the caller.
     *
     * <p>Chaque role retourne est enrichi de sa trace d'attribution
     * ( {@link UserDTO.RoleAttribution} : date , UUID admin , login admin )
     * via une lecture de {@code oa_audit.role_grant_audit} aggregee en memoire .
     * Les roles attribues avant l'activation de l'audit ( V15 ) ont
     * {@code grantedAt = null , grantedBy = null} .
     */
    public Optional<UserDTO.UserDetail> findDetail(UUID userId) {
        requireAdminOrAnyManager();
        CurrentUserRoles caller = authenticationService.getCurrentUserRoles();
        Optional<OreSiUser> userOpt = userRepository.tryFindById(userId);
        if (userOpt.isEmpty()) {
            return Optional.empty();
        }
        OreSiUser user = userOpt.get();
        CurrentUserRoles userRoles = loadUserRoles(user);
        Map<String, List<String>> appsByUuid = userRoles.applicationRoles();

        if (!caller.isOpenAdomAdmin() && !isVisibleToManagerByApps(appsByUuid, caller)) {
            return Optional.empty();
        }

        // Charge la trace d'attribution en 1 query + resolve les logins des
        // admins grantor en 1 batch ( perf : evite N+1 sur findByLogin ) .
        AttributionLookup lookup = buildAttributionLookup(userId);

        List<UserDTO.RoleAttribution> globals = collectGlobalRoles(userRoles).stream()
                .map(name -> toAttribution(name, /*roleName*/ name, /*sql*/ name,
                        UserDTO.SCOPE_GLOBAL, /*appId*/ null, lookup))
                .toList();

        List<UserDTO.AppMembership> apps = appsByUuid.entrySet().stream()
                .map(e -> buildAppMembership(e.getKey(), e.getValue(), lookup))
                .flatMap(Optional::stream)
                .sorted((a, b) -> a.applicationName().compareToIgnoreCase(b.applicationName()))
                .toList();

        return Optional.of(new UserDTO.UserDetail(
                user.getId(),
                user.getLogin(),
                user.getEmail(),
                user.getAccountstate() != null ? user.getAccountstate().name() : null,
                globals,
                apps));
    }

    // ---------------------------------------------------------------- //
    //  grant                                                           //
    // ---------------------------------------------------------------- //

    /**
     * Grants a role to a user. Delegates to {@link AuthenticationService} when a
     * dedicated helper exists ({@code applicationManager}, {@code userManager},
     * {@code openAdomAdmin}); otherwise performs a raw {@code GRANT} for the
     * {@code reader}/{@code writer} variants. En cas de succes , log un row GRANT
     * dans {@code oa_audit.role_grant_audit} ( best-effort ) .
     *
     * @throws IllegalArgumentException if the role name is unknown
     * @throws AccessDeniedException    if the caller lacks the right scope
     */
    @Transactional
    public void grantRole(UUID userId, UUID applicationId, String roleName) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roleName, "roleName");
        requireGrantPermission(applicationId, roleName);
        ensureUserExists(userId);

        if (applicationId == null) {
            grantGlobalRole(userId, roleName);
        } else {
            Application application = applicationRepository.findApplication(applicationId);
            grantApplicationRole(userId, application, roleName);
        }
        auditLogger.logGrant(userId, roleName, applicationId);
    }

    // ---------------------------------------------------------------- //
    //  revoke                                                          //
    // ---------------------------------------------------------------- //

    /**
     * Revokes a role from a user. Mirrors {@link #grantRole(UUID, UUID, String)}:
     * delegates to {@code deleteUserRight*} when available, otherwise runs a raw
     * {@code REVOKE} and refreshes the user's {@code authorizations} Set so that
     * subsequent reads reflect the current PG state. En cas de succes , log un
     * row REVOKE dans {@code oa_audit.role_grant_audit} ( best-effort ) .
     *
     * @throws IllegalArgumentException if the role name is unknown
     * @throws AccessDeniedException    if the caller lacks the right scope
     */
    @Transactional
    public void revokeRole(UUID userId, UUID applicationId, String roleName) {
        Objects.requireNonNull(userId, "userId");
        Objects.requireNonNull(roleName, "roleName");
        requireGrantPermission(applicationId, roleName);
        ensureUserExists(userId);

        if (applicationId == null) {
            revokeGlobalRole(userId, roleName);
        } else {
            Application application = applicationRepository.findApplication(applicationId);
            revokeApplicationRole(userId, application, roleName);
        }
        auditLogger.logRevoke(userId, roleName, applicationId);
    }

    // ---------------------------------------------------------------- //
    //  permission helpers                                              //
    // ---------------------------------------------------------------- //

    /**
     * Whether the candidate user is visible to {@code caller} under the given scope.
     * Exposed for unit tests and for controllers that need to filter rows pre-DTO.
     */
    public boolean isVisibleToCurrentUser(OreSiUser candidate, CurrentUserRoles caller) {
        if (candidate == null || caller == null) {
            return false;
        }
        if (caller.isOpenAdomAdmin()) {
            return true;
        }
        CurrentUserRoles candidateRoles = loadUserRoles(candidate);
        return isVisibleToManagerByApps(candidateRoles.applicationRoles(), caller);
    }

    private CurrentUserRoles requireAdminOrAnyManager() {
        CurrentUserRoles caller = authenticationService.getCurrentUserRoles();
        if (caller == null || caller.memberOf() == null) {
            throw new AccessDeniedException("No authenticated user");
        }
        if (caller.isOpenAdomAdmin()) {
            return caller;
        }
        boolean isAnyManager = caller.memberOf().stream()
                .anyMatch(role -> {
                    var m = APP_ROLE_PATTERN.matcher(role);
                    return m.matches()
                            && (ROLE_APPLICATION_MANAGER.equals(m.group(2))
                                || ROLE_USER_MANAGER.equals(m.group(2)));
                });
        if (!isAnyManager) {
            throw new AccessDeniedException(
                    "Reserved to openAdomAdmin or application/user managers");
        }
        return caller;
    }

    private void requireGrantPermission(UUID applicationId, String roleName) {
        validateRoleName(applicationId, roleName);
        CurrentUserRoles caller = authenticationService.getCurrentUserRoles();
        if (caller == null) {
            throw new AccessDeniedException("No authenticated user");
        }
        if (caller.isOpenAdomAdmin()) {
            return;
        }
        if (applicationId == null) {
            // Global role grants are admin-only by design (no manager scope to inherit from).
            throw new AccessDeniedException("Only openAdomAdmin can grant global roles");
        }
        Application application = applicationRepository.tryFindApplication(applicationId.toString())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown application: " + applicationId));
        if (!caller.applicationManagerOf(application)) {
            throw new AccessDeniedException(
                    "Reserved to applicationManager of " + application.getName());
        }
    }

    private void validateRoleName(UUID applicationId, String roleName) {
        if (applicationId == null) {
            if (!GLOBAL_ROLES.contains(roleName)) {
                throw new IllegalArgumentException(
                        ERR_UNKNOWN_GLOBAL_ROLE + roleName + " (expected one of " + GLOBAL_ROLES + ")");
            }
        } else {
            if (!APP_SCOPED_ROLES.contains(roleName)) {
                throw new IllegalArgumentException(
                        ERR_UNKNOWN_APPLICATION_ROLE + roleName + " (expected one of " + APP_SCOPED_ROLES + ")");
            }
        }
    }

    // ---------------------------------------------------------------- //
    //  attribution lookup ( read-side )                                //
    // ---------------------------------------------------------------- //
    //
    // Audit write-side ( grant / revoke logging ) extrait dans
    // {@link UserRolesAuditLogger} pour respecter SRP . Cette section
    // est la read-side qui consomme la table audit pour enrichir le
    // detail user expose par l'API .

    /**
     * Charge la trace d'attribution complete pour un user + resoud les logins
     * des admins grantor . Capsule a la fois la map "derniere action GRANT
     * par (role, app)" et la table de resolution UUID -> login .
     */
    private AttributionLookup buildAttributionLookup(UUID userId) {
        List<RoleGrantAudit> rows;
        try {
            rows = auditRepository.findByUser(userId);
        } catch (RuntimeException ex) {
            log.debug("role_grant_audit findByUser failed for {} : {}", userId, ex.getMessage());
            rows = List.of();
        }
        // Derniere action GRANT par (role, applicationId) - on prend la plus
        // recente avant un eventuel REVOKE pour les roles encore actifs .
        // Note : la presence reelle du role est determinee en amont par
        // userRoles.applicationRoles() , l'audit sert uniquement a fournir
        // la trace ( date + auteur ) , pas la verite metier .
        Map<String, RoleGrantAudit> latestGrant = new HashMap<>();
        Set<UUID> grantorIds = new HashSet<>();
        for (RoleGrantAudit row : rows) {
            if (!row.isGrant()) continue;
            String key = attributionKey(row.roleName(), row.applicationId());
            // findByUser retourne deja DESC ; ne remplace pas une entree plus recente .
            latestGrant.putIfAbsent(key, row);
            if (row.grantedBy() != null) grantorIds.add(row.grantedBy());
        }
        Map<UUID, String> loginByUuid = resolveLoginsSequentially(grantorIds);
        return new AttributionLookup(latestGrant, loginByUuid);
    }

    /**
     * Resoud login par UUID en boucle sequentielle ( 1 query par UUID
     * distinct ) . Volume faible attendu : 1-5 admins distincts par user
     * typiquement ; passer en vrai batch SQL ( {@code WHERE id IN (...)} )
     * serait premature . Pour les UUIDs introuvables ( admin supprime ) ,
     * la map ne contient pas l'entree .
     */
    private Map<UUID, String> resolveLoginsSequentially(Set<UUID> uuids) {
        if (uuids == null || uuids.isEmpty()) return Map.of();
        Map<UUID, String> out = new HashMap<>();
        for (UUID id : uuids) {
            try {
                userRepository.tryFindById(id)
                        .map(OreSiUser::getLogin)
                        .ifPresent(login -> out.put(id, login));
            } catch (RuntimeException ex) {
                log.debug("resolveLogin failed for {} : {}", id, ex.getMessage());
            }
        }
        return out;
    }

    /**
     * Construit une {@link UserDTO.RoleAttribution} pour un role donne en
     * croisant les infos PG ( {@code sqlRoleName} ) et l'audit trace .
     */
    private UserDTO.RoleAttribution toAttribution(String roleKeyName,
                                                  String roleLogical,
                                                  String sqlRoleName,
                                                  String scope,
                                                  UUID applicationId,
                                                  AttributionLookup lookup) {
        RoleGrantAudit audit = lookup.latestGrant.get(attributionKey(roleLogical, applicationId));
        java.time.OffsetDateTime grantedAt = audit == null ? null : audit.grantedAt();
        UUID grantedBy = audit == null ? null : audit.grantedBy();
        String grantedByLogin = grantedBy == null ? null : lookup.loginByUuid.get(grantedBy);
        return new UserDTO.RoleAttribution(
                roleLogical, sqlRoleName, scope, applicationId,
                grantedAt, grantedBy, grantedByLogin);
    }

    private static String attributionKey(String roleName, UUID applicationId) {
        return (applicationId == null ? "global" : applicationId.toString()) + "|" + roleName;
    }

    /** Bundle tx-scoped des donnees d'attribution chargees une fois par findDetail . */
    private record AttributionLookup(
            Map<String, RoleGrantAudit> latestGrant,
            Map<UUID, String>           loginByUuid) {
    }

    // ---------------------------------------------------------------- //
    //  internal helpers                                                //
    // ---------------------------------------------------------------- //

    private void ensureUserExists(UUID userId) {
        if (userRepository.tryFindById(userId).isEmpty()) {
            throw new IllegalArgumentException("Unknown user: " + userId);
        }
    }

    private void grantGlobalRole(UUID userId, String roleName) {
        switch (roleName) {
            case ROLE_OPEN_ADOM_ADMIN -> authenticationService.addUserRightopenAdomAdmin(userId);
            case ROLE_USER_MANAGER -> {
                // No global userManager helper exists; this case is reserved for future
                // expansion of the global scope. We refuse explicitly to avoid silent
                // no-ops that would mislead admins.
                throw new IllegalArgumentException(
                        "Global userManager grant is not supported; grant per application");
            }
            default -> throw new IllegalArgumentException(ERR_UNKNOWN_GLOBAL_ROLE + roleName);
        }
    }

    private void grantApplicationRole(UUID userId, Application application, String roleName) {
        switch (roleName) {
            case ROLE_APPLICATION_MANAGER -> authenticationService.addUserRightApplicationManager(userId, application);
            case ROLE_USER_MANAGER -> authenticationService.addUserRightUserManager(userId, application);
            case ROLE_READER -> grantRawRole(userId, buildAppRoleSqlName(application.getId(), ROLE_READER));
            case ROLE_WRITER -> grantRawRole(userId, buildAppRoleSqlName(application.getId(), ROLE_WRITER));
            default -> throw new IllegalArgumentException(ERR_UNKNOWN_APPLICATION_ROLE + roleName);
        }
    }

    private void revokeGlobalRole(UUID userId, String roleName) {
        switch (roleName) {
            case ROLE_OPEN_ADOM_ADMIN -> authenticationService.deleteUserRightopenAdomAdmin(userId);
            case ROLE_USER_MANAGER -> throw new IllegalArgumentException(
                    "Global userManager revoke is not supported; revoke per application");
            default -> throw new IllegalArgumentException(ERR_UNKNOWN_GLOBAL_ROLE + roleName);
        }
    }

    private void revokeApplicationRole(UUID userId, Application application, String roleName) {
        switch (roleName) {
            case ROLE_APPLICATION_MANAGER -> authenticationService.deleteUserRightApplicationManager(userId, application);
            case ROLE_USER_MANAGER -> authenticationService.deleteUserRightUserManager(userId, application);
            case ROLE_READER -> revokeRawRole(userId, buildAppRoleSqlName(application.getId(), ROLE_READER));
            case ROLE_WRITER -> revokeRawRole(userId, buildAppRoleSqlName(application.getId(), ROLE_WRITER));
            default -> throw new IllegalArgumentException(ERR_UNKNOWN_APPLICATION_ROLE + roleName);
        }
    }

    /**
     * Raw GRANT path for {@code reader}/{@code writer} roles which do not have a
     * dedicated helper in {@link AuthenticationService}. Updates the user's
     * {@code authorizations} Set so that the persisted state matches PG.
     */
    private void grantRawRole(UUID userId, String pgRoleName) {
        String sql = "GRANT \"" + pgRoleName + "\" TO \"" + userId + "\"";
        jdbcTemplate.execute(sql);
        OreSiUser user = userRepository.findById(userId);
        Set<String> authorizations = new HashSet<>(user.getAuthorizations());
        authorizations.add(pgRoleName);
        userRepository.updateAuthorizations(userId, authorizations);
    }

    /**
     * Raw REVOKE path symmetric to {@link #grantRawRole(UUID, String)}.
     */
    private void revokeRawRole(UUID userId, String pgRoleName) {
        String sql = "REVOKE \"" + pgRoleName + "\" FROM \"" + userId + "\"";
        jdbcTemplate.execute(sql);
        OreSiUser user = userRepository.findById(userId);
        Set<String> authorizations = new HashSet<>(user.getAuthorizations());
        authorizations.remove(pgRoleName);
        userRepository.updateAuthorizations(userId, authorizations);
    }

    /**
     * Builds the PG role name for an application-scoped role
     * (same convention as {@code OreSiRightOnApplicationRole.getAsSqlRole}).
     */
    static String buildAppRoleSqlName(UUID applicationId, String roleType) {
        String name = applicationId + "_" + roleType;
        return name.substring(0, Math.min(name.length(), 63));
    }

    private CurrentUserRoles loadUserRoles(OreSiUser user) {
        return authenticationService.getCurrentUserRoles(user.getId().toString());
    }

    private List<String> collectGlobalRoles(CurrentUserRoles userRoles) {
        if (userRoles == null || userRoles.memberOf() == null) {
            return List.of();
        }
        return userRoles.memberOf().stream()
                .filter(r -> !APP_ROLE_PATTERN.matcher(r).matches())
                // Skip the user's own SQL role (UUID = user id) which always appears
                // in memberOf and would pollute the "global roles" column.
                .filter(r -> !r.equals(userRoles.userId() != null ? userRoles.userId().toString() : ""))
                .sorted()
                .toList();
    }

    private int countGlobalRoles(CurrentUserRoles userRoles) {
        return collectGlobalRoles(userRoles).size();
    }

    /**
     * Construit la {@link UserDTO.AppMembership} d'une app pour un user donne :
     * resoud l'app par UUID , transforme chaque role PG en
     * {@link UserDTO.RoleAttribution} avec sa trace , compte les autorisations
     * fines .
     */
    private Optional<UserDTO.AppMembership> buildAppMembership(String appUuid,
                                                               List<String> roleTypes,
                                                               AttributionLookup lookup) {
        UUID uuid;
        try {
            uuid = UUID.fromString(appUuid);
        } catch (IllegalArgumentException ex) {
            // The role prefix is not a UUID (legacy or non-application role); skip.
            return Optional.empty();
        }
        Optional<Application> appOpt = applicationRepository.tryFindApplication(appUuid);
        if (appOpt.isEmpty()) {
            return Optional.empty();
        }
        Application app = appOpt.get();
        List<String> roles = roleTypes == null ? List.of()
                : roleTypes.stream().sorted().distinct().toList();
        List<UserDTO.RoleAttribution> attributions = roles.stream()
                .map(role -> toAttribution(role, role,
                        buildAppRoleSqlName(uuid, role),
                        UserDTO.SCOPE_APPLICATION, uuid, lookup))
                .toList();
        int authCount = countAuthorizations(app.getName(), uuid);
        return Optional.of(new UserDTO.AppMembership(uuid, app.getName(), attributions, authCount));
    }

    private int countAuthorizations(String applicationName, UUID applicationId) {
        if (!isSafeIdentifier(applicationName)) {
            return 0;
        }
        try {
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*)::int FROM \"" + applicationName + "\".oresiauthorization "
                            + "WHERE application = ?::uuid",
                    Integer.class, applicationId.toString());
            return count == null ? 0 : count;
        } catch (RuntimeException ex) {
            // The application schema may not have the table yet (fresh install) or
            // the caller may lack SELECT privilege. Treat as 0 rather than failing
            // the whole detail view.
            log.debug("countAuthorizations failed for {} : {}", applicationName, ex.getMessage());
            return 0;
        }
    }

    private static boolean isSafeIdentifier(String s) {
        return s != null && s.matches("[a-zA-Z_][a-zA-Z0-9_]*");
    }

    private static int compareLogins(OreSiUser a, OreSiUser b) {
        String la = a.getLogin() == null ? "" : a.getLogin();
        String lb = b.getLogin() == null ? "" : b.getLogin();
        return la.compareToIgnoreCase(lb);
    }

    private static boolean matchesLogin(OreSiUser user, String loginFilter) {
        if (loginFilter == null || loginFilter.isEmpty()) {
            return true;
        }
        String login = user.getLogin() == null ? "" : user.getLogin().toLowerCase(Locale.ROOT);
        String email = user.getEmail() == null ? "" : user.getEmail().toLowerCase(Locale.ROOT);
        return login.contains(loginFilter) || email.contains(loginFilter);
    }

    private static boolean matchesState(OreSiUser user, String stateFilter) {
        if (stateFilter == null) {
            return true;
        }
        return user.getAccountstate() != null
                && stateFilter.equalsIgnoreCase(user.getAccountstate().name());
    }

    private static boolean matchesAppFilter(Map<String, List<String>> apps, UUID appIdFilter) {
        if (appIdFilter == null) {
            return true;
        }
        return apps.containsKey(appIdFilter.toString());
    }

    private static boolean matchesRoleFilter(UserSummaryWithRoles s, String roleFilter, UUID appIdFilter) {
        if (roleFilter == null) {
            return true;
        }
        if (GLOBAL_ROLES.contains(roleFilter)) {
            // Match if the user has the global role.
            String role = roleFilter;
            return s.userRoles.memberOf().contains(role);
        }
        if (APP_SCOPED_ROLES.contains(roleFilter)) {
            if (appIdFilter != null) {
                List<String> rs = s.apps.get(appIdFilter.toString());
                return rs != null && rs.contains(roleFilter);
            }
            return s.apps.values().stream().anyMatch(rs -> rs.contains(roleFilter));
        }
        return false;
    }

    private boolean isVisibleToManager(UserSummaryWithRoles s, CurrentUserRoles caller) {
        return isVisibleToManagerByApps(s.apps, caller);
    }

    /**
     * A non-admin caller sees a candidate user only if at least one application
     * appears in both their {@code applicationManager_*} scopes and the candidate's
     * role set. Pure global users are hidden from managers.
     */
    private boolean isVisibleToManagerByApps(Map<String, List<String>> candidateApps,
                                              CurrentUserRoles caller) {
        if (caller == null) {
            return false;
        }
        Set<String> managedAppIds = caller.memberOf().stream()
                .map(APP_ROLE_PATTERN::matcher)
                .filter(java.util.regex.Matcher::matches)
                .filter(m -> ROLE_APPLICATION_MANAGER.equals(m.group(2)))
                .map(m -> m.group(1))
                .collect(Collectors.toSet());
        if (managedAppIds.isEmpty()) {
            return false;
        }
        for (String appId : candidateApps.keySet()) {
            if (managedAppIds.contains(appId)) {
                return true;
            }
        }
        return false;
    }

    /** Internal tuple used to carry pre-computed role data through the list pipeline. */
    private record UserSummaryWithRoles(OreSiUser user,
                                         CurrentUserRoles userRoles,
                                         Map<String, List<String>> apps,
                                         int appCount,
                                         int globalCount) { }

    // Kept package-private for unit tests that need to assert on the regex contract.
    static Pattern appRolePattern() {
        return APP_ROLE_PATTERN;
    }
}