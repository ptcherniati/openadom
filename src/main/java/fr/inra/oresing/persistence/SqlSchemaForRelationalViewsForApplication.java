package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.rest.ViewStrategy;
import lombok.Value;
import org.testcontainers.shaded.com.google.common.base.Preconditions;

@Value
public class SqlSchemaForRelationalViewsForApplication implements SqlSchema {

    Application application;

    ViewStrategy viewStrategy;

    @Override
    public String getName() {
        return String.join("_", application.getName(), viewStrategy.name());
    }

    public SqlTable forDataType(String dataType) {
        checkDataType(dataType);
        return new SqlTable(this, dataType);
    }

    public SqlTable forDenormalizedDataType(String dataType) {
        checkDataType(dataType);
        return new SqlTable(this, "denormalized_" + dataType);
    }

    private void checkDataType(String dataType) {
        Preconditions.checkArgument(application.getDataType().contains(dataType), dataType + " n'est pas un type de données de " + getApplication());
    }

    public SqlTable forReferenceType(String referenceName) {
        Preconditions.checkArgument(application.getReferenceType().contains(referenceName), referenceName + " n'est pas un référentiel de " + getApplication());
        return new SqlTable(this, referenceName);
    }
}
