package fr.inra.oresing.workflow.cascade.config;

/**
 * Abstraction sur la modification a chaud des pools cascade .
 * Permet de mocker / faker pour les tests unitaires sans dependre du
 * singleton {@code WorkflowPoolRegistry} .
 *
 * @author R.YAHIAOUI
 */
public interface PoolReloader {

    enum Stage {
        SOURCE, TRANSFORM, SINK, ORDERING
    }

    /**
     * Resize {@code corePoolSize} et {@code maximumPoolSize} du pool cible .
     * @throws IllegalArgumentException si {@code newSize < 1}
     * @throws IllegalStateException si le pool ne supporte pas le resize
     *         ( virtual threads executor par exemple )
     */
    void resize(Stage stage, int newSize);

    /** Snapshot du pool courant . */
    PoolSnapshot snapshot(Stage stage);

    record PoolSnapshot(
            Stage stage,
            int corePoolSize,
            int maximumPoolSize,
            int activeCount,
            int poolSize,
            int queueSize,
            int queueCapacity) { }

    static Stage parseStage(String raw) {
        if (raw == null) throw new IllegalArgumentException("stage is null");
        try {
            return Stage.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown stage : " + raw
                    + " ( expected one of " + java.util.Arrays.toString(Stage.values()) + " )");
        }
    }
}
