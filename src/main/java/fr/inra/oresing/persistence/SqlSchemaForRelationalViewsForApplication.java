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

    public SqlTable forDataset(String datasetName) {
        checkDatasetName(datasetName);
        return new SqlTable(this, datasetName);
    }

    public SqlTable forDenormalizedDataset(String datasetName) {
        checkDatasetName(datasetName);
        return new SqlTable(this, "denormalized_" + datasetName);
    }

    private void checkDatasetName(String datasetName) {
        Preconditions.checkArgument(application.getDataType().contains(datasetName), datasetName + " n'est pas un dataset de " + getApplication());
    }

    public SqlTable forReferenceType(String referenceName) {
        Preconditions.checkArgument(application.getReferenceType().contains(referenceName), referenceName + " n'est pas un référentiel de " + getApplication());
        return new SqlTable(this, referenceName);
    }
}
