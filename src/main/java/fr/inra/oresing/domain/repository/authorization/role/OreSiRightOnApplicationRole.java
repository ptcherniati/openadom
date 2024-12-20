package fr.inra.oresing.domain.repository.authorization.role;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.SqlSchemaForApplication;

import java.util.UUID;
public record OreSiRightOnApplicationRole(
        UUID applicationId,
        String profile,
        String comment,
        UUID authorizationId
) implements OreSiRoleManagedByApplication, OreSiRoleToBeGranted, OreSiRoleWeCanGrantOtherRolesTo {

    public static final String APPLICATION_MANAGER = "applicationManager";
    public static final String USER_MANAGER = "userManager";
    public static final String PUBLIC = "public";
    public static final String PUBLIC__ = "__public__";

    public static OreSiRightOnApplicationRole adminOn(final Application application) {
        return adminOn(application.getId(), "Administrateur de l'application %s".formatted(application.getName()));
    }

    public static OreSiRightOnApplicationRole userAdminOn(final Application application) {
        return userAdminOn(application.getId(), "Administrateur des drits des utilisateurs sur les données de l'application %s".formatted(application.getName()));
    }

    private static OreSiRightOnApplicationRole adminOn(final UUID applicationId, String comment) {
        return new OreSiRightOnApplicationRole(applicationId, APPLICATION_MANAGER, comment, null);
    }

    private static OreSiRightOnApplicationRole userAdminOn(final UUID applicationId, String comment) {
        return new OreSiRightOnApplicationRole(applicationId, USER_MANAGER, comment, null);
    }

    public static OreSiRightOnApplicationRole PUBLIC() {
        return new OreSiRightOnApplicationRole(null, PUBLIC, PUBLIC__, SqlSchemaForApplication.PUBLIC_UUID);
    }

    public static OreSiRightOnApplicationRole readerOn(final Application application) {
        return new OreSiRightOnApplicationRole(application.getId(), "reader", """
                Reader permission on application %s
                Requires policies to read.""".formatted(application.getName()), null);
    }

    public static OreSiRightOnApplicationRole writerOn(final Application application) {
        return new OreSiRightOnApplicationRole(application.getId(), "writer",  """
                Writer permission on application %s
                Requires policies to write.""".formatted(application.getName()), null);
    }

    /**
     * créé un role permettant pour poser des policies
     */
    public static OreSiRightOnApplicationRole managementRole(final Application application, final UUID uuid) {
        return new OreSiRightOnApplicationRole(application.getId(), String.format("mgt_%s", uuid.toString().substring(0, 8)),
                """
                Mamagement of data  permission on application %s
                with policies to write and/or to read data and binaryfiles.""".formatted(application.getName()), uuid);
    }

    @Override
    public String getAsSqlRole() {
        if ("public".equals(profile) || applicationId == null) {
            return authorizationId.toString();
        }
        final String rightAsSqlRole = applicationId + "_" + profile;
        return rightAsSqlRole.substring(0, Math.min(rightAsSqlRole.length(), 63));
    }

}