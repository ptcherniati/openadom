package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;

public interface SqlSchema extends WithSqlIdentifier {

    static OreSiSqlSchema main() {
        return OreSiSqlSchema.MAIN;
    }

    static SqlSchemaForRelationalViewsForApplication forRelationalViewsOf(Application application) {
        return new SqlSchemaForRelationalViewsForApplication(application);
    }
}
