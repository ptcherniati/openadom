package fr.inra.oresing;

import fr.inra.oresing.model.OreSiUser;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString
public class OreSiUserRole {

    private static final OreSiUserRole ANONYMOUS_SINGLETON = forSqlRole("anonymous");

    private static final OreSiUserRole SUPERADMIN_SINGLETON = forSqlRole("superadmin");

    private static final OreSiUserRole APPLICATION_CREATOR_SINGLETON = forSqlRole("applicationCreator");

    private String asSqlRole;

    public static OreSiUserRole anonymous() {
        return ANONYMOUS_SINGLETON;
    }

    public static OreSiUserRole superadmin() {
        return SUPERADMIN_SINGLETON;
    }

    public static OreSiUserRole applicationCreator() {
        return APPLICATION_CREATOR_SINGLETON;
    }

    public static OreSiUserRole forUser(OreSiUser user) {
        String userAsSqlRole = user.getId().toString();
        return forSqlRole(userAsSqlRole);
    }

    private static OreSiUserRole forSqlRole(String asSqlRole) {
        OreSiUserRole newUserRole = new OreSiUserRole();
        newUserRole.setAsSqlRole(asSqlRole);
        return newUserRole;
    }

    public boolean isAnonymous() {
        return ANONYMOUS_SINGLETON == this;
    }
}
