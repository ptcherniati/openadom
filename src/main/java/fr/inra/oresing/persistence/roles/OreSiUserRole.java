package fr.inra.oresing.persistence.roles;

import fr.inra.oresing.model.OreSiUser;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class OreSiUserRole implements OreSiRoleToAccessDatabase, OreSiRoleManagedByApplication, OreSiRoleWeCanGrantOtherRolesTo {

    public static OreSiUserRole forUser(OreSiUser user) {
        String userAsSqlRole = user.getId().toString();
        OreSiUserRole oreSiUserRole = new OreSiUserRole();
        oreSiUserRole.setAsSqlRole(userAsSqlRole);
        return oreSiUserRole;
    }

    private String asSqlRole;

}
