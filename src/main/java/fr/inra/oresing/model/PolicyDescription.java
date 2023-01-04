package fr.inra.oresing.model;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.sql.ResultSet;
import java.sql.SQLException;

@Getter
@Setter
@ToString(callSuper = true)
public class PolicyDescription {
    public static PolicyDescription convert(ResultSet rs, int rowNum)  {
        try {
            final PolicyDescription policyDescription = new PolicyDescription();
            policyDescription.policyname = rs.getString("policyname");
            policyDescription.schemaname = rs.getString("schemaname");
            policyDescription.tablename = rs.getString("tablename");
            return policyDescription;
        }catch (SQLException  e){
            return null;
        }
    }
    String policyname;
    String schemaname;
    String tablename;
   // List<String> roles;
}