package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inrae.ore.cascade.api.builder.SinkBuilder;
import fr.inrae.ore.cascade.api.defaults.db.RowSerializer;
import fr.inrae.ore.cascade.api.defaults.db.staging.FinalizeHook;
import fr.inrae.ore.cascade.api.defaults.db.staging.StagingTableSpec;
import fr.inrae.ore.cascade.core.defaults.db.WriteMode;
import fr.inrae.ore.cascade.model.core.Sink;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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
    public static Sink<Path> directCopy(DataRepository referenceValueRepository,
                                         ImportProperties props) {

        DataSource ds = referenceValueRepository.getDataSource();
        if (ds == null) {
            throw new IllegalStateException("DataRepository did not expose a DataSource ; "
                    + "cannot build StagingPostgresSink");
        }

        StagingTableSpec spec = switch (props.getStagingStrategy()) {
            case PER_CONNECTION_TEMP -> new StagingTableSpec.PerConnectionTemp(
                    "referencevalue_import",
                    "CREATE TEMP TABLE referencevalue_import (data jsonb) ON COMMIT DROP");
            case SHARED_UNLOGGED -> new StagingTableSpec.SharedUnlogged(
                    props.getStagingSharedTableName(),
                    "correlation_id",
                    "data",
                    "created_at",
                    props.getStagingSharedOrphanTtlMinutes());
        };

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

        FinalizeHook hook = ctx -> {
            String corridFilter = (spec instanceof StagingTableSpec.SharedUnlogged) ? ctx.correlationId() : null;
            StagingFinalizeSql.runFinalize(
                    ctx.connection(),
                    schemaName,
                    targetTableId,
                    orderedCols,
                    stagingTable,
                    corridFilter,
                    idJsonPath);
        };

        // COPY column lists :
        //   PER_CONNECTION_TEMP : "data" only ( the temp table has 1 column ) .
        //   SHARED_UNLOGGED     : "correlation_id, data" ( the permanent
        //                         table tags every row with the workflow id
        //                         to isolate concurrent imports ) .
        // The pass-through serializer prepends the correlationId prefix on
        // every physical line for SHARED_UNLOGGED ( cascade 1.8.0
        // RowSerializer contract supports multi-line records ) .
        String copyCols = switch (props.getStagingStrategy()) {
            case PER_CONNECTION_TEMP -> "data";
            case SHARED_UNLOGGED     -> "correlation_id, data";
        };

        // cascade 1.8.0 unified WriteMode hierarchy : StagingUpsert config
        // record encapsulates staging spec + finalize hook + COPY format .
        WriteMode.StagingUpsert mode = WriteMode.stagingUpsert()
                .stagingSpec(spec)
                .finalizeHook(hook)
                .copyColumns(copyCols)
                .delimiter('\t')
                .nullString("\\N")
                .done();

        // cascade 1.8.0 fluent API : SinkBuilder.create().<T>stagingPostgres()
        return SinkBuilder.create().<Path>stagingPostgres()
                .dataSource(ds)
                .writeMode(mode)
                .rowSerializer(passthrough)
                .build();
    }
}
