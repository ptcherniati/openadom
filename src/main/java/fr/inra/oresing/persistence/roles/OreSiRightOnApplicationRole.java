package fr.inra.oresing.persistence.roles;

import fr.inra.oresing.model.Application;
import lombok.Value;

import java.util.UUID;

@Value
public class OreSiRightOnApplicationRole implements OreSiRoleManagedByApplication, OreSiRoleToBeGranted, OreSiRoleWeCanGrantOtherRolesTo {

    UUID applicationId;
    String profile;
    UUID authorizationId;

    public static OreSiRightOnApplicationRole adminOn(Application application) {
        return adminOn(application.getId());
    }

    public static OreSiRightOnApplicationRole adminOn(UUID applicationId) {
        return new OreSiRightOnApplicationRole(applicationId, "admin", null);
    }

    public static OreSiRightOnApplicationRole readerOn(Application application) {
        return new OreSiRightOnApplicationRole(application.getId(), "reader", null);
    }

    public static OreSiRightOnApplicationRole writerOn(Application application) {
        return new OreSiRightOnApplicationRole(application.getId(), "writer", null);
    }

    public static OreSiRightOnApplicationRole managementRole(Application application, UUID uuid) {
        return new OreSiRightOnApplicationRole(application.getId(), String.format("mgt_%s", uuid.toString().substring(0,8)), uuid);
    }

    @Override
    public String getAsSqlRole() {
        String rightAsSqlRole = getApplicationId().toString() + "_" + profile;
        return rightAsSqlRole.substring(0,Math.min(rightAsSqlRole.length(), 63));
    }

}