package fr.inra.oresing.domain.data.deposit.bundle;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.data.rapport.BundleReport;
import fr.inra.oresing.domain.event.DomainProgressEvent;
import fr.inra.oresing.domain.event.ImportProgressEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests unitaires pour {@link RegisterReactiveResult}.
 */
@Tag("domain.model")
@DisplayName("RegisterReactiveResult – add, progression, exception")
class RegisterReactiveResultTest {

    private static Application buildApp(String name) {
        Application app = new Application();
        app.setName(name);
        return app;
    }

    private static BundleReport bundleReport() {
        return new BundleReport(Locale.FRENCH, "test", buildApp("app"), obj -> "{}");
    }

    // ─────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Constructeur(consumer, countFiles, bundleReport) initialise done=0")
    void constructor() {
        BundleReport report = bundleReport();
        RegisterReactiveResult rrr = new RegisterReactiveResult(e -> {}, 3, report);
        assertThat(rrr.done().get()).isZero();
        assertThat(rrr.countFiles()).isEqualTo(3);
        assertThat(rrr.bundleReport()).isSameAs(report);
    }

    // ─────────────────────────────────────────────────────────────────
    // add(event, false) – pas d'incrément
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("add(event, false) émet l'événement et l'ajoute au rapport sans incrémenter")
    void addWithoutIncrement() {
        List<ImportProgressEvent> emitted = new ArrayList<>();
        BundleReport report = bundleReport();
        RegisterReactiveResult rrr = new RegisterReactiveResult(emitted::add, 5, report);

        ImportProgressEvent event = new DomainProgressEvent(0.2);
        rrr.add(event, false);

        assertThat(emitted).containsExactly(event);
        assertThat(report.results()).containsExactly(event);
        assertThat(rrr.done().get()).isZero();
    }

    // ─────────────────────────────────────────────────────────────────
    // add(event, true) – avec incrément
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("add(event, true) incrémente done et émet un DomainProgressEvent supplémentaire")
    void addWithIncrement() {
        List<ImportProgressEvent> emitted = new ArrayList<>();
        BundleReport report = bundleReport();
        RegisterReactiveResult rrr = new RegisterReactiveResult(emitted::add, 10, report);

        ImportProgressEvent event = new DomainProgressEvent(0.1);
        rrr.add(event, true);

        // 2 événements émis : l'original + le DomainProgressEvent synthétique
        assertThat(emitted).hasSize(2);
        assertThat(emitted.get(0)).isSameAs(event);
        assertThat(emitted.get(1)).isInstanceOf(DomainProgressEvent.class);
        assertThat(rrr.done().get()).isEqualTo(1);
        assertThat(report.results()).containsExactly(event);
    }

    @Test
    @DisplayName("add(event, true) plusieurs fois incrémente progressivement")
    void addMultipleIncrements() {
        List<ImportProgressEvent> emitted = new ArrayList<>();
        BundleReport report = bundleReport();
        RegisterReactiveResult rrr = new RegisterReactiveResult(emitted::add, 5, report);

        for (int i = 0; i < 3; i++) {
            rrr.add(new DomainProgressEvent(0.1 * i), true);
        }
        assertThat(rrr.done().get()).isEqualTo(3);
        // 3 originaux + 3 synthétiques
        assertThat(emitted).hasSize(6);
    }

    // ─────────────────────────────────────────────────────────────────
    // Propagation d'exception du consommateur
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("add() propage l'exception du consommateur en IllegalStateException")
    void addConsumerThrowsIllegalState() {
        BundleReport report = bundleReport();
        RegisterReactiveResult rrr = new RegisterReactiveResult(
                e -> { throw new RuntimeException("sink closed"); }, 5, report);

        ImportProgressEvent event = new DomainProgressEvent(0.5);
        assertThatThrownBy(() -> rrr.add(event, false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to emit import progress event");
    }

    @Test
    @DisplayName("add() enregistre l'événement dans le rapport même si le consommateur lève une exception")
    void addConsumerExceptionStillAddsToReport() {
        BundleReport report = bundleReport();
        AtomicInteger callCount = new AtomicInteger(0);
        RegisterReactiveResult rrr = new RegisterReactiveResult(
                e -> {
                    if (callCount.incrementAndGet() == 1) {
                        throw new RuntimeException("boom");
                    }
                }, 5, report);

        ImportProgressEvent event = new DomainProgressEvent(0.5);
        try {
            rrr.add(event, false);
        } catch (IllegalStateException ignored) {
            // expected
        }
        // finally block doit quand même ajouter l'événement au rapport
        assertThat(report.results()).containsExactly(event);
    }

    // ─────────────────────────────────────────────────────────────────
    // DomainProgressEvent – valeur de progression
    // ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("DomainProgressEvent synthétique a une progression entre 0.0 et 1.0")
    void syntheticProgressInRange() {
        AtomicReference<ImportProgressEvent> lastEvent = new AtomicReference<>();
        BundleReport report = bundleReport();
        RegisterReactiveResult rrr = new RegisterReactiveResult(lastEvent::set, 10, report);

        rrr.add(new DomainProgressEvent(0.0), true);

        assertThat(lastEvent.get()).isInstanceOf(DomainProgressEvent.class);
        double progress = ((DomainProgressEvent) lastEvent.get()).progress();
        assertThat(progress).isBetween(0.0, 1.0);
    }
}
