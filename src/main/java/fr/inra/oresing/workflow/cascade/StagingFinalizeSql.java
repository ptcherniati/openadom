package fr.inra.oresing.workflow.cascade;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
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

    /** Functional interface used internally to execute a single SQL batch,
     *  covering both {@link PreparedStatement} (filtered) and
     *  {@link java.sql.Statement} (non-filtered) paths without duplicating the loop. */
    @FunctionalInterface
    private interface SqlBatchExecutor {
        int execute() throws SQLException;
    }

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
     * @param statementTimeoutMinutes Postgres {@code statement_timeout} ( minutes ) applique en debut de
     *                                finalize via {@code SET LOCAL} . Garde-fou contre les UPSERTs bloques
     *                                infiniment ( deadlock pur , lock advisory ) que le heartbeat ne detecte
     *                                pas . 0 = pas de timeout ( deconseille en prod ) .
     * @throws SQLException on any SQL failure ( caller must rollback )
     */
    /**
     * Surcharge historique sans publication de progress . Conserve la
     * compat avec les appelants qui ne souhaitent pas instrumenter la
     * boucle UPSERT ( tests , appels directs ) .
     */
    public static void runFinalize(
            Connection connection,
            String schemaName,
            String targetTableSqlId,
            String[] targetColumns,
            String stagingTable,
            String correlationId,
            String idJsonPath,
            int    statementTimeoutMinutes
    ) throws SQLException {
        runFinalize(connection, schemaName, targetTableSqlId, targetColumns,
                stagingTable, correlationId, idJsonPath, statementTimeoutMinutes,
                n -> { });
    }

    /**
     * Variante instrumentee : invoque {@code onBatchUpserted} apres chaque
     * batch UPSERT TEMP -> table finale avec le rowcount affected . Permet
     * a {@code CascadeSinkFactory.directCopy} de propager le progres au
     * {@code WorkflowActiveRegistry.addFinalRows} pour que la live view
     * affiche une progress bar determinate ( au lieu d'indeterminate
     * trompeuse alors que le rowcount est calculable ) .
     *
     * @param onBatchUpserted callback synchrone invoque dans la transaction
     *                        avec le rowcount du batch UPSERT . Ne doit pas
     *                        throw ( l'implementation enveloppe en
     *                        try/catch best-effort pour ne pas casser le
     *                        UPSERT en cours ) .
     */
    public static void runFinalize(
            Connection connection,
            String schemaName,
            String targetTableSqlId,
            String[] targetColumns,
            String stagingTable,
            String correlationId,
            String idJsonPath,
            int    statementTimeoutMinutes,
            java.util.function.LongConsumer onBatchUpserted
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
        log.info("StagingFinalize start : thread={} , correlationId={} , autoCommit={} , inSpringTx={} , statement_timeout={}min",
                Thread.currentThread().getName(),
                correlationId == null ? "(none)" : correlationId.substring(0, Math.min(8, correlationId.length())),
                connection.getAutoCommit(),
                inSpringTx,
                statementTimeoutMinutes);

        // Garde-fou Postgres : SET LOCAL statement_timeout limite le temps
        // d'execution de chaque statement de cette transaction . Couvre le
        // scenario "UPSERT bloque infiniment" ( deadlock pur , lock
        // advisory non release ) que le heartbeat ne detecte pas
        // ( cf javadoc Point 4 : heartbeat = liveness probe pas success
        // probe ) . LOCAL = portee transaction , reset auto a la fin .
        // Ne s'applique pas si statementTimeoutMinutes = 0 ( opt-out ) .
        if (statementTimeoutMinutes > 0) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SET LOCAL statement_timeout = '" + statementTimeoutMinutes + "min'")) {
                ps.execute();
            }
        }

        String columnList = Arrays.stream(targetColumns)
                .map(String::toLowerCase)
                .collect(Collectors.joining(","));

        boolean filtered = (correlationId != null && !correlationId.isBlank());
        String filterSql = filtered ? " AND correlation_id = ? " : "";

        // Reconstruction reference_reference - 4 etapes ordonnees :
        //
        //   1. Snapshot ( id , referencesby ) du jsonb refslinkedto dans
        //      une temp table dediee . Doit se faire AVANT le bulk INSERT
        //      car celui-ci consomme la staging table via DELETE RETURNING
        //      ( CTE batchee ) .
        //   2. DELETE des liens reference_reference existants pour les ids
        //      importes ( re-import = reconstruction propre ) .
        //   3. Bulk UPSERT vers la table cible referencevalue ( consomme la
        //      staging table ) .
        //   4. INSERT reference_reference depuis le snapshot .
        //
        // Ordre crucial : la FK reference_reference_referenceid_fkey pointe
        // sur referencevalue(id) NON deferred . Le INSERT reference_reference
        // DOIT se faire APRES le bulk UPSERT sinon la FK echoue au tout
        // premier depot d'un referentiel ( les UUIDs n'existent pas encore
        // dans referencevalue ) . Cf bug identique fixe dans
        // DataRepository.storeAll ( chemin MERGE_FILE ) .
        try (Statement stmt = connection.createStatement()) {
            stmt.execute(
                    "CREATE TEMP TABLE refref_pending ("
                            + "  referenceid  uuid,"
                            + "  referencesby uuid"
                            + ") ON COMMIT DROP");
        }

        String snapshotRefRefSql = "INSERT INTO refref_pending(referenceid, referencesby)"
                + " SELECT DISTINCT (s.data->>'" + idJsonPath + "')::uuid AS referenceid,"
                + "                 referencesby::uuid                  AS referencesby"
                + " FROM " + stagingTable + " s, JSON_TABLE("
                + "     s.data, '$.refslinkedto.*.*.*.uuids' COLUMNS ("
                + "         NESTED PATH '$[*]' COLUMNS(referencesby TEXT PATH '$')"
                + "     )"
                + " ) as joins"
                + (filtered ? " WHERE s.correlation_id = ?" : "");
        if (filtered) {
            try (PreparedStatement ps = connection.prepareStatement(snapshotRefRefSql)) {
                ps.setObject(1, UUID.fromString(correlationId));
                ps.executeUpdate();
            }
        } else {
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(snapshotRefRefSql);
            }
        }

        String deleteRefRefSql = "DELETE FROM " + schemaName + ".reference_reference"
                + " WHERE referenceid IN ( SELECT referenceid FROM refref_pending )";
        try (PreparedStatement ps = connection.prepareStatement(deleteRefRefSql)) {
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
        // Prepare the batch executor : use PreparedStatement with ? parameter when
        // filtered ( SHARED_UNLOGGED , needs correlation_id filter ) , plain Statement
        // otherwise ( PER_CONNECTION_TEMP , no parameter needed ) .
        // This avoids the Sonar S4174 "PreparedStatement has no parameters" warning
        // while keeping the loop body identical between both paths .
        final Statement batchStmt;
        final SqlBatchExecutor batchExecutor;
        if (filtered) {
            PreparedStatement ps = connection.prepareStatement(batchInsertSql);
            if (UPSERT_BATCH_TIMEOUT_SECONDS > 0) {
                ps.setQueryTimeout(UPSERT_BATCH_TIMEOUT_SECONDS);
            }
            UUID corrUuid = UUID.fromString(correlationId);
            batchExecutor = () -> { ps.setObject(1, corrUuid); return ps.executeUpdate(); };
            batchStmt = ps;
        } else {
            Statement stmt = connection.createStatement();
            if (UPSERT_BATCH_TIMEOUT_SECONDS > 0) {
                stmt.setQueryTimeout(UPSERT_BATCH_TIMEOUT_SECONDS);
            }
            batchExecutor = () -> stmt.executeUpdate(batchInsertSql);
            batchStmt = stmt;
        }
        try (Statement ignored = batchStmt) {
            int batchNum = 0;
            long totalAffected = 0L;
            while (stagingPrev > 0) {
                batchNum++;
                int affected = batchExecutor.execute();
                totalAffected += affected;
                if (affected > 0) {
                    try {
                        onBatchUpserted.accept((long) affected);
                    } catch (RuntimeException ignored) {
                        /* best effort : un consommateur fautif ne doit pas
                           casser le UPSERT en cours */
                    }
                }

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

        // 4) reference_reference est maintenant valide a inserer car les
        // UUIDs existent dans referencevalue ( bulk UPSERT vient de finir ) .
        String insertRefRefSql = "INSERT INTO " + schemaName + ".reference_reference(referenceid, referencesby)"
                + " SELECT referenceid, referencesby FROM refref_pending";
        try (PreparedStatement ps = connection.prepareStatement(insertRefRefSql)) {
            int refrefInserted = ps.executeUpdate();
            log.info("StagingFinalize : reference_reference rebuilt with {} link(s)", refrefInserted);
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
        if (filtered) {
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, UUID.fromString(correlationId));
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? rs.getLong(1) : 0L;
                }
            }
        } else {
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(sql)) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }
}