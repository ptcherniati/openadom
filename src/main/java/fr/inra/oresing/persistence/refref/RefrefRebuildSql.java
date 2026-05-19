package fr.inra.oresing.persistence.refref;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

/**
 * Centralizes the reference_reference rebuild SQL flow shared between
 * {@code DataRepository.storeAll} ( MERGE_FILE path ) and
 * {@code StagingFinalizeSql.runFinalize} ( DIRECT_COPY path ) .
 *
 * <h2>Why</h2>
 *
 * <p>Both paths execute the SAME 5-step SQL sequence to atomically
 * rebuild {@code reference_reference} ( junction M:N table ) when a file
 * is published / republished :
 *
 * <ol>
 *   <li>Snapshot identity keys + refslinkedto from staging into
 *       {@code refref_source} BEFORE the UPSERT consumes the staging
 *       ( {@link #createSourceTable} + {@link #populateSource} ) .</li>
 *   <li>DELETE old reference_reference rows via JOIN on referencevalue
 *       ( referenceids preserved by ON CONFLICT DO UPDATE ) - see
 *       {@link #deleteOldLinks} .</li>
 *   <li>Caller performs the actual UPSERT into referencevalue .</li>
 *   <li>Build {@code refref_pending} POST-UPSERT via JOIN on
 *       referencevalue ( rv.id correct for existing=OLD , new=NEW ) +
 *       JSON_TABLE extraction of refslinkedto - see
 *       {@link #createPendingTable} + {@link #populatePending} .</li>
 *   <li>INSERT new reference_reference rows from refref_pending -
 *       {@link #insertReferenceReference} .</li>
 * </ol>
 *
 * <h2>Refacto B context ( 16-05-26 )</h2>
 *
 * <p>Before this refacto the two callers duplicated the SQL inline ,
 * leading to a bug : when {@code storedReferences} preload was skipped
 * for non-recursive datatypes ( {@link
 * fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext}
 * gain : ~110s + ~2.1 GB heap ) , both call sites had to be updated to
 * source refref ids via JOIN on referencevalue POST-UPSERT . Missing one
 * meant FK violation on republish .
 *
 * <p>Now consolidated : a single source of truth . Changing the rebuild
 * algorithm touches one file ; both paths follow .
 *
 * <h2>Atomicity</h2>
 *
 * <p>All methods assume the caller holds an open transaction with
 * {@code autoCommit=false} . Failures throw {@link SQLException} and
 * leave PG in a rollback-able state . The caller is responsible for
 * commit / rollback .
 *
 * <h2>Index dependency</h2>
 *
 * <p>The JOIN predicate in {@link #deleteOldLinks} and
 * {@link #populatePending} matches the {@code hierarchicalKey_uniqueness}
 * UNIQUE constraint on
 * {@code ( application , referencetype , hierarchicalkey , patterncolumnname )}
 * - guarantees 1:1 lookup , no cartesian product , no full scan even on
 * 100M+ row tables .
 *
 * @author R.YAHIAOUI
 */
public final class RefrefRebuildSql {

    private RefrefRebuildSql() {
        // utility class
    }

    /**
     * Step 1.a : Create the {@code refref_source} temp table that will
     * hold the staging snapshot ( referencetype , hierarchicalkey ,
     * patterncolumnname , refslinkedto ) needed AFTER the UPSERT
     * consumes the staging table .
     *
     * <p>{@code ON COMMIT DROP} : table disappears at end of the
     * enclosing transaction - no cleanup needed .
     */
    public static void createSourceTable(Connection cn) throws SQLException {
        try (Statement stmt = cn.createStatement()) {
            stmt.execute("""
                    CREATE TEMP TABLE refref_source (
                        rt   text,
                        hk   ltree,
                        pc   text,
                        rsl  jsonb
                    ) ON COMMIT DROP
                    """);
        }
    }

    /**
     * Step 1.b : Populate {@code refref_source} from the staging table .
     * Extracts the 3 identity keys ( referencetype , hierarchicalkey ,
     * patterncolumnname ) needed for the JOIN on referencevalue plus the
     * {@code refslinkedto} jsonb needed by {@link #populatePending} .
     *
     * @param cn                  open connection ( tx in progress )
     * @param stagingTable        fully-qualified staging table name
     *                            ( e.g. {@code referencevalue_import} or
     *                            {@code oa_staging.referencevalue_import_shared} )
     * @param correlationIdFilter optional ; if non-null the staging is
     *                            filtered by {@code correlation_id} - used
     *                            by SHARED_UNLOGGED to isolate per-workflow
     *                            rows in a shared table . Null = no filter
     *                            ( merged.csv path uses a private temp ) .
     */
    public static void populateSource(Connection cn,
                                      String stagingTable,
                                      UUID correlationIdFilter) throws SQLException {
        boolean filtered = correlationIdFilter != null;
        String sqlBase = "INSERT INTO refref_source(rt, hk, pc, rsl)"
                + " SELECT s.data->>'referencetype',"
                + "        (s.data->>'hierarchicalkey')::ltree,"
                + "        s.data->>'patterncolumnname',"
                + "        s.data->'refslinkedto'"
                + " FROM " + stagingTable + " s";
        if (filtered) {
            try (PreparedStatement ps = cn.prepareStatement(sqlBase + " WHERE s.correlation_id = ?")) {
                ps.setObject(1, correlationIdFilter);
                ps.executeUpdate();
            }
        } else {
            try (java.sql.Statement stmt = cn.createStatement()) {
                stmt.executeUpdate(sqlBase);
            }
        }
    }

    /**
     * Step 2 : DELETE old {@code reference_reference} rows pointing to
     * referencevalue rows that are being re-published . Uses a JOIN on
     * referencevalue rather than relying on {@code (s.data->>'id')} to
     * locate the old ids ; this is essential because for non-recursive
     * datatypes the staging.data.id is now random ( refacto B skip
     * preload ) - only referencevalue.id ( preserved by ON CONFLICT ) is
     * authoritative .
     *
     * @return rows deleted from reference_reference
     */
    public static int deleteOldLinks(Connection cn, String appSchema) throws SQLException {
        String sql = "DELETE FROM " + appSchema + ".reference_reference"
                + " WHERE referenceid IN ("
                + "     SELECT rv.id"
                + "     FROM " + appSchema + ".referencevalue rv"
                + "     JOIN refref_source rs"
                + "       ON rv.referencetype = rs.rt"
                + "      AND rv.hierarchicalkey = rs.hk"
                + "      AND rv.patterncolumnname IS NOT DISTINCT FROM rs.pc"
                + " )";
        try (Statement stmt = cn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    /**
     * Step 4.a : Create the {@code refref_pending} temp table ( holds
     * the new {@code reference_reference} rows about to be inserted ) .
     * Same {@code ON COMMIT DROP} pattern as refref_source .
     */
    public static void createPendingTable(Connection cn) throws SQLException {
        try (Statement stmt = cn.createStatement()) {
            stmt.execute("""
                    CREATE TEMP TABLE refref_pending (
                        referenceid  uuid,
                        referencesby uuid
                    ) ON COMMIT DROP
                    """);
        }
    }

    /**
     * Step 4.b : Populate {@code refref_pending} from {@code refref_source}
     * via JOIN on referencevalue ( POST-UPSERT ) + JSON_TABLE extraction
     * of refslinkedto . This is what makes refacto B possible : the
     * referenceid is sourced from rv.id ( authoritative ) , not from
     * staging.data.id ( may be random for non-recursive datatypes ) .
     *
     * <p>JSON_TABLE path is {@code '$.*.*.*.uuids'} ( not
     * {@code '$.refslinkedto.*'} ) because {@code rs.rsl} is already the
     * extracted refslinkedto jsonb value from step 1.b .
     *
     * <p>Filters out rows whose refslinkedto is null or empty {@code {}}
     * to avoid producing rows with NULL referencesby ( would fail FK ) .
     *
     * @return rows inserted into refref_pending
     */
    public static int populatePending(Connection cn, String appSchema) throws SQLException {
        String sql = "INSERT INTO refref_pending(referenceid, referencesby)"
                + " SELECT DISTINCT"
                + "     rv.id                       AS referenceid,"
                + "     (joins.referencesby)::uuid  AS referencesby"
                + " FROM refref_source rs"
                + " JOIN " + appSchema + ".referencevalue rv"
                + "   ON rv.referencetype = rs.rt"
                + "  AND rv.hierarchicalkey = rs.hk"
                + "  AND rv.patterncolumnname IS NOT DISTINCT FROM rs.pc,"
                + " JSON_TABLE (rs.rsl, '$.*.*.*.uuids' COLUMNS ("
                + "     NESTED PATH '$[*]' COLUMNS(referencesby TEXT PATH '$')"
                + " )) as joins"
                + " WHERE rs.rsl IS NOT NULL"
                + "   AND rs.rsl <> '{}'::jsonb";
        try (Statement stmt = cn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }

    /**
     * Step 5 : INSERT the rebuilt links into {@code reference_reference} .
     * FK satisfied at this point because the bulk UPSERT into
     * referencevalue happened between step 2 and step 4 .
     *
     * @return rows inserted into reference_reference
     */
    public static int insertReferenceReference(Connection cn, String appSchema) throws SQLException {
        String sql = "INSERT INTO " + appSchema + ".reference_reference(referenceid, referencesby)"
                + " SELECT referenceid, referencesby FROM refref_pending";
        try (Statement stmt = cn.createStatement()) {
            return stmt.executeUpdate(sql);
        }
    }
}
