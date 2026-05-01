package fr.inra.oresing.monitoring.session;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Snapshot in-memory d'une session utilisateur authentifiee . Carry-over
 * de la phase login dans le {@link UserSessionRegistry} . Lifecycle :
 *
 * <ul>
 *   <li>creation au login ( {@code AuthenticationResources.login} ) avec
 *       {@code endTime = null} et {@code endReason = null} .</li>
 *   <li>termination soit par un click logout ( {@code endReason = LOGOUT} )
 *       soit par expiration JWT detectee lazily quand un consumer liste
 *       les sessions ( {@code endReason = JWT_EXPIRED} ) .</li>
 * </ul>
 *
 * <p>Le statut binaire {@code isActive()} est derive lazily de
 * {@code endTime == null && expiresAt > now} - aucune mutation
 * synchrone necessaire .
 *
 * @param sessionId   identifiant interne unique ( UUID v4 )
 * @param userId      id metier utilisateur
 * @param userLogin   login affichable
 * @param ipAddress   IP source ( resolved via X-Forwarded-For si proxy )
 * @param userAgent   header HTTP User-Agent ( tronque a 500 chars )
 * @param loginTime   timestamp d'arrivee dans le registry
 * @param expiresAt   {@code loginTime + jwt.expiration} ; sert a detecter
 *                    JWT_EXPIRED sans tracker l'activite
 * @param endTime     null tant que la session est active
 * @param endReason   null tant qu'active ; sinon LOGOUT | JWT_EXPIRED | KICK
 */
public record SessionInfo(
        UUID    sessionId,
        UUID    userId,
        String  userLogin,
        String  ipAddress,
        String  userAgent,
        Instant loginTime,
        Instant expiresAt,
        Instant endTime,
        String  endReason) {

    public static final String END_LOGOUT      = "LOGOUT";
    public static final String END_JWT_EXPIRED = "JWT_EXPIRED";
    public static final String END_KICK        = "KICK";

    /** Active = pas encore terminee ET pas encore expiree par TTL JWT . */
    public boolean isActive(Instant now) {
        return endTime == null && (expiresAt == null || now.isBefore(expiresAt));
    }

    /** Status user-facing : ACTIVE | DISCONNECTED . */
    public String status(Instant now) {
        return isActive(now) ? "ACTIVE" : "DISCONNECTED";
    }

    /** Duree totale ( ou en cours ) , null si donnees insuffisantes . */
    public Duration duration(Instant now) {
        if (loginTime == null) return null;
        Instant end = endTime != null ? endTime : (expiresAt != null && now.isAfter(expiresAt) ? expiresAt : now);
        return Duration.between(loginTime, end);
    }

    /** Helper immutable pour terminer la session avec une raison . */
    public SessionInfo withEnd(Instant endTime, String reason) {
        return new SessionInfo(sessionId, userId, userLogin, ipAddress, userAgent,
                loginTime, expiresAt, endTime, reason);
    }
}
