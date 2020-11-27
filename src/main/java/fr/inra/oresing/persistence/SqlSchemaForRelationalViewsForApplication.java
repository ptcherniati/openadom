package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import org.testcontainers.shaded.com.google.common.base.Preconditions;

@AllArgsConstructor(access = AccessLevel.PACKAGE)
@Getter
@ToString
public class SqlSchemaForRelationalViewsForApplication implements SqlSchema {

    private final Application application;

    @Override
    public String getSqlIdentifier() {
        return WithSqlIdentifier.escapeSqlIdentifier(application.getName());
    }

    public SqlTable forDataset(String datasetName) {
        Preconditions.checkArgument(application.getDataType().contains(datasetName), datasetName + " n'est pas un dataset de " + getApplication());
        return new SqlTable(this, datasetName);
    }

    public SqlTable forReferenceType(String referenceName) {
        Preconditions.checkArgument(application.getReferenceType().contains(referenceName), referenceName + " n'est pas un dataset de " + getApplication());
        return new SqlTable(this, referenceName);
    }
}
