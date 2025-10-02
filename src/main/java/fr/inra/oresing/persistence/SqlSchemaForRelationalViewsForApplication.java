package fr.inra.oresing.persistence;

import com.google.common.base.Preconditions;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.rest.ViewStrategy;


public record SqlSchemaForRelationalViewsForApplication(Application application,
                                                        ViewStrategy viewStrategy) implements SqlSchema {

    @Override
    public String getName() {
        return String.join("_", application.getName(), viewStrategy.name());
    }

    public SqlTable forDataType(final String dataType) {
        checkDataName(dataType);
        return new SqlTable(this, dataType);
    }

    public SqlTable forNormalizedDataType(final String dataType) {
        checkDataName(dataType);
        return new SqlTable(this, "normalized_" + dataType);
    }

    private void checkDataName(final String dataName) {
        Preconditions.checkArgument(application.existsData(dataName), dataName + " n'est pas un type de données de " + application);
    }

    public SqlTable forReferenceType(final String referenceName) {
        checkDataName(referenceName);
        return new SqlTable(this, referenceName);
    }

    public SqlTable forAssociation(final String referenceName, final DataColumn referenceColumn) {
        checkDataName(referenceName);
        return new SqlTable(this, referenceName + "_" + referenceColumn.column());
    }
}