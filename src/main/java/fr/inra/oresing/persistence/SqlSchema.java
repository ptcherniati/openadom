package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.persistence.roles.OreSiRole;
import fr.inra.oresing.rest.ViewStrategy;

public interface SqlSchema extends WithSqlIdentifier {

    static OreSiSqlSchema main() {
        return OreSiSqlSchema.MAIN;
    }

    static SqlSchemaForRelationalViewsForApplication forRelationalViewsOf(Application application, ViewStrategy viewStrategy) {
        return new SqlSchemaForRelationalViewsForApplication(application, viewStrategy);
    }

    static SqlSchemaForApplication forApplication(Application application) {
        return new SqlSchemaForApplication(application);
    }

    String getName();

    @Override
    default String getSqlIdentifier() {
        return WithSqlIdentifier.escapeSqlIdentifier(getName());
    }
    default String setSchemaOwnerSql(OreSiRole owner){
        return "ALTER SCHEMA " + getSqlIdentifier() + " OWNER TO " + owner.getSqlIdentifier();
    }
    default String setGrantToSql(OreSiRole grantTo){
        return "GRANT USAGE ON SCHEMA " + getSqlIdentifier() + " TO " + grantTo.getSqlIdentifier();
    }
}