package fr.inra.oresing.workflow.cascade;

import fr.inra.oresing.workflow.cascade.config.ImportProperties.IntraDuplicatePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires {@link IntraImportDuplicateDetector} ( P1-3 ) . On mocke le
 * JDBC et on verifie : le SQL genere ( fige toute regression de cle ) , le
 * no-op {@code OFF} / {@code null} , le cas nominal sans doublon ( aucun scan
 * sample ) , et les politiques {@code WARN} ( pas d'exception ) / {@code FAIL}
 * ( {@link IntraImportDuplicateException} avec detail ) .
 *
 * @author R.YAHIAOUI
 */
@Tag("domain.model")
@DisplayName("IntraImportDuplicateDetector - detection doublons staging")
class IntraImportDuplicateDetectorTest {

    private static final String STAGING = "oa_staging.referencevalue_import_shared";
    private static final String CORR = "96a7ee3f-a9d9-43ff-93aa-3ecd31ca906f";

    @Test
    @DisplayName("countSql : groupe sur les 4 cles de hierarchicalKey_uniqueness + filtre correlation")
    void countSqlShape() {
        String filtered = IntraImportDuplicateDetector.countSql(STAGING, true);
        assertThat(filtered)
                .contains("data->>'application'")
                .contains("data->>'referencetype'")
                .contains("data->>'hierarchicalkey'")
                .contains("data->>'patterncolumnname'")
                .contains("HAVING count(*) > 1")
                .contains("WHERE s.correlation_id = ?::uuid")
                .contains(STAGING);

        // PER_CONNECTION_TEMP : pas de colonne correlation_id -> pas de WHERE .
        assertThat(IntraImportDuplicateDetector.countSql(STAGING, false))
                .doesNotContain("correlation_id");
    }

    @Test
    @DisplayName("policy OFF ou null : aucune interaction JDBC ( 0 overhead )")
    void offIsNoOp() throws Exception {
        Connection c = mock(Connection.class);
        IntraImportDuplicateDetector.check(c, STAGING, CORR, IntraDuplicatePolicy.OFF);
        IntraImportDuplicateDetector.check(c, STAGING, CORR, null);
        verifyNoInteractions(c);
    }

    @Test
    @DisplayName("aucun doublon ( dup_groups=0 ) : pas d'exception , pas de requete sample")
    void cleanImportRunsNoSample() throws Exception {
        Connection c = mock(Connection.class);
        PreparedStatement countPs = mock(PreparedStatement.class);
        ResultSet rs = mock(ResultSet.class);
        when(c.prepareStatement(IntraImportDuplicateDetector.countSql(STAGING, true))).thenReturn(countPs);
        when(countPs.executeQuery()).thenReturn(rs);
        when(rs.getLong(1)).thenReturn(0L);
        when(rs.getLong(2)).thenReturn(0L);

        IntraImportDuplicateDetector.check(c, STAGING, CORR, IntraDuplicatePolicy.FAIL);

        verify(countPs).setObject(1, UUID.fromString(CORR));
        // La requete sample ( LIMIT ) ne doit jamais etre preparee si 0 doublon .
        verify(c, never()).prepareStatement(IntraImportDuplicateDetector.sampleSql(STAGING, true));
    }

    @Test
    @DisplayName("FAIL + doublons : leve IntraImportDuplicateException avec le detail")
    void failThrowsWithDetail() throws Exception {
        Connection c = stubWithDuplicates();
        assertThatThrownBy(() ->
                IntraImportDuplicateDetector.check(c, STAGING, CORR, IntraDuplicatePolicy.FAIL))
                .isInstanceOf(IntraImportDuplicateException.class)
                .hasMessageContaining("2 clé(s) en double")
                .hasMessageContaining("ligne(s) excédentaire(s)");
    }

    @Test
    @DisplayName("WARN + doublons : trace mais ne leve pas ( meme resultat qu'avant )")
    void warnDoesNotThrow() throws Exception {
        Connection c = stubWithDuplicates();
        // Ne doit pas lever : l'UPSERT se deroule ensuite comme avant .
        IntraImportDuplicateDetector.check(c, STAGING, CORR, IntraDuplicatePolicy.WARN);
    }

    /** Connexion mockée renvoyant 2 groupes en doublon ( 3 lignes excédentaires ) + un échantillon . */
    private Connection stubWithDuplicates() throws Exception {
        Connection c = mock(Connection.class);
        PreparedStatement countPs = mock(PreparedStatement.class);
        ResultSet countRs = mock(ResultSet.class);
        when(c.prepareStatement(IntraImportDuplicateDetector.countSql(STAGING, true))).thenReturn(countPs);
        when(countPs.executeQuery()).thenReturn(countRs);
        when(countRs.getLong(1)).thenReturn(2L);
        when(countRs.getLong(2)).thenReturn(3L);

        PreparedStatement samplePs = mock(PreparedStatement.class);
        ResultSet sampleRs = mock(ResultSet.class);
        when(c.prepareStatement(IntraImportDuplicateDetector.sampleSql(STAGING, true))).thenReturn(samplePs);
        when(samplePs.executeQuery()).thenReturn(sampleRs);
        when(sampleRs.next()).thenReturn(true, false);
        when(sampleRs.getString(1)).thenReturn("app-uuid");
        when(sampleRs.getString(2)).thenReturn("communes");
        when(sampleRs.getString(3)).thenReturn("a.b.c");
        when(sampleRs.getString(4)).thenReturn("nom");
        when(sampleRs.getLong(5)).thenReturn(2L);
        return c;
    }
}
