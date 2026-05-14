package fr.inra.oresing.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;

/**
 * Second {@link javax.sql.DataSource} dedicated to download endpoints
 * ( CSV , ZIP , charte , additional files ) which keep a JDBC cursor
 * open for the whole HTTP transfer . Isolating these long-held
 * connections from the main pool prevents a slow client from starving
 * cascade workers + API + schedulers .
 *
 * <p>Both pools point to the same Postgres server and database ; only
 * the Hikari tuning differs :
 *
 * <ul>
 *   <li><b>main</b> : connection-init-sql posts a 30 min statement
 *   timeout and a 5 min idle_in_transaction timeout , leak detection
 *   is enabled in preprod for diagnostic purposes ;</li>
 *   <li><b>streaming</b> ( this class ) : statement_timeout and
 *   idle_in_transaction_session_timeout raised to 6 hours , leak
 *   detection disabled by design ( cursors that stay open during a
 *   client download are not leaks ) .</li>
 * </ul>
 *
 * <p>The bean is exposed under the qualifier {@code streamingDataSource}
 * and accompanied by ready-to-use {@link JdbcTemplate} and
 * {@link NamedParameterJdbcTemplate} instances . Production code that
 * wants to stream from this pool must inject those qualified beans
 * explicitly ; the default {@code @Primary} {@link JdbcTemplate}
 * remains wired to the main pool .
 *
 * @author R.YAHIAOUI
 * @since AUDIT 06-05-26 streaming pool isolation
 */
@Configuration
public class StreamingDataSourceConfig {

    /**
     * URL / credentials sourced from {@code spring.datasource.streaming.*}
     * which mirror the main {@code spring.datasource.*} but allow
     * override should we ever want to point streaming at a replica .
     */
    @Bean(name = "streamingDataSourceProperties")
    @ConfigurationProperties("spring.datasource.streaming")
    public DataSourceProperties streamingDataSourceProperties() {
        return new DataSourceProperties();
    }

    /**
     * Builds the streaming Hikari pool from
     * {@code spring.datasource.streaming.hikari.*} . Pool-name is
     * preset so the Micrometer metric tag {@code pool="streamingPool"}
     * is distinguishable from the main {@code "HikariPool-1"} .
     */
    @Bean(name = "streamingDataSource")
    @ConfigurationProperties("spring.datasource.streaming.hikari")
    public DataSource streamingDataSource(
            @org.springframework.beans.factory.annotation.Qualifier("streamingDataSourceProperties")
            DataSourceProperties props) {
        return props.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }

    @Bean(name = "streamingJdbcTemplate")
    public JdbcTemplate streamingJdbcTemplate(
            @org.springframework.beans.factory.annotation.Qualifier("streamingDataSource")
            DataSource ds) {
        return new JdbcTemplate(ds);
    }

    @Bean(name = "streamingNamedJdbcTemplate")
    public NamedParameterJdbcTemplate streamingNamedJdbcTemplate(
            @org.springframework.beans.factory.annotation.Qualifier("streamingDataSource")
            DataSource ds) {
        return new NamedParameterJdbcTemplate(ds);
    }
}