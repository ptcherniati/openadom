package fr.inra.oresing.workflow.cascade;

import java.sql.Connection;
import java.sql.PreparedStatement;
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

    /** Bulk-INSERT batch size ( rows ) . Override via {@code -Dapp.import.bulkInsertBatchSize=N} . */
    public static final int BULK_INSERT_BATCH_SIZE =
            Integer.getInteger("app.import.bulkInsertBatchSize", 50_000);

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

        try (PreparedStatement ps = connection.prepareStatement(batchInsertSql)) {
            while (true) {
                if (filtered) ps.setObject(1, UUID.fromString(correlationId));
                int affected = ps.executeUpdate();
                if (affected <= 0) break;
            }
        }
    }
}
