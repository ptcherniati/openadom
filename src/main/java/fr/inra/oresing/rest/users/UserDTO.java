package fr.inra.oresing.rest.users;

import fr.inra.oresing.domain.OreSiUser;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTOs pour l'admin tab "Utilisateurs" d'oa-live :
 * <ul>
 *   <li>{@link UserSummary} : 1 ligne par user dans la liste ;</li>
 *   <li>{@link UserDetail}  : detail expand row , apps + roles globaux ;</li>
 *   <li>{@link AppMembership} : 1 entry par app pour ce user ;</li>
 *   <li>{@link RoleAttribution} : 1 entry par role accorde a un user , avec
 *       son SQL identifier , son scope ( GLOBAL / APPLICATION ) et sa trace
 *       d'attribution ( date + auteur ) lue depuis {@code oa_audit.role_grant_audit} ;</li>
 *   <li>{@link GrantRoleRequest} / {@link RevokeRoleRequest} : payloads d'edition .</li>
 * </ul>
 *
 * <p>Records immuables - serialise direct en JSON par Spring/Jackson . Aucun
 * setter / getter manuel a maintenir ( DRY ) . Validation cote service ;
 * les records ne validate que la presence ( nullability ) .
 */
public final class UserDTO {

    /** Scope d'un role : applicatif ( rattache a une app ) ou global . */
    public static final String SCOPE_GLOBAL      = "GLOBAL";
    public static final String SCOPE_APPLICATION = "APPLICATION";

    private UserDTO() { }

    /**
     * Vue minimale pour la liste : 1 ligne par user . {@code appCount} permet
     * a la table UI d'afficher "Utilisateur X a acces a N applications" sans
     * tirer le detail complet ( perf : evite N+1 sur les apps ) .
     */
    public record UserSummary(
            UUID    id,
            String  login,
            String  email,
            String  accountState,
            int     appCount,
            int     globalRolesCount
    ) {
        public static UserSummary of(OreSiUser user, int appCount, int globalRolesCount) {
            return new UserSummary(
                    user.getId(),
                    user.getLogin(),
                    user.getEmail(),
                    user.getAccountstate() != null ? user.getAccountstate().name() : null,
                    appCount,
                    globalRolesCount);
        }
    }

    /**
     * Detail complet : apps + roles globaux . Renvoye par
     * {@code GET /admin/users/{id}} et embarque par le panel detail UI .
     *
     * <p>{@code globalRoles} : roles non-scopes app ( ex {@code openAdomAdmin} ) .
     * {@code applications} : 1 entry par application avec ses roles applicatifs .
     */
    public record UserDetail(
            UUID                    id,
            String                  login,
            String                  email,
            String                  accountState,
            List<RoleAttribution>   globalRoles,
            List<AppMembership>     applications
    ) {
    }

    /**
     * 1 entry par application pour un user .
     * <ul>
     *   <li>{@code roles} : roles applicatifs ( {@code applicationManager} ,
     *       {@code userManager} , {@code reader} , {@code writer} ) , chacun
     *       avec sa trace d'attribution ;</li>
     *   <li>{@code authorizationCount} : nombre de rows oresiauthorization
     *       fines ( scopes datatypes / time range ) - le detail complet est
     *       expose par un endpoint dedie a la demande pour ne pas alourdir
     *       la liste ( perf : evite N+1 sur les schemas applicatifs ) .</li>
     * </ul>
     */
    public record AppMembership(
            UUID                    applicationId,
            String                  applicationName,
            List<RoleAttribution>   roles,
            int                     authorizationCount
    ) {
    }

    /**
     * 1 role accorde a un user , avec son identifier SQL et sa trace
     * d'attribution .
     *
     * <ul>
     *   <li>{@code roleName} : nom logique ( {@code reader} , {@code openAdomAdmin} , ... )
     *       utilise par {@link GrantRoleRequest} / {@link RevokeRoleRequest} ;</li>
     *   <li>{@code sqlRoleName} : SQL identifier reel en BDD - pour roles
     *       applicatifs : {@code <appUUID>_<roleType>} ( tronque a 63 chars
     *       cf {@code OreSiRightOnApplicationRole.getAsSqlRole} ) ; pour
     *       roles globaux : identique a {@code roleName} ;</li>
     *   <li>{@code scope} : {@link #SCOPE_GLOBAL} ou {@link #SCOPE_APPLICATION} ;</li>
     *   <li>{@code applicationId} : UUID de l'app ou {@code null} si global ;</li>
     *   <li>{@code grantedAt} : timestamp du GRANT depuis {@code oa_audit.role_grant_audit} ,
     *       {@code null} si le role a ete attribue avant l'activation du log
     *       ( deploiement V15 ) ou si le log est indisponible ;</li>
     *   <li>{@code grantedBy} : UUID de l'admin qui a fait le GRANT , {@code null}
     *       dans les memes cas ;</li>
     *   <li>{@code grantedByLogin} : login resolu via {@code UserRepository}
     *       a partir de {@code grantedBy} , {@code null} si admin supprime
     *       depuis ou audit pre-V15 .</li>
     * </ul>
     */
    public record RoleAttribution(
            String          roleName,
            String          sqlRoleName,
            String          scope,
            UUID            applicationId,
            OffsetDateTime  grantedAt,
            UUID            grantedBy,
            String          grantedByLogin
    ) {
    }

    /** Payload {@code POST /admin/users/{id}/roles} . */
    public record GrantRoleRequest(
            UUID    applicationId,   // null = role global ( ex openAdomAdmin )
            String  roleName         // ex applicationManager , dataReader , openAdomAdmin
    ) {
    }

    /** Payload {@code DELETE /admin/users/{id}/roles} ( body au lieu de path pour
     *  ne pas exposer des role names parfois prefixes en URL ) . */
    public record RevokeRoleRequest(
            UUID    applicationId,
            String  roleName
    ) {
    }
}
