package fr.inrae.ore.cascade.api.builder;
public class SinkBuilder {
    private SinkBuilder() {}
    public static SinkBuilder create() { return new SinkBuilder(); }
    @SuppressWarnings("unchecked")
    public <T> StagingPostgresBuilder<T> stagingPostgres() { return new StagingPostgresBuilder<>(); }
}
