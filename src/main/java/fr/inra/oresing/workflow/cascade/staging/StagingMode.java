package fr.inra.oresing.workflow.cascade.staging;

import fr.inra.oresing.workflow.cascade.config.ImportProperties;
import fr.inrae.ore.cascade.api.defaults.db.staging.StagingTableSpec;

import java.util.Locale;
import java.util.UUID;

/**
 * Abstraction unique sur les 3 strategies de staging supportees par
 * {@code DIRECT_COPY} . Encapsule le choix de la spec cascade , le nom
 * de la table , la presence de la colonne {@code correlation_id} dans le
 * CSV produit , et les hooks de cycle de vie ( CREATE / DROP par workflow ) .
 *
 * <p>Garantit que le code appelant ( CascadeSinkFactory , CascadeImportPipeline ,
 * StagingFinalizeSql , StagingOrphanSweeper ) ne contient plus de switch
 * isole ( fini les if/else duplique ) .
 *
 * <p>Strategies :
 * <ul>
 *   <li>{@link PerConnectionTempMode} : 1 TEMP table per-conn ( sticky , 1 sink ) .</li>
 *   <li>{@link SharedUnloggedMode} : 1 table UNLOGGED partagee + tag correlation_id .</li>
 *   <li>{@link PerWorkflowTableMode} : 1 table UNLOGGED dediee creee/droppee par workflow .</li>
 * </ul>
 *
 * @author R.YAHIAOUI
 */
public sealed interface StagingMode
        permits StagingMode.PerConnectionTempMode,
                StagingMode.SharedUnloggedMode,
                StagingMode.PerWorkflowTableMode {

    ImportProperties.StagingStrategy strategy();

    /** Spec cascade lib utilisee par le sink . */
    StagingTableSpec cascadeSpec();

    /** Liste de colonnes COPY ( ex {@code "data"} ou {@code "correlation_id, data"} ) . */
    String copyColumns();

    /** Permet de filter le UPSERT par correlation_id ( SHARED_UNLOGGED uniquement ) . */
    String correlationIdFilter(UUID correlationId);

    /** SQL CREATE a executer avant cascade ( null si rien a faire ) . */
    String createTableSql();

    /** SQL DROP a executer apres cascade succes ( null si rien a faire ) . */
    String dropTableSql();

    /**
     * Parametre UUID a lier en position 1 du PreparedStatement pour
     * {@link #createTableSql()} / {@link #dropTableSql()} .
     * Retourne null si le SQL ne comporte pas de placeholder ( ? ) .
     */
    UUID tableDdlParam();

    /** Nom de la table staging effective ( utile pour SELECT count cote sweeper / dashboard ) . */
    String tableName();

    /**
     * Factory : construit le mode adapte a la config courante + correlationId
     * du workflow en cours .
     */
    static StagingMode of(ImportProperties.StagingStrategy strategy,
                          UUID correlationId,
                          String sharedTableName,
                          int sharedOrphanTtlMinutes) {
        return switch (strategy) {
            case PER_CONNECTION_TEMP -> new PerConnectionTempMode();
            case SHARED_UNLOGGED -> new SharedUnloggedMode(sharedTableName, sharedOrphanTtlMinutes);
            case PER_WORKFLOW_TABLE -> {
                if (correlationId == null) {
                    throw new IllegalArgumentException(
                            "correlationId is required for PER_WORKFLOW_TABLE strategy");
                }
                yield new PerWorkflowTableMode(correlationId);
            }
        };
    }

    /** Convention de nommage pour les tables per-workflow ( oa_staging.referencevalue_import_<UUID-_> ) . */
    static String perWorkflowTableName(UUID correlationId) {
        if (correlationId == null) {
            throw new IllegalArgumentException("correlationId is required for PER_WORKFLOW_TABLE");
        }
        return "oa_staging.referencevalue_import_"
                + correlationId.toString().replace('-', '_').toLowerCase(Locale.ROOT);
    }

    // ------------------------------------------------------------- //
    //  Implementations                                              //
    // ------------------------------------------------------------- //

    /** PER_CONNECTION_TEMP : TEMP table per-conn , aucun create/drop side-channel . */
    record PerConnectionTempMode() implements StagingMode {
        @Override public ImportProperties.StagingStrategy strategy()         { return ImportProperties.StagingStrategy.PER_CONNECTION_TEMP; }
        @Override public StagingTableSpec cascadeSpec()                      {
            return new StagingTableSpec.PerConnectionTemp(
                    "referencevalue_import",
                    "CREATE TEMP TABLE referencevalue_import (data jsonb) ON COMMIT DROP");
        }
        @Override public String copyColumns()                                { return "data"; }
        @Override public String correlationIdFilter(UUID correlationId)      { return null; }
        @Override public String createTableSql()                             { return null; }
        @Override public String dropTableSql()                               { return null; }
        @Override public UUID tableDdlParam()                                { return null; }
        @Override public String tableName()                                  { return "referencevalue_import"; }
    }

    /** SHARED_UNLOGGED : table partagee + tag correlation_id . */
    record SharedUnloggedMode(String sharedTableName, int orphanTtlMinutes) implements StagingMode {
        @Override public ImportProperties.StagingStrategy strategy()         { return ImportProperties.StagingStrategy.SHARED_UNLOGGED; }
        @Override public StagingTableSpec cascadeSpec()                      {
            return new StagingTableSpec.SharedUnlogged(
                    sharedTableName, "correlation_id", "data", "created_at", orphanTtlMinutes);
        }
        @Override public String copyColumns()                                { return "correlation_id, data"; }
        @Override public String correlationIdFilter(UUID correlationId)      { return correlationId == null ? null : correlationId.toString(); }
        @Override public String createTableSql()                             { return null; }
        @Override public String dropTableSql()                               { return null; }
        @Override public UUID tableDdlParam()                                { return null; }
        @Override public String tableName()                                  { return sharedTableName; }
    }

    /**
     * PER_WORKFLOW_TABLE : 1 table UNLOGGED dediee . Reuse cascade
     * SharedUnlogged spec mais avec tableName per-workflow ; le tag
     * {@code correlation_id} reste cosmetique ( table dediee donc
     * filtre redondant , mais utile pour symetrie / inspection DB ) .
     */
    record PerWorkflowTableMode(UUID correlationId) implements StagingMode {
        @Override public ImportProperties.StagingStrategy strategy()         { return ImportProperties.StagingStrategy.PER_WORKFLOW_TABLE; }
        @Override public StagingTableSpec cascadeSpec()                      {
            // SharedUnlogged spec , mais avec un nom de table specifique au workflow .
            // Cleanup orphan automatique geree par {@link StagingOrphanSweeper}
            // ( DROP TABLE apres TTL si workflow plus actif ) .
            return new StagingTableSpec.SharedUnlogged(
                    perWorkflowTableName(correlationId), "correlation_id", "data", "created_at",
                    /* orphanTtl absorbed by sweeper external */ Integer.MAX_VALUE);
        }
        @Override public String copyColumns()                                { return "correlation_id, data"; }
        @Override public String correlationIdFilter(UUID correlationId)      { return correlationId == null ? null : correlationId.toString(); }
        @Override public String createTableSql()                             {
            // SECURITY DEFINER function ( cf V3 migration ) : cree la table
            // UNLOGGED dans oa_staging avec les droits de openAdomTechUser
            // et grant SELECT/INSERT/DELETE TO PUBLIC pour que le COPY
            // de l'app ( role per-app ) y accede . Evite GRANT CREATE TO
            // PUBLIC sur le schema , bien plus permissif .
            // The ? placeholder is bound to the correlationId UUID by the caller
            // via PreparedStatement to avoid dynamic SQL formatting.
            return "SELECT oa_staging.create_per_workflow_referencevalue_import(?)";
        }
        @Override public String dropTableSql()                               {
            // The ? placeholder is bound to the correlationId UUID by the caller
            // via PreparedStatement to avoid dynamic SQL formatting.
            return "SELECT oa_staging.drop_per_workflow_referencevalue_import(?)";
        }
        @Override public UUID tableDdlParam()                                { return correlationId; }
        @Override public String tableName()                                  { return perWorkflowTableName(correlationId); }
    }
}
