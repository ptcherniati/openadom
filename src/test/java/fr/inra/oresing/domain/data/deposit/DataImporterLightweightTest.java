package fr.inra.oresing.domain.data.deposit;

import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires du flag {@code lightweight} sur {@link DataImporter} -
 * verifie l'exposition publique sans declencher l'init complet du contexte
 * ( qui requiert une vraie Application Spring + DB ) .
 */
class DataImporterLightweightTest {

    @Test
    @DisplayName("Constructeur 1-arg : lightweight = false par defaut")
    void defaultCtorNotLite() {
        AsynchroneFileImporterContext ctx = mock(AsynchroneFileImporterContext.class);
        when(ctx.isRecursive()).thenReturn(false);
        DataImporter importer = new DataImporter(ctx);
        assertThat(importer.isLightweight()).isFalse();
    }

    @Test
    @DisplayName("Constructeur 2-arg : lightweight = false par defaut")
    void twoArgCtorNotLite() {
        AsynchroneFileImporterContext ctx = mock(AsynchroneFileImporterContext.class);
        when(ctx.isRecursive()).thenReturn(false);
        DataImporter importer = new DataImporter(ctx, null);
        assertThat(importer.isLightweight()).isFalse();
    }

    @Test
    @DisplayName("Constructeur 3-arg lightweight=true : expose true")
    void liteCtorLite() {
        AsynchroneFileImporterContext ctx = mock(AsynchroneFileImporterContext.class);
        when(ctx.isRecursive()).thenReturn(false);
        DataImporter importer = new DataImporter(ctx, null, true);
        assertThat(importer.isLightweight()).isTrue();
    }

    @Test
    @DisplayName("Constructeur 3-arg lightweight=false : expose false")
    void liteCtorFull() {
        AsynchroneFileImporterContext ctx = mock(AsynchroneFileImporterContext.class);
        when(ctx.isRecursive()).thenReturn(false);
        DataImporter importer = new DataImporter(ctx, null, false);
        assertThat(importer.isLightweight()).isFalse();
    }
}
