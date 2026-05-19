package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonProcessingException;
import fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery;
import io.micrometer.core.annotation.Timed;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1")
@SecurityRequirement(name = "Bearer Authentication")
public class DataResources {

    private final OreSiResources oreSiResources;

    public DataResources(OreSiResources oreSiResources) {
        this.oreSiResources = oreSiResources;
    }

    @GetMapping(value = "/applications/{nameOrId}/data", produces = MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ_SOME')")
    public ResponseEntity<List<String>> listData(@PathVariable("nameOrId") final String nameOrId) {
        return oreSiResources.listData(nameOrId);
    }

    @Timed(value = "application/data/create")
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_WRITE')")
    @PostMapping(value = "/applications/{nameOrId}/data/{dataName}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createData(
            HttpServletRequest request,
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("dataName") final String dataName,
            @RequestParam(value = "file", required = false) final MultipartFile file,
            @RequestParam(value = "params", required = false) final String params) throws JsonProcessingException {
        return oreSiResources.createData(request, nameOrId, dataName, file, params);
    }

    @Operation(
            description = "Return an extraction of data of datatType 'dataName' of application 'nameOrId'. "
                    + "Le payload est versionné par un ETag faible : un client qui possède déjà une version "
                    + "peut envoyer If-None-Match et recevoir un 304 Not Modified si le contenu n'a pas changé. "
                    + "Le hash ETag est calculé sur le JSON sérialisé , donc tout changement effectif "
                    + "( import , delete , grant ) se traduit en mismatch et nouveau payload."
    )
    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DATA_READ')")
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}/json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<String> getAllDataJson(
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("dataType") final String dataName,
            @JsonParam(value = "downloadDatasetQuery", required = false) final DownloadDatasetQuery params,
            @RequestParam(defaultValue = "false") boolean loadExample,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) final String ifNoneMatch) {
        return oreSiResources.getAllDataJson(nameOrId, dataName, params, loadExample, ifNoneMatch);
    }

    @PreAuthorize("hasPermission('APPLICATION', 'APPLICATION_DELETE_FILE')")
    @DeleteMapping(value = "/applications/{nameOrId}/data/{dataType}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> deleteData(
            @PathVariable("nameOrId") final String nameOrId,
            @PathVariable("dataType") final String dataName,
            @JsonParam(value = "downloadDatasetQuery", required = false) final DownloadDatasetQuery params) {
        return oreSiResources.deleteData(nameOrId, dataName, params);
    }
}
