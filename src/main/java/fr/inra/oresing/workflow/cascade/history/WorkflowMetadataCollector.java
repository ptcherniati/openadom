package fr.inra.oresing.workflow.cascade.history;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Construit la map metadata persistee dans
 * {@code oa_audit.workflow_log.metadata} : parallelisme , strategy ,
 * JVM stats . Decouple du {@code CascadeImportPipeline} pour eviter
 * d'aggraver son God-method statu et permettre des tests unitaires
 * isoles avec un FakeWorkflowActiveRegistry .
 *
 * @author R.YAHIAOUI
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WorkflowMetadataCollector {

    private final WorkflowActiveRegistry activeRegistry;

    /** Lookup non-null memory snapshot ; null si workflow plus dans registry . */
    public Map<String, Object> collect(UUID correlationId) {
        if (correlationId == null) return null;
        try {
            WorkflowSnapshot snap = activeRegistry.find(correlationId).orElse(null);
            if (snap == null) return null;
            Map<String, Object> m = new LinkedHashMap<>();
            putParallelism(m, snap);
            putStrategy(m, snap);
            putImportConfig(m, snap);
            putJvmStats(m);
            putBinaryFileId(m, correlationId);
            return m;
        } catch (RuntimeException ex) {
            log.warn("WorkflowMetadataCollector.collect threw : {}", ex.getMessage());
            return null;
        }
    }

    /**
     * Stocke le binaryfile source dans metadata.binaryFileId quand le caller
     * l'a publie via {@link WorkflowActiveRegistry#setBinaryFileId} .
     * Permet a {@code IntegrityService} de retrouver les rows referencevalue
     * inserees pour ce workflow ( referencevalue.binaryfile = ce UUID ) sans
     * ajouter de colonne dediee a la table workflow_log .
     */
    private void putBinaryFileId(Map<String, Object> m, UUID correlationId) {
        activeRegistry.findBinaryFileId(correlationId)
                .ifPresent(id -> m.put("binaryFileId", id.toString()));
    }

    private void putParallelism(Map<String, Object> m, WorkflowSnapshot snap) {
        if (snap.parallelism() == null) return;
        m.put("parallelism", Map.of(
                "source",    snap.parallelism().source(),
                "transform", snap.parallelism().transform(),
                "sink",      snap.parallelism().sink()));
    }

    private void putStrategy(Map<String, Object> m, WorkflowSnapshot snap) {
        if (snap.strategy() == null) return;
        Map<String, Object> strat = new LinkedHashMap<>();
        strat.put("sinkStrategy",        snap.strategy().sinkStrategy());
        strat.put("stagingStrategy",     snap.strategy().stagingStrategy());
        strat.put("pipelineMode",        snap.strategy().pipelineMode());
        strat.put("sinkParallelism",     snap.strategy().sinkParallelism());
        m.put("strategy", strat);
    }

    /**
     * Persiste la config import capturee au demarrage ( cf .
     * {@link ImportConfigSnapshot} ) dans metadata.importConfig pour que
     * le Detail oa-live puisse afficher chunkSize / pools / staging /
     * metriques meme apres la fin du workflow ( registry vide ) .
     */
    private void putImportConfig(Map<String, Object> m, WorkflowSnapshot snap) {
        if (snap.importConfig() == null) return;
        Map<String, Object> ic = new LinkedHashMap<>();
        ic.put("chunkSizeLines",         snap.importConfig().chunkSizeLines());
        ic.put("progressBatchSize",      snap.importConfig().progressBatchSize());
        ic.put("maxErrorsThreshold",     snap.importConfig().maxErrorsThreshold());
        ic.put("collectorChunkSize",     snap.importConfig().collectorChunkSize());
        ic.put("stagingSharedOrphanTtl", snap.importConfig().stagingSharedOrphanTtl());
        ic.put("stagingSharedTableName", snap.importConfig().stagingSharedTableName());
        ic.put("enableMetrics",          snap.importConfig().enableMetrics());
        ic.put("skipCsvReencoding",      snap.importConfig().skipCsvReencoding());
        ic.put("poolSource",             snap.importConfig().poolSource());
        ic.put("poolTransform",          snap.importConfig().poolTransform());
        ic.put("poolSink",               snap.importConfig().poolSink());
        ic.put("poolOrdering",           snap.importConfig().poolOrdering());
        m.put("importConfig", ic);
    }

    private void putJvmStats(Map<String, Object> m) {
        Runtime r = Runtime.getRuntime();
        m.put("jvm", Map.of(
                "availableProcessors", r.availableProcessors(),
                "maxHeapMB",   r.maxMemory()   / (1024 * 1024),
                "totalHeapMB", r.totalMemory() / (1024 * 1024)));
    }
}
