package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import lombok.Value;

@Value
public class SqlSchemaForApplication implements SqlSchema {

    Application application;

    @Override
    public String getName() {
        return application.getName();
    }

    public SqlTable data() {
        return new SqlTable(this, "data");
    }

    public SqlTable referenceValue() {
        return new SqlTable(this, "referenceValue");
    }

    public SqlTable binaryFile() {
        return new SqlTable(this, "binaryFile");
    }

    public SqlTable authorization() {
        return new SqlTable(this, "oreSiAuthorization");
    }
}
