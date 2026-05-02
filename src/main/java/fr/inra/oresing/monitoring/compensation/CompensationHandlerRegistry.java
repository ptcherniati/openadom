package fr.inra.oresing.monitoring.compensation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Registry des {@link CompensationHandler} disponibles . Spring auto-collecte
 * tous les beans implementant l'interface , indexed par
 * {@link CompensationHandler#operationType()} .
 *
 * <p>Utilise par le {@code CompensationSweeper} pour router une row
 * compensation_log vers le bon handler selon son {@code operation_type} .
 *
 * @author R.YAHIAOUI
 */
@Component
@Slf4j
public class CompensationHandlerRegistry {

    private final Map<String, CompensationHandler> handlersByType = new HashMap<>();

    public CompensationHandlerRegistry(List<CompensationHandler> handlers) {
        for (CompensationHandler h : handlers) {
            CompensationHandler prev = handlersByType.put(h.operationType(), h);
            if (prev != null) {
                throw new IllegalStateException(
                        "Duplicate CompensationHandler for type '" + h.operationType()
                                + "' : " + prev.getClass().getName()
                                + " vs " + h.getClass().getName());
            }
        }
        log.info("CompensationHandlerRegistry : {} handlers registered : {}",
                handlersByType.size(), handlersByType.keySet());
    }

    public Optional<CompensationHandler> handlerFor(String operationType) {
        return Optional.ofNullable(handlersByType.get(operationType));
    }

    public int size() { return handlersByType.size(); }
}
