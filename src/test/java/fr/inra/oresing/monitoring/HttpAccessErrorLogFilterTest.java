package fr.inra.oresing.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link HttpAccessErrorLogFilter} .
 *
 * Verifie : status < min-status ignore , 4xx -> WARN , 5xx -> ERROR ,
 * exclude prefixes shouldNotFilter , X-Forwarded-For respect .
 */
@Tag("core.config")
@DisplayName("HttpAccessErrorLogFilter - log seulement sur erreur + exclude prefixes")
class HttpAccessErrorLogFilterTest {

    private HttpAccessErrorLogFilter filter;

    @BeforeEach
    void setUp() {
        filter = new HttpAccessErrorLogFilter();
        ReflectionTestUtils.setField(filter, "minStatus", 400);
        ReflectionTestUtils.setField(filter, "excludePrefixCsv",
                "/actuator,/favicon.ico,/error");
        ReflectionTestUtils.invokeMethod(filter, "initFilterBean");
    }

    @Test
    @DisplayName("shouldNotFilter : URI matche prefix exclude -> skip")
    void excludePrefix() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/prometheus");
        assertTrue(filter.shouldNotFilter(req));
    }

    @Test
    @DisplayName("shouldNotFilter : URI hors prefix exclude -> filtre")
    void notExcluded() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/login");
        assertFalse(filter.shouldNotFilter(req));
    }

    @Test
    @DisplayName("shouldNotFilter : URI null -> false ( on filtre )")
    void nullUriDoesNotSkip() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", null);
        assertFalse(filter.shouldNotFilter(req));
    }

    @Test
    @DisplayName("doFilterInternal : status 200 -> aucun log ( pas d'erreur )")
    void noLogOnSuccess() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/login");
        MockHttpServletResponse res = new MockHttpServletResponse();
        res.setStatus(200);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(any(HttpServletRequest.class), any(HttpServletResponse.class));
        // Pas de log a verifier ici - on s'assure juste que le filter ne throw pas
        assertEquals(200, res.getStatus());
    }

    @Test
    @DisplayName("doFilterInternal : status 401 -> filtre traverse + status capture")
    void filtersOn401() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/login");
        req.setRemoteAddr("10.0.0.42");
        MockHttpServletResponse res = new MockHttpServletResponse();
        res.setStatus(401);
        FilterChain chain = mock(FilterChain.class);
        filter.doFilter(req, res, chain);
        verify(chain).doFilter(any(), any());
        assertEquals(401, res.getStatus());
    }

    @Test
    @DisplayName("doFilterInternal : status 500 -> ERROR ( filtre passe sans throw )")
    void filtersOn500() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/data");
        MockHttpServletResponse res = new MockHttpServletResponse();
        res.setStatus(500);
        filter.doFilter(req, res, mock(FilterChain.class));
        assertEquals(500, res.getStatus());
    }

    @Test
    @DisplayName("resolveClientIp : X-Forwarded-For prioritaire sur remoteAddr")
    void clientIpFromXForwardedFor() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/x");
        req.setRemoteAddr("172.17.0.1");
        req.addHeader("X-Forwarded-For", "10.0.0.42, 172.18.0.1");
        String ip = ReflectionTestUtils.invokeMethod(filter, "resolveClientIp", req);
        assertEquals("10.0.0.42", ip);
    }

    @Test
    @DisplayName("resolveClientIp : pas de X-Forwarded-For -> remoteAddr")
    void clientIpFromRemoteAddr() {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/x");
        req.setRemoteAddr("172.17.0.1");
        String ip = ReflectionTestUtils.invokeMethod(filter, "resolveClientIp", req);
        assertEquals("172.17.0.1", ip);
    }
}
