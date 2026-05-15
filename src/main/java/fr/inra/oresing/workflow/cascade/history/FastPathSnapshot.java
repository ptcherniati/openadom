package fr.inra.oresing.workflow.cascade.history;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshot d'un workflow publish executé via le FAST path
 * ( cache {@code processed_data} directement projeté en SQL pur
 * vers {@code referencevalue} , sans cascade DataImporter ) .
 *
 * <p>Phases distinctes du pipeline FAST , exposées au frontend pour
 * remplacer la grille cascade ( workers SOURCE/TRANSFORM/SINK ) non
 * pertinente quand DataImporter est bypass :
 *
 * <ul>
 *   <li>{@link #PHASE_STREAM_CACHE} : Java lit Large Object processed_data
 *       + INSERT bulk dans temp table parsed_lines . <b>Seule phase avec
 *       progression live</b> ( ticks tous les 5000 lignes ) .</li>
 *   <li>{@link #PHASE_BUILD_REFREF} : INSERT INTO refref_pending
 *       SELECT depuis parsed_lines + JSON_TABLE refsLinkedTo .</li>
 *   <li>{@link #PHASE_DELETE_REFREF} : DELETE FROM reference_reference
 *       WHERE referenceid IN ( refref_pending ) .</li>
 *   <li>{@link #PHASE_UPSERT_FINAL} : INSERT INTO referencevalue ON CONFLICT
 *       DO UPDATE depuis parsed_lines + jsonb_populate_record .
 *       Statement unique , pas de progression intermediaire .</li>
 *   <li>{@link #PHASE_INSERT_REFREF} : INSERT INTO reference_reference
 *       SELECT FROM refref_pending .</li>
 *   <li>{@link #PHASE_CACHE_CLEAR} : UPDATE binaryfile SET processed_data
 *       = NULL ( CACHED_ROTATION uniquement ) .</li>
 *   <li>{@link #PHASE_DONE} : terminé .</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
public record FastPathSnapshot(
        String              phase,
        long                cacheSizeBytes,
        long                streamedRows,
        long                upsertedRows,
        Instant             startedAt,
        Map<String, Long>   phaseDurations,
        /** UUID du binaryfile cible ( oa-live workflow detail diagnostic ) . Null si non resolu . */
        UUID                fileId,
        /** Nom du fichier d'origine ( {@code BinaryFile.name} ) . Affiche tooltip oa-live . Null si non resolu . */
        String              filename) {

    public static final String PHASE_STREAM_CACHE   = "STREAM_CACHE";
    public static final String PHASE_BUILD_REFREF   = "BUILD_REFREF";
    public static final String PHASE_DELETE_REFREF  = "DELETE_REFREF";
    public static final String PHASE_UPSERT_FINAL   = "UPSERT_FINAL";
    public static final String PHASE_INSERT_REFREF  = "INSERT_REFREF";
    public static final String PHASE_CACHE_CLEAR    = "CACHE_CLEAR";
    public static final String PHASE_DONE           = "DONE";

    public static FastPathSnapshot starting(long cacheBytes, Instant at, UUID fileId, String filename) {
        return new FastPathSnapshot(
                PHASE_STREAM_CACHE,
                cacheBytes,
                0L,
                0L,
                at,
                new LinkedHashMap<>(),
                fileId,
                filename);
    }

    public FastPathSnapshot withPhase(String newPhase) {
        return new FastPathSnapshot(
                newPhase, cacheSizeBytes, streamedRows, upsertedRows,
                startedAt, phaseDurations, fileId, filename);
    }

    public FastPathSnapshot withStreamedRows(long rows) {
        return new FastPathSnapshot(
                phase, cacheSizeBytes, rows, upsertedRows,
                startedAt, phaseDurations, fileId, filename);
    }

    public FastPathSnapshot withUpsertedRows(long rows) {
        return new FastPathSnapshot(
                phase, cacheSizeBytes, streamedRows, rows,
                startedAt, phaseDurations, fileId, filename);
    }

    public FastPathSnapshot withPhaseDuration(String forPhase, long durationMs) {
        Map<String, Long> next = new LinkedHashMap<>(phaseDurations);
        next.put(forPhase, durationMs);
        return new FastPathSnapshot(
                phase, cacheSizeBytes, streamedRows, upsertedRows,
                startedAt, next, fileId, filename);
    }
}
