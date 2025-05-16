package fr.inra.oresing.persistence;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import fr.inra.oresing.rest.ViewStrategy;

public interface SqlSchema extends WithSqlIdentifier {
    static SqlSchemaForApplication forApplication(Application application) {
        return new SqlSchemaForApplication(application);
    }

    static OreSiSqlSchema mainSchema() {
        return OreSiSqlSchema.MAIN;
    }

    static SqlSchemaForRelationalViewsForApplication forRelationalViewsOf(final Application application, final ViewStrategy viewStrategy) {
        return new SqlSchemaForRelationalViewsForApplication(application, viewStrategy);
    }

    String getName();

    @Override
    default String getSqlIdentifier() {
        return WithSqlIdentifier.escapeSqlIdentifier(getName());
    }

    default String setSchemaOwnerSql(final OreSiRole owner) {
        return "ALTER SCHEMA " + getSqlIdentifier() + " OWNER TO " + owner.getSqlIdentifier();
    }

    default String setGrantToSql(final OreSiRole grantTo) {
        return "GRANT USAGE ON SCHEMA " + getSqlIdentifier() + " TO " + grantTo.getSqlIdentifier();
    }
}