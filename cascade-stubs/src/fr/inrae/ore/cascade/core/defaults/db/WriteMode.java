package fr.inrae.ore.cascade.core.defaults.db;
import fr.inrae.ore.cascade.api.defaults.db.staging.FinalizeHook;
import fr.inrae.ore.cascade.api.defaults.db.staging.StagingTableSpec;
public class WriteMode {
    public enum CopyFormat { TEXT, CSV, BINARY }
    public static class StagingUpsert {
        public StagingUpsert stagingSpec(StagingTableSpec spec) { return this; }
        public StagingUpsert finalizeHook(FinalizeHook hook) { return this; }
        public StagingUpsert copyColumns(String cols) { return this; }
        public StagingUpsert delimiter(char d) { return this; }
        public StagingUpsert nullString(String n) { return this; }
        public StagingUpsert format(CopyFormat f) { return this; }
        public StagingUpsert done() { return this; }
    }
    public static StagingUpsert stagingUpsert() { return new StagingUpsert(); }
}
