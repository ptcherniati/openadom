package fr.inrae.ore.cascade.api.builder;
import fr.inrae.ore.cascade.api.defaults.db.RowSerializer;
import fr.inrae.ore.cascade.api.defaults.db.staging.FinalizeMode;
import fr.inrae.ore.cascade.core.defaults.db.WriteMode;
import fr.inrae.ore.cascade.model.core.Sink;
import javax.sql.DataSource;
public class StagingPostgresBuilder<T> {
    public StagingPostgresBuilder<T> dataSource(DataSource ds) { return this; }
    public StagingPostgresBuilder<T> writeMode(WriteMode.StagingUpsert wm) { return this; }
    public StagingPostgresBuilder<T> rowSerializer(RowSerializer<T> rs) { return this; }
    public StagingPostgresBuilder<T> finalizeMode(FinalizeMode fm) { return this; }
    public Sink<T> build() { return null; }
}
