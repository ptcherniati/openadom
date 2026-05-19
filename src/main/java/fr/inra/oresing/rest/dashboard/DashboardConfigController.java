package fr.inra.oresing.rest.dashboard;

import fr.inra.oresing.workflow.cascade.config.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
    private final ConfigEditService configEditService;
    private final StrategyOptionsResolver strategyOptionsResolver;

    @Operation(
        summary = "Get cascade + openADOM runtime configuration",
        description = "Returns the values currently used by the import pipeline "
                + "( chunkSize , parallelism , progressBatchSize , maxErrors ) , "
                + "the rate-limit quotas , and the runtime state ( cascade version , "
                + "Java version , virtual threads on/off , active workflow count ). "
                + "Reserved to admin users.")
    @ApiResponse(responseCode = "200", description = "Runtime configuration",
        content = @Content(schema = @Schema(implementation = DashboardConfigDTO.class)))
    @ApiResponse(responseCode = "401", description = "Missing or invalid JWT")
    @ApiResponse(responseCode = "403", description = "User is not an admin")
    @GetMapping
    public ResponseEntity<DashboardConfigDTO> getConfig() {
        return ResponseEntity.ok(service.getConfig());
    }

    @Operation(
        summary = "Patch live de la configuration cascade",
        description = "Applique les changements à chaud . Les fields hot "
                + "( chunk size , parallelism , strategies , ... ) prennent effet "
                + "immédiatement ou au prochain workflow . Les fields cold "
                + "( queue size , virtualThreads , tempDirs ) ne sont pas exposés ici : "
                + "ils nécessitent un redémarrage . Réservé aux admins .")
    @ApiResponse(responseCode = "200", description = "Patch appliqué")
    @ApiResponse(responseCode = "400", description = "Valeur invalide ou hors plage")
    @ApiResponse(responseCode = "401", description = "JWT absent ou invalide")
    @ApiResponse(responseCode = "403", description = "Réservé aux admins")
    @PutMapping("/import")
    public ResponseEntity<ConfigEditService.PatchResult> patchImport(
            @RequestBody Map<String, Object> patch) {
        return ResponseEntity.ok(configEditService.applyPatch(patch));
    }

    @Operation(
        summary = "Schéma des champs config ( métadonnées UI )",
        description = "Liste de chaque field config avec : type , hot/cold , "
                + "redémarrage requis , description , valeurs autorisées . Utilisé "
                + "par l'UI pour rendre les champs editables / read-only avec tooltip .")
    @GetMapping("/schema")
    public ResponseEntity<List<ConfigFieldRegistry.FieldMeta>> schema() {
        return ResponseEntity.ok(configEditService.schema());
    }

    @Operation(
        summary = "Audit log des modifications de config ( in-memory , last 200 )",
        description = "Ring buffer non persistant ; vidé au redémarrage backend .")
    @GetMapping("/audit")
    public ResponseEntity<List<ConfigChangeAudit.Entry>> audit() {
        return ResponseEntity.ok(configEditService.auditList());
    }

    @Operation(
        summary = "Snapshot de toutes les valeurs config courantes",
        description = "Lit toutes les valeurs via ConfigFieldRegistry "
                + "( hot + cold ) . Utilisé par l'UI pour bootstraper le "
                + "form d'édition avec les valeurs réellement appliquées .")
    @GetMapping("/snapshot")
    public ResponseEntity<Map<String, Object>> snapshot() {
        Map<String, Object> snap = configEditService.snapshot();
        return ResponseEntity.ok(buildPreviewPayload(snap));
    }

    @Operation(
        summary = "Preview de la configuration ( options dynamiques + sink estimate )",
        description = "Calcule , pour un état hypothétique fourni par le client , "
                + "les valeurs autorisées par champ enum ( filtrage des règles BLOCKING ) "
                + "et l'estimation sink concurrency . Permet à l'UI de griser les "
                + "options non-applicables sans dupliquer la logique de cohérence .")
    @PostMapping("/preview")
    public ResponseEntity<Map<String, Object>> preview(
            @RequestBody(required = false) Map<String, Object> hypothetical) {
        Map<String, Object> base = configEditService.snapshot();
        Map<String, Object> effective = new java.util.LinkedHashMap<>(base);
        if (hypothetical != null) effective.putAll(hypothetical);
        return ResponseEntity.ok(buildPreviewPayload(effective));
    }

    private Map<String, Object> buildPreviewPayload(Map<String, Object> effective) {
        var sinkEst = SinkConcurrencyEstimator.calculateEstimate(effective);
        var options = strategyOptionsResolver.resolve(effective);
        return Map.of(
                "snapshot", effective,
                "sinkEstimate", Map.of(
                        "mode",            sinkEst.mode().name(),
                        "effectiveSinks",  sinkEst.effectiveSinks(),
                        "explanation",     sinkEst.explanation()),
                "strategyOptions", options);
    }

    /**
     * Retourne 400 + body { code , message } pour les erreurs metier
     * du patch ( valeur hors plage , combo non supporte , field
     * read-only ) . Sans ce handler , Spring transforme
     * IllegalArgumentException / UnsupportedOperationException en 500
     * generique sans le message , ce qui rend le diagnostic impossible
     * cote UI .
     */
    @org.springframework.web.bind.annotation.ExceptionHandler({
            IllegalArgumentException.class,
            UnsupportedOperationException.class,
            java.util.NoSuchElementException.class
    })
    public ResponseEntity<Map<String, String>> handleConfigPatchError(RuntimeException ex) {
        final String code;
        if (ex instanceof UnsupportedOperationException) {
            code = "FIELD_READ_ONLY";
        } else if (ex instanceof java.util.NoSuchElementException) {
            code = "FIELD_UNKNOWN";
        } else {
            code = "VALIDATION_ERROR";
        }
        return ResponseEntity.badRequest().body(Map.of(
                "code", code,
                "message", ex.getMessage() == null ? "" : ex.getMessage()));
    }

    /**
     * 400 + body explicite pour les erreurs de parsing JSON ( ex.
     * valeur enum invalide passee en string ) . Spring traite ces
     * erreurs en 400 par defaut mais sans body utilisable cote UI .
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(
            org.springframework.http.converter.HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleBadJson(
            org.springframework.http.converter.HttpMessageNotReadableException ex) {
        String msg = ex.getMostSpecificCause().getMessage();
        return ResponseEntity.badRequest().body(Map.of(
                "code", "BAD_REQUEST", "message", msg == null ? "" : msg));
    }
}