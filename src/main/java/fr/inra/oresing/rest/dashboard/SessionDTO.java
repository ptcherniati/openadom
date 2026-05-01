package fr.inra.oresing.rest.dashboard;

import com.fasterxml.jackson.annotation.JsonInclude;
import fr.inra.oresing.monitoring.session.SessionInfo;
import fr.inra.oresing.monitoring.session.UserSessionLogEntry;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire-format pour l'onglet Sessions de oa-live . Decline le record
 * interne {@link SessionInfo} ( vue live in-memory ) et
 * {@link UserSessionLogEntry} ( vue history DB ) en un seul DTO commun
 * pour simplifier le rendu cote frontend .
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(name = "DashboardSession",
        description = "Session utilisateur authentifiee ; vue live ou historique")
public record SessionDTO(
        UUID    sessionId,
        UUID    userId,
        String  userLogin,
        String  ipAddress,
        String  userAgent,
        Instant loginTime,
        Instant expiresAt,
        Instant logoutTime,
        Long    durationMs,
        String  status,                      // ACTIVE | DISCONNECTED
        String  endReason) {                 // null | LOGOUT | JWT_EXPIRED | KICK

    /** Conversion depuis la vue in-memory ( onglet Active ) . */
    public static SessionDTO fromSession(SessionInfo s, Instant now) {
        Duration d = s.duration(now);
        return new SessionDTO(
                s.sessionId(), s.userId(), s.userLogin(),
                s.ipAddress(), s.userAgent(),
                s.loginTime(), s.expiresAt(),
                s.endTime(),
                d == null ? null : d.toMillis(),
                s.status(now),
                s.endReason());
    }

    /** Conversion depuis la row DB ( onglet Historique ) . */
    public static SessionDTO fromLogEntry(UserSessionLogEntry e) {
        return new SessionDTO(
                e.sessionId(), e.userId(), e.userLogin(),
                e.ipAddress(), e.userAgent(),
                e.loginTime(), null,
                e.logoutTime(),
                e.durationMs(),
                "DISCONNECTED",
                e.endReason());
    }

    /** Pagination wrapper aligne sur DashboardWorkflowDTO.Page . */
    @Schema(name = "DashboardSessionList",
            description = "Resultat paginated de l'historique des sessions")
    public record Page(
            java.util.List<SessionDTO> items,
            long total,
            int  limit,
            int  offset) { }
}
