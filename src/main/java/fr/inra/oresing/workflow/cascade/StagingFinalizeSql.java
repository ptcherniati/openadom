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

    /**
     * Robustness layers 1 + 2 configuration suppliers . Injected by
     * {@link BackendPidRegistryBridge} at Spring boot from
     * {@link fr.inra.oresing.workflow.cascade.config.ImportProperties} so that
     * values can be edited live via oa-live admin without redeploy .
     *
     * <p>Defaults restent conservateurs si non wires ( contexte hors Spring ,
     * tests unitaires ) : valeurs alignees sur les defaults d'ImportProperties .
     */
    private static volatile java.util.function.IntSupplier lockTimeoutMinutesSupplier   = () -> 30;
    private static volatile java.util.function.IntSupplier lockRetryMaxAttemptsSupplier = () -> 3;
    private static volatile java.util.function.LongSupplier lockRetryBackoffInitialMsSupplier = () -> 1000L;
    private static volatile java.util.function.LongSupplier lockRetryBackoffMaxMsSupplier     = () -> 60_000L;

    /** Setter Spring bridge - injecte au boot Spring depuis ImportProperties . */
    public static void setLockTimeoutMinutesSupplier(java.util.function.IntSupplier s) {
        lockTimeoutMinutesSupplier = s != null ? s : () -> 30;
    }

    /** Setter Spring bridge - injecte au boot Spring depuis ImportProperties . */
    public static void setLockRetryMaxAttemptsSupplier(java.util.function.IntSupplier s) {
        lockRetryMaxAttemptsSupplier = s != null ? s : () -> 3;
    }

    /** Setter Spring bridge - injecte au boot Spring depuis ImportProperties . */
    public static void setLockRetryBackoffInitialMsSupplier(java.util.function.LongSupplier s) {
        lockRetryBackoffInitialMsSupplier = s != null ? s : () -> 1000L;
    }

    /** Setter Spring bridge - injecte au boot Spring depuis ImportProperties . */
    public static void setLockRetryBackoffMaxMsSupplier(java.util.function.LongSupplier s) {
        lockRetryBackoffMaxMsSupplier = s != null ? s : () -> 60_000L;
    }

    /**
     * SQLSTATES considerees retriables ( contention transitoire ) :
     * <ul>
     *   <li>{@code 55P03} : {@code lock_not_available} ( lock_timeout fired ) ;</li>
     *   <li>{@code 40P01} : {@code deadlock_detected} ( PG resolved by killing one tx ) ;</li>
     *   <li>{@code 40001} : {@code serialization_failure} ( SSI conflict ) .</li>
     * </ul>
     *
     * <p>Toutes ces erreurs sont logiquement transitoires : un retry apres
     * backoff a une probabilite forte de succeder . Les autres SQLSTATES
     * ( constraint violations , type errors , etc . ) sont structurelles
     * et propagees immediatement sans retry .
     */
    private static final java.util.Set<String> RETRIABLE_SQLSTATES =
            java.util.Set.of("55P03", "40P01", "40001");

    private StagingFinalizeSql() { }

    /**
     * Reference statique vers le registry de pid PG , injectee par
     * {@link BackendPidRegistryBridge} au demarrage Spring . Optional : si
     * non setee ( contexte hors Spring , tests unitaires ) , le register
     * silencieux n'a aucun effet et l'annulation cancel-via-pid est inactive .
     */
    private static volatile BackendPidRegistry backendPidRegistry;

    /** Setter usage internal : invoked by Spring bridge bean at boot . */
    public static void setBackendPidRegistry(BackendPidRegistry registry) {
        backendPidRegistry = registry;
    }

    /**
     * P4b - Supplier dynamique vers {@code ImportProperties.isUseColumnExtractionUpsert} .
     * Injecte au boot Spring via {@link BackendPidRegistryBridge#wire} ; lit la valeur
     * courante a chaque appel a {@link #runFinalize} pour supporter l'edition live
     * du flag via oa-live admin sans redeploy . Defaut conservateur : {@code false}
     * ( legacy jsonb_populate_record ) tant que non injecte ou hors Spring ( tests ) .
     */
    private static volatile java.util.function.BooleanSupplier useColumnExtractionUpsertSupplier =
            () -> false;

    /** Setter Spring bridge - injecte au boot Spring . */
    public static void setUseColumnExtractionUpsertSupplier(java.util.function.BooleanSupplier supplier) {
        useColumnExtractionUpsertSupplier = supplier != null ? supplier : () -> false;
    }

    /**
     * Recupere le {@code pg_backend_pid()} de la connection courante et
     * l'enregistre dans le registry pour permettre une annulation reelle
     * du statement en cours via {@code pg_cancel_backend(pid)} depuis une
     * connection separee . No-op si registry non injecte ou correlationId
     * invalide .
     *
     * @return le pid si register reussi , 0 sinon ( pour deregistration safe )
     */
    /** Expose en public pour appelants externes ( FAST executors , doUnpublish ) . */
    public static int registerCurrentBackendPid(java.sql.Connection connection, String correlationId) {
        return tryRegisterBackendPid(connection, correlationId);
    }

    /** Expose en public . */
    public static void deregisterBackendPid(String correlationId) {
        tryDeregisterBackendPid(correlationId);
    }

    private static int tryRegisterBackendPid(java.sql.Connection connection, String correlationId) {
        if (backendPidRegistry == null || correlationId == null || correlationId.isBlank()) return 0;
        try (PreparedStatement ps = connection.prepareStatement("SELECT pg_backend_pid()");
             java.sql.ResultSet rs = ps.executeQuery()) {
            if (rs.next()) {
                int pid = rs.getInt(1);
                // Fix critique : register peut throw CancelledBeforeRegistrationException
                // si l'utilisateur a cancel AVANT que ce thread n'arrive ici . On laisse
                // propager pour que le caller ( finalize hook , FAST executor ) abort le
                // commit terminale et rollback la tx ( rule "jamais commit tant que tout
                // n'a pas ete copie" ) .
                backendPidRegistry.register(UUID.fromString(correlationId), pid);
                return pid;
            }
        } catch (BackendPidRegistry.CancelledBeforeRegistrationException cancelled) {
            // Propager : caller catch en RuntimeException -> rollback tx .
            throw cancelled;
        } catch (java.sql.SQLException | RuntimeException ex) {
            log.warn("tryRegisterBackendPid failed for {} : {}", correlationId, ex.getMessage());
        }
        return 0;
    }

    /** Counterpart de {@link #tryRegisterBackendPid} , safe en finally . */
    private static void tryDeregisterBackendPid(String correlationId) {
        if (backendPidRegistry == null || correlationId == null || correlationId.isBlank()) return;
        try {
            backendPidRegistry.deregister(UUID.fromString(correlationId));
        } catch (RuntimeException ignore) { /* best-effort */ }
    }

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

        // CRITIQUE : enregistre {@code pg_backend_pid()} AU TOUT DEBUT de la
        // methode , AVANT toute operation SQL . Raison :
        //   - si l'utilisateur a cancel APRES le start de cascade mais AVANT
        //     ce point ( pendant transform/sink phase ) , le signal a deja ete
        //     enregistre dans BackendPidRegistry.preCancelledCids ;
        //   - le register ici detecte le pre-cancel et leve
        //     {@link BackendPidRegistry.CancelledBeforeRegistrationException}
        //     -> caller catch -> finalize abort SANS aucun UPSERT terminale .
        // Garantie : rule "ne jamais commit la table terminale tant que tout
        // n'a pas ete copie ET le cancel n'a pas ete observe" .
        tryRegisterBackendPid(connection, correlationId);
        try {

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

        // Robustness layer 1 : override le cluster lock_timeout ( 30s default
        // typique ) pour donner suffisamment de marge aux UPSERT batches lorsqu'un
        // finalize concurrent ( different fileId , meme datatype ) detient des
        // btree pages partagees de l'index hierarchicalKey_uniqueness ou la row
        // partagee de referencevalue_count_stats via le trigger statement-level .
        // Sans cet override , 2 publishes parallelises ( cf Phase2Handler narrow
        // lock refactor ) hittent systematiquement SQLSTATE 55P03 . LOCAL =
        // portee transaction , reset auto a la fin .
        final int lockTimeoutMinutes = lockTimeoutMinutesSupplier.getAsInt();
        if (lockTimeoutMinutes > 0) {
            try (PreparedStatement ps = connection.prepareStatement(
                    "SET LOCAL lock_timeout = '" + lockTimeoutMinutes + "min'")) {
                ps.execute();
            }
        }

        // P1a : SET LOCAL synchronous_commit = local . Pendant le finalize ,
        // un crash kernel/disk perdrait au pire l'import en cours ( WAL replay
        // ramene la base a un etat coherent , les rows non flushees disparaissent
        // simplement = re-import par le user ) . Vs synchronous_commit=on qui
        // attend fsync apres CHAQUE COMMIT batch -> bottleneck I/O fort sur
        // gros datasets ( 1M+ rows = 20+ commits batch ) . LOCAL fait le COMMIT
        // visible immediatement aux autres backends + WAL ecrit en async .
        // Trade-off documente : durabilite immediate sacrifiee pour gain
        // throughput 5-8% sur finalize . Acceptable car les imports ont une
        // source de verite externe ( fichier CSV ) , re-import idempotent .
        // Phase D F-1 : on n'emet PLUS de SET LOCAL synchronous_commit .
        // Le cluster est configure avec synchronous_commit = off ( cf
        // local_deployment/infra/postgres/conf/openadom.conf ) qui est
        // STRICTEMENT plus permissif que 'local' ( ne wait meme pas le
        // flush WAL local ) . L'ancien SET LOCAL 'local' etait donc
        // legerement plus restrictif que le default cluster - on perdait
        // 1-3% de throughput sans raison . Iso-resultat ( durabilite
        // identique = "best effort post commit" deja en place ) .

        // Phase B L1 : ANALYZE staging table juste avant la boucle UPSERT .
        // Les UNLOGGED tables ne sont pas analysees aussi agressivement par
        // autovacuum ; apres un bulk COPY le planner peut voir reltuples=0
        // et choisir des plans degenres ( seq scan au lieu de parallel ,
        // mauvaise estimation pour la CTE snapshot avec JSON_TABLE ) .
        // Un ANALYZE explicit cote 1-2 sec sur 1M rows et debloque tous
        // les plans en aval . Iso-resultat ( stats only ) .
        try (Statement stmt = connection.createStatement()) {
            stmt.execute("ANALYZE " + stagingTable);
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
        try (PreparedStatement ps = connection.prepareStatement(snapshotRefRefSql)) {
            if (filtered) ps.setObject(1, UUID.fromString(correlationId));
            ps.executeUpdate();
        }

        String deleteRefRefSql = "DELETE FROM " + schemaName + ".reference_reference"
                + " WHERE referenceid IN ( SELECT referenceid FROM refref_pending )";
        try (PreparedStatement ps = connection.prepareStatement(deleteRefRefSql)) {
            ps.executeUpdate();
        }

        // 3) Batched UPSERT into target table : DELETE batch from staging RETURNING data ,
        //    INSERT INTO target SELECT cols FROM batch ON CONFLICT DO UPDATE .
        //
        // P4b feature flag : useColumnExtractionUpsert ( default false ) :
        //   - false ( legacy ) : utilise jsonb_populate_record(NULL::target, data)
        //     dans le SELECT . Postgres auto-type via syscache lookup ; simple
        //     mais 20-30% du temps finalize sur gros datasets .
        //   - true  ( P4b ) : extraction colonne par colonne (data->>'col')::pg_type .
        //     Types resolus une fois via information_schema , cache statique .
        //     Iso-resultats garantis par mapping udt_name -> cast PG correct .
        //
        // Le builder centralise les deux strategies + permet rollback trivial via flag .
        // Cf StagingUpsertSqlBuilder javadoc .
        final boolean useColumnExtraction = useColumnExtractionUpsertSupplier.getAsBoolean();
        String batchInsertSql = null;
        if (useColumnExtraction) {
            // Resolution lazy des types PG ( cache statique , 1 query par schema.table ) .
            // En cas d'echec ( colonne sans type mappe ) , fallback automatique sur le legacy
            // pour garantir aucune regression bloquante .
            String[] parts = targetTableSqlId.split("\\.");
            String schema = parts.length == 2 ? parts[0] : "public";
            String table  = parts.length == 2 ? parts[1] : targetTableSqlId;
            try {
                java.util.Map<String, String> pgTypes =
                        StagingUpsertSqlBuilder.fetchColumnPgTypes(connection, schema, table);
                batchInsertSql = StagingUpsertSqlBuilder.buildColumnExtraction(
                        stagingTable, targetTableSqlId, targetColumns, pgTypes, filtered);
                log.debug("StagingFinalize : using P4b column extraction strategy for {}", targetTableSqlId);
            } catch (RuntimeException | SQLException ex) {
                log.warn("StagingFinalize : P4b column extraction failed ( {} ) , fallback legacy jsonb_populate_record",
                        ex.getMessage());
                // batchInsertSql restera null -> rebascule sur legacy ci-dessous .
            }
        }
        if (batchInsertSql == null) {
            batchInsertSql = StagingUpsertSqlBuilder.buildJsonbPopulateRecord(
                    stagingTable, targetTableSqlId, columnList, filtered);
        }

        // Phase A L2 : boucle UPSERT pilotee par {@code affected} - on
        // ELIMINE les 2 {@code SELECT COUNT(*)} par iteration ( garde pre +
        // post ) qui coutaient autant que l'UPSERT lui-meme sur 1M+ rows
        // ( N+1 full filtered scans de la staging UNLOGGED par finalize ) .
        //
        // Conditions de terminaison ( logique equivalente , iso-resultat ) :
        //   ( a ) {@code affected == 0} : le {@code DELETE ... RETURNING}
        //       n'a pas trouve de row a deleter -> staging vide pour ce
        //       correlation_id -> exit normal .
        //   ( b ) {@code affected < BULK_INSERT_BATCH_SIZE} : derniere batch
        //       partielle - aucune row ne reste -> exit normal .
        //
        // Protection contre boucle infinie ( robustesse , pas semantique ) :
        //   - max iterations = {@code 100k} = couvre 5 milliards de rows a
        //     50k batchSize . Au-dela = bug certain , throw .
        //   - per-batch query timeout ( JVM property , default 1h ) - couvre
        //     le cas DB hang sans cap arbitraire sur duree totale du finalize .
        //
        // Cancel admin-side : timeout postgres OU connection kill cote DB
        // declencheront une SQLException ici , propagee en SinkException +
        // rollback automatique de la transaction sticky .
        try (PreparedStatement ps = connection.prepareStatement(batchInsertSql)) {
            if (UPSERT_BATCH_TIMEOUT_SECONDS > 0) {
                ps.setQueryTimeout(UPSERT_BATCH_TIMEOUT_SECONDS);
            }
            final int batchSize = BULK_INSERT_BATCH_SIZE;
            final int maxBatches = 100_000;
            int batchNum = 0;
            long totalAffected = 0L;
            while (batchNum < maxBatches) {
                batchNum++;
                if (filtered) ps.setObject(1, UUID.fromString(correlationId));
                // Robustness layer 2 : execute le batch avec retry sur
                // SQLSTATE 55P03 ( lock_timeout ) . SAVEPOINT autour de
                // l'executeUpdate pour pouvoir rollback un batch echoue
                // sans tuer toute la transaction sticky ; backoff
                // exponentiel entre retries ; couche 5 ( log enriched )
                // injectee directement dans le message SQLException si
                // tous les retries epuises .
                int affected = executeBatchWithLockRetry(connection, ps, batchNum, totalAffected,
                        correlationId, targetTableSqlId);
                totalAffected += affected;
                if (affected > 0) {
                    try {
                        onBatchUpserted.accept((long) affected);
                    } catch (RuntimeException ignored) {
                        /* best effort : un consommateur fautif ne doit pas
                           casser le UPSERT en cours */
                    }
                }
                if (log.isDebugEnabled()) {
                    log.debug("StagingFinalize batch #{} : affected={} ( total upserted {} )",
                            batchNum, affected, totalAffected);
                }
                if (affected == 0) {
                    // Cas ( a ) : staging epuise pour ce correlation_id .
                    break;
                }
                if (affected < batchSize) {
                    // Cas ( b ) : derniere batch partielle , staging epuise
                    // ( DELETE LIMIT n'a pu prendre que les rows restantes ) .
                    break;
                }
            }
            if (batchNum >= maxBatches) {
                throw new IllegalStateException(
                        "StagingFinalize : exceeded " + maxBatches + " batches"
                                + " ( total upserted=" + totalAffected
                                + " ) - aborting potential infinite-loop pattern");
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
        } finally {
            // Liberation du registry pid : evite la fuite memoire long-terme
            // + evite un cancel ulterieur ciblant ce pid alors qu'il aurait
            // ete reutilise pour un autre workflow ( Hikari connection reuse ) .
            tryDeregisterBackendPid(correlationId);
        }
    }

    /**
     * Robustness layer 2 + 5 : execute un batch UPSERT avec retry automatique
     * sur SQLSTATE {@code 55P03} ( {@code canceling statement due to lock timeout} )
     * et erreur enrichie en cas d'echec definitif .
     *
     * <h2>Mecanisme retry</h2>
     *
     * <p>SAVEPOINT pose autour du {@code executeUpdate} : si l'execution
     * echoue avec 55P03 ( contention btree pages ou trigger lock_timeout sur
     * referencevalue_count_stats ) , on rollback au SAVEPOINT et on retry
     * apres backoff exponentiel ( 1s , 2s , 4s ) . Tx parent reste vivante .
     *
     * <p>Sequence retry : tentative initiale + N retries = N+1 tentatives max .
     * Defaut N=3 : 4 tentatives totales , 7s backoff cumule max .
     *
     * <h2>Errors enrichies ( layer 5 )</h2>
     *
     * <p>Si tous les retries epuises OU si SQLSTATE != 55P03 : on re-throw
     * une SQLException avec message enrichi contenant :
     * <ul>
     *   <li>SQLSTATE original ( facilite filtrage logs / alerting ) ;</li>
     *   <li>batch number + total upserted at failure point ;</li>
     *   <li>correlationId ( liens metrics / Grafana ) ;</li>
     *   <li>target table ( contention par datatype ) ;</li>
     *   <li>nombre de retries effectues .</li>
     * </ul>
     *
     * <p>Le message enrichi remonte tel quel dans {@code workflow_log.fatal_error}
     * et est affiche dans le bloc ERREUR FATALE de oa-live workflow detail .
     *
     * @param connection          tx parent ( autoCommit=false , owned by caller )
     * @param ps                  PreparedStatement deja prepare avec params bind
     * @param batchNum            numero du batch courant ( logging )
     * @param totalUpsertedSoFar  rows deja upsertees ( logging , ne contient
     *                            PAS la batch courante en cas d'echec )
     * @param correlationId       workflow correlation id ( logging )
     * @param targetTableSqlId    target table fqdn ( logging )
     * @return rowcount du UPSERT executeUpdate ( 0 si staging epuise )
     * @throws SQLException si tous les retries epuises ou erreur non-retriable
     */
    private static int executeBatchWithLockRetry(
            Connection connection,
            PreparedStatement ps,
            int batchNum,
            long totalUpsertedSoFar,
            String correlationId,
            String targetTableSqlId
    ) throws SQLException {
        // Snapshot une fois la config par appel : la valeur ne change pas
        // pendant l'execution d'un batch ( on relit live entre batches ) .
        final int  maxAttempts        = Math.max(1, 1 + lockRetryMaxAttemptsSupplier.getAsInt());
        final long backoffInitialMs   = Math.max(0L, lockRetryBackoffInitialMsSupplier.getAsLong());
        final long backoffMaxMs       = Math.max(backoffInitialMs, lockRetryBackoffMaxMsSupplier.getAsLong());
        SQLException lastFailure = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            java.sql.Savepoint sp = null;
            try {
                sp = connection.setSavepoint("batch_" + batchNum + "_attempt_" + attempt);
            } catch (SQLException spEx) {
                // SAVEPOINT non supporte ( ne devrait pas arriver sur PG ) :
                // log + fallback execution sans retry .
                log.warn("StagingFinalize : SAVEPOINT not supported ({}), retry disabled for this batch",
                        spEx.getMessage());
                return ps.executeUpdate();
            }
            try {
                int affected = ps.executeUpdate();
                connection.releaseSavepoint(sp);
                if (attempt > 1) {
                    log.info("StagingFinalize batch #{} : recovered after {} retry attempt(s) ( affected={} )",
                            batchNum, attempt - 1, affected);
                }
                return affected;
            } catch (SQLException ex) {
                lastFailure = ex;
                // Toujours rollback au SAVEPOINT pour reset l'etat tx avant
                // retry OU re-throw ( garde la tx parent vivante ) .
                try {
                    connection.rollback(sp);
                } catch (SQLException rbEx) {
                    log.warn("StagingFinalize batch #{} : SAVEPOINT rollback failed ({}), original cause was {}",
                            batchNum, rbEx.getMessage(), ex.getMessage());
                    // Si rollback impossible , la tx parent est probablement
                    // aborted - inutile de retry , re-throw l'original enrichi .
                    throw enrichLockTimeoutError(ex, batchNum, attempt - 1,
                            totalUpsertedSoFar, correlationId, targetTableSqlId);
                }
                boolean retriable = RETRIABLE_SQLSTATES.contains(ex.getSQLState());
                if (!retriable || attempt >= maxAttempts) {
                    // Layer 5 : enrichir le message avec contexte diagnostic
                    // avant de propager . Ce message sera vu dans
                    // workflow_log.fatal_error + oa-live bloc ERREUR FATALE .
                    throw enrichLockTimeoutError(ex, batchNum, attempt - 1,
                            totalUpsertedSoFar, correlationId, targetTableSqlId);
                }
                // Backoff exponentiel cape par backoffMaxMs : 1s , 2s , 4s ... cap .
                long uncappedBackoff = backoffInitialMs * (1L << Math.min(attempt - 1, 30));
                long backoffMs = Math.min(uncappedBackoff, backoffMaxMs);
                log.warn("StagingFinalize batch #{} : SQLSTATE {} retriable , attempt {}/{} , backoff {} ms",
                        batchNum, ex.getSQLState(), attempt, maxAttempts, backoffMs);
                try {
                    if (backoffMs > 0) Thread.sleep(backoffMs);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw enrichLockTimeoutError(ex, batchNum, attempt - 1,
                            totalUpsertedSoFar, correlationId, targetTableSqlId);
                }
                // Continue loop -> next attempt re-uses same PS ( params toujours bind ) .
            }
        }
        // Inatteignable : la boucle exit via return ou throw . Sentinel safety .
        throw enrichLockTimeoutError(lastFailure, batchNum, maxAttempts - 1,
                totalUpsertedSoFar, correlationId, targetTableSqlId);
    }

    /**
     * Layer 5 : enrichit un {@link SQLException} batch UPSERT avec le contexte
     * diagnostic utile pour debug ( SQLSTATE original , batch , correlationId ,
     * target , retries effectues ) . Le message resultant est rendu visible
     * via {@code workflow_log.fatal_error} et le bloc ERREUR FATALE de
     * oa-live workflow detail .
     */
    private static SQLException enrichLockTimeoutError(
            SQLException original,
            int batchNum,
            int retriesAttempted,
            long totalUpsertedSoFar,
            String correlationId,
            String targetTableSqlId
    ) {
        String shortCid = (correlationId == null || correlationId.isBlank())
                ? "(none)"
                : correlationId.substring(0, Math.min(8, correlationId.length()));
        String enriched = String.format(
                "[SQLSTATE %s] StagingFinalize UPSERT failed at batch #%d "
                        + "( target=%s , upsertedSoFar=%d , correlationId=%s , retries=%d ) : %s",
                original.getSQLState(),
                batchNum,
                targetTableSqlId,
                totalUpsertedSoFar,
                shortCid,
                retriesAttempted,
                original.getMessage() == null ? "(no message)" : original.getMessage());
        SQLException wrapped = new SQLException(enriched, original.getSQLState(), original.getErrorCode(), original);
        // Preserve toute la chaine de causes potentielle ( PSQLException.getNextException ) .
        SQLException next = original.getNextException();
        if (next != null) wrapped.setNextException(next);
        return wrapped;
    }

}
