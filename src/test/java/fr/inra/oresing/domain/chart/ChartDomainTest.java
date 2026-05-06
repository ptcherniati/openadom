package fr.inra.oresing.domain.chart;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
@Tag("domain.model")
@DisplayName("Chart domain - Chart record et OreSiSynthesis")
class ChartDomainTest {
    @Test
    @DisplayName("Chart.toSQL genere le SQL attendu")
    void chartToSql_generatesSqlWithDataName() {
        String sql = Chart.toSQL("myDataType");
        assertThat(sql).contains("'myDataType'");
    }
    @Test
    @DisplayName("Chart record - accesseurs")
    void chartRecord_accessors() {
        Chart chart = new Chart("value1", "aggComp", "unitComp", "stdDevComp", "1 day", "Mon titre");
        assertThat(chart.value()).isEqualTo("value1");
        assertThat(chart.aggregationComponent()).isEqualTo("aggComp");
        assertThat(chart.unitComponent()).isEqualTo("unitComp");
        assertThat(chart.standardDeviationComponent()).isEqualTo("stdDevComp");
        assertThat(chart.gap()).isEqualTo("1 day");
        assertThat(chart.title()).isEqualTo("Mon titre");
    }
    @Test
    @DisplayName("Chart.VAR_SQL_TEMPLATE contient les placeholders")
    void chartVarSqlTemplate_containsPlaceholders() {
        assertThat(Chart.VAR_SQL_TEMPLATE).contains("%1$s").contains("%2$s").contains("%3$s").contains("%4$s");
    }
    @Test
    @DisplayName("Chart.VAR_SQL_DEFAULT_TEMPLATE contient le placeholder")
    void chartVarSqlDefaultTemplate_containsPlaceholder() {
        assertThat(Chart.VAR_SQL_DEFAULT_TEMPLATE).contains("%s");
    }
    @Test
    @DisplayName("Chart record equals / hashCode / toString")
    void chartRecord_equalsHashCodeToString() {
        Chart c1 = new Chart("v", "agg", "unit", "std", "gap", "title");
        Chart c2 = new Chart("v", "agg", "unit", "std", "gap", "title");
        assertThat(c1).isEqualTo(c2);
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
        assertThat(c1.toString()).isNotBlank();
    }
    @Test
    @DisplayName("OreSiSynthesis - getters/setters Lombok")
    void oreSiSynthesis_gettersSetters() {
        OreSiSynthesis s = new OreSiSynthesis();
        UUID appId = UUID.randomUUID();
        s.setApplication(appId);
        s.setDatatype("datatypeName");
        s.setVariable("myVar");
        s.setRequiredAuthorizations(Map.of("k", "v"));
        s.setAggregation("sum");
        LocalDateTimeRange range = LocalDateTimeRange.always();
        s.setRanges(List.of(range));
        assertThat(s.getApplication()).isEqualTo(appId);
        assertThat(s.getDatatype()).isEqualTo("datatypeName");
        assertThat(s.getVariable()).isEqualTo("myVar");
        assertThat(s.getRequiredAuthorizations()).containsEntry("k", "v");
        assertThat(s.getAggregation()).isEqualTo("sum");
        assertThat(s.getRanges()).hasSize(1);
    }
    @Test
    @DisplayName("OreSiSynthesis - toString ne leve pas d exception")
    void oreSiSynthesis_toStringNoException() {
        OreSiSynthesis s = new OreSiSynthesis();
        s.setDatatype("test");
        assertThat(s.toString()).isNotBlank();
    }
}