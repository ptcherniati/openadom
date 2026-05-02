package fr.inra.oresing.monitoring.session;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Row immutable persistee dans {@code oa_audit.user_session_log} .
 *
 * <p>Mirroir de la table SQL ( cf. {@code V5__user_session_log.sql} ) ;
 * conversion {@link #fromSession(SessionInfo)} centralisee pour eviter
 * la duplication de logique entre le writer et les tests .
 */
public record UserSessionLogEntry(
        UUID    sessionId,
        UUID    userId,
        String  userLogin,
        String  ipAddress,
        String  userAgent,
        Instant loginTime,
        Instant logoutTime,
        long    durationMs,
        String  endReason) {

    /**
     * Construit l'entry log a partir d'une {@link SessionInfo} terminee .
     *
     * @throws IllegalArgumentException si la session n'a pas
     *         {@code endTime} ( = pas encore terminee ) .
     */
    public static UserSessionLogEntry fromSession(SessionInfo s) {
        if (s.endTime() == null) {
            throw new IllegalArgumentException(
                    "Cannot persist a session that has not ended : " + s.sessionId());
        }
        long durationMs = s.loginTime() != null
                ? Duration.between(s.loginTime(), s.endTime()).toMillis()
                : 0L;
        return new UserSessionLogEntry(
                s.sessionId(), s.userId(), s.userLogin(),
                s.ipAddress(), s.userAgent(),
                s.loginTime(), s.endTime(), durationMs,
                s.endReason());
    }
}
