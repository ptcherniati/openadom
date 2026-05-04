package fr.inra.oresing.workflow.cascade.history;

/**
 * Configuration snapshot capturee au demarrage d'un workflow d'import .
 *
 * <p>Mirroir des champs {@code ImportProperties} qui influent sur les perfs
 * et le comportement runtime ( taille de chunk , parallelisme , staging ,
 * metriques , ... ) . Expose dans le Detail du workflow ( oa-live ) pour
 * permettre d'analyser a posteriori pourquoi un import a ete lent ou a
 * echoue ( "config X => latence Y" ) sans avoir a fouiller logs / env vars .
 *
 * <p>Resolu une fois a workflow registration time et jamais mute .
 *
 * @param chunkSizeLines             cf. cascade.import.chunk-size-lines
 * @param progressBatchSize          cf. cascade.import.progress-batch-size
 * @param maxErrorsThreshold         seuil d'erreurs avant abort
 * @param collectorChunkSize         taille consolidation collector cascade
 * @param stagingSharedOrphanTtl     TTL ( minutes ) sweeper orphelins SHARED_UNLOGGED
 * @param stagingSharedTableName     nom table SHARED_UNLOGGED
 * @param enableMetrics              MetricsChunkInterceptor + JVM stats
 * @param skipCsvReencoding          bypass re-encoding CSV ( perf gain )
 * @param poolSource                 cascade.pool.source effectif
 * @param poolTransform              cascade.pool.transform effectif
 * @param poolSink                   cascade.pool.sink effectif
 * @param poolOrdering               cascade.pool.ordering effectif ( 0 si non resolu )
 */
public record ImportConfigSnapshot(
        int     chunkSizeLines,
        int     progressBatchSize,
        int     maxErrorsThreshold,
        int     collectorChunkSize,
        int     stagingSharedOrphanTtl,
        String  stagingSharedTableName,
        boolean enableMetrics,
        boolean skipCsvReencoding,
        int     poolSource,
        int     poolTransform,
        int     poolSink,
        int     poolOrdering) {
}
