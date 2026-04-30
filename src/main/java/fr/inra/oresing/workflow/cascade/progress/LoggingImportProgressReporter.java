package fr.inra.oresing.workflow.cascade.progress;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementation par defaut de {@link ImportProgressReporter} : agrège un
 * compteur en memoire par {@code correlationId} et trace une ligne de log
 * a chaque batch.
 *
 * <p>Pas de persistence : si la JVM redemarre, l'historique est perdu.
 * Pour exposer la progression a un endpoint REST ou un dashboard, ajouter
 * une seconde implementation et l'injecter en {@code @Primary}.
 */
@Slf4j
@Component
public class LoggingImportProgressReporter implements ImportProgressReporter {

    private final Map<String, AtomicLong> totals = new ConcurrentHashMap<>();

    @Override
    public void onLinesProcessed(String correlationId, int delta) {
        long total = totals
                .computeIfAbsent(correlationId, k -> new AtomicLong())
                .addAndGet(delta);
        log.debug("[{}] +{} lignes traitées (total : {})", correlationId, delta, total);
    }

    /**
     * Releases the per-correlationId counter when the workflow ends.
     *
     * <p>without this purge , {@code totals} grew unbounded across the
     * JVM lifetime ( one AtomicLong per workflow forever ) and was a
     * confirmed leak source contributing to the 1st->2nd deposit
     * degradation. Called from
     * {@code CascadeImportPipeline.execute()} 's finally block.
     */
    public long release(String correlationId) {
        if (correlationId == null) {
            return 0L;
        }
        AtomicLong removed = totals.remove(correlationId);
        return removed != null ? removed.get() : 0L;
    }

    /** Returns the number of correlation IDs currently retained ( diagnostics ). */
    public int trackedCount() {
        return totals.size();
    }
}
