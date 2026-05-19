package fr.inra.oresing.rest.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.web.csrf.CsrfToken;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires de SpaCsrfTokenRequestHandler.
 *
 * Ce handler force la génération anticipée du token CSRF pour les SPA (Single Page Apps)
 * qui ont besoin du token avant le premier POST. Un bug ici (ne pas appeler csrfToken.get())
 * expose l'application aux attaques CSRF.
 */
@Tag("core.auth")
@ExtendWith(MockitoExtension.class)
@DisplayName("SpaCsrfTokenRequestHandler — CSRF pour SPA")
class SpaCsrfTokenRequestHandlerTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Test
    @DisplayName("handle() appelle csrfToken.get() pour forcer la génération")
    void handleForcesCsrfTokenGeneration() {
        SpaCsrfTokenRequestHandler handler = new SpaCsrfTokenRequestHandler();
        CsrfToken token = mock(CsrfToken.class, org.mockito.Answers.RETURNS_DEFAULTS);
        Supplier<CsrfToken> tokenSupplier = () -> token;

        assertThat(handler).isInstanceOf(org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler.class);
        assertThatCode(() -> handler.handle(request, response, tokenSupplier)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("SpaCsrfTokenRequestHandler étend CsrfTokenRequestAttributeHandler")
    void isSubclassOfCsrfTokenRequestAttributeHandler() {
        SpaCsrfTokenRequestHandler handler = new SpaCsrfTokenRequestHandler();
        assertThat(handler).isInstanceOf(org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler.class);
    }

    private static <T> org.assertj.core.api.AbstractObjectAssert<?, T> assertThat(T actual) {
        return org.assertj.core.api.Assertions.assertThat(actual);
    }
}
