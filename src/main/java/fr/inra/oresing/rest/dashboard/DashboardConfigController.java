package fr.inra.oresing.rest.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only admin endpoint exposing the cascade + openADOM runtime
 * configuration. Consumed by the oa-live "Configuration" page so an
 * operator can answer common questions without ssh-ing on the host
 * ( chunk size , parallelism , rate-limit quotas , cascade version ,
 * virtual threads on/off , etc. ).
 *
 * <p>Phase 3 dashboard ( issue #62 - plan D ).
 */
@RestController
@RequestMapping("/api/dashboard/config")
@RequiredArgsConstructor
@Tag(name = "Dashboard Configuration",
     description = "Read-only runtime configuration ( admin only )")
@SecurityRequirement(name = "bearerAuth")
public class DashboardConfigController {

    private final DashboardService service;

    @Operation(
        summary = "Get cascade + openADOM runtime configuration",
        description = "Returns the values currently used by the import pipeline "
                + "( chunkSize , parallelism , progressBatchSize , maxErrors ) , "
                + "the rate-limit quotas , and the runtime state ( cascade version , "
                + "Java version , virtual threads on/off , active workflow count ). "
                + "Reserved to admin users.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Runtime configuration",
            content = @Content(schema = @Schema(implementation = DashboardConfigDTO.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "User is not an admin")
    })
    @GetMapping
    public ResponseEntity<DashboardConfigDTO> getConfig() {
        return ResponseEntity.ok(service.getConfig());
    }
}
