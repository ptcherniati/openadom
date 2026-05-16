package fr.inra.oresing.rest.services;

import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.type.CheckerEnum;
import fr.inra.oresing.domain.data.DataFile;
import fr.inra.oresing.domain.exceptions.ExceptionMessage;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.configuration.ConfigurationException;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.rest.MultiYaml;
import fr.inra.oresing.rest.model.configuration.builder.ConfigurationBuilder;
import fr.inra.oresing.rest.reactive.ReactiveEventHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

@Component
@Slf4j
public class ApplicationConfigurationService {
    private static final ImmutableSet<CheckerEnum> CHECKER_ON_TARGET_NAMES =
            ImmutableSet.of(CheckerEnum.OA_date, CheckerEnum.OA_integer, CheckerEnum.OA_float, CheckerEnum.OA_string, CheckerEnum.OA_reference);

    static {
        ImmutableSet.<CheckerEnum>builder()
                .addAll(CHECKER_ON_TARGET_NAMES)
                .add(CheckerEnum.OA_groovyExpression)
                .build();
    }

    private ApplicationConfigurationService() {
    }

    public static Application unzipConfiguration(final DataFile file, ReactiveEventHelper eventHelper, long maxBytesAllowed) throws IOException {
        InputStream inputStream = MultiYaml.parseConfigurationBytes(file, maxBytesAllowed);
        return ApplicationConfigurationService.parseConfigurationBytes(
                "", "",
                eventHelper,
                FileBomResolver.of(inputStream));
    }

    public static Application parseConfigurationBytes(
            final String applicationName,
            final String comment,
            ReactiveEventHelper eventHelper,
            final FileBomResolver fileBomResolver) {
        eventHelper.pushMessage("testYamlIsvalid", null);
        try {

            if (fileBomResolver.markSupported()) {
                fileBomResolver.mark(1);
                int firstByte = fileBomResolver.read();
                if (firstByte == -1) {
                    eventHelper.pushError(ConfigurationException.EMPTY_FILE, Map.of());
                    eventHelper.complete();
                    return null;
                }
                fileBomResolver.reset(); // On revient au début pour tout relire
            }

            eventHelper.pushMessage("yamlIsvalid", null);
            eventHelper.pushMessage("versionIsValid", null);
            eventHelper.incrementAndPush(i -> i + 0.01D);

            final Configuration configuration;
            configuration = ConfigurationBuilder.build(fileBomResolver, eventHelper, comment);
            final ReactiveEventHelper helperForCheckSyntax = eventHelper.withSubLabel("CheckSyntax");
            if (configuration == null) {
                eventHelper.complete();
                return null;
            }
            return getConfigurationParsingResultForSyntacticallyValidYaml(helperForCheckSyntax, configuration);
        } catch (IOException e) {
            throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage());
        }
    }

    private static Application getConfigurationParsingResultForSyntacticallyValidYaml(final ReactiveEventHelper eventHelper, final Configuration configuration) {
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
        final ReactiveEventHelper helperValidation = eventHelper.withSubLabel("startValidation");
        helperValidation.pushMessage("start", Map.of("applicationName", applicationName));
        application.setVersion(application.getConfiguration().applicationDescription().version().version());
        return application;
    }
}