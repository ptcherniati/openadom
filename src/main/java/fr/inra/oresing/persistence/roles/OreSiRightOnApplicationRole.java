package fr.inra.oresing.persistence.roles;

import fr.inra.oresing.model.ApplicationRight;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.UUID;

@AllArgsConstructor
@Getter
@ToString
public class OreSiRightOnApplicationRole implements OreSiRoleManagedByApplication, OreSiRoleToBeGranted, OreSiRoleWeCanGrantOtherRolesTo {

    private final UUID applicationId;

    private final ApplicationRight right;

    public static OreSiRightOnApplicationRole forRightOnApplication(UUID applicationId, ApplicationRight applicationRight) {
        return new OreSiRightOnApplicationRole(applicationId, applicationRight);
    }

    @Override
    public String getAsSqlRole() {
        String rightAsSqlRole = getApplicationId().toString() + "_" + getRight().name();
        return rightAsSqlRole;
    }

}
