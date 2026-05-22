package fr.inra.oresing.domain.cancel;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CancellationTokenTest {

    @Test
    void none_isCancelled_returnsFalse() {
        assertThat(CancellationToken.NONE.isCancelled()).isFalse();
    }

    @Test
    void none_throwIfCancelled_doesNotThrow() {
        assertThatCode(CancellationToken.NONE::throwIfCancelled)
                .doesNotThrowAnyException();
    }

    @Test
    void none_throwIfCancelled_withStage_doesNotThrow() {
        assertThatCode(() -> CancellationToken.NONE.throwIfCancelled("myStage"))
                .doesNotThrowAnyException();
    }

    @Test
    void of_notCancelled_isCancelled_returnsFalse() {
        CancellationToken token = CancellationToken.of(() -> false);
        assertThat(token.isCancelled()).isFalse();
    }

    @Test
    void of_cancelled_isCancelled_returnsTrue() {
        CancellationToken token = CancellationToken.of(() -> true);
        assertThat(token.isCancelled()).isTrue();
    }

    @Test
    void throwIfCancelled_whenCancelled_throwsCancellationException() {
        CancellationToken token = CancellationToken.of(() -> true);
        assertThatThrownBy(token::throwIfCancelled)
                .isInstanceOf(CancellationException.class);
    }

    @Test
    void throwIfCancelled_whenNotCancelled_doesNotThrow() {
        CancellationToken token = CancellationToken.of(() -> false);
        assertThatCode(token::throwIfCancelled).doesNotThrowAnyException();
    }

    @Test
    void throwIfCancelled_withStage_whenCancelled_throwsCancellationExceptionMentioningStage() {
        CancellationToken token = CancellationToken.of(() -> true);
        assertThatThrownBy(() -> token.throwIfCancelled("importStep"))
                .isInstanceOf(CancellationException.class)
                .hasMessageContaining("importStep");
    }

    @Test
    void throwIfCancelled_withStage_whenNotCancelled_doesNotThrow() {
        CancellationToken token = CancellationToken.of(() -> false);
        assertThatCode(() -> token.throwIfCancelled("anyStage")).doesNotThrowAnyException();
    }

    @Test
    void of_dynamicSupplier_reflectsStateChange() {
        AtomicBoolean cancelled = new AtomicBoolean(false);
        CancellationToken token = CancellationToken.of(cancelled::get);

        assertThat(token.isCancelled()).isFalse();
        cancelled.set(true);
        assertThat(token.isCancelled()).isTrue();
        assertThatThrownBy(token::throwIfCancelled)
                .isInstanceOf(CancellationException.class);
    }
}