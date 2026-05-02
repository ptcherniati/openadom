package fr.inra.oresing.workflow.cascade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * SQL helpers reused between {@link fr.inra.oresing.persistence.DataRepository#storeAll(java.nio.file.Path)}
 * ( legacy MERGE_FILE path ) and the cascade {@code StagingPostgresSink}
 * finalize hook ( DIRECT_COPY path ) .
 *
 * <p>The two paths execute the SAME SQL ( reference_reference cleanup +
 * reference_reference rebuild + batched UPSERT into the target table ) ;
 * only the trigger differs ( a {@code Path} parameter for legacy , a hook
 * callback for direct ) . Centralising the SQL avoids drift between the
 * two paths .
 *
 * <h2>Atomicity</h2>
 *
 * <p>All statements run on the {@link Connection} passed in . The caller
 * owns the transaction lifecycle : commit if everything succeeds , rollback
 * if anything throws . This class never commits / rollbacks .
 *
 * @author R.YAHIAOUI
 * @since cascade 1.7.0 integration
 */
public final class StagingFinalizeSql {

    private static final Logger log = LoggerFactory.getLogger(StagingFinalizeSql.class);

    /** Bulk-INSERT batch size ( rows ) . Override via {@code -Dapp.import.bulkInsertBatchSize=N} . */
    public static final int BULK_INSERT_BATCH_SIZE =
            Integer.getInteger("app.import.bulkInsertBatchSize", 50_000);

    /**
     * Per-batch query timeout ( seconds ) . Postgres tue la requete si elle
     * depasse cette duree , ce qui declenche {@link SQLException} ,
     * propage en {@code SinkException} cote cascade et rollback la
     * transaction sticky ( les rows deja upsertees dans la table cible
     * sont effacees atomiquement par le ROLLBACK ) .
     *
     * <p>Default 3600s ( 1h ) : marge tres large pour absorber la
     * contention DB sous charge . Override via la JVM property
     * {@code -Dapp.import.staging.upsert.batchTimeoutSeconds=N} .
     *
     * <p>0 = pas de timeout client-side ( delegue au {@code statement_timeout}
     * Postgres-side configure par le DBA ) .
     */
    public static final int UPSERT_BATCH_TIMEOUT_SECONDS =
            Integer.getInteger("app.import.staging.upsert.batchTimeoutSeconds", 3600);

    private StagingFinalizeSql() { }

    /**
     * Executes the openADOM finalize sequence : delete-then-insert
     * {@code reference_reference} , batched UPSERT into the target table .
     *
     * <p>For PER_CONNECTION_TEMP ( single sticky connection ) , {@code stagingTable}
     * is the TEMP table name and {@code correlationId} is ignored ( pass null ) .
     * For SHARED_UNLOGGED , {@code stagingTable} is the permanent table name
     * and {@code correlationId} is the workflow uuid used to filter the rows
     * relevant to this import ( the staging table also carries rows from
     * other concurrent workflows ) .
     *
     * @param connection         active sticky connection ( autoCommit = false )
     * @param schemaName         application schema ( e.g. "adjacentcomponents" )
     * @param targetTableSqlId   target table fully-qualified SQL identifier ( e.g. {@code "adjacentcomponents.referenceValue"} )
     * @param targetColumns      target table column list in INSERT order ( e.g. ORDERED_COLUMNS )
     * @param stagingTable       staging table name ( unqualified ; e.g. {@code "referencevalue_import"} or {@code "referencevalue_import_shared"} )
     * @param correlationId      workflow correlation id for SHARED_UNLOGGED filter ; pass null for PER_CONNECTION_TEMP
     * @param idJsonPath         JSONB path for the id column inside the {@code data} field ( e.g. {@code "id"} )
     * @throws SQLException on any SQL failure ( caller must rollback )
     */
    public static void runFinalize(
            Connection connection,
            String schemaName,
            String targetTableSqlId,
            String[] targetColumns,
            String stagingTable,
            String correlationId,
            String idJsonPath
    ) throws SQLException {

        // Diagnostic : permet de detecter le scenario "cascade tourne hors
        // tx Spring" qui cause des FK violations sur binaryfile non encore
        // committe . Utile pour le post-mortem des erreurs intermittentes
        // ( 1er upload OK , 2eme upload FK violation ) .
        boolean inSpringTx = false;
        try {
            inSpringTx = org.springframework.transaction.support
                    .TransactionSynchronizationManager.isActualTransactionActive();
        } catch (NoClassDefFoundError | RuntimeException ignore) { /* hors contexte Spring : best-effort */ }
        log.info("StagingFinalize start : thread={} , correlationId={} , autoCommit={} , inSpringTx={}",
                Thread.currentThread().getName(),
                correlationId == null ? "(none)" : correlationId.substring(0, Math.min(8, correlationId.length())),
                connection.getAutoCommit(),
                inSpringTx);

        String columnList = Arrays.stream(targetColumns)
                .map(String::toLowerCase)
                .collect(Collectors.joining(","));

        boolean filtered = (correlationId != null && !correlationId.isBlank());
        String filterSql = filtered ? " AND correlation_id = ? " : "";

        // 1) DELETE FROM reference_reference WHERE referenceid IN (SELECT data->>id FROM staging [WHERE correlation_id = ?])
        String deleteRefRefSql = "DELETE FROM " + schemaName + ".reference_reference"
                + " WHERE referenceid IN ("
                + " SELECT (data->>'" + idJsonPath + "')::uuid FROM " + stagingTable
                + (filtered ? " WHERE correlation_id = ?" : "")
                + " )";
        try (PreparedStatement ps = connection.prepareStatement(deleteRefRefSql)) {
            if (filtered) ps.setObject(1, UUID.fromString(correlationId));
            ps.executeUpdate();
        }

        // 2) INSERT INTO reference_reference (refid, refby) SELECT DISTINCT FROM staging
        String insertRefRefSql = "INSERT INTO " + schemaName + ".reference_reference(referenceid, referencesby)"
                + " SELECT DISTINCT (s.data->>'" + idJsonPath + "')::uuid referenceid, referencesby::uuid"
                + " FROM " + stagingTable + " s, JSON_TABLE("
                + "     s.data, '$.refslinkedto.*.*.*.uuids' COLUMNS ("
                + "         NESTED PATH '$[*]' COLUMNS(referencesby TEXT PATH '$')"
                + "     )"
                + " ) as joins"
                + (filtered ? " WHERE s.correlation_id = ?" : "");
        try (PreparedStatement ps = connection.prepareStatement(insertRefRefSql)) {
            if (filtered) ps.setObject(1, UUID.fromString(correlationId));
            ps.executeUpdate();
        }

        // 3) Batched UPSERT into target table : DELETE batch from staging RETURNING data ,
        //    INSERT INTO target SELECT cols FROM batch ON CONFLICT DO UPDATE .
        String batchInsertSql = "WITH batch AS ("
                + "   DELETE FROM " + stagingTable
                + "   WHERE ctid IN ("
                + "     SELECT ctid FROM " + stagingTable
                + (filtered ? "     WHERE correlation_id = ?" : "")
                + "     LIMIT " + BULK_INSERT_BATCH_SIZE
                + "   )"
                + "   RETURNING data"
                + " )"
                + " INSERT INTO " + targetTableSqlId + " (" + columnList + ")"
                + " SELECT " + columnList
                + " FROM batch , jsonb_populate_record( NULL::" + targetTableSqlId + " , data )"
                + " ON CONFLICT ON CONSTRAINT \"hierarchicalKey_uniqueness\""
                + " DO UPDATE SET"
                + "   updateDate     = current_timestamp,"
                + "   hierarchicalKey = EXCLUDED.hierarchicalKey,"
                + "   naturalKey     = EXCLUDED.naturalKey,"
                + "   refsLinkedTo   = EXCLUDED.refsLinkedTo,"
                + "   refValues      = EXCLUDED.refValues,"
                + "   binaryFile     = EXCLUDED.binaryFile,"
                + "   \"authorization\" = EXCLUDED.\"authorization\"";

        // CR-3 audit cascade-integration : la boucle UPSERT etait protegee
        // uniquement par "affected <= 0" , ce qui ouvrait une porte sur :
        //   - boucle infinie si DELETE n'a pas effectivement vide le staging
        //     ( bug trigger , race MVCC tres rare , ou concurrent INSERT
        //       sur SHARED_UNLOGGED ) ,
        //   - hang infini si la query DB se bloque ( lock long , deadlock ) .
        // Protection multi-couches :
        //   ( 1 ) garde de progression monotone : on verifie que la staging
        //         table retrecit reellement entre 2 iterations . Si ce n'est
        //         pas le cas alors qu'on a affecte > 0 rows -> throw direct ,
        //         pas besoin d'attendre un timeout cumule .
        //   ( 2 ) per-batch query timeout ( JVM property , default 1h ) ,
        //         couvre le cas DB hang sans introduire de cap arbitraire
        //         sur la duree totale du finalize ( un import 50M lignes
        //         pourra continuer aussi longtemps que necessaire ) .
        // Cancel admin-side : un appel WorkflowEventBus.cancel(corrId)
        // ne touche pas directement cette boucle ( elle ne lit pas le flag ) ,
        // mais le timeout postgres OU l'admin qui kill la connection
        // cote DB declenchera une SQLException ici , propagee en
        // SinkException + rollback automatique de la transaction sticky .
        long stagingPrev = countStagingRows(connection, stagingTable, correlationId, filtered);
        try (PreparedStatement ps = connection.prepareStatement(batchInsertSql)) {
            if (UPSERT_BATCH_TIMEOUT_SECONDS > 0) {
                ps.setQueryTimeout(UPSERT_BATCH_TIMEOUT_SECONDS);
            }
            int batchNum = 0;
            long totalAffected = 0L;
            while (stagingPrev > 0) {
                batchNum++;
                if (filtered) ps.setObject(1, UUID.fromString(correlationId));
                int affected = ps.executeUpdate();
                totalAffected += affected;

                long stagingNow = countStagingRows(connection, stagingTable, correlationId, filtered);
                if (log.isDebugEnabled()) {
                    log.debug("StagingFinalize batch #{} : affected={} , staging {} -> {} ( total upserted {} )",
                            batchNum, affected, stagingPrev, stagingNow, totalAffected);
                }
                if (affected <= 0 && stagingNow >= stagingPrev) {
                    // Cas double : INSERT n'a rien fait ET staging n'a pas
                    // diminue . Soit staging vide ( normal exit ) , soit
                    // pathologique . Le while ( stagingPrev > 0 ) tranche :
                    // on n'entre pas si staging deja vide en debut .
                    break;
                }
                if (stagingNow >= stagingPrev) {
                    throw new IllegalStateException(
                            "StagingFinalize : staging table did not shrink "
                                    + "( before=" + stagingPrev + " , after=" + stagingNow
                                    + " , affected=" + affected + " , batch #" + batchNum
                                    + " ) - aborting potential infinite-loop pattern");
                }
                stagingPrev = stagingNow;
            }
            log.info("StagingFinalize : completed in {} batches , {} rows upserted into target",
                    batchNum, totalAffected);
        }
    }

    /**
     * Compte les rows restantes dans la staging table , filtrees sur
     * correlation_id si on est en mode SHARED_UNLOGGED ( {@code filtered = true} ) .
     *
     * <p>Utilise comme garde de progression monotone par
     * {@link #runFinalize} : si le compteur ne diminue pas entre deux
     * batches alors qu'on a insere des rows , on est dans une boucle
     * pathologique => abort .
     */
    private static long countStagingRows(Connection conn, String stagingTable,
                                         String correlationId, boolean filtered) throws SQLException {
        String sql = "SELECT COUNT(*) FROM " + stagingTable
                + (filtered ? " WHERE correlation_id = ?" : "");
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (filtered) ps.setObject(1, UUID.fromString(correlationId));
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }
}
