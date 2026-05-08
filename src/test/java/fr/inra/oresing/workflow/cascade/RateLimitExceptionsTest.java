package fr.inra.oresing.workflow.cascade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Rate-limit exceptions — message et annotations HTTP")
@Tag("domain.model")
class RateLimitExceptionsTest {

    @Test
    @DisplayName("ImportRateLimitExceededException est une RuntimeException")
    void importExceptionIsRuntimeException() {
        var ex = new ImportRateLimitExceededException("user1", 3, 3);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("ImportRateLimitExceededException message contient userId, active et max")
    void importExceptionMessageContainsContext() {
        var ex = new ImportRateLimitExceededException("user1", 3, 3);
        assertThat(ex.getMessage())
                .contains("user1")
                .contains("3");
    }

    @Test
    @DisplayName("ImportRateLimitExceededException porte @ResponseStatus(TOO_MANY_REQUESTS)")
    void importExceptionHasTooManyRequestsStatus() {
        ResponseStatus annotation = ImportRateLimitExceededException.class
                .getAnnotation(ResponseStatus.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }

    @Test
    @DisplayName("ExtractionRateLimitExceededException est une RuntimeException")
    void extractionExceptionIsRuntimeException() {
        var ex = new ExtractionRateLimitExceededException("user2", 2, 5);
        assertThat(ex).isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("ExtractionRateLimitExceededException message contient userId, active et max")
    void extractionExceptionMessageContainsContext() {
        var ex = new ExtractionRateLimitExceededException("user2", 2, 5);
        assertThat(ex.getMessage())
                .contains("user2")
                .contains("2")
                .contains("5");
    }

    @Test
    @DisplayName("ExtractionRateLimitExceededException porte @ResponseStatus(TOO_MANY_REQUESTS)")
    void extractionExceptionHasTooManyRequestsStatus() {
        ResponseStatus annotation = ExtractionRateLimitExceededException.class
                .getAnnotation(ResponseStatus.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
    }
}
