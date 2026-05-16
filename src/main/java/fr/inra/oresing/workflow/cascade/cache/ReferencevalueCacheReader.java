package fr.inra.oresing.workflow.cascade.cache;

import org.postgresql.PGConnection;
import org.postgresql.copy.CopyManager;
import org.postgresql.largeobject.LargeObject;
import org.postgresql.largeobject.LargeObjectManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Replays the cache stream produced by {@link ReferencevalueCacheWriter}
 * back into {@code referencevalue} via a single
 * {@code COPY referencevalue ( cols ) FROM stdin ( FORMAT BINARY )} .
 *
 * <h2>Validation</h2>
 *
 * <p>Before any byte is fed to PG , the 8-byte {@link ReferencevalueCacheFormat
 * OAVR header} is read from the Large Object and validated
 * ( {@link ReferencevalueCacheFormat#isCompatible} ) . If the header is
 * absent ( legacy cache ) , has wrong magic , or carries an incompatible
 * version , a {@link IncompatibleCacheFormatException} is thrown without
 * touching the {@code referencevalue} table ; the caller is expected to
 * catch it and fall back to the LITE / FULL path .
 *
 * <h2>Cancellation</h2>
 *
 * <p>The COPY IN statement runs inside the caller's transaction . A
 * {@code pg_cancel_backend} issued against the connection's pid will
 * raise {@link SQLException} 57014 inside {@link CopyManager#copyIn} ;
 * the caller is expected to roll back the transaction .
 *
 * @author R.YAHIAOUI
 */
public final class ReferencevalueCacheReader {

    private static final Logger log = LoggerFactory.getLogger(ReferencevalueCacheReader.class);

    private ReferencevalueCacheReader() { }

    /**
     * Reads the cache stored as Large Object {@code oid} on {@code connection} ,
     * validates the OAVR header , and streams the remaining bytes into
     * {@code referencevalue} via {@code COPY FROM stdin ( FORMAT BINARY )} .
     *
     * @param connection active JDBC connection ( {@code autoCommit=false} )
     * @param schemaName application schema ( e . g . {@code "si_acbb"} )
     * @param oid        Postgres Large Object oid pointing to the cache bytes
     * @return rowcount reported by {@code COPY IN} ( number of rows inserted
     *         into {@code referencevalue} ) , minus any rejected duplicates
     * @throws IncompatibleCacheFormatException if the header is absent ,
     *         malformed , or carries an incompatible {@link ReferencevalueCacheFormat#FORMAT_VERSION
     *         version} . The {@code referencevalue} table is left untouched .
     * @throws SQLException for any JDBC / COPY / LO error
     * @throws IOException  for any unexpected byte-stream read failure
     */
    public static long replayCache(Connection connection,
                                    String schemaName,
                                    long oid) throws SQLException, IOException {
        if (connection == null) throw new IllegalArgumentException("connection must not be null");
        if (schemaName == null || schemaName.isBlank()) {
            throw new IllegalArgumentException("schemaName must not be blank");
        }

        PGConnection pgConn = connection.unwrap(PGConnection.class);
        LargeObjectManager loMgr = pgConn.getLargeObjectAPI();
        LargeObject lo = loMgr.open(oid, LargeObjectManager.READ);
        try (InputStream in = lo.getInputStream()) {
            // 1. Header gating - reject any non-OAVR or wrong-version blob
            //    before we let PG ingest a single byte .
            byte[] header = in.readNBytes(ReferencevalueCacheFormat.HEADER_SIZE_BYTES);
            if (!ReferencevalueCacheFormat.isCompatible(header)) {
                throw new IncompatibleCacheFormatException(
                        "Cache oid=" + oid + " has unexpected header ; expected "
                                + "OAVR v" + ReferencevalueCacheFormat.FORMAT_VERSION
                                + " ( bytes " + describe(header) + " )");
            }

            // 2. Stream the remaining bytes into COPY referencevalue FROM stdin .
            String copyInSql = ""
                    + "COPY " + schemaName + ".referencevalue ( "
                    + ReferencevalueCacheFormat.columnsSqlList()
                    + " ) FROM STDIN ( FORMAT BINARY )";
            CopyManager copyMgr = pgConn.getCopyAPI();
            long rows = copyMgr.copyIn(copyInSql, in);
            log.info("ReferencevalueCacheReader : COPY IN restored {} rows into {}.referencevalue ( oid={} )",
                    rows, schemaName, oid);
            return rows;
        } finally {
            try { lo.close(); }
            catch (SQLException ignored) { /* best-effort - LO is closed at tx end anyway */ }
        }
    }

    /** Hex dump of the bytes for diagnostic messages ( e . g . {@code 4F 41 56 52 00 00 00 02} ) . */
    private static String describe(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "(empty)";
        StringBuilder sb = new StringBuilder(bytes.length * 3);
        for (int i = 0; i < bytes.length; i++) {
            if (i > 0) sb.append(' ');
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * Thrown when the Large Object header is missing , has wrong magic , or
     * carries a non-supported {@code FORMAT_VERSION} . Indicates the cache
     * was written by a different application or by a previous incompatible
     * code revision , and the FAST path must fall back to LITE / FULL .
     */
    public static final class IncompatibleCacheFormatException extends SQLException {

        public IncompatibleCacheFormatException(String message) {
            // Use SQLSTATE 22000 ( data_exception ) so generic SQLException catchers
            // see a non-retriable error and propagate it up to the FAST router .
            super(message, "22000");
        }
    }
}
