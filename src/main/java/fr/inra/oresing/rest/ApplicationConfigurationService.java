package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.type.CheckerEnum;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.rest.model.configuration.builder.ConfigurationBuilder;
import fr.inra.oresing.rest.reactive.ReactiveProgression;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
@Slf4j
public class ApplicationConfigurationService {
    private static final ImmutableSet<CheckerEnum> CHECKER_ON_TARGET_NAMES =
            ImmutableSet.of(CheckerEnum.OA_date, CheckerEnum.OA_integer, CheckerEnum.OA_float, CheckerEnum.OA_string, CheckerEnum.OA_reference);
    private static final ImmutableSet<CheckerEnum> ALL_CHECKER_NAMES = ImmutableSet.<CheckerEnum>builder()
            .addAll(CHECKER_ON_TARGET_NAMES)
            .add(CheckerEnum.OA_groovyExpression)
            .build();

    static Application unzipConfiguration(final MultipartFile file, ReactiveProgression.CreateApplicationProgression fluxSink) throws IOException {
        InputStream inputStream = MultiYaml.parseConfigurationBytes(file);
        return ApplicationConfigurationService.parseConfigurationBytes(null,
                fluxSink,
                FileBomResolver.of(inputStream));
}

    static <P extends ReactiveProgression.ChangeOrCreateApplicationProgression> Application parseConfigurationBytes(final
                                                                                                                    String comment,
                                                                                                                    P progression,
                                                                                                                    final FileBomResolver fileBomResolver) {
        progression.pushMessage("testYamlIsvalid", null);
        try {
            byte[] bytes = fileBomResolver.readAllBytes();

            if (bytes.length == 0) {
                progression.pushError(ConfigurationException.EMPTY_FILE, Map.of());
                progression.complete();
                return null;
            }
            progression.pushMessage("yamlIsvalid", null);
            progression.pushMessage("versionIsValid", null);
            P progression1 = (P) progression.incrementAndPush(i -> i + 0.01D);

            final Configuration configuration;
            configuration = ConfigurationBuilder.build(bytes, progression1, comment);
            final ReactiveProgression.ChangeOrCreateApplicationProgression progressionForCheckSyntax = (ReactiveProgression.ChangeOrCreateApplicationProgression) progression1.withSubLabel("CheckSyntax");
            if (configuration == null) {
                progression1.complete();
                return null;
            }
            final Application application = getConfigurationParsingResultForSyntacticallyValidYaml(progressionForCheckSyntax, configuration);
            return application;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static <P extends ReactiveProgression.ChangeOrCreateApplicationProgression> Application getConfigurationParsingResultForSyntacticallyValidYaml(final P progression, final Configuration configuration) {
        final Application application = new Application();

        final List<String> data = new ArrayList<>(configuration.dataDescription().keySet());
        application.setName(configuration.applicationDescription().name());
        application.setData(data);
        application.setConfiguration(configuration);
        Optional.ofNullable(configuration.additionalFiles())
                .map(Map::keySet)
                .ifPresentOrElse(
                        af -> application.setAdditionalFiles(new LinkedList<>(af)),
                        () -> application.setAdditionalFiles(List.of())
                );
        final String applicationName = configuration.applicationDescription().name();
        final ReactiveProgression.ChangeOrCreateApplicationProgression progressionValidation = (ReactiveProgression.ChangeOrCreateApplicationProgression) progression.withSubLabel("startValidation");
        progressionValidation.pushMessage("start", Map.of("applicationName", applicationName));
        return application;
    }


}