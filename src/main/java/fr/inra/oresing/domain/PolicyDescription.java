package fr.inra.oresing.domain;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
@Setter
@ToString(callSuper = true)
public class PolicyDescription {
    String policyname;
    String schemaname;
    String tablename;

    public static PolicyDescription convert(final ResultSet rs, final int rowNum) {
        try {
            PolicyDescription policyDescription = new PolicyDescription();
            policyDescription.policyname = rs.getString("policyname");
            policyDescription.schemaname = rs.getString("schemaname");
            policyDescription.tablename = rs.getString("tablename");
            return policyDescription;
        } catch (final SQLException e) {
            return null;
        }
    }
    // List<String> roles;
}