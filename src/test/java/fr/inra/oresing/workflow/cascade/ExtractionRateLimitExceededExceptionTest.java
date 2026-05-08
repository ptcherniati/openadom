package fr.inra.oresing.workflow.cascade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests unitaires pour {@link ExtractionRateLimitExceededException}.
 */
@Tag("domain.model")
@DisplayName("ExtractionRateLimitExceededException — 429 quota extractions")
class ExtractionRateLimitExceededExceptionTest {

    @Test
    @DisplayName("getMessage() contient l'userId, le compteur actif et le max")
    void messageContainsDetails() {
        ExtractionRateLimitExceededException ex =
                new ExtractionRateLimitExceededException("user-99", 3, 5);
        assertThat(ex.getMessage())
                .contains("user-99")
                .contains("3")
                .contains("5");
    }

    @Test
    @DisplayName("est une RuntimeException")
    void isRuntimeException() {
        assertThat(new ExtractionRateLimitExceededException("u", 1, 1))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("@ResponseStatus TOO_MANY_REQUESTS est présent")
    void responseStatusAnnotation() {
        org.springframework.web.bind.annotation.ResponseStatus annotation =
                ExtractionRateLimitExceededException.class
                        .getAnnotation(org.springframework.web.bind.annotation.ResponseStatus.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value())
                .isEqualTo(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("message différencie deux userId distincts")
    void messageDifferentUsers() {
        String msg1 = new ExtractionRateLimitExceededException("alice", 2, 2).getMessage();
        String msg2 = new ExtractionRateLimitExceededException("bob",   2, 2).getMessage();
        assertThat(msg1).contains("alice");
        assertThat(msg2).contains("bob");
        assertThat(msg1).isNotEqualTo(msg2);
    }
}
