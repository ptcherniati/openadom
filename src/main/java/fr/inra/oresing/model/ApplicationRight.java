package fr.inra.oresing.model;

import lombok.AllArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.UUID;

@AllArgsConstructor
public enum ApplicationRight {

    // "{ ALL | SELECT | INSERT | UPDATE | DELETE } ]"),
    ADMIN("ALL", "ALL", "ALL", "ALL"),
    WRITER("SELECT", "ALL", "ALL", "ALL"),
    DATA_WRITER("SELECT", "SELECT", "SELECT", "ALL"),
    READER("SELECT", "SELECT", "SELECT", "SELECT"),
    RESTRICTED_READER("SELECT", "SELECT", "SELECT", "SELECT");

    private static final String sql =
            "CREATE POLICY \"%3$s_%2$s\" ON %2$s"+ // nom policy, table
            "    AS PERMISSIVE" +
            "    FOR %4$s" + // action
            "    TO \"%3$s\"" + //  role
            "    USING ( %5$s = '%1$s' )"; // field, appId
    private static final String sqlWithCheck =
            "    WITH CHECK ( %5$s = '%1$s' )"; // field, appId

    private String appAction;
    private String fileAction;
    private String refAction;
    private String dataAction;

    public String getRole(UUID appId) {
        return appId.toString() + "_" + name();
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
        String query = sql;
        if (!StringUtils.equalsAnyIgnoreCase("SELECT", "DELETE")) {
            query += sqlWithCheck;
        }
        return String.format(sql, appId, "Application", getRole(appId), appAction, "id");
    }

    public String getSqlBinaryFile(UUID appId) {
        return String.format(sql, appId, "BinaryFile", getRole(appId), fileAction, "application");
    }

    public String getSqlReferenceValue(UUID appId) {
        return String.format(sql, appId, "ReferenceValue", getRole(appId), refAction, "application");
    }

    public String getSqlData(UUID appId) {
        return String.format(sql, appId, "Data", getRole(appId), dataAction, "application");
    }
}
