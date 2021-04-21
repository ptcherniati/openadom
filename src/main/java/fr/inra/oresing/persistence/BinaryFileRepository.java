package fr.inra.oresing.persistence;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.BinaryFile;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

@Component
@Scope(scopeName = ConfigurableBeanFactory.SCOPE_PROTOTYPE)
public class BinaryFileRepository extends ApplicationEntityDao<BinaryFile> {

    public BinaryFileRepository(Application application) {
        super(application);
    }

    @Override
    public SqlTable getTable() {
        return getSchema().binaryFile();
    }

    @Override
    protected String getUpsertQuery() {
        return "INSERT INTO " + getTable().getSqlIdentifier() + "(id, application, name, size, data) SELECT id, application, name, size, data FROM json_populate_recordset(NULL::" + getTable().getSqlIdentifier() + ", :json::json) "
                + " ON CONFLICT (id) DO UPDATE SET updateDate=current_timestamp, application=EXCLUDED.application, name=EXCLUDED.name, size=EXCLUDED.size, data=EXCLUDED.data"
                + " RETURNING id";
    }

    @Override
    protected Class<BinaryFile> getEntityClass() {
        return BinaryFile.class;
    }
}
