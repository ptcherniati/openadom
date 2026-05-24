package fr.inra.oresing.rest;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.rest.services.ServiceContainer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

/**
 * Endpoints d'administration systeme pour la consultation des tables
 * de statistiques internes maintenues par l'application :
 *
 * <ul>
 *   <li>{@code <schema>.referencevalue_count_stats} ( V5 ) : compteurs
 *       par referencetype , maintenus par triggers AFTER INSERT/DELETE
 *       statement-level + endpoint admin de recompute exact .</li>
 *   <li>{@code <schema>.oresisynthesis} : synthese pre-calculee des
 *       ranges + variables par datatype , alimente le tableau de bord
 *       et l'extraction CSV .</li>
 * </ul>
 *
 * <p>Trois endpoints exposes , tous sous garde {@code SYSTEM_OPENADOM_ADMIN} :
 *
 * <ul>
 *   <li>{@code GET /admin/applications/{name}/stats-tables/summary} :
 *       compteurs agreges des 2 tables ( rows , last_update , size ) .</li>
 *   <li>{@code GET /admin/applications/{name}/stats-tables/count-stats} :
 *       liste paginee de {@code referencevalue_count_stats} . Filtre
 *       optionnel {@code refType} .</li>
 *   <li>{@code GET /admin/applications/{name}/stats-tables/synthesis} :
 *       liste paginee de {@code oresisynthesis} . Filtre optionnel
 *       {@code datatype} .</li>
 * </ul>
 *
 * <p>Le bouton " Recompute exact " de {@code referencevalue_count_stats}
 * reste sur {@code POST /applications/{name}/admin/recompute-referencevalue-count-stats}
 * ( ApplicationResources ) , consomme tel quel par l'UI .
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/applications")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminSystemResources {

    /** Cap par defaut pour la pagination ( 50 ) . */
    private static final int DEFAULT_LIMIT = 50;
    /** Cap maximum pour eviter de remonter trop de rows en un seul appel . */
    private static final int MAX_LIMIT = 500;

    private static final String PARAM_APP_NAME = "applicationName";
    private static final String SQL_SUFFIX_REF_COUNT_STATS = ".referencevalue_count_stats";
    private static final String SQL_SELECT_COUNT = "SELECT count(*) FROM ";
    private static final String KEY_LAST_UPDATE = "lastUpdate";
    private static final String KEY_ERROR = "error";
    private static final String SQL_SUFFIX_ORESI_SYNTHESIS = ".oresisynthesis";
    private static final String SQL_AND = " AND ";
    private static final String SQL_WHERE = " WHERE ";
    private static final String SQL_SUFFIX_BINARYFILE = ".\"binaryfile\"";
    private static final String PARAM_FILE_ID = "fileId";

    private final ServiceContainer serviceContainer;
    private final JdbcTemplate jdbc;
    private final fr.inra.oresing.rest.usecases.admin.BuildCacheService buildCacheService;

    @Autowired
    public AdminSystemResources(ServiceContainer serviceContainer, JdbcTemplate jdbc,
                                 fr.inra.oresing.rest.usecases.admin.BuildCacheService buildCacheService) {
        this.serviceContainer  = serviceContainer;
        this.jdbc              = jdbc;
        this.buildCacheService = buildCacheService;
    }

    // ---------------------------------------------------------------- //
    //  applications listing ( lightweight , bypass configuration       //
    //  serialization which can fail with NPE on apps with null config  //
    //  cf. /applications NDJSON endpoint )                             //
    // ---------------------------------------------------------------- //

    @Operation(summary = "Liste legere des applications ( nom + id seulement ) .")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping(value = "/list",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, Object>>> listApplications() {
        List<Map<String, Object>> apps = jdbc.query(
                "SELECT name, id::text AS id FROM application ORDER BY name ASC",
                (rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", rs.getString("name"));
                    row.put("id", rs.getString("id"));
                    return row;
                });
        return ResponseEntity.ok(apps);
    }

    // ---------------------------------------------------------------- //
    //  summary                                                         //
    // ---------------------------------------------------------------- //

    @Operation(summary = "Compteurs agreges des 3 tables de stats internes pour une application .")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping(value = "/{nameOrId}/stats-tables/summary",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> summary(
            @Parameter(description = "Nom ou UUID de l'application")
            @PathVariable("nameOrId") String nameOrId) {

        Application application = serviceContainer.applicationService()
                .getApplicationOrApplicationAccordingToRights(nameOrId);
        String schema = quoteIdent(application.getName());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(PARAM_APP_NAME, application.getName());
        body.put("countStats", countStatsSummary(schema));
        body.put("synthesis", synthesisSummary(schema));
        return ResponseEntity.ok(body);
    }

    private Map<String, Object> countStatsSummary(String schema) {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            Long rows = jdbc.queryForObject(
                    SQL_SELECT_COUNT + schema + SQL_SUFFIX_REF_COUNT_STATS,
                    Long.class);
            Timestamp lastUpdate = jdbc.queryForObject(
                    "SELECT max(updated_at) FROM " + schema + SQL_SUFFIX_REF_COUNT_STATS,
                    Timestamp.class);
            m.put("rows", rows == null ? 0L : rows);
            m.put(KEY_LAST_UPDATE, lastUpdate == null ? null : lastUpdate.toInstant().toString());
        } catch (RuntimeException e) {
            log.warn("countStats summary failed for {} : {}", schema, e.getMessage());
            m.put("rows", 0L);
            m.put(KEY_ERROR, e.getMessage());
        }
        return m;
    }

    private Map<String, Object> synthesisSummary(String schema) {
        Map<String, Object> m = new LinkedHashMap<>();
        try {
            Long rows = jdbc.queryForObject(
                    SQL_SELECT_COUNT + schema + SQL_SUFFIX_ORESI_SYNTHESIS,
                    Long.class);
            Timestamp lastUpdate = jdbc.queryForObject(
                    "SELECT max(updatedate) FROM " + schema + SQL_SUFFIX_ORESI_SYNTHESIS,
                    Timestamp.class);
            m.put("rows", rows == null ? 0L : rows);
            m.put(KEY_LAST_UPDATE, lastUpdate == null ? null : lastUpdate.toInstant().toString());
        } catch (RuntimeException e) {
            log.warn("synthesis summary failed for {} : {}", schema, e.getMessage());
            m.put("rows", 0L);
            m.put(KEY_ERROR, e.getMessage());
        }
        return m;
    }

    // ---------------------------------------------------------------- //
    //  count-stats listing                                             //
    // ---------------------------------------------------------------- //

    /**
     * Filtre {@code kind} :
     * <ul>
     *   <li>{@code references} : seuls les referencetype prefixes par
     *       {@code tr_} ( tables de reference / lookup ) .</li>
     *   <li>{@code data} : seuls les referencetype prefixes par
     *       {@code t_} mais pas {@code tr_} ( datatypes contenant des
     *       mesures ) .</li>
     *   <li>{@code all} ou absent : tous les referencetypes .</li>
     * </ul>
     */
    @Operation(summary = "Liste paginee de referencevalue_count_stats .")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping(value = "/{nameOrId}/stats-tables/count-stats",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> listCountStats(
            @PathVariable("nameOrId") String nameOrId,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "refType", required = false) String refType,
            @RequestParam(name = "kind", required = false) String kind) {

        Application application = serviceContainer.applicationService()
                .getApplicationOrApplicationAccordingToRights(nameOrId);
        String schema = quoteIdent(application.getName());
        int l = boundedLimit(limit);
        int o = nonNegativeOffset(offset);

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (refType != null && !refType.isBlank()) {
            where.append(where.length() == 0 ? SQL_WHERE : SQL_AND);
            where.append(" referencetype = ? ");
            params.add(refType);
        }
        if ("references".equalsIgnoreCase(kind)) {
            where.append(where.length() == 0 ? SQL_WHERE : SQL_AND);
            where.append(" referencetype LIKE 'tr\\_%' ESCAPE '\\' ");
        } else if ("data".equalsIgnoreCase(kind)) {
            where.append(where.length() == 0 ? SQL_WHERE : SQL_AND);
            where.append(" referencetype LIKE 't\\_%' ESCAPE '\\' AND referencetype NOT LIKE 'tr\\_%' ESCAPE '\\' ");
        }

        Long total = jdbc.queryForObject(
                SQL_SELECT_COUNT + schema + SQL_SUFFIX_REF_COUNT_STATS + where,
                params.toArray(), Long.class);

        params.add(l);
        params.add(o);
        List<Map<String, Object>> items = jdbc.query(
                "SELECT referencetype, line_count, updated_at "
                + "FROM " + schema + SQL_SUFFIX_REF_COUNT_STATS
                + where
                + " ORDER BY referencetype ASC LIMIT ? OFFSET ?",
                params.toArray(),
                (rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("referenceType", rs.getString("referencetype"));
                    row.put("lineCount", rs.getLong("line_count"));
                    Timestamp ts = rs.getTimestamp("updated_at");
                    row.put("updatedAt", ts == null ? null : ts.toInstant().toString());
                    return row;
                });
        return paged(application.getName(), total, l, o, items);
    }

    // ---------------------------------------------------------------- //
    //  synthesis listing                                               //
    // ---------------------------------------------------------------- //

    @Operation(summary = "Liste paginee de oresisynthesis .")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping(value = "/{nameOrId}/stats-tables/synthesis",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> listSynthesis(
            @PathVariable("nameOrId") String nameOrId,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "datatype", required = false) String datatype) {

        Application application = serviceContainer.applicationService()
                .getApplicationOrApplicationAccordingToRights(nameOrId);
        String schema = quoteIdent(application.getName());
        int l = boundedLimit(limit);
        int o = nonNegativeOffset(offset);

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (datatype != null && !datatype.isBlank()) {
            where.append(" WHERE datatype = ? ");
            params.add(datatype);
        }

        Long total = jdbc.queryForObject(
                SQL_SELECT_COUNT + schema + SQL_SUFFIX_ORESI_SYNTHESIS + where,
                params.toArray(), Long.class);

        params.add(l);
        params.add(o);
        List<Map<String, Object>> items = jdbc.query(
                "SELECT id::text, datatype, variable, aggregation, "
                + "       ranges::text AS ranges_text, updatedate "
                + "FROM " + schema + SQL_SUFFIX_ORESI_SYNTHESIS
                + where
                + " ORDER BY datatype, variable LIMIT ? OFFSET ?",
                params.toArray(),
                (rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", rs.getString("id"));
                    row.put("datatype", rs.getString("datatype"));
                    row.put("variable", rs.getString("variable"));
                    row.put("aggregation", rs.getString("aggregation"));
                    row.put("ranges", rs.getString("ranges_text"));
                    Timestamp ts = rs.getTimestamp("updatedate");
                    row.put("updateDate", ts == null ? null : ts.toInstant().toString());
                    return row;
                });
        return paged(application.getName(), total, l, o, items);
    }

    // ---------------------------------------------------------------- //
    //  binaryfile-cache ( processed_data ) admin listing + clear       //
    // ---------------------------------------------------------------- //

    /**
     * Liste paginee des fichiers binaires d'une application avec la taille
     * de leur cache {@code processed_data} ( colonne JSON aggregee
     * post-validation alimentant le Publish FAST path ) .
     *
     * <p>Filtres optionnels :
     * <ul>
     *   <li>{@code dataName} : filtre sur le datatype ( params -&gt; binaryFiledataset.datatype ) ;</li>
     *   <li>{@code onlyWithCache=true} : ne retourne que les fichiers avec processed_data non NULL ;</li>
     *   <li>{@code onlyWithoutCache=true} : inverse , utile pour identifier les candidats au pre-compute .</li>
     * </ul>
     *
     * <p>Pas d'expose du blob processed_data lui-meme ( potentiellement plusieurs MB
     * par fichier ) ; uniquement {@code length} via {@code processed_size} +
     * {@code processed_at} pour observabilite .
     */
    @Operation(summary = "Liste paginee du cache binaryfile.processed_data ( admin ) .")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @GetMapping(value = "/{nameOrId}/binaryfile-cache",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> listBinaryFileCache(
            @PathVariable("nameOrId") String nameOrId,
            @RequestParam(name = "limit", required = false) Integer limit,
            @RequestParam(name = "offset", required = false) Integer offset,
            @RequestParam(name = "dataName", required = false) String dataName,
            @RequestParam(name = "onlyWithCache", required = false) Boolean onlyWithCache,
            @RequestParam(name = "onlyWithoutCache", required = false) Boolean onlyWithoutCache) {

        Application application = serviceContainer.applicationService()
                .getApplicationOrApplicationAccordingToRights(nameOrId);
        String schema = quoteIdent(application.getName());
        String table  = schema + SQL_SUFFIX_BINARYFILE;
        int l = boundedLimit(limit);
        int o = nonNegativeOffset(offset);

        StringBuilder where = new StringBuilder();
        List<Object> params = new ArrayList<>();
        if (dataName != null && !dataName.isBlank()) {
            where.append(where.length() == 0 ? SQL_WHERE : SQL_AND);
            where.append(" (params -> 'binaryfiledataset' ->> 'datatype') = ? ");
            params.add(dataName);
        }
        // " with cache " = column NOT NULL AND size > 0 . A 0-byte LO
        // ( corrupt write / abandoned BUILD ) is technically NOT NULL but
        // useless for FAST path : we exclude it to avoid misleading admins .
        if (Boolean.TRUE.equals(onlyWithCache)) {
            where.append(where.length() == 0 ? SQL_WHERE : SQL_AND);
            where.append(" processed_data IS NOT NULL AND COALESCE(processed_size, 0) > 0 ");
        } else if (Boolean.TRUE.equals(onlyWithoutCache)) {
            where.append(where.length() == 0 ? SQL_WHERE : SQL_AND);
            where.append(" ( processed_data IS NULL OR COALESCE(processed_size, 0) = 0 ) ");
        }

        Long total = jdbc.queryForObject(
                SQL_SELECT_COUNT + table + where,
                params.toArray(), Long.class);

        params.add(l);
        params.add(o);
        List<Map<String, Object>> items = jdbc.query(
                "SELECT id, "
              + "       name, "
              + "       (params -> 'binaryfiledataset' ->> 'datatype') AS data_name, "
              + "       COALESCE(params ->> 'configHash', '')           AS config_hash, "
              + "       COALESCE((params ->> 'published')::boolean, false) AS published, "
              + "       COALESCE(processed_size, 0)                     AS processed_size, "
              // Real CSV size from the bytea column ; lets the user compare the JSON
              // cache size vs the original CSV size in the UI ( cf "gap json vs csv" ) .
              + "       COALESCE(octet_length(fileData), 0)             AS file_size, "
              + "       processed_at, "
              + "       updateDate "
              + "  FROM " + table
              + where
              + " ORDER BY processed_at DESC NULLS LAST , updateDate DESC LIMIT ? OFFSET ?",
                params.toArray(),
                (rs, n) -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put(PARAM_FILE_ID,        rs.getString("id"));
                    row.put("fileName",      rs.getString("name"));
                    row.put("dataName",      rs.getString("data_name"));
                    row.put("configHash",    rs.getString("config_hash"));
                    row.put("published",     rs.getBoolean("published"));
                    row.put("processedSize", rs.getLong("processed_size"));
                    row.put("fileSize",      rs.getLong("file_size"));
                    Timestamp pat = rs.getTimestamp("processed_at");
                    row.put("processedAt",   pat == null ? null : pat.toInstant().toString());
                    Timestamp ud  = rs.getTimestamp("updateDate");
                    row.put("updatedAt",     ud == null ? null : ud.toInstant().toString());
                    return row;
                });
        return paged(application.getName(), total, l, o, items);
    }

    /**
     * Clear ( vide ) le cache {@code processed_data} d'un fichier binaire .
     * Set la colonne a NULL + reset {@code processed_size} et {@code processed_at} .
     * Le binaryfile lui-meme ( data + params ) reste intact .
     *
     * <p>Idempotent : 200 OK meme si deja NULL . 404 si fichier inexistant .
     * Status binaryfile.params.published preserve ( le clear ne depublie pas ) .
     */
    @Operation(summary = "Clear le cache binaryfile.processed_data d'un fichier ( admin ) .")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @DeleteMapping(value = "/{nameOrId}/binaryfile-cache/{fileId}",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> clearBinaryFileCache(
            @PathVariable("nameOrId") String nameOrId,
            @PathVariable(PARAM_FILE_ID) UUID fileId) {

        Application application = serviceContainer.applicationService()
                .getApplicationOrApplicationAccordingToRights(nameOrId);
        String table = quoteIdent(application.getName()) + SQL_SUFFIX_BINARYFILE;

        Integer exists = jdbc.queryForObject(
                "SELECT count(*)::int FROM " + table + " WHERE id = ?::uuid",
                Integer.class, fileId.toString());
        if (exists == null || exists == 0) {
            return ResponseEntity.notFound().build();
        }

        int updated = jdbc.update(
                "UPDATE " + table + " SET processed_data = NULL, processed_size = NULL, processed_at = NULL "
              + " WHERE id = ?::uuid AND processed_data IS NOT NULL",
                fileId.toString());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(PARAM_APP_NAME, application.getName());
        body.put(PARAM_FILE_ID,          fileId.toString());
        body.put("cleared",         updated > 0);
        body.put("alreadyEmpty",    updated == 0);
        body.put("at",              Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    /**
     * Clear bulk : vide le cache {@code processed_data} de TOUS les fichiers
     * d'un datatype ( {@code dataName} requis ) ou de l'application entiere
     * ( si {@code dataName} non fourni - mode tres destructif , reserve aux
     * scenarios de cleanup admin ) .
     *
     * <p>Retourne le nombre de fichiers impactes ( processed_data passe de
     * NON-NULL a NULL ; les fichiers deja sans cache ne sont pas comptes ) .
     */
    @Operation(summary = "Clear bulk du cache binaryfile.processed_data ( admin , scope datatype ou app ) .")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @DeleteMapping(value = "/{nameOrId}/binaryfile-cache",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> clearBinaryFileCacheBulk(
            @PathVariable("nameOrId") String nameOrId,
            @RequestParam(name = "dataName", required = false) String dataName) {

        Application application = serviceContainer.applicationService()
                .getApplicationOrApplicationAccordingToRights(nameOrId);
        String table = quoteIdent(application.getName()) + SQL_SUFFIX_BINARYFILE;

        StringBuilder where = new StringBuilder(" WHERE processed_data IS NOT NULL ");
        List<Object> params = new ArrayList<>();
        if (dataName != null && !dataName.isBlank()) {
            where.append(" AND (params -> 'binaryfiledataset' ->> 'datatype') = ? ");
            params.add(dataName);
        }
        int updated = jdbc.update(
                "UPDATE " + table + " SET processed_data = NULL, processed_size = NULL, processed_at = NULL "
              + where,
                params.toArray());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(PARAM_APP_NAME, application.getName());
        body.put("dataName",        dataName);
        body.put("clearedCount",    updated);
        body.put("at",              Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    /**
     * Lance un BUILD_CACHE async sur un fichier binaire : execute cascade
     * en mode {@code SinkStrategy.DISCARD} ( cascade 3.2.0 ) pour
     * regenerer le JSON valide+transforme dans la colonne
     * {@code processed_data} sans toucher {@code referencevalue} . Active
     * Publish FAST path pour le prochain republish hashMatch .
     *
     * <p>Retour HTTP 202 + correlationId pour polling client .
     * Workflow_log type {@code BUILD_CACHE} visible dans oa-live history .
     */
    @Operation(summary = "Pre-compute admin du cache binaryfile.processed_data ( BUILD_CACHE async ) .")
    @PreAuthorize("hasPermission('SYSTEM', 'SYSTEM_OPENADOM_ADMIN')")
    @PostMapping(value = "/{nameOrId}/binaryfile-cache/{fileId}/build",
            produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> buildBinaryFileCache(
            @PathVariable("nameOrId") String nameOrId,
            @PathVariable(PARAM_FILE_ID) UUID fileId) {

        Application application = serviceContainer.applicationService()
                .getApplicationOrApplicationAccordingToRights(nameOrId);
        UUID correlationId = buildCacheService.startBuildCache(application.getName(), fileId);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put(PARAM_APP_NAME, application.getName());
        body.put(PARAM_FILE_ID,          fileId.toString());
        body.put("correlationId",   correlationId.toString());
        body.put("status",          "IN_PROGRESS");
        body.put("at",              Instant.now().toString());
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(body);
    }

    // ---------------------------------------------------------------- //
    //  helpers                                                         //
    // ---------------------------------------------------------------- //

    private static String quoteIdent(String schemaName) {
        if (schemaName == null || !schemaName.matches("[a-zA-Z_][a-zA-Z0-9_]*")) {
            throw new IllegalArgumentException(
                    "Invalid schema name ( authorized pattern : [a-zA-Z_][a-zA-Z0-9_]* ) : " + schemaName);
        }
        return "\"" + schemaName + "\"";
    }

    private static int boundedLimit(Integer limit) {
        if (limit == null || limit <= 0) return DEFAULT_LIMIT;
        return Math.min(limit, MAX_LIMIT);
    }

    private static int nonNegativeOffset(Integer offset) {
        return offset == null || offset < 0 ? 0 : offset;
    }

    private static ResponseEntity<Map<String, Object>> paged(
            String appName, Long total, int limit, int offset, List<Map<String, Object>> items) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(PARAM_APP_NAME, appName);
        body.put("total", total == null ? 0L : total);
        body.put("limit", limit);
        body.put("offset", offset);
        body.put("items", items);
        return ResponseEntity.ok()
                .header("X-Total-Count", String.valueOf(total == null ? 0L : total))
                .body(body);
    }
}