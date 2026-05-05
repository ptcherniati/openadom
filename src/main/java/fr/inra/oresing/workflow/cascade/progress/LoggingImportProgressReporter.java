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
 * <p>Dès que {@link #onTotalLinesKnown} a été appelé, les logs affichent
 * une barre de progression ASCII sur 25 caractères :
 * <pre>
 *   [uuid] [█████████████░░░░░░░░░░░░] 52% (2500 / 4843) Δ+100
 * </pre>
 *
 * <p>Pas de persistence : si la JVM redemarre, l'historique est perdu.
 * Pour exposer la progression a un endpoint REST ou un dashboard, ajouter
 * une seconde implementation et l'injecter en {@code @Primary}.
 */
@Slf4j
@Component
public class LoggingImportProgressReporter implements ImportProgressReporter {

    /** Largeur de la barre de progression en caractères. */
    private static final int BAR_WIDTH = 25;
    private static final String FILLED = "█";
    private static final String EMPTY  = "░";

    private final Map<String, AtomicLong> totals      = new ConcurrentHashMap<>();
    private final Map<String, Long>       grandTotals = new ConcurrentHashMap<>();

    @Override
    public void onTotalLinesKnown(String correlationId, long totalLines) {
        grandTotals.put(correlationId, totalLines);
        log.info("[{}] Import démarré — {} lignes de données à traiter", correlationId, totalLines);
    }

    @Override
    public void onLinesProcessed(String correlationId, int delta) {
        long processed = totals
                .computeIfAbsent(correlationId, k -> new AtomicLong())
                .addAndGet(delta);
        Long grand = grandTotals.get(correlationId);
        if (grand != null && grand > 0) {
            int pct    = (int) (processed * 100 / grand);
            int filled = (int) (BAR_WIDTH * processed / grand);
            int empty  = BAR_WIDTH - filled;
            String bar = "[" + FILLED.repeat(filled) + EMPTY.repeat(empty) + "]";
            log.debug("[{}] {} {}% ({} / {}) Δ+{}", correlationId, bar, pct, processed, grand, delta);
        } else {
            log.debug("[{}] +{} lignes traitées (total : {})", correlationId, delta, processed);
        }
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
        grandTotals.remove(correlationId);
        AtomicLong removed = totals.remove(correlationId);
        return removed != null ? removed.get() : 0L;
    }

    /** Returns the number of correlation IDs currently retained ( diagnostics ). */
    public int trackedCount() {
        return totals.size();
    }
}