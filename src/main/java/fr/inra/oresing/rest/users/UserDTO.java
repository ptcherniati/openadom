package fr.inra.oresing.rest.users;

import fr.inra.oresing.domain.OreSiUser;

import java.util.List;
import java.util.UUID;

/**
 * DTOs pour l'admin tab "Utilisateurs" d'oa-live :
 * <ul>
 *   <li>{@link UserSummary} : 1 ligne par user dans la liste ;</li>
 *   <li>{@link UserDetail}  : detail expand row , apps + roles ;</li>
 *   <li>{@link AppMembership} : 1 entry par app pour ce user ( roles PG metier
 *       + scopes oresiauthorization ) ;</li>
 *   <li>{@link GrantRoleRequest} / {@link RevokeRoleRequest} : payloads d'edition .</li>
 * </ul>
 *
 * <p>Records immuables - serialise direct en JSON par Spring/Jackson . Aucun
 * setter / getter manuel a maintenir ( DRY ) . Validation cote service ;
 * les records ne validate que la presence ( nullability ) .
 */
public final class UserDTO {

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
     * {@code GET /admin/users/{id}} et embarque par detail panel UI .
     *
     * <p>Roles globaux = roles non-scopes app ( ex {@code openAdomAdmin} ,
     * {@code userManager} ) extraits du PG role membership .
     */
    public record UserDetail(
            UUID               id,
            String             login,
            String             email,
            String             accountState,
            List<String>       globalRoles,
            List<AppMembership> applications
    ) {
    }

    /**
     * 1 entry par application pour un user :
     * <ul>
     *   <li>{@code businessRoles} : roles PG specifiques a l'app ( ex
     *       {@code applicationManager_<appId>} , {@code dataReader_<appId>} ) ,
     *       prefix supprime pour lisibilite UI ;</li>
     *   <li>{@code authorizationCount} : nombre de rows oresiauthorization
     *       fines ( scopes datatypes / time range ) - le detail complet est
     *       expose par un endpoint dedie a la demande pour ne pas alourdir
     *       la liste ( perf : evite N+1 sur les schemas applicatifs ) .</li>
     * </ul>
     */
    public record AppMembership(
            UUID         applicationId,
            String       applicationName,
            List<String> businessRoles,
            int          authorizationCount
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
