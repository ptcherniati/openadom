package fr.inra.oresing.rest;

import fr.inra.oresing.rest.reactive.ReactiveResult;
import fr.inra.oresing.rest.usecases.application.GetApplicationUseCase;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.util.Locale;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "Bearer Authentication")
public class BundleResources {

    private final GetApplicationUseCase getApplicationUseCase;

    public BundleResources(GetApplicationUseCase getApplicationUseCase) {
        this.getApplicationUseCase = getApplicationUseCase;
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_APPLICATION_MODIFY')")
    @GetMapping(value = "/applications/{nameOrId}/upload-bundle")
    public ResponseEntity<?> getUploadBundle(
            @PathVariable("nameOrId") String nameOrId,
            @RequestParam(value = "withData", required = false, defaultValue = "false") boolean withData,
            @RequestParam(value = "locale", required = false) Locale locale,
            HttpServletRequest request) {
        // Simplified version - placeholder for full implementation
        return ResponseEntity.ok().build();
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_DOWNLOAD_BUNDLE')")
    @PostMapping(value = "/applications/{nameOrId}/download-bundle", produces = MediaType.APPLICATION_NDJSON_VALUE)
    public Flux<ReactiveResult> uploadBundle(
            HttpServletRequest request,
            @PathVariable String nameOrId,
            @RequestParam("file") MultipartFile zipBundle) {
        // Simplified version - placeholder for full implementation
        return Flux.empty();
    }
}
