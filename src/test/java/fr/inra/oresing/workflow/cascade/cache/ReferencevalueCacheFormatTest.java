package fr.inra.oresing.workflow.cascade.cache;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link ReferencevalueCacheFormat} : header construction ,
 * compatibility check and SQL column list rendering . These are pure-Java
 * tests with no Postgres dependency .
 *
 * @author R.YAHIAOUI
 */
@DisplayName("ReferencevalueCacheFormat")
class ReferencevalueCacheFormatTest {

    @Test
    @DisplayName("buildHeader produces 8 bytes : 'OAVR' + big-endian int32 version")
    void buildHeaderProducesExpectedBytes() {
        byte[] header = ReferencevalueCacheFormat.buildHeader();
        assertEquals(8, header.length);
        // ASCII magic
        assertEquals((byte) 'O', header[0]);
        assertEquals((byte) 'A', header[1]);
        assertEquals((byte) 'V', header[2]);
        assertEquals((byte) 'R', header[3]);
        // int32 big-endian , current version = 2
        assertEquals(0, header[4]);
        assertEquals(0, header[5]);
        assertEquals(0, header[6]);
        assertEquals(2, header[7]);
    }

    @Test
    @DisplayName("isCompatible accepts header produced by buildHeader")
    void isCompatibleAcceptsOwnHeader() {
        assertTrue(ReferencevalueCacheFormat.isCompatible(ReferencevalueCacheFormat.buildHeader()));
    }

    @Test
    @DisplayName("isCompatible rejects null / short / empty inputs")
    void isCompatibleRejectsInvalidLengths() {
        assertFalse(ReferencevalueCacheFormat.isCompatible(null));
        assertFalse(ReferencevalueCacheFormat.isCompatible(new byte[0]));
        assertFalse(ReferencevalueCacheFormat.isCompatible(new byte[]{'O', 'A', 'V', 'R'}));
        assertFalse(ReferencevalueCacheFormat.isCompatible(new byte[]{'O', 'A', 'V', 'R', 0, 0, 0, 2, 0}));
    }

    @Test
    @DisplayName("isCompatible rejects wrong magic ( foreign blob )")
    void isCompatibleRejectsWrongMagic() {
        byte[] foreign = new byte[]{'O', 'T', 'H', 'R', 0, 0, 0, 2};
        assertFalse(ReferencevalueCacheFormat.isCompatible(foreign));
    }

    @Test
    @DisplayName("isCompatible rejects unsupported version ( older / newer )")
    void isCompatibleRejectsWrongVersion() {
        // Version 1 ( legacy JSON-lines cache )
        byte[] v1 = new byte[]{'O', 'A', 'V', 'R', 0, 0, 0, 1};
        assertFalse(ReferencevalueCacheFormat.isCompatible(v1));
        // Version 999 ( future format , unreadable for us )
        byte[] v999 = new byte[]{'O', 'A', 'V', 'R', 0, 0, 0x03, (byte) 0xE7};
        assertFalse(ReferencevalueCacheFormat.isCompatible(v999));
    }

    @Test
    @DisplayName("columnsSqlList quotes the 'authorization' keyword , leaves others bare")
    void columnsSqlListQuotesReservedKeywords() {
        String sql = ReferencevalueCacheFormat.columnsSqlList();
        // The 12 cache columns - generated ones excluded
        assertTrue(sql.contains("id"));
        assertTrue(sql.contains("hierarchicalKey"));
        assertTrue(sql.contains("refsLinkedTo"));
        // authorization is a SQL reserved keyword - must be quoted to compile
        assertTrue(sql.contains("\"authorization\""),
                "Expected 'authorization' to be quoted , got : " + sql);
        // Other identifiers are NOT quoted
        assertFalse(sql.contains("\"id\""));
        assertFalse(sql.contains("\"refValues\""));
        // 12 columns -> 11 commas
        long commas = sql.chars().filter(c -> c == ',').count();
        assertEquals(11, commas, "Expected 11 commas in : " + sql);
    }

    @Test
    @DisplayName("buildHeader returns a fresh copy each call ( caller can mutate safely )")
    void buildHeaderReturnsFreshArray() {
        byte[] a = ReferencevalueCacheFormat.buildHeader();
        byte[] b = ReferencevalueCacheFormat.buildHeader();
        assertArrayEquals(a, b);
        a[0] = 'X';
        assertEquals((byte) 'O', b[0], "Mutating a must not affect a subsequent buildHeader() output");
    }

    @Test
    @DisplayName("FORMAT_VERSION + HEADER_SIZE_BYTES are exposed as compile-time constants")
    void exposesConstants() {
        assertEquals(2, ReferencevalueCacheFormat.FORMAT_VERSION);
        assertEquals(8, ReferencevalueCacheFormat.HEADER_SIZE_BYTES);
    }
}
