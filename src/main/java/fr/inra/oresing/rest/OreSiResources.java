package fr.inra.oresing.rest;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import fr.inra.oresing.checker.CheckerException;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.ApplicationRight;
import fr.inra.oresing.model.BinaryFile;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.persistence.AuthRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

@RestController
@RequestMapping("/api/v1")
public class OreSiResources {

    @Autowired
    private OreSiRepository repo;

    @Autowired
    private AuthRepository authRepo;

    @Autowired
    private OreSiService service;

    @GetMapping(value = "/files/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getFile(@PathVariable("id") UUID id) {
        Optional<BinaryFile> optionalBinaryFile = repo.findById(BinaryFile.class, id);
        if (optionalBinaryFile.isPresent()) {
            BinaryFile binaryFile = optionalBinaryFile.get();
            HttpHeaders headers = new HttpHeaders();
            headers.setContentLength(binaryFile.getSize());
            headers.set("Content-disposition", "attachment;filename=" + binaryFile.getName());
            return new ResponseEntity(binaryFile.getData(), headers, HttpStatus.OK);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping(value = "/applications", produces = MediaType.APPLICATION_JSON_VALUE)
    public List<Application> getApplications() {
        List<Application> result = repo.findAll(Application.class);
        return result;
    }

    @PostMapping(value = "/applications/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createApplication(@PathVariable("name") String name, @RequestParam("file") MultipartFile file) throws IOException {
        UUID result = service.createApplication(name, file);
        String uri = UriUtils.encodePath("/applications/" + result, Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("id", result.toString()));
    }

    @GetMapping(value = "/applications/{nameOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Application> getApplication(@PathVariable("nameOrId") String nameOrId) {
        Optional<Application> opt = repo.findApplication(nameOrId);
        return opt.map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    @GetMapping(value = "/applications/{nameOrId}/configuration", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getConfiguration(@PathVariable("nameOrId") String nameOrId) {
        Optional<Application> opt = repo.findApplication(nameOrId);
        return opt.map(Application::getConfigFile).map(this::getFile).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping(value = "/applications/{nameOrId}/configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> changeConfiguration(@PathVariable("nameOrId") String nameOrId, @RequestParam("file") MultipartFile file) throws IOException {
        Optional<Application> opt = repo.findApplication(nameOrId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Application app = opt.get();
        if (!file.isEmpty()) {
            UUID result = service.changeApplicationConfiguration(app, file);
            String uri = UriUtils.encodePath(String.format("/applications/%s/configuration/%s", nameOrId, result), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(Map.of("id", result.toString()));
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * Ajout un utilisateur pour l'application
     *
     * @param nameOrId l'id ou le nom de l'application
     * @param role le role que doit prendre l'utilisateur, le role doit être un des roles de {@link ApplicationRight}
     * @param userId l'identifiant de l'utilisateur a ajouter
     * @param excludedReference les UUID des valeurs de referenciel qui ne peuvent pas etre lu par cette utilisateur
     * ce parametre n'est pris en compte que si role est {@link ApplicationRight#RESTRICTED_READER}
     * @return ok (200)
     */
    @PutMapping(value = "/applications/{nameOrId}/users/{role}/{userId}")
    public ResponseEntity addUserForApplication(@PathVariable("nameOrId") String nameOrId,
                                  @PathVariable("role") String role,
                                  @PathVariable("userId") UUID userId,
                                  @RequestBody(required = false) UUID[] excludedReference) {
        Optional<Application> opt = repo.findApplication(nameOrId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Application app = opt.get();
        ApplicationRight appRole = ApplicationRight.valueOf(StringUtils.upperCase(role));
        authRepo.addUserRight(userId, app.getId(), appRole, excludedReference);

        return ResponseEntity.ok().build();
    }

    /**
     * Liste les noms des types de referenciels disponible
     * @param nameOrId l'id ou le nom de l'application
     * @return un tableau de chaine
     */
    @GetMapping(value = "/applications/{nameOrId}/references", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> listNameReferences(@PathVariable("nameOrId") String nameOrId) {
        Optional<Application> opt = repo.findApplication(nameOrId);
        return opt.map(Application::getReferenceType).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /**
     * Liste toutes les valeurs possibles pour un type de referenciel
     * @param nameOrId l'id ou le nom de l'application
     * @param refType le type du referenciel
     * @return un tableau de chaine
     */
    @GetMapping(value = "/applications/{nameOrId}/references/{refType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ReferenceValue>> listReferences(
            @PathVariable("nameOrId") String nameOrId,
            @PathVariable("refType") String refType,
            @RequestParam MultiValueMap<String, String> params) {
        Optional<Application> opt = repo.findApplication(nameOrId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Application app = opt.get();
        List<ReferenceValue> list = repo.findReference(app.getId(), refType, params);
        return ResponseEntity.ok(list);
    }

    @GetMapping(value = "/applications/{nameOrId}/references/{refType}/{column}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> listReferences(@PathVariable("nameOrId") String nameOrId, @PathVariable("refType") String refType, @PathVariable("column") String column) {
        Optional<Application> opt = repo.findApplication(nameOrId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Application app = opt.get();
        List<String> list = repo.findReferenceValue(app.getId(), refType, column);
        return ResponseEntity.ok(list);
    }

    @PostMapping(value = "/applications/{nameOrId}/references/{refType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createReference(@PathVariable("nameOrId") String nameOrId, @PathVariable("refType") String refType, @RequestParam("file") MultipartFile file) throws IOException {
        Optional<Application> opt = repo.findApplication(nameOrId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Application app = opt.get();
        if (!file.isEmpty()) {
            UUID result = service.addReference(app, refType, file);
            String uri = UriUtils.encodePath(String.format("/applications/%s/references/%s", nameOrId, refType), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(Map.of("id", result.toString()));
        } else {
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping(value = "/applications/{nameOrId}/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> listDataType(@PathVariable("nameOrId") String nameOrId) {
        Optional<Application> opt = repo.findApplication(nameOrId);
        return opt.map(Application::getDataType).map(ResponseEntity::ok).orElse(ResponseEntity.notFound().build());
    }

    /** export as JSON */
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<Map<String, String>>> getAllDataJson(@PathVariable("nameOrId") String nameOrId, @PathVariable("dataType") String dataType, @RequestParam MultiValueMap<String, String> params) {
        Optional<Application> opt = repo.findApplication(nameOrId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Application app = opt.get();
        List<Map<String, String>> list = service.findData(app, dataType, params);
        return ResponseEntity.ok(list);
    }

    /** export as CSV */
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}/csv", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getAllDataCsvForce(
            @PathVariable("nameOrId") String nameOrId,
            @PathVariable("dataType") String dataType,
            @RequestParam MultiValueMap<String, String> params) throws JsonProcessingException {
        return getAllDataCsv(nameOrId, dataType, params);
    }

    /** export as CSV */
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getAllDataCsv(
            @PathVariable("nameOrId") String nameOrId,
            @PathVariable("dataType") String dataType,
            @RequestParam MultiValueMap<String, String> params) throws JsonProcessingException {
        Optional<Application> opt = repo.findApplication(nameOrId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Application app = opt.get();

        String outColumn = params.getFirst("outColumn");
        params.remove("outColumn");
        List<Map<String, String>> list = service.findData(app, dataType, params);

        String result = "";
        if (list.size() > 0) {
            CsvSchema.Builder schemaBuilder = CsvSchema.builder();
            if (StringUtils.isNotBlank(outColumn)) {
                Stream.of(outColumn.split(";")).forEach(schemaBuilder::addColumn);
            } else {
                list.get(0).keySet().forEach(schemaBuilder::addColumn);
            }
            CsvSchema schema = schemaBuilder.setUseHeader(true).setColumnSeparator(';').build();

            CsvMapper mapper = new CsvMapper();
            mapper.configure(JsonGenerator.Feature.IGNORE_UNKNOWN, true);
            result = mapper.writer(schema).writeValueAsString(list);
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping(value = "/applications/{nameOrId}/data/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createData(@PathVariable("nameOrId") String nameOrId, @PathVariable("dataType") String dataType, @RequestParam("file") MultipartFile file) throws IOException, CheckerException {
        Optional<Application> opt = repo.findApplication(nameOrId);
        if (opt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Application app = opt.get();
        if (!file.isEmpty()) {
            UUID result = service.addData(app, dataType, file);
            String uri = UriUtils.encodePath(String.format("/applications/%s/references/%s", nameOrId, dataType), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(Map.of("id", result.toString()));
        } else {
            return ResponseEntity.badRequest().build();
        }
    }


}