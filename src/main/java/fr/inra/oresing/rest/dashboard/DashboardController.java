package fr.inra.oresing.rest.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.NoSuchElementException;
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
        description = "Paginated list of finished workflows from oa_audit.workflow_log. "
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
                + "falls back on oa_audit.workflow_log when the workflow has finished. "
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

    @Operation(
        summary = "Live cascade pipeline state",
        description = "Returns the current pipeline snapshot ( source / transform / sink "
                + "workers , queue depths , recent flow events , rolling throughput ) for "
                + "the running workflow . Powered by cascade 1.9.0 push events . Returns "
                + "404 once the workflow has finished ( history endpoint exposes the final "
                + "outcome ) .")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Pipeline snapshot",
            content = @Content(schema = @Schema(implementation = PipelineDTO.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "404", description = "No active workflow with this id , or not visible to this user")
    })
    @GetMapping("/{correlationId}/pipeline")
    public ResponseEntity<PipelineDTO> pipeline(
            @Parameter(description = "Correlation id of the workflow ( UUID )")
            @PathVariable UUID correlationId) {
        return service.pipeline(correlationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Bloc CHARGEMENT FINAL",
        description = "Snapshot temps reel de la phase chargement final ( cascade emit -> "
                + "finalize -> rollback ) pour un workflow en cours . Decompose la duree totale "
                + "en duree cascade ( emit chunks , debit fige a 100 % ) + duree finalize "
                + "( UPSERT staging->final ou COPY merged.csv->final ) + duree rollback . "
                + "Le frontend poll cet endpoint pendant que phase != COMPLETED / ROLLBACK_DONE .")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Snapshot du bloc CHARGEMENT FINAL",
            content = @Content(schema = @Schema(implementation = FinalizeProgressDTO.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT"),
        @ApiResponse(responseCode = "404", description = "No active workflow with this id , or not visible to this user")
    })
    @GetMapping("/{correlationId}/finalize")
    public ResponseEntity<FinalizeProgressDTO> finalizeProgress(
            @Parameter(description = "Correlation id of the workflow ( UUID )")
            @PathVariable UUID correlationId) {
        return service.finalizeProgress(correlationId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Operation(
        summary = "Agregat global CHARGEMENT FINAL",
        description = "Resume des workflows actuellement actifs : combien en finalize , en "
                + "rollback , total rows attendues / arrivees / staging restant , debit "
                + "finalize cumule . Sert au bloc permanent en tete de la page Live . "
                + "Non-admin = ses propres workflows uniquement .")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Agregat ( meme structure quand 0 workflow ; champs a 0 )",
            content = @Content(schema = @Schema(implementation = FinalizeAggregateDTO.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @GetMapping("/finalize/aggregate")
    public ResponseEntity<FinalizeAggregateDTO> finalizeAggregate() {
        return ResponseEntity.ok(service.finalizeAggregate());
    }

    @Operation(
        summary = "Pools cascade en idle ( supervision sans workflow )",
        description = "Retourne la structure courante des pools cascade ( SOURCE / TRANSFORM "
                + "/ SINK / ORDERING ) : parallelism configure , taille queue , profondeur "
                + "courante . Permet a oa-live d'afficher le pipeline avec workers en "
                + "placeholder repos meme quand aucun workflow ne tourne .")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Snapshot pools cascade",
            content = @Content(schema = @Schema(implementation = PipelinePoolsDTO.class))),
        @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    })
    @GetMapping("/cascade/pools")
    public ResponseEntity<PipelinePoolsDTO> cascadePools() {
        return ResponseEntity.ok(service.cascadePools());
    }

    // ---------------------------------------------------------------- //
    //  Sessions ( onglet Sessions de oa-live , admin only )            //
    // ---------------------------------------------------------------- //

    @Operation(
        summary = "Sessions utilisateurs ACTIVE",
        description = "Liste in-memory des sessions ACTIVE ( JWT non expire et "
                + "user pas encore deconnecte ) . Reservee aux admins "
                + "( openAdomAdmin ) ; les autres recoivent 403 .")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Liste des sessions actives",
            content = @Content(schema = @Schema(implementation = SessionDTO.class))),
        @ApiResponse(responseCode = "401", description = "JWT absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Reserve aux admins")
    })
    @GetMapping("/sessions/active")
    public ResponseEntity<List<SessionDTO>> sessionsActive() {
        return ResponseEntity.ok(service.listActiveSessions());
    }

    @Operation(
        summary = "Historique des sessions utilisateurs",
        description = "Pagination sur oa_audit.user_session_log . Filtres "
                + "optionnels : userLogin partial-match , endReason exacte . "
                + "Reservee aux admins .")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Page d'historique",
            content = @Content(schema = @Schema(implementation = SessionDTO.Page.class))),
        @ApiResponse(responseCode = "401", description = "JWT absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Reserve aux admins")
    })
    @GetMapping("/sessions/history")
    public ResponseEntity<SessionDTO.Page> sessionsHistory(
            @Parameter(description = "Max rows ( 1-500 , default 100 )")
            @RequestParam(required = false) Integer limit,
            @Parameter(description = "Offset ( default 0 )")
            @RequestParam(required = false) Integer offset,
            @Parameter(description = "Match ILIKE %x% sur user_login")
            @RequestParam(required = false) String user,
            @Parameter(description = "Filtre exact sur end_reason ( LOGOUT , JWT_EXPIRED , KICK )")
            @RequestParam(required = false) String endReason) {
        return ResponseEntity.ok(service.listSessionsHistory(limit, offset, user, endReason));
    }

    @Operation(
        summary = "Deconnecter une session ( vue dashboard )",
        description = "Marque la session comme KICK dans le registry in-memory et "
                + "ecrit la cloture dans oa_audit.user_session_log . **Cosmetique** : "
                + "le JWT de l'utilisateur reste valide jusqu'a son expiration ; "
                + "l'utilisateur disparait de l'onglet Sessions Active de oa-live "
                + "mais peut continuer a appeler les endpoints API tant que son token "
                + "n'a pas expire . Reservee aux admins ( openAdomAdmin ) .")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Session marquee deconnectee"),
        @ApiResponse(responseCode = "401", description = "JWT absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Reserve aux admins"),
        @ApiResponse(responseCode = "404", description = "Session inconnue ou deja terminee")
    })
    @org.springframework.web.bind.annotation.PostMapping("/sessions/{sessionId}/disconnect")
    public ResponseEntity<Void> disconnectSession(
            @Parameter(description = "Identifiant interne de la session ( UUID )")
            @PathVariable UUID sessionId) {
        try {
            service.disconnectSession(sessionId);
            return ResponseEntity.noContent().build();
        } catch (NoSuchElementException ex) {
            return ResponseEntity.notFound().build();
        }
    }

    @Operation(
        summary = "Lister la blacklist JWT",
        description = "Retourne les tokens JWT actuellement revoques par un kick admin . "
                + "Capacite max 100 ; les tokens expires sont purges automatiquement . "
                + "Reservee aux admins .")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Liste blacklist"),
        @ApiResponse(responseCode = "401", description = "JWT absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Reserve aux admins")
    })
    @GetMapping("/blacklist")
    public ResponseEntity<List<fr.inra.oresing.monitoring.session.JwtBlacklistRegistry.Entry>> listBlacklist() {
        return ResponseEntity.ok(service.listBlacklist());
    }

    @Operation(
        summary = "Retirer une entree blacklist",
        description = "Annule un kick admin : le JWT correspondant redevient valide "
                + "( jusqu'a son expiration naturelle ) . Reserve aux admins .")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Entree retiree"),
        @ApiResponse(responseCode = "401", description = "JWT absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Reserve aux admins"),
        @ApiResponse(responseCode = "404", description = "Aucune entree avec ce hash")
    })
    @DeleteMapping("/blacklist/{tokenHash}")
    public ResponseEntity<Void> removeBlacklistEntry(
            @Parameter(description = "Hash SHA-256 hex du token a deblacklister")
            @PathVariable String tokenHash) {
        return service.removeBlacklistEntry(tokenHash)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }

    @Operation(
        summary = "Vider la blacklist JWT",
        description = "Supprime toutes les entrees de la blacklist . Toutes les "
                + "sessions revoquees redeviennent valides jusqu'a leur expiration "
                + "JWT naturelle . Reserve aux admins .")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Nombre d'entrees vidées"),
        @ApiResponse(responseCode = "401", description = "JWT absent ou invalide"),
        @ApiResponse(responseCode = "403", description = "Reserve aux admins")
    })
    @DeleteMapping("/blacklist")
    public ResponseEntity<java.util.Map<String, Integer>> clearBlacklist() {
        int n = service.clearBlacklist();
        return ResponseEntity.ok(java.util.Map.of("cleared", n));
    }

    @Operation(
        summary = "Annuler un workflow en cours",
        description = """
                Signale une demande d'annulation au {@code ChunkCancellationRegistry}
                interne de cascade. L'appel est **asynchrone** : il retourne dès que
                le drapeau d'annulation est posé. Le workflow se finalise ensuite
                en statut {@code CANCELLED} lorsque tous les chunks en cours
                terminent leur traitement (les chunks en attente lèvent une
                {@code ChunkCancelledException} à leur prochain check).

                **Autorisation** :
                * un administrateur ({@code openAdomAdmin}) peut annuler n'importe
                  quel workflow ;
                * un utilisateur non-admin ne peut annuler que les workflows qu'il
                  a lui-même initiés.

                Les requêtes provenant d'un utilisateur ni admin ni propriétaire
                renvoient **404** — délibérément la même réponse que pour un
                workflow inexistant — afin d'empêcher l'énumération d'identifiants
                de corrélation.
                """,
        tags = {"Dashboard"})
    @ApiResponses({
        @ApiResponse(responseCode = "200",
            description = "Demande d'annulation acceptée. Le corps indique si "
                    + "le drapeau a été posé pour la première fois (signalled=true) "
                    + "ou si le workflow était déjà en cours d'annulation (false).",
            content = @Content(
                    schema = @Schema(implementation = DashboardService.CancelResult.class),
                    examples = {
                        @ExampleObject(name = "Première demande",
                                value = "{\"signalled\": true}"),
                        @ExampleObject(name = "Workflow déjà en cours d'annulation",
                                value = "{\"signalled\": false}")
                    })),
        @ApiResponse(responseCode = "401",
            description = "JWT absent ou invalide"),
        @ApiResponse(responseCode = "404",
            description = "Aucun workflow actif avec cet identifiant de corrélation, "
                    + "ou le workflow n'est ni visible ni détenu par l'appelant")
    })
    @DeleteMapping("/{correlationId}")
    public ResponseEntity<DashboardService.CancelResult> cancel(
            @Parameter(
                description = "Identifiant de corrélation du workflow (UUID v4)",
                in = ParameterIn.PATH,
                required = true,
                examples = {
                    @ExampleObject(name = "UUID v4",
                            value = "f3c6a7b2-1d8e-4a9f-91c5-7e2c0a4b3d11")
                })
            @PathVariable UUID correlationId) {
        try {
            return ResponseEntity.ok(service.cancelWorkflow(correlationId));
        } catch (NoSuchElementException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
