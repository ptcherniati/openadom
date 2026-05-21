package fr.inra.oresing.rest.users;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/**
 * Repository d'acces a {@code oa_audit.role_grant_audit} :
 * <ul>
 *   <li>{@link #logAction} : INSERT via le wrapper SECURITY DEFINER
 *       {@code oa_audit.record_role_grant_audit} ( pattern identique
 *       workflow_log / compensation_log ) ;</li>
 *   <li>{@link #findByUser} : SELECT direct ( {@code SELECT} ouvert a
 *       PUBLIC sur la table ) - utilise par {@link UserRolesService}
 *       pour aggreger la trace dans la vue detail .</li>
 * </ul>
 *
 * <p>L'INSERT est best-effort : si l'audit echoue ( ex extension oa_audit
 * indisponible suite a un downgrade ) , le service caller log warn et
 * continue ( la trace est diagnostique , pas bloquante ) .
 */
@Repository
@Slf4j
public class RoleGrantAuditRepository {

    private static final RowMapper<RoleGrantAudit> MAPPER = (ResultSet rs, int rowNum) -> {
        UUID id            = (UUID) rs.getObject("id");
        UUID userId        = (UUID) rs.getObject("user_id");
        String roleName    = rs.getString("role_name");
        UUID applicationId = (UUID) rs.getObject("application_id");
        String action      = rs.getString("action");
        Timestamp ts       = rs.getTimestamp("granted_at");
        OffsetDateTime grantedAt = ts == null ? null
                : OffsetDateTime.ofInstant(ts.toInstant(), ZoneOffset.UTC);
        UUID grantedBy     = (UUID) rs.getObject("granted_by");
        return new RoleGrantAudit(id, userId, roleName, applicationId,
                action, grantedAt, grantedBy);
    };

    private final JdbcTemplate jdbcTemplate;

    @Autowired
    public RoleGrantAuditRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Append une row dans le log . Best-effort - le caller swallow
     * d'eventuelles SQLException pour ne pas casser le metier .
     *
     * @param userId        UUID du user destinataire du GRANT / REVOKE
     * @param roleName      nom logique ( {@code applicationManager} , {@code reader} , ... )
     * @param applicationId UUID app ou {@code null} pour role global
     * @param action        {@link RoleGrantAudit#ACTION_GRANT} / {@link RoleGrantAudit#ACTION_REVOKE}
     * @param grantedBy     UUID de l'admin qui a fait l'action ( peut etre null en cas
     *                      d'operation system mais ne devrait jamais l'etre en UI ) .
     */
    public void logAction(UUID userId, String roleName, UUID applicationId,
                          String action, UUID grantedBy) {
        jdbcTemplate.queryForObject(
                "SELECT oa_audit.record_role_grant_audit(?::uuid, ?, ?::uuid, ?, ?::uuid)",
                UUID.class,
                userId == null ? null : userId.toString(),
                roleName,
                applicationId == null ? null : applicationId.toString(),
                action,
                grantedBy == null ? null : grantedBy.toString());
    }

    /**
     * Toutes les rows de log d'un user , triees du plus recent au plus
     * ancien . Inclut GRANTs ET REVOKEs ; le caller filtre selon son
     * besoin ( ex "derniere action active par (role, app)" ) .
     */
    public List<RoleGrantAudit> findByUser(UUID userId) {
        if (userId == null) return List.of();
        return jdbcTemplate.query(
                "SELECT id, user_id, role_name, application_id, action, granted_at, granted_by "
                        + "FROM oa_audit.role_grant_audit "
                        + "WHERE user_id = ?::uuid "
                        + "ORDER BY granted_at DESC",
                MAPPER, userId.toString());
    }
}
