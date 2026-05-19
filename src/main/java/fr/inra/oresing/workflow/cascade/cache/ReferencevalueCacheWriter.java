package fr.inra.oresing.workflow.cascade.cache;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

/**
 * Captures the rows just inserted into {@code referencevalue} for a given
 * {@code binaryfile} into a stream-friendly , byte-level reproducible blob
 * usable by {@link ReferencevalueCacheReader} at republish time .
 *
 * <h2>What is written</h2>
 *
 * <p>The output stream receives :
 * <ol>
 *   <li>the 8-byte {@link ReferencevalueCacheFormat#buildHeader() OAVR header} ;</li>
 *   <li>the raw bytes of a server-side {@code COPY ( SELECT &lt;cols&gt; FROM
 *       referencevalue WHERE binaryfile = ? ) TO stdout ( FORMAT BINARY )}
 *       streamed by the JDBC {@link CopyManager} .</li>
 * </ol>
 *
 * <p>The column projection is locked by {@link ReferencevalueCacheFormat#COLUMNS}
 * and excludes generated columns ; the reader will use the symmetric column
 * list when replaying the COPY .
 *
 * <h2>Streaming behaviour</h2>
 *
 * <p>The PG binary protocol is a true stream : memory pressure on the JVM
 * remains constant regardless of the number of rows ( hundreds of millions
 * of rows are processed without OOM as long as the destination stream
 * absorbs bytes at PG's pace ) . The caller is expected to pass a stream
 * that funnels the bytes into a Large Object via
 * {@code BinaryFileRepository.storeProcessedData} .
 *
 * <h2>Transaction context</h2>
 *
 * <p>This writer issues a single {@code COPY ... TO stdout} on the connection
 * it receives . The caller controls the transaction lifecycle ; for the
 * upload path the {@code COPY TO} runs in the same transaction as the
 * preceding finalize so a row count discrepancy ( {@code COPY TO} sees
 * MVCC-visible rows for the current tx ) is impossible .
 *
 * @author R.YAHIAOUI
 */
public final class ReferencevalueCacheWriter {

    private static final Logger log = LoggerFactory.getLogger(ReferencevalueCacheWriter.class);

    private ReferencevalueCacheWriter() { }

    /**
     * Captures all {@code referencevalue} rows belonging to {@code fileId} and
     * writes the cache stream ( OAVR header + PG binary COPY payload ) into
     * {@code out} .
     *
     * @param connection active JDBC connection . The caller owns the
     *                   transaction lifecycle .
     * @param schemaName fully-resolved application schema ( e . g .
     *                   {@code "si_acbb"} ) , unqualified ; the writer
     *                   prefixes it onto {@code referencevalue} .
     * @param fileId     UUID of the source {@code binaryfile} ; only rows
     *                   whose {@code binaryfile} column equals this UUID
     *                   are captured .
     * @param out        destination stream . Receives header bytes first ,
     *                   then PG binary COPY bytes . The writer never closes
     *                   {@code out} - it remains the caller's responsibility .
     * @return the number of bytes of PG binary COPY payload written ( does
     *         not include the 8-byte header ) , as reported by
     *         {@link CopyManager#copyOut(String, OutputStream)} .
     *
     * @throws SQLException for any JDBC error or unwrap failure
     * @throws IOException  if writing the header bytes to {@code out} fails
     */
    public static long writeCache(Connection connection,
                                   String schemaName,
                                   UUID fileId,
                                   OutputStream out) throws SQLException, IOException {
        if (connection == null) throw new IllegalArgumentException("connection must not be null");
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("schemaName must not be blank");
        }
        if (fileId == null) throw new IllegalArgumentException("fileId must not be null");
        if (out == null)    throw new IllegalArgumentException("out must not be null");

        // 1. Write our magic + version prefix so the reader can sanity-check
        //    before letting PG ingest anything .
        out.write(ReferencevalueCacheFormat.buildHeader());

        // 2. Stream the binary COPY directly server-side -> JDBC stream -> out .
        //    Note : we cannot bind ? for binaryfile in a COPY statement ( PG
        //    forbids parameter placeholders in COPY ) , so we inline the UUID
        //    as a literal . UUID.toString() is constrained to [0-9a-f-] : no
        //    SQL injection vector .
        String copyOutSql = ""
                + "COPY ( SELECT " + ReferencevalueCacheFormat.columnsSqlList()
                + " FROM " + schemaName + ".referencevalue"
                + " WHERE binaryFile = '" + fileId + "'::uuid )"
                + " TO STDOUT ( FORMAT BINARY )";
        CopyManager copyMgr = connection.unwrap(PGConnection.class).getCopyAPI();
        long bytes = copyMgr.copyOut(copyOutSql, out);
        out.flush();
        log.info("ReferencevalueCacheWriter : captured {} bytes ( PG binary COPY ) "
                + "for binaryfile={} from {}.referencevalue",
                bytes, fileId, schemaName);
        return bytes;
    }
}
