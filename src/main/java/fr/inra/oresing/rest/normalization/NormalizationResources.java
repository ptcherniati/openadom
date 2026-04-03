package fr.inra.oresing.rest.normalization;

import fr.inra.oresing.rest.usecases.metadata.BuildNormalizedSchemaUseCase;
import fr.inra.oresing.rest.usecases.metadata.GetNormalizedSchemaUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api/v1")
public class NormalizationResources {
    private final BuildNormalizedSchemaUseCase buildNormalizedSchemaUseCase;
    private final GetNormalizedSchemaUseCase getNormalizedSchemaUseCase;

    public NormalizationResources(
            BuildNormalizedSchemaUseCase buildNormalizedSchemaUseCase,
            GetNormalizedSchemaUseCase getNormalizedSchemaUseCase) {
        this.buildNormalizedSchemaUseCase = buildNormalizedSchemaUseCase;
        this.getNormalizedSchemaUseCase = getNormalizedSchemaUseCase;
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD')")
    @PostMapping(value = "/applications/{nameOrId}/normalized",produces = {MediaType.TEXT_PLAIN_VALUE})
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<String> buildNormalizedSchema(@PathVariable("nameOrId") final String nameOrId) throws ExecutionException, InterruptedException {
        final String sql = buildNormalizedSchemaUseCase.execute(nameOrId);
        return ResponseEntity.ok(sql);
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_AUTHORIZATION_MANAGEMENT_FOR_ADD')")
    @GetMapping(value = "/applications/{nameOrId}/normalized",produces = {MediaType.TEXT_PLAIN_VALUE})
    @SecurityRequirement(name = "Bearer Authentication")
    public ResponseEntity<String> getNormalizedSchema(@PathVariable("nameOrId") final String nameOrId) throws ExecutionException, InterruptedException {
        final String sql = getNormalizedSchemaUseCase.execute(nameOrId);
        return ResponseEntity.ok(sql);
    }


}