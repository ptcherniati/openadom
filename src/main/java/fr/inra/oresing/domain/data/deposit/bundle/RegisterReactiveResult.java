package fr.inra.oresing.domain.data.deposit.bundle;

import fr.inra.oresing.domain.data.rapport.BundleReport;
import fr.inra.oresing.domain.event.DomainProgressEvent;
import fr.inra.oresing.domain.event.ImportProgressEvent;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Contexte de collecte et d'émission des événements d'avancement d'un import bundle.
 *
 * <p>Utilise un {@link Consumer} domaine au lieu d'un {@code FluxSink} Reactor,
 * ce qui permet au domaine d'émettre des événements sans dépendre de l'infrastructure
 * réactive REST. La couche REST passe un adaptateur {@code sink::next} comme consommateur.
 */
public record RegisterReactiveResult(
        Consumer<ImportProgressEvent> eventConsumer,
        BundleReport bundleReport,
        int countFiles,
        AtomicInteger done
) {
    public RegisterReactiveResult(Consumer<ImportProgressEvent> eventConsumer, int countFiles, BundleReport bundleReport) {
        this(eventConsumer, bundleReport, countFiles, new AtomicInteger(0));
    }

    /**
     * Émet un événement vers le consommateur et l'enregistre dans le rapport.
     *
     * @param event     l'événement à émettre
     * @param increment si {@code true}, incrémente la progression
     */
    public void add(ImportProgressEvent event, boolean increment) {
        try {
            eventConsumer().accept(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to emit import progress event: " + e.getMessage(), e);
        } finally {
            bundleReport().add(event);
            if (increment) {
                int progress = done.incrementAndGet();
                // Émet un événement de progression synthétique (signal d'avancement pur,
                // non enregistré dans le rapport)
                eventConsumer().accept(new DomainProgressEvent(0.1 + (0.9 / countFiles) * progress));
            }

        }
    }
}