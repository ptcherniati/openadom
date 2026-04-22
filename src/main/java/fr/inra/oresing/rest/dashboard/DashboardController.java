package fr.inra.oresing.rest.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST endpoints consumed by the oa-live real-time dashboard
 * ( https://forge.inrae.fr/anaee-dev/openadom/oa-live ).
 *
 * <p>Three endpoints , documented in Swagger under the tag "Dashboard" :
 * <ul>
 *   <li>GET /api/dashboard/workflows/in-progress</li>
 *   <li>GET /api/dashboard/workflows/history</li>
 *   <li>GET /api/dashboard/workflows/{correlationId}</li>
 * </ul>
 *
 * <p>All endpoints apply row-level authorisation : an authenticated user
 * with the role {@code openAdomAdmin} sees every workflow , every other
 * authenticated user sees only the ones they initiated.
 *
 * <p>Phase 3 dashboard ( issue #62 ).
 */
@RestController
@RequestMapping("/api/dashboard/workflows")
@RequiredArgsConstructor
@Tag(name = "Dashboard",
     description = "Endpoints consumed by the oa-live real-time workflow dashboard")
@SecurityRequirement(name = "bearerAuth")
public class DashboardController {

    private final DashboardService service;

    @Operation(
        summary = "List in-progress workflows",
        description = "Returns every workflow currently running ( imports + extractions ). "
                + "Admin users get the full list ; non-admin users only see their own workflows. "
                + "Snapshots come from the in-memory WorkflowActiveRegistry , refreshed by the "
                + "backend orchestrators on every progress event.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "List of active workflows ( possibly empty )",
            content = @Content(schema = @Schema(implementation = DashboardWorkflowDTO.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "403", description = "User not authorised")
    })
    @GetMapping("/in-progress")
    public ResponseEntity<List<DashboardWorkflowDTO>> inProgress() {
        return ResponseEntity.ok(service.listInProgress());
    }

    @Operation(
        summary = "Paginated workflow history",
        description = "Paginated list of finished workflows from oa_metrics.workflow_log. "
                + "Admin users see every row ; non-admin users are filtered to their own "
                + "workflows on the SQL side. Sorted by start_time DESC.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page of history rows",
            content = @Content(schema = @Schema(implementation = DashboardWorkflowDTO.Page.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @GetMapping("/history")
    public ResponseEntity<DashboardWorkflowDTO.Page> history(
            @Parameter(description = "Max number of rows returned ( 1-500 , default 100 )")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Offset in the sorted result set ( default 0 )")
            @RequestParam(required = false) Integer offset,
            @Parameter(description = "Filter on workflow_type ( IMPORT , EXTRACT_ZIP , ... )")
            @RequestParam(required = false) String type,
            @Parameter(description = "Filter on status ( COMPLETED , FAILED , CANCELLED , RATE_LIMITED )")
            @RequestParam(required = false) String status,
            @Parameter(description = "Partial match on application_name ( ILIKE %..% )")
            @RequestParam(required = false) String app,
            @Parameter(description = "Partial match on user_login OR user_id ( ILIKE %..% )")
            @RequestParam(required = false) String user) {
        return ResponseEntity.ok(
                service.listHistory(limit, offset, type, status, app, user));
    }

    @Operation(
        summary = "Workflow detail",
        description = "Full detail of a single workflow , identified by its correlation id. "
                + "Looks up the in-memory registry first ( live data for running workflows ) , "
                + "falls back on oa_metrics.workflow_log when the workflow has finished. "
                + "Non-admin users can only fetch their own workflows ; others return 404 on "
                + "purpose to prevent id enumeration.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Workflow detail",
            content = @Content(schema = @Schema(implementation = DashboardWorkflowDTO.Detail.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "404", description = "No workflow with this correlation id , or not visible to this user")
    })
    @GetMapping("/{correlationId}")
    public ResponseEntity<DashboardWorkflowDTO.Detail> detail(
            @Parameter(description = "Correlation id of the workflow ( UUID )")
            @PathVariable UUID correlationId) {
        return service.findDetail(correlationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
