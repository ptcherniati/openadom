package fr.inra.oresing.rest.model.data;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;
import org.testcontainers.shaded.com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Fixture {
    public static final String DATATYPE = "pem";
    public static final Resource yaml = new ClassPathResource("data/configuration/data.configuration.monsore.json");

    public static DownloadDatasetQuery addApplication(final DownloadDatasetQuery downloadDatasetQuery) throws IOException {
        final InputStream yamlContent = yaml.getInputStream();
        final YAMLMapper mapper = new YAMLMapper();
        final Configuration configuration = new JsonRowMapper<>()
                .readStream(yamlContent, Configuration.class);
        final ImmutableSet.Builder<String> requiredAuthorizationsAttributesBuilder = ImmutableSet.builder();

        for (final Map.Entry<String, StandardDataDescription> dataTypeEntry : configuration.dataDescription().entrySet()) {
            Optional.ofNullable(dataTypeEntry.getValue())
                    .map(StandardDataDescription::submission)
                    .map(Submission::submissionScope)
                    .ifPresent(authorization -> requiredAuthorizationsAttributesBuilder.addAll(authorization.componentNames()));
        }
        configuration.requiredAuthorizationsAttributes().clear();
        configuration.requiredAuthorizationsAttributes()
                .addAll(List.copyOf(requiredAuthorizationsAttributesBuilder.build()));
        final Application application = new Application();
        application.setConfiguration(configuration);
        downloadDatasetQuery.setApplication(application);
        downloadDatasetQuery.setDataName(DATATYPE);
        return downloadDatasetQuery;

    }
}