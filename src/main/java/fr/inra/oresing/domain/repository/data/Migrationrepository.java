package fr.inra.oresing.domain.repository.data;

import fr.inra.oresing.domain.application.configuration.migration.context.DataInfo;
import fr.inra.oresing.domain.application.configuration.migration.context.SchemaInfo;

public interface Migrationrepository {
    DataInfo getDataInfo(String schemaName);
    SchemaInfo getSchemaInfo(String schemaName);
}