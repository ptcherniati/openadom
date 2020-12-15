package fr.inra.oresing.persistence.roles;

import fr.inra.oresing.model.Application;
import lombok.Value;

import java.util.UUID;

@Value
public class OreSiRightOnApplicationRole implements OreSiRoleManagedByApplication, OreSiRoleToBeGranted, OreSiRoleWeCanGrantOtherRolesTo {

    UUID applicationId;

    String profile;

    public static OreSiRightOnApplicationRole adminOn(Application application) {
        return adminOn(application.getId());
    }

    public static OreSiRightOnApplicationRole adminOn(UUID applicationId) {
        return new OreSiRightOnApplicationRole(applicationId, "admin");
    }

    public static OreSiRightOnApplicationRole readerOn(Application application) {
        return new OreSiRightOnApplicationRole(application.getId(), "reader");
    }

    @Override
    public String getAsSqlRole() {
        String rightAsSqlRole = getApplicationId().toString() + "_" + profile;
        return rightAsSqlRole;
    }

}
