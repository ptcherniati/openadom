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
}
