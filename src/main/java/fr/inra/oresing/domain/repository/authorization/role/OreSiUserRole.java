package fr.inra.oresing.domain.repository.authorization.role;

import fr.inra.oresing.domain.OreSiUser;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class OreSiUserRole implements OreSiRoleToAccessDatabase, OreSiRoleManagedByApplication, OreSiRoleWeCanGrantOtherRolesTo {

    public static OreSiUserRole forUser(final OreSiUser user) {
        final String userAsSqlRole = user.getId().toString();
        final OreSiUserRole oreSiUserRole = new OreSiUserRole();
        oreSiUserRole.setAsSqlRole(userAsSqlRole);
        return oreSiUserRole;
    }

    private String asSqlRole;

}
