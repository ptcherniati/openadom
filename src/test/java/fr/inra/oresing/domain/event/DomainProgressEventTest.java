package fr.inra.oresing.domain.event;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link DomainProgressEvent}.
 */
@Tag("domain.model")
@DisplayName("DomainProgressEvent – record, progress()")
class DomainProgressEventTest {

    @Test
    @DisplayName("progress() retourne la valeur passée au constructeur")
    void progressAccessor() {
        DomainProgressEvent event = new DomainProgressEvent(0.75);
        assertThat(event.progress()).isEqualTo(0.75);
    }

    @Test
    @DisplayName("progress() = 0.0 est valide")
    void zeroProgress() {
        DomainProgressEvent event = new DomainProgressEvent(0.0);
        assertThat(event.progress()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("progress() = 1.0 est valide")
    void fullProgress() {
        DomainProgressEvent event = new DomainProgressEvent(1.0);
        assertThat(event.progress()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("implémente ImportProgressEvent")
    void implementsInterface() {
        assertThat(new DomainProgressEvent(0.5)).isInstanceOf(ImportProgressEvent.class);
    }

    @Test
    @DisplayName("equals et hashCode sont cohérents (record)")
    void equalsAndHashCode() {
        DomainProgressEvent e1 = new DomainProgressEvent(0.3);
        DomainProgressEvent e2 = new DomainProgressEvent(0.3);
        assertThat(e1).isEqualTo(e2);
        assertThat(e1.hashCode()).isEqualTo(e2.hashCode());
    }

    @Test
    @DisplayName("toString contient la valeur de progression")
    void toStringContainsProgress() {
        DomainProgressEvent event = new DomainProgressEvent(0.42);
        assertThat(event.toString()).contains("0.42");
    }
}
