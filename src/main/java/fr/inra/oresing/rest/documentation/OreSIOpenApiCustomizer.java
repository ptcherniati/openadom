package fr.inra.oresing.rest.documentation;

import com.fasterxml.jackson.core.type.TypeReference;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.persistence.JsonRowMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.parameters.Parameter;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

@Slf4j
@Component
    public class OreSIOpenApiCustomizer implements OpenApiCustomizer {

    private static final String TARGET_PARAMETER_NAME = "downloadDatasetQuery";
    public static final String DOCUMENTATION_OPENAPI_EXAMPLES_JSON = "/documentation/openapi-examples.json";
    private static final String AUTHORIZATION_OPENAPI_DOC_FILE = "documentation/openapi-authorization.json";
    private final JsonRowMapper mapper;

    public OreSIOpenApiCustomizer(JsonRowMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void customise(OpenAPI openApi) {
        // Parcourt tous les paths et toutes les opérations (GET, POST, etc.)
        openApi.getPaths().values().forEach(pathItem ->
                pathItem.readOperations().forEach(operation -> {

                    if (operation.getParameters() == null) {
                        return;
                    }
                    applyOperationDocumentation(operation);
                    operation.getParameters().stream()
                            .filter(p -> TARGET_PARAMETER_NAME.equals(p.getName()))
                            .findFirst()
                            .ifPresent(this::addExamplesToParameter);
                })
        );

    }


    private void applyOperationDocumentation(Operation operation) {
        String operationId = operation.getOperationId();
        final Map<String, Operation> authorizationOperationsDoc = loadAuthorizationDocumentation();
        if (operationId != null && authorizationOperationsDoc.containsKey(operationId)) {
            Operation docSource = authorizationOperationsDoc.get(operationId);

            // Fusionne les informations du fichier JSON dans l'opération existante
            if (StringUtils.isNotBlank(docSource.getSummary())) {
                operation.setSummary(docSource.getSummary());
            }
            if (StringUtils.isNotBlank(docSource.getDescription())) {
                operation.setDescription(docSource.getDescription());
            }
            if (docSource.getResponses() != null) {
                docSource.getResponses().forEach(operation.getResponses()::put);
            }
            // On pourrait ajouter d'autres champs à fusionner ici (tags, etc.)
        }
    }

    @PostConstruct
    public Map<String, Operation> loadAuthorizationDocumentation() {
        try {
            ClassPathResource resource = new ClassPathResource(AUTHORIZATION_OPENAPI_DOC_FILE);
            try (InputStream is = resource.getInputStream()) {
                final Map<String, Operation> authorizationOperationsDoc = mapper.getJsonMapper().readValue(is, new TypeReference<Map<String, Operation>>() {
                });
                log.info("Loaded documentation for {} operations from {}", authorizationOperationsDoc.size(), AUTHORIZATION_OPENAPI_DOC_FILE);
                return authorizationOperationsDoc;
            }
        } catch (IOException e) {
            log.error("Failed to load OpenAPI authorization documentation from {}: {}", AUTHORIZATION_OPENAPI_DOC_FILE, e.getMessage());
        }
        return null;
    }

    private void addExamplesToParameter(Parameter parameter)  {
        if (parameter.getExamples() == null || parameter.getExamples().isEmpty()) {
            try {
                loadExamplesFromJson().entrySet()
                        .forEach((entry) -> parameter.addExample(entry.getKey(), entry.getValue()));
            } catch (IOException e) {
                throw new OreSiTechnicalException("Erreur de chargement des exemples OpenAPI", e);
            }
        }
    }
    private Map<String, Example> loadExamplesFromJson() throws IOException {
        Resource resource = new ClassPathResource(DOCUMENTATION_OPENAPI_EXAMPLES_JSON);

        try (InputStream inputStream = resource.getInputStream()) {
            return mapper.getJsonMapper().readValue(
                    inputStream,
                    new TypeReference<Map<String, Example>>() {});
        }
    }
}