package fr.inra.oresing.workflow.cascade.history;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Snapshot d'un workflow publish executé via le FAST path direct COPY
 * ( cache {@code processed_data} restauré tel quel dans {@code referencevalue}
 * via {@code COPY ... FROM stdin ( FORMAT BINARY )} , sans cascade
 * DataImporter ni table intermédiaire ) .
 *
 * <p>Phases distinctes du pipeline , exposées au frontend pour remplacer
 * la grille cascade ( workers SOURCE/TRANSFORM/SINK ) non pertinente quand
 * DataImporter est bypassé :
 *
 * <ul>
 *   <li>{@link #PHASE_DELETE_REFREF} : DELETE FROM reference_reference WHERE
 *       referenceid IN ( SELECT id FROM referencevalue WHERE binaryfile=? ) .
 *       Supprime les liens pointant vers les rows qu'on va wiper .</li>
 *   <li>{@link #PHASE_DELETE_EXISTING} : DELETE FROM referencevalue WHERE
 *       binaryfile = ? . On vide les rows existantes du fichier avant le
 *       reload depuis cache .</li>
 *   <li>{@link #PHASE_COPY_IN} : {@code COPY referencevalue FROM stdin
 *       ( FORMAT BINARY )} streamé depuis le Large Object cache . Une
 *       seule commande SQL ; PG encode/décode lui-même les types
 *       composites ( authorization ) en binaire natif .</li>
 *   <li>{@link #PHASE_INSERT_REFREF} : INSERT INTO reference_reference
 *       reconstruit depuis {@code referencevalue.refsLinkedTo} après le
 *       COPY IN ( JSON_TABLE extraction filtré par binaryfile ) .</li>
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

    /**
     * @deprecated The FAST path no longer emits a dedicated DELETE_REFREF
     *             phase : the cascade DELETE on referencevalue auto-cleans
     *             reference_reference via the {@code ON DELETE CASCADE}
     *             foreign key on referenceid . Kept here ONLY for backwards
     *             compatibility with historical workflow_log rows that may
     *             still carry this phase string . New runs start with
     *             {@link #PHASE_DELETE_EXISTING} .
     */
    @Deprecated
    public static final String PHASE_DELETE_REFREF   = "DELETE_REFREF";
    public static final String PHASE_DELETE_EXISTING = "DELETE_EXISTING";
    public static final String PHASE_COPY_IN         = "COPY_IN";
    public static final String PHASE_INSERT_REFREF   = "INSERT_REFREF";
    public static final String PHASE_CACHE_CLEAR     = "CACHE_CLEAR";
    public static final String PHASE_DONE            = "DONE";

    public static FastPathSnapshot starting(long cacheBytes, Instant at, UUID fileId, String filename) {
        return new FastPathSnapshot(
                PHASE_DELETE_EXISTING,
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
