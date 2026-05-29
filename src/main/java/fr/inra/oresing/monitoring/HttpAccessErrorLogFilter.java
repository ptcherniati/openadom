package fr.inra.oresing.monitoring;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Trace en WARN ( 4xx ) ou ERROR ( 5xx ) chaque reponse HTTP en erreur .
 *
 * <p>But : rendre visible dans Loki / Grafana tout echec HTTP ( 401 bad
 * password , 404 endpoint inconnu , 500 exception non gere ... ) sans
 * avoir a instrumenter chaque controller / endpoint . Une seule ligne
 * structuree par requete en erreur , parseable par regexp LogQL .
 *
 * <p>Format de la ligne :
 * {@code HTTP method=POST uri=/api/v1/login status=401 ip=10.0.0.1 login=admin duration=12ms}
 *
 * <p>SRP : ne fait que logger les erreurs HTTP - aucune autre logique .
 * <br>OCP : seuil status configurable via {@code openadom.access-log.min-status} ;
 * URI exclues via {@code openadom.access-log.exclude-prefix} .
 * <br>DRY : 1 seul point d'instrumentation - remplace les logs ad-hoc dans
 * les controllers / handlers .
 *
 * <p>Toggle complet via {@code openadom.access-log.enabled=false} ( default true ) .
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@ConditionalOnProperty(name = "openadom.access-log.enabled", havingValue = "true", matchIfMissing = true)
public class HttpAccessErrorLogFilter extends OncePerRequestFilter {

    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    /** Seuil de status a partir duquel la requete est tracee . 400 par defaut . */
    @Value("${openadom.access-log.min-status:400}")
    private int minStatus;

    /**
     * Prefixes d'URI a exclure ( scrapes Prometheus , favicon , healthcheck ) .
     * Comma-separated , trimmes . Exemple :
     * {@code /actuator,/favicon.ico,/static}
     */
    @Value("${openadom.access-log.exclude-prefix:/actuator,/favicon.ico,/error}")
    private String excludePrefixCsv;

    private String[] excludePrefixes;

    @Override
    protected void initFilterBean() {
        excludePrefixes = excludePrefixCsv == null || excludePrefixCsv.isBlank()
                ? new String[0]
                : excludePrefixCsv.split("\\s*,\\s*");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) return false;
        for (String prefix : excludePrefixes) {
            if (!prefix.isEmpty() && uri.startsWith(prefix)) return true;
        }
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        long startNs = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            if (status >= minStatus) {
                long durationMs = (System.nanoTime() - startNs) / 1_000_000L;
                logErrorResponse(request, status, durationMs);
            }
        }
    }

    private void logErrorResponse(HttpServletRequest request, int status, long durationMs) {
        String method = request.getMethod();
        String uri = request.getRequestURI();
        String ip = resolveClientIp(request);
        String login = resolveAuthenticatedUser();
        if (status >= 500) {
            log.error("HTTP method={} uri={} status={} ip={} login={} duration={}ms",
                    method, uri, status, ip, login, durationMs);
        } else {
            log.warn("HTTP method={} uri={} status={} ip={} login={} duration={}ms",
                    method, uri, status, ip, login, durationMs);
        }
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    private String resolveAuthenticatedUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return "-";
        String name = auth.getName();
        return name == null || name.isBlank() ? "-" : name;
    }
}
