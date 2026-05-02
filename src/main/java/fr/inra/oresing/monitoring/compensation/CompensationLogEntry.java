package fr.inra.oresing.monitoring.compensation;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Row de la table {@code oa_audit.compensation_log} : journal d'intent
 * pour les operations a compenser en cas d'echec ( crash JVM , timeout
 * DB , etc. ) .
 *
 * <p>Lifecycle :
 * <ol>
 *   <li>INSERT au demarrage de l'op ( status = PENDING )</li>
 *   <li>DELETE apres succes confirme</li>
 *   <li>Apres TTL : sweeper invoque le handler associe pour
 *       compenser ( DELETE row metier ) avec smart-check protection</li>
 *   <li>Si handler echoue {@link #MAX_RETRIES} fois : status = FAILED ,
 *       intervention humaine requise</li>
 * </ol>
 *
 * @author R.YAHIAOUI
 */
public record CompensationLogEntry(
        UUID    id,
        String  operationType,
        String  targetSchema,
        String  targetTable,
        String  targetId,
        UUID    correlationId,
        UUID    userId,
        String  userLogin,
        Map<String, Object> payload,
        String  status,
        Instant startedAt,
        int     ttlMinutes,
        int     attemptCount,
        Instant lastAttemptAt,
        String  lastError) {

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_FAILED  = "FAILED";

    /** Default TTL avant qu'une row PENDING soit consideree orpheline ( 4h ) . */
    public static final int DEFAULT_TTL_MINUTES = 240;

    /** Max retries handler avant marquage FAILED . */
    public static final int MAX_RETRIES = 5;
}
