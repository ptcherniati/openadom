package fr.inra.oresing;

import fr.inra.oresing.persistence.flyway.MigrateService;
import fr.inra.oresing.rest.OreSiHandler;
import fr.inra.oresing.rest.filesenderclient.FileInfos;
import fr.inra.oresing.rest.filesenderclient.FileRepository;
import fr.inra.oresing.rest.filesenderclient.FileSenderRepository;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.info.GitInfoContributor;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.actuate.info.InfoPropertiesInfoContributor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.Properties;

@Slf4j
@EnableWebMvc
@SpringBootApplication(scanBasePackages = "fr.inra.oresing")
public class OreSiNg implements WebMvcConfigurer {

    @Value("${allowed.origin}")
    private String allowedOrigin;

    public static void main(final String[] args) {
        SpringApplication.run(OreSiNg.class, args);
    }

    @Autowired
    private OreSiHandler oreSiHandler;
    @Autowired
    private MigrateService migrate;

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        //registry.addResourceHandler("swagger-ui.html").addResourceLocations("classpath:/META-INF/resources/");
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
        registry
                .addResourceHandler("/static/**")
                .addResourceLocations("file://" + new File(".").getAbsolutePath() + "/src/main/resources/web/", "classpath:/web/")
                .setCachePeriod(0)
                .resourceChain(false)
                .addResolver(new PathResourceResolver());
    }
    @Configuration
    public class GitInfoConfig {

        @Bean
        @ConditionalOnMissingBean
        public GitProperties gitProperties() throws Exception {
            Properties properties = new Properties();
            Resource resource = new ClassPathResource("git.properties");
            if (resource.exists()) {
                try (InputStream inputStream = resource.getInputStream()) {
                    properties.load(inputStream);
                }
            }
            return new GitProperties(properties);
        }
    }

    @Component
    public class GitInfoContributor implements InfoContributor {

        private final GitProperties gitProperties;

        public GitInfoContributor(GitProperties gitProperties) {
            this.gitProperties = gitProperties;
        }

        @Override
        public void contribute(org.springframework.boot.actuate.info.Info.Builder builder) {
            builder.withDetail("git", buildGitInfo());
        }

        private Object buildGitInfo() {
            return new org.springframework.boot.actuate.info.Info.Builder()
                    .withDetail("branch", gitProperties.get("git.branch"))
                    .withDetail("commit.id", gitProperties.get("git.commit.id"))
                    .withDetail("commit.abbrev", gitProperties.get("git.commit.id.abbrev"))
                    .withDetail("time", gitProperties.get("git.commit.time"))
                    .withDetail("remote.origin.url", gitProperties.get("git.remote.origin.url"))
                    .withDetail("commit.user.name", gitProperties.get("git.commit.user.name"))
                    .withDetail("commit.user.email", gitProperties.get("git.commit.user.email"))
                    .withDetail("commit.message.short", gitProperties.get("git.commit.message.short"))
                    .withDetail("commit.message.full", gitProperties.get("git.commit.message.full"))
                    .build();
        }
    }

    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:8080")
                .allowedOrigins(allowedOrigin)
                .allowedMethods("POST", "PUT", "GET", "DELETE")
                .allowCredentials(true);
    }

    @Configuration
    public class OpenApiConfig {

        @Value("${allowed.origin}")
        private String allowedOrigin;

        @Bean
        public OpenAPI customOpenAPI() {
            System.out.println("demarrage de open api");
            System.out.println("Allowed Origin: " + allowedOrigin);
            return new OpenAPI()
                    .info(new Info()
                            .title("openadom-ng")
                            .version("1.0")
                            .description("Api Rest pour le stockage et la restitution de fichier CSV"))
                    .servers(List.of(new Server().url(allowedOrigin)));
        }
    }

    @Override
    public void addInterceptors(final InterceptorRegistry registry) {
        registry.addInterceptor(oreSiHandler);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateFlywayDataBases() {
        migrate.migrateAll();
    }

    @Profile("testmail")
    @Configuration
    public static class MailSenderForTest {
        @Bean("mailSender")
        public JavaMailSender mailSender() {
            return Mockito.mock(JavaMailSender.class);
        }

        @Bean
        public FileRepository fileRepository() {
            return fileInfos -> "mockedWebAddress";
        }
    }

    @Bean
    public ThreadPoolTaskExecutor mvcTaskExecutor() {
        final ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(10);
        taskExecutor.setMaxPoolSize(10);
        return taskExecutor;
    }

    public void configureAsyncSupport(final AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(mvcTaskExecutor());
    }

}
