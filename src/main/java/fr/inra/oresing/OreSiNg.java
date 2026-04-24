package fr.inra.oresing;

import fr.inra.oresing.persistence.flyway.MigrateService;
import fr.inra.oresing.rest.JsonRequestParamArgumentResolver;
import fr.inra.oresing.rest.filesenderclient.FileRepository;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import lombok.extern.slf4j.Slf4j;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.actuate.info.InfoContributor;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.info.GitProperties;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Properties;
import java.util.concurrent.Executor;

@Slf4j
@EnableWebMvc
@OpenAPIDefinition(
        servers = @io.swagger.v3.oas.annotations.servers.Server(url = "${springdoc.swagger-ui.server-url}", description = "Server URL")
)

@SpringBootApplication(scanBasePackages = "fr.inra.oresing")
@EnableScheduling
public class OreSiNg implements WebMvcConfigurer {

    private final MigrateService migrate;
    @Value("${allowed.origin}")
    private String allowedOrigin;
    private final JsonRequestParamArgumentResolver jsonRequestParamArgumentResolver;
    private final Executor normalServiceExecutor;

    public OreSiNg(MigrateService migrate,
                   JsonRequestParamArgumentResolver jsonRequestParamArgumentResolver,
                   @Qualifier("normalServiceExecutor") Executor normalServiceExecutor) {
        this.migrate = migrate;
        this.jsonRequestParamArgumentResolver = jsonRequestParamArgumentResolver;
        this.normalServiceExecutor = normalServiceExecutor;
    }

    public static void main(final String[] args) {
        SpringApplication.run(OreSiNg.class, args);
    }


    // ✅ Implémenter ici directement
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        log.info("=== Adding JsonRequestParamArgumentResolver ===");
        resolvers.addFirst(jsonRequestParamArgumentResolver);
        log.info("=== Total resolvers: " + resolvers.size() + " ===");
    }
    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/webjars/**").addResourceLocations("classpath:/META-INF/resources/webjars/");
        registry
                .addResourceHandler("/static/**")
                .addResourceLocations("file://" + new File(".").getAbsolutePath() + "/src/main/resources/web/", "classpath:/web/")
                .setCachePeriod(0)
                .resourceChain(false)
                .addResolver(new PathResourceResolver());
    }

    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setTaskExecutor(new ConcurrentTaskExecutor(normalServiceExecutor));
    }

    @EventListener(ApplicationReadyEvent.class)
    public void migrateFlywayDataBases() {
        migrate.migrateAll();
    }

    @Bean
    public MessageSource emailMessageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasenames("emailMessage"); // Nom de base des fichiers de propriétés
        messageSource.setDefaultEncoding("UTF-8");
        return messageSource;
    }

    @Configuration
    public static class GitInfoConfig {

        @Bean
        @ConditionalOnMissingBean
        public GitProperties gitProperties() throws IOException {
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
    public static class GitInfoContributor implements InfoContributor {

        private final GitProperties gitProperties;

        public GitInfoContributor(GitProperties gitProperties) {
            this.gitProperties = gitProperties;
        }

        @Override
        public void contribute(org.springframework.boot.actuate.info.Info.Builder builder) {
            builder.withDetail("git", buildGitInfo());
        }

        private Object buildGitInfo() {
                org.springframework.boot.actuate.info.Info.Builder infoBuilder = new org.springframework.boot.actuate.info.Info.Builder();
            infoBuilder.withDetail("branch", Optional.ofNullable(gitProperties.get("git.branch")).orElse(""));
            infoBuilder.withDetail("commit.id", Optional.ofNullable(gitProperties.get("git.commit.id")).orElse(""));
            infoBuilder.withDetail("commit.abbrev", Optional.ofNullable(gitProperties.get("git.commit.id.abbrev")).orElse(""));
            infoBuilder.withDetail("time", Optional.ofNullable(gitProperties.get("git.commit.time")).orElse(""));
            infoBuilder.withDetail("remote.origin.url", Optional.ofNullable(gitProperties.get("git.remote.origin.url")).orElse(""));
            infoBuilder.withDetail("commit.user.name", Optional.ofNullable(gitProperties.get("git.commit.user.name")).orElse(""));
            infoBuilder.withDetail("commit.user.email", Optional.ofNullable(gitProperties.get("git.commit.user.email")).orElse(""));
            infoBuilder.withDetail("commit.message.short", Optional.ofNullable(gitProperties.get("git.commit.message.short")).orElse(""));
            infoBuilder.withDetail("commit.message.full", Optional.ofNullable(gitProperties.get("git.commit.message.full")).orElse(""));
            return infoBuilder.build();
        }
    }


    @Configuration
    @SecurityScheme(
            name = "Bearer Authentication",
            type = SecuritySchemeType.HTTP,
            bearerFormat = "JWT",
            scheme = "bearer"
    )
    public static class OpenApiConfig {

        @Value("${allowed.origin}")
        private String allowedOrigin;

        @Bean
        public OpenAPI customOpenAPI() {
            log.info("demarrage de open api");
            log.info("Allowed Origin: %1$s".formatted(allowedOrigin));
            return new OpenAPI()
                    .info(new Info()
                            .title("openadom-ng")
                            .version("1.0")
                            .description("Api Rest pour le stockage et la restitution de fichier CSV"))
                    .servers(List.of(new Server().url(allowedOrigin)));
        }
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
            return _ -> "mockedWebAddress";
        }
    }
}