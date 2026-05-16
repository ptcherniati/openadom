package fr.inra.oresing.persistence.refref;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RefrefRebuildSql} - verifies the SQL strings
 * sent to the JDBC driver match the expected shape ( table names ,
 * JOIN predicate , JSON_TABLE path , filter clause ) . Does not
 * connect to a real PG ; integration coverage is provided by the
 * publish/unpublish suite ( mvn -Pall-tests ) .
 */
class RefrefRebuildSqlTest {

    private Connection cn;
    private Statement  stmt;

    @BeforeEach
    void setUp() throws Exception {
        cn   = mock(Connection.class);
        stmt = mock(Statement.class);
        when(cn.createStatement()).thenReturn(stmt);
    }

    @Test
    void createSourceTable_creates_temp_table_with_expected_columns() throws Exception {
        RefrefRebuildSql.createSourceTable(cn);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(stmt).execute(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();

        assertThat(sql).contains("CREATE TEMP TABLE refref_source");
        assertThat(sql).contains("rt   text");
        assertThat(sql).contains("hk   ltree");
        assertThat(sql).contains("pc   text");
        assertThat(sql).contains("rsl  jsonb");
        assertThat(sql).contains("ON COMMIT DROP");
    }

    @Test
    void populateSource_without_filter_omits_where_clause() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(cn.prepareStatement(anyString())).thenReturn(ps);

        RefrefRebuildSql.populateSource(cn, "referencevalue_import", null);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(cn).prepareStatement(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();

        assertThat(sql).contains("INSERT INTO refref_source");
        assertThat(sql).contains("FROM referencevalue_import s");
        assertThat(sql).doesNotContain("WHERE s.correlation_id");
        verify(ps, never()).setObject(anyInt(), any());
        verify(ps).executeUpdate();
    }

    @Test
    void populateSource_with_filter_binds_correlation_id() throws Exception {
        PreparedStatement ps = mock(PreparedStatement.class);
        when(cn.prepareStatement(anyString())).thenReturn(ps);
        UUID cid = UUID.randomUUID();

        RefrefRebuildSql.populateSource(cn, "oa_staging.referencevalue_import_shared", cid);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(cn).prepareStatement(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();

        assertThat(sql).contains("FROM oa_staging.referencevalue_import_shared s");
        assertThat(sql).contains("WHERE s.correlation_id = ?");
        verify(ps).setObject(1, cid);
        verify(ps).executeUpdate();
    }

    @Test
    void deleteOldLinks_joins_on_uniqueness_constraint_columns() throws Exception {
        RefrefRebuildSql.deleteOldLinks(cn, "myapp");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(stmt).executeUpdate(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();

        assertThat(sql).contains("DELETE FROM myapp.reference_reference");
        assertThat(sql).contains("FROM myapp.referencevalue rv");
        assertThat(sql).contains("rv.referencetype = rs.rt");
        assertThat(sql).contains("rv.hierarchicalkey = rs.hk");
        assertThat(sql).contains("rv.patterncolumnname IS NOT DISTINCT FROM rs.pc");
    }

    @Test
    void createPendingTable_creates_temp_table_with_expected_columns() throws Exception {
        RefrefRebuildSql.createPendingTable(cn);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(stmt).execute(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();

        assertThat(sql).contains("CREATE TEMP TABLE refref_pending");
        assertThat(sql).contains("referenceid  uuid");
        assertThat(sql).contains("referencesby uuid");
        assertThat(sql).contains("ON COMMIT DROP");
    }

    @Test
    void populatePending_uses_json_table_with_correct_path_and_filters_empty_rsl() throws Exception {
        RefrefRebuildSql.populatePending(cn, "myapp");

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(stmt).executeUpdate(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();

        assertThat(sql).contains("INSERT INTO refref_pending");
        assertThat(sql).contains("rv.id");
        assertThat(sql).contains("FROM refref_source rs");
        assertThat(sql).contains("JOIN myapp.referencevalue rv");
        // JSON_TABLE on rs.rsl ( not s.data.refslinkedto since rsl is already extracted )
        assertThat(sql).contains("JSON_TABLE (rs.rsl, '$.*.*.*.uuids'");
        assertThat(sql).contains("NESTED PATH '$[*]'");
        assertThat(sql).contains("referencesby TEXT PATH '$'");
        // Skip rows with empty / null refslinkedto - avoid NULL referencesby in target table
        assertThat(sql).contains("rs.rsl IS NOT NULL");
        assertThat(sql).contains("rs.rsl <> '{}'::jsonb");
    }

    @Test
    void insertReferenceReference_inserts_from_pending_into_application_schema() throws Exception {
        when(stmt.executeUpdate(anyString())).thenReturn(42);

        int inserted = RefrefRebuildSql.insertReferenceReference(cn, "myapp");

        assertThat(inserted).isEqualTo(42);
        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(stmt).executeUpdate(sqlCaptor.capture());
        String sql = sqlCaptor.getValue();

        assertThat(sql).contains("INSERT INTO myapp.reference_reference(referenceid, referencesby)");
        assertThat(sql).contains("SELECT referenceid, referencesby FROM refref_pending");
    }
}
