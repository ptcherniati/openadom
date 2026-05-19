package fr.inra.oresing.workflow.cascade.cache;

import java.util.Arrays;

/**
 * Constants and helpers defining the binary on-disk format of the
 * {@code binaryfile.processed_data} cache used by the FAST republish path .
 *
 * <h2>Format layout</h2>
 *
 * <p>The Large Object content is the concatenation of two consecutive byte
 * regions :
 * <ol>
 *   <li><b>Magic header</b> ( 8 bytes ) : 4 ASCII bytes {@code "OAVR"} ( OpenAdom
 *       referenceValue Record ) followed by a 4-byte big-endian {@code int32}
 *       version number ( currently {@link #FORMAT_VERSION} ) .</li>
 *   <li><b>PG binary COPY payload</b> : raw bytes produced by
 *       {@code COPY ( SELECT ... FROM referencevalue WHERE binaryfile=? )
 *       TO stdout ( FORMAT BINARY )} . Includes the standard
 *       {@code PGCOPY\n\xFF\r\n\x00} 11-byte signature , flags , header
 *       extension area and the actual row tuples .</li>
 * </ol>
 *
 * <p>Total prefix overhead : 8 bytes ( negligible on any cache &gt; 1 KB ) .
 *
 * <h2>Why a custom header</h2>
 *
 * <p>PG binary COPY already carries its own {@code PGCOPY} signature , but that
 * signature only attests that the bytes are a valid PG COPY stream . It does
 * not attest which application produced it , what schema it targets , or which
 * column order was used . Our header :
 *
 * <ul>
 *   <li>identifies the bytes as the OpenAdom referencevalue cache , preventing
 *       a confused-deputy scenario where a stranger LargeObject could be
 *       mistakenly fed to {@code COPY referencevalue FROM stdin} ;</li>
 *   <li>carries an application-level format version : whenever the column
 *       layout of the cache changes ( DDL evolution , new column included ) ,
 *       the version is bumped , older caches are rejected at read time , and
 *       the FAST path falls back to LITE/FULL automatically until the cache
 *       is regenerated on the next upload .</li>
 * </ul>
 *
 * <h2>Backward compatibility</h2>
 *
 * <p>This is a fresh format introduced for the direct-COPY refactor . Any
 * pre-existing cache uses the legacy JSON-lines payload and lacks our
 * magic header , so reads will fail the version check and the workflow will
 * fall through to LITE/FULL . Operators do not need to clear old caches by
 * hand .
 *
 * @author R.YAHIAOUI
 */
public final class ReferencevalueCacheFormat {

    private ReferencevalueCacheFormat() { }

    /** ASCII identifier of the OpenAdom referencevalue cache ( {@code 'O','A','V','R'} ) . */
    static final byte[] MAGIC_BYTES = new byte[] { 'O', 'A', 'V', 'R' };

    /**
     * Current cache format version . Bump whenever the on-disk layout
     * changes in a way that makes older caches unreadable ( column added /
     * removed / reordered , type changed , etc . ) .
     */
    public static final int FORMAT_VERSION = 2;

    /** Total size of the prefix header in bytes ( {@code 4 magic + 4 version} ) . */
    public static final int HEADER_SIZE_BYTES = 8;

    /**
     * Ordered list of {@code referencevalue} columns participating in the
     * cache COPY ( excludes generated columns {@code lineHierarchicalKeyPatternColumnName}
     * and {@code lineNaturalKeyPatternColumnName} which PG recomputes on insert ) .
     *
     * <p>The order is locked - any change must bump {@link #FORMAT_VERSION} .
     */
    public static final String[] COLUMNS = new String[] {
            "id",
            "patternColumnName",
            "creationDate",
            "updateDate",
            "application",
            "referenceType",
            "hierarchicalKey",
            "naturalKey",
            "refsLinkedTo",
            "refValues",
            "binaryFile",
            "authorization"
    };

    /**
     * Produces the 8 bytes that must prefix every cache Large Object .
     * The returned array is a fresh copy ; the caller may mutate it safely .
     */
    public static byte[] buildHeader() {
        byte[] out = new byte[HEADER_SIZE_BYTES];
        System.arraycopy(MAGIC_BYTES, 0, out, 0, MAGIC_BYTES.length);
        // Big-endian int32 version - matches Java's DataOutputStream.writeInt
        // and is trivially decoded by any platform .
        out[4] = (byte) ((FORMAT_VERSION >>> 24) & 0xFF);
        out[5] = (byte) ((FORMAT_VERSION >>> 16) & 0xFF);
        out[6] = (byte) ((FORMAT_VERSION >>>  8) & 0xFF);
        out[7] = (byte) ( FORMAT_VERSION         & 0xFF);
        return out;
    }

    /**
     * Validates an 8-byte buffer presumed to be the cache header .
     *
     * @return {@code true} only when magic matches <b>and</b> version equals
     *         {@link #FORMAT_VERSION} . Magic mismatch is treated as
     *         "not our cache at all" ; version mismatch is treated as
     *         "older / newer format , unreadable" . Both result in the FAST
     *         path declining and falling back to LITE/FULL .
     */
    public static boolean isCompatible(byte[] header) {
        if (header == null || header.length != HEADER_SIZE_BYTES) return false;
        for (int i = 0; i < MAGIC_BYTES.length; i++) {
            if (header[i] != MAGIC_BYTES[i]) return false;
        }
        int v = ((header[4] & 0xFF) << 24)
              | ((header[5] & 0xFF) << 16)
              | ((header[6] & 0xFF) <<  8)
              |  (header[7] & 0xFF);
        return v == FORMAT_VERSION;
    }

    /**
     * Comma-separated SQL identifier list of the cache columns , suitable for
     * direct injection into a {@code COPY referencevalue ( cols ) FROM stdin}
     * statement . Identifiers are <b>not</b> quoted ; the column names listed
     * in {@link #COLUMNS} are all valid unquoted SQL identifiers except
     * {@code authorization} which is a reserved keyword and therefore quoted .
     */
    public static String columnsSqlList() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < COLUMNS.length; i++) {
            if (i > 0) sb.append(", ");
            String col = COLUMNS[i];
            if ("authorization".equalsIgnoreCase(col)) {
                sb.append('"').append(col).append('"');
            } else {
                sb.append(col);
            }
        }
        return sb.toString();
    }

    /** Returns a defensive copy of the magic bytes for tests / diagnostics . */
    public static byte[] magicBytesCopy() {
        return Arrays.copyOf(MAGIC_BYTES, MAGIC_BYTES.length);
    }
}
