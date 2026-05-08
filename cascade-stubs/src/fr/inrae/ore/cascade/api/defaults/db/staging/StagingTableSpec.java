package fr.inrae.ore.cascade.api.defaults.db.staging;
public abstract class StagingTableSpec {
    public abstract String tableName();
    public static class PerConnectionTemp extends StagingTableSpec {
        public PerConnectionTemp() {}
        public PerConnectionTemp(Object... args) {}
        @Override public String tableName() { return "cascade_staging_temp"; }
    }
    public static class SharedUnlogged extends StagingTableSpec {
        public SharedUnlogged(Object... args) {}
        @Override public String tableName() { return "cascade_staging_shared"; }
    }
}
