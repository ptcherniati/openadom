package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inra.oresing.workflow.cascade.history.HeartbeatService;
import fr.inra.oresing.workflow.cascade.staging.StagingMode;
import fr.inrae.ore.cascade.api.builder.SinkBuilder;
import fr.inrae.ore.cascade.api.defaults.db.RowSerializer;
import fr.inrae.ore.cascade.api.defaults.db.staging.FinalizeHook;
import fr.inrae.ore.cascade.api.defaults.db.staging.FinalizeMode;
import fr.inrae.ore.cascade.api.defaults.db.staging.StagingPostgresSink;
import fr.inrae.ore.cascade.api.defaults.db.staging.StagingTableSpec;
import fr.inrae.ore.cascade.core.defaults.db.WriteMode;
import fr.inrae.ore.cascade.model.core.Sink;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Factory de {@link Sink}s pour le pipeline d'import openADOM .
 *
 * <p>Concentre la construction du {@link StagingPostgresSink} cascade 1.7.0
 * pour la stratégie {@code DIRECT_COPY} : choix de la
 * {@link StagingTableSpec} ( PER_CONNECTION_TEMP / SHARED_UNLOGGED ) ,
 * du {@link RowSerializer} ( pass-through fichier processé ) , et du
 * {@link FinalizeHook} ( UPSERT openADOM via {@link StagingFinalizeSql} ) .
 *
 * <p>La stratégie est choisie via les properties Spring
 * {@code cascade.import.sink-strategy} et {@code cascade.import.staging-strategy} ,
 * surchargeables via env vars {@code CASCADE_IMPORT_SINK_STRATEGY} et
 * {@code CASCADE_IMPORT_STAGING_STRATEGY} .
 *
 * @author cascade-1.7.0 integration
 */
public final class CascadeSinkFactory {

    private CascadeSinkFactory() { }

    /**
     * Construit un {@link StagingPostgresSink}{@code <Path>} qui streame
     * chaque chunk processé ( un fichier CSV par chunk ) directement vers
     * la table de staging PostgreSQL via {@code COPY FROM STDIN} , puis
     * exécute la finalize hook openADOM ( UPSERT vers les tables métier ) .
     *
     * <p>Le {@link RowSerializer} est un pass-through : il copie le contenu
     * brut du fichier processé sur le pipe COPY ( les lignes sont déjà
     * formatées en CSV par {@code DataImporterTransformation} ) .
     *
     * <p>Pour {@code SHARED_UNLOGGED} avec le pass-through fichier , la
     * table de staging doit avoir une colonne {@code correlation_id} et
     * le fichier processé doit l'inclure DÉJÀ ( ce n'est pas le cas
     * actuellement -> SHARED_UNLOGGED requiert un upgrade de
     * {@code DataImporterTransformation} pour inclure la colonne corrid ;
     * pour 1.7.0 on garde {@code PER_CONNECTION_TEMP} comme défaut ) .
     */
    /** Surcharge historique : pas de publication de progress UPSERT . */
    public static Sink<Path> directCopy(DataRepository referenceValueRepository,
                                         ImportProperties props,
                                         UUID correlationId,
                                         HeartbeatService heartbeatService) {
        return directCopy(referenceValueRepository, props, correlationId,
                heartbeatService, null, FinalizeMode.SYNCHRONOUS);
    }

    /** Surcharge cascade 2.x compat ( SYNCHRONOUS finalize inline ) . */
    public static Sink<Path> directCopy(DataRepository referenceValueRepository,
                                         ImportProperties props,
                                         UUID correlationId,
                                         HeartbeatService heartbeatService,
                                         fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry activeRegistry) {
        return directCopy(referenceValueRepository, props, correlationId,
                heartbeatService, activeRegistry, FinalizeMode.SYNCHRONOUS);
    }

    /**
     * Variante instrumentee : publie chaque batch UPSERT au registry pour
     * que la live view affiche une progress bar determinate au lieu d'une
     * indeterminate trompeuse alors que le rowcount est calculable .
     *
     * @param activeRegistry registry de progression live . Optionnel ( null = pas de
     *                       publication ) . Quand fourni , chaque batch
     *                       UPSERT TEMP -> table finale invoque
     *                       {@code activeRegistry.addFinalRows(corrId , n)} .
     */
    /**
     * Cascade 3.0.0 : finalizeMode arg permet de differer la finalize SQL
     * lourde ( UPSERT staging -> table finale ) hors du sink thread cascade .
     *
     * <ul>
     *   <li>{@link FinalizeMode#SYNCHRONOUS} : comportement historique , la
     *       finalize tourne inline dans le sink ( cascade 2.x ) .</li>
     *   <li>{@link FinalizeMode#DEFERRED_TO_CALLER} : la finalize est
     *       capturee comme {@code DeferredFinalize} dans
     *       {@link fr.inrae.ore.cascade.model.workflow.WorkflowResult#deferredFinalize()} ;
     *       le caller execute le UPSERT plus tard sur sa propre connection
     *       ( typiquement Spring tx {@code afterCommit} ) , evitant le
     *       deadlock sink-thread vs caller-thread sur les row-locks de la
     *       table finale .</li>
     * </ul>
     */
    public static Sink<Path> directCopy(DataRepository referenceValueRepository,
                                         ImportProperties props,
                                         UUID correlationId,
                                         HeartbeatService heartbeatService,
                                         fr.inra.oresing.workflow.cascade.history.WorkflowActiveRegistry activeRegistry,
                                         FinalizeMode finalizeMode) {

        DataSource raw = referenceValueRepository.getDataSource();
        if (raw == null) {
            throw new IllegalStateException("DataRepository did not expose a DataSource ; "
                    + "cannot build StagingPostgresSink");
        }
        // FK-violation fix : cascade ouvre sa propre Connection via
        // dataSource.getConnection() . Sans wrapping , cette
        // connection ne fait PAS partie de la transaction Spring
        // @Transactional courante . Resultat : les rows
        // binaryfile fraichement INSERT-ees par
        // StoreFile.loadOrCreateFile ne sont pas visibles cote
        // cascade ( pas encore commit-ees ) , et le UPSERT vers
        // referencevalue echoue avec la FK
        // referencevalue_binaryfile_fkey .
        //
        // TransactionAwareDataSourceProxy renvoie une connection
        // qui :
        //   - delegue a la connection bound a la tx Spring courante si
        //     une tx est active ,
        //   - ignore les appels commit / rollback / setAutoCommit
        //     ( Spring les gere a l'exit du @Transactional ) ,
        //   - ignore le close ( la connection retourne au pool a
        //     la fin de la tx ) .
        //
        // Net effet : cascade rejoint la tx Spring de createData /
        // VersioningService et voit les binaryfile rows fraichement
        // INSERT . Hors tx Spring ( ex. tests unitaires direct ) , la
        // proxy delegue exactement comme avant .
        DataSource ds = new org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy(raw);

        // Strategy pattern : StagingMode encapsule cascade spec , copy
        // columns , correlation_id filter , et hooks CREATE/DROP per-workflow .
        // Toutes les decisions specifiques a la strategy sont concentrees
        // dans une seule classe ( cf. {@link StagingMode} ) .
        StagingMode mode = StagingMode.of(
                props.getStagingStrategy(),
                correlationId,
                props.getStagingSharedTableName(),
                props.getStagingSharedOrphanTtlMinutes());
        StagingTableSpec spec = mode.cascadeSpec();

        // Pass-through serializer : the chunk's processed file already
        // contains one CSV jsonb-encoded line per logical row. We read
        // it line-by-line and prepend the SHARED_UNLOGGED correlationId
        // prefix on every line ( empty string for PerConnectionTemp ) ,
        // then stream onto the COPY pipe . File is deleted post-consumption .
        RowSerializer<Path> passthrough = (path, w, ctx) -> {
            String prefix = ctx.correlationIdPrefix();
            try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                String line;
                while ((line = r.readLine()) != null) {
                    if (!prefix.isEmpty()) w.write(prefix);
                    w.write(line);
                    w.write('\n');
                }
            }
            try { Files.deleteIfExists(path); } catch (java.io.IOException ignore) { /* best-effort */ }
        };

        // openADOM finalize : reuse the SQL helpers shared with the legacy
        // storeAll(Path) path so both routes execute equivalent statements.
        String schemaName     = referenceValueRepository.getSchemaName();
        String targetTableId  = referenceValueRepository.getTable().getSqlIdentifier();
        String[] orderedCols  = DataRepository.ORDERED_COLUMNS;
        String stagingTable   = spec.tableName();
        String idJsonPath     = "id";

        FinalizeHook hook = (ctx, registry) -> {
            // Filter par correlation_id : SHARED_UNLOGGED ( table partagee
            // multi-workflows ) + PER_WORKFLOW_TABLE ( tag cosmetique mais
            // garde la symetrie avec cascade SharedUnlogged spec ) .
            // PER_CONNECTION_TEMP : null ( table TEMP isolated ) .
            UUID corrId = correlationId != null ? correlationId : safeUuid(ctx.correlationId());
            String corridFilter = mode.correlationIdFilter(corrId);
            // Callback de progress UPSERT TEMP -> table finale . Quand un
            // registry est fourni , chaque batch incremente finalRows pour
            // alimenter la progress bar UPSERT de la live view .
            java.util.function.LongConsumer onBatch = (activeRegistry != null && corrId != null)
                    ? n -> activeRegistry.addFinalRows(corrId, n)
                    : n -> { };

            if (registry != null) {
                // Cascade 3.0.0 DEFERRED_TO_CALLER : on enregistre l action
                // SQL lourde au lieu de l executer inline . Le caller la
                // declenchera plus tard sur sa propre connexion ( ex.
                // afterCommit Spring tx ) , avec un heartbeat dedie pour
                // que la phase ne soit pas vue zombie .
                final UUID  finalCorrId      = corrId;
                final String finalCorrFilter = corridFilter;
                registry.register(conn -> {
                    HeartbeatService.Heartbeat hb = heartbeatService != null && finalCorrId != null
                            ? heartbeatService.start(finalCorrId)
                            : null;
                    try {
                        StagingFinalizeSql.runFinalize(
                                conn,
                                schemaName,
                                targetTableId,
                                orderedCols,
                                stagingTable,
                                finalCorrFilter,
                                idJsonPath,
                                props.getFinalizeStatementTimeoutMinutes(),
                                onBatch);
                    } finally {
                        if (hb != null) hb.close();
                    }
                });
                return;
            }

            // SYNCHRONOUS : finalize inline dans le sink ( cascade 2.x compat ) .
            // Heartbeat actif pendant la finalize ( phase potentiellement
            // longue : UPSERT 274k+ rows ) . try-with-resources : scheduler
            // stop garanti meme en cas d exception SQL ( rollback , timeout ) .
            HeartbeatService.Heartbeat hb = heartbeatService != null
                    ? heartbeatService.start(corrId)
                    : null;
            try {
                StagingFinalizeSql.runFinalize(
                        ctx.connection(),
                        schemaName,
                        targetTableId,
                        orderedCols,
                        stagingTable,
                        corridFilter,
                        idJsonPath,
                        props.getFinalizeStatementTimeoutMinutes(),
                        onBatch);
            } finally {
                if (hb != null) hb.close();
            }
        };

        // COPY column lists :
        //   PER_CONNECTION_TEMP : "data" only ( the temp table has 1 column ) .
        //   SHARED_UNLOGGED     : "correlation_id, data" ( the permanent
        //                         table tags every row with the workflow id
        //                         to isolate concurrent imports ) .
        // The pass-through serializer prepends the correlationId prefix on
        // every physical line for SHARED_UNLOGGED ( cascade 1.8.0
        // RowSerializer contract supports multi-line records ) .
        String copyCols = mode.copyColumns();

        // cascade 1.8.0 unified WriteMode hierarchy : StagingUpsert config
        // record encapsulates staging spec + finalize hook + COPY format .
        WriteMode.StagingUpsert writeMode = WriteMode.stagingUpsert()
                .stagingSpec(spec)
                .finalizeHook(hook)
                .copyColumns(copyCols)
                .delimiter('\t')
                .nullString("\\N")
                // openADOM streame du JSONB raw ( une ligne par enregistrement )
                // dans la colonne "data" ; FORMAT TEXT preserve les guillemets
                // internes ( CSV mode les interprete comme delimiteurs de
                // champ et corrompt le JSON ) .
                .format(WriteMode.CopyFormat.TEXT)
                .done();

        // cascade 3.0.0 : finalizeMode pilote l execution synchrone vs
        // deferree de la finalize hook ( cf javadoc directCopy ) .
        return SinkBuilder.create().<Path>stagingPostgres()
                .dataSource(ds)
                .writeMode(writeMode)
                .rowSerializer(passthrough)
                .finalizeMode(finalizeMode != null ? finalizeMode : FinalizeMode.SYNCHRONOUS)
                .build();
    }

    /** Best-effort UUID parse ; null si malforme . */
    private static UUID safeUuid(String s) {
        if (s == null) return null;
        try { return UUID.fromString(s); } catch (IllegalArgumentException e) { return null; }
    }
}