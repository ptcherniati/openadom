package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.OreSiEntity;

public abstract class ApplicationEntityDao<T extends OreSiEntity> extends JsonTableRepositoryTemplate<T> {

    private final Application application;

    private final SqlSchemaForApplication schema;

    public ApplicationEntityDao(Application application) {
        this.application = application;
        schema = SqlSchema.forApplication(application);
    }

    protected SqlSchemaForApplication getSchema() {
        return schema;
    }

    protected Application getApplication() {
        return application;
    }
}
