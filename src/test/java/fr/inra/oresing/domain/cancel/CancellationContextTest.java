package fr.inra.oresing.domain.cancel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationContextTest {

    @AfterEach
    void cleanup() {
        CancellationContext.clear();
    }

    // ---- currentToken sans scope ----

    @Test
    void currentToken_withoutScope_returnsNone() {
        assertThat(CancellationContext.currentToken()).isSameAs(CancellationToken.NONE);
    }

    @Test
    void currentParentCid_withoutScope_returnsNull() {
        assertThat(CancellationContext.currentParentCid()).isNull();
    }

    @Test
    void checkpoint_withoutScope_doesNotThrow() {
        assertThatCode(CancellationContext::checkpoint).doesNotThrowAnyException();
    }

    // ---- set / clear ----

    @Test
    void set_exposesTokenAndParentCid() {
        UUID parentCid = UUID.randomUUID();
        CancellationToken token = CancellationToken.of(() -> false);

        CancellationContext.set(parentCid, token);

        assertThat(CancellationContext.currentToken()).isSameAs(token);
        assertThat(CancellationContext.currentParentCid()).isEqualTo(parentCid);
    }

    @Test
    void set_nullToken_usesNoneSentinel() {
        UUID parentCid = UUID.randomUUID();
        CancellationContext.set(parentCid, null);

        assertThat(CancellationContext.currentToken()).isSameAs(CancellationToken.NONE);
        assertThat(CancellationContext.currentParentCid()).isEqualTo(parentCid);
    }

    @Test
    void clear_removesScope() {
        UUID parentCid = UUID.randomUUID();
        CancellationContext.set(parentCid, CancellationToken.of(() -> false));
        CancellationContext.clear();

        assertThat(CancellationContext.currentToken()).isSameAs(CancellationToken.NONE);
        assertThat(CancellationContext.currentParentCid()).isNull();
    }

    // ---- checkpoint ----

    @Test
    void checkpoint_withActiveNotCancelledToken_doesNotThrow() {
        CancellationContext.set(UUID.randomUUID(), CancellationToken.of(() -> false));
        assertThatCode(CancellationContext::checkpoint).doesNotThrowAnyException();
    }

    @Test
    void checkpoint_withCancelledToken_throwsCancellationException() {
        CancellationContext.set(UUID.randomUUID(), CancellationToken.of(() -> true));
        assertThatThrownBy(CancellationContext::checkpoint)
                .isInstanceOf(CancellationException.class);
    }

    @Test
    void checkpoint_withStage_withCancelledToken_throwsAndMentionsStage() {
        CancellationContext.set(UUID.randomUUID(), CancellationToken.of(() -> true));
        assertThatThrownBy(() -> CancellationContext.checkpoint("validationStep"))
                .isInstanceOf(CancellationException.class)
                .hasMessageContaining("validationStep");
    }

    @Test
    void checkpoint_withStage_withNotCancelledToken_doesNotThrow() {
        CancellationContext.set(UUID.randomUUID(), CancellationToken.of(() -> false));
        assertThatCode(() -> CancellationContext.checkpoint("anyStep"))
                .doesNotThrowAnyException();
    }

    // ---- dynamic cancel ----

    @Test
    void set_dynamicToken_reflectsLiveState() {
        AtomicBoolean flag = new AtomicBoolean(false);
        CancellationContext.set(UUID.randomUUID(), CancellationToken.of(flag::get));

        assertThatCode(CancellationContext::checkpoint).doesNotThrowAnyException();

        flag.set(true);
        assertThatThrownBy(CancellationContext::checkpoint)
                .isInstanceOf(CancellationException.class);
    }

    // ---- clear idempotency ----

    @Test
    void clear_calledTwice_doesNotThrow() {
        CancellationContext.set(UUID.randomUUID(), CancellationToken.NONE);
        assertThatCode(() -> {
            CancellationContext.clear();
            CancellationContext.clear();
        }).doesNotThrowAnyException();
    }
}
