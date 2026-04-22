package fr.inra.oresing.persistence;

import com.zaxxer.hikari.HikariDataSource;
import fr.inra.oresing.fileprocessor.workflow.config.WorkflowProperties;
import fr.inra.oresing.fileprocessor.workflow.control.orchestration.WorkflowLifecycleManager;
import fr.inra.oresing.fileprocessor.workflow.control.processing.LoaderService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.concurrent.ExecutorService;

/**
 * Déclaration du second pool de connexions DB (workflowDataSource) dédié au
 * pipeline file-processor (imports, workers, loader).
 *
 * <p>Le pool par défaut (HTTP) reste auto-configuré par Spring Boot à partir
 * de {@code spring.datasource.*}. Ce pool-ci est configuré via
 * {@code app.datasource.workflow.*} (cf. application.properties).
 *
 * <p>Redéclare également {@link LoaderService} comme bean explicite pour
 * forcer l'injection du {@code workflowJdbcTemplate} (et non du JdbcTemplate
 * par défaut). {@link LoaderService} est exclu du component-scan dans la
 * classe principale {@code OreSiNg} pour éviter un doublon.
 */
@Configuration
public class DataSourceConfiguration {

    // -------------------------------------------------------------------- //
    //  WorkflowDataSource : pool dédié aux imports / workflows longs       //
    // -------------------------------------------------------------------- //

    /**
     * DataSource Hikari du pool workflow.
     *
     * <p>Les credentials (URL, username, password) sont injectés explicitement
     * depuis {@code app.datasource.workflow.*} via {@code @Value}. Toutes les
     * autres propriétés Hikari (pool size, timeouts, etc.) sont bindées
     * automatiquement depuis {@code app.datasource.workflow.hikari.*} grâce à
     * {@code @ConfigurationProperties}.
     */
    @Bean
    @Qualifier("workflowDataSource")
    @ConfigurationProperties("app.datasource.workflow.hikari")
    public HikariDataSource workflowDataSource(
            @Value("${app.datasource.workflow.url}") final String url,
            @Value("${app.datasource.workflow.username}") final String username,
            @Value("${app.datasource.workflow.password}") final String password) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    // -------------------------------------------------------------------- //
    //  Transaction manager + JdbcTemplates liés au workflowDataSource      //
    // -------------------------------------------------------------------- //

    @Bean("workflowTxManager")
    public PlatformTransactionManager workflowTransactionManager(
            @Qualifier("workflowDataSource") final DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean("workflowJdbcTemplate")
    public JdbcTemplate workflowJdbcTemplate(
            @Qualifier("workflowDataSource") final DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean("workflowNamedParameterJdbcTemplate")
    public NamedParameterJdbcTemplate workflowNamedParameterJdbcTemplate(
            @Qualifier("workflowDataSource") final DataSource dataSource) {
        return new NamedParameterJdbcTemplate(dataSource);
    }

    // -------------------------------------------------------------------- //
    //  Redéclaration de LoaderService (file-processor JAR) pour forcer      //
    //  l'injection du workflowJdbcTemplate au lieu du JdbcTemplate par     //
    //  défaut. LoaderService est @Service dans le JAR ; il est donc exclu  //
    //  de l'auto-scan dans OreSiNg.java pour éviter la double définition.   //
    // -------------------------------------------------------------------- //

    @Bean
    public LoaderService loaderService(
            final WorkflowProperties workflowProperties,
            final WorkflowLifecycleManager workflowLifecycleManager,
            @Qualifier("workflowJdbcTemplate") final JdbcTemplate workflowJdbcTemplate,
            @Qualifier("loaderExecutor") final ExecutorService loaderExecutor) {
        return new LoaderService(
                workflowProperties,
                workflowLifecycleManager,
                workflowJdbcTemplate,
                loaderExecutor);
    }
}
