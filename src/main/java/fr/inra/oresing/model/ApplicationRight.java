package fr.inra.oresing.model;

import fr.inra.oresing.OreSiUserRole;
import lombok.AllArgsConstructor;

import java.util.UUID;

/**
 * Enum des droit que l'on peut donner a un utilisateur
 * TODO: il faudrait revoir son implantation pour mieux gerer les differentes
 * possibilites (on ne peut pas mettre plusieurs actions, INSERT ne fonctionne pas, ...)
 * et la facon de faire est trop complique
 */
@AllArgsConstructor
public enum ApplicationRight {

    // "{ ALL | SELECT | INSERT | UPDATE | DELETE } ]"),
    ADMIN("ALL", "ALL", "ALL", "ALL"),
    WRITER("SELECT", "ALL", "ALL", "ALL"),
    DATA_WRITER("SELECT", "SELECT", "SELECT", "ALL"),
    READER("SELECT", "SELECT", "SELECT", "SELECT"),
    RESTRICTED_READER("SELECT", "SELECT", "SELECT", "SELECT");

    private static final String SQL =
            "CREATE POLICY \"%3$s_%2$s\" ON %2$s"+ // nom policy, table
            "    AS PERMISSIVE" +
            "    FOR %4$s" + // action
            "    TO \"%3$s\"" + //  role
            "    USING ( %5$s = '%1$s' )"; // field, appId

    private final String appAction;
    private final String fileAction;
    private final String refAction;
    private final String dataAction;

    public OreSiUserRole getRole(UUID appId) {
        return OreSiUserRole.forRightOnApplication(appId, this);
    }

    public String getAllSql(UUID appId) {
        return String.join(";",
                getSqlApplication(appId),
                getSqlBinaryFile(appId),
                getSqlReferenceValue(appId),
                getSqlData(appId)
        );
    }

    public String getSqlApplication(UUID appId) {
        return String.format(SQL, appId, "Application", getRole(appId).getAsSqlRole(), appAction, "id");
    }

    public String getSqlBinaryFile(UUID appId) {
        return String.format(SQL, appId, "BinaryFile", getRole(appId).getAsSqlRole(), fileAction, "application");
    }

    public String getSqlReferenceValue(UUID appId) {
        return String.format(SQL, appId, "ReferenceValue", getRole(appId).getAsSqlRole(), refAction, "application");
    }

    public String getSqlData(UUID appId) {
        return String.format(SQL, appId, "Data", getRole(appId).getAsSqlRole(), dataAction, "application");
    }
}
