package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import fr.inra.oresing.checker.InvalidDatasetContentException;
import fr.inra.oresing.checker.LineChecker;
import fr.inra.oresing.model.*;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.OreSiRepository;
import org.assertj.core.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriUtils;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1")
public class OreSiResources {

    private static final Predicate<String> INVALID_APPLICATION_NAME_PREDICATE =
            Pattern.compile("[a-z]+").asMatchPredicate().negate();

    @Autowired
    private OreSiRepository repo;

    @Autowired
    private OreSiService service;

    @DeleteMapping(value = "/applications/{name}/file/{id}")
    public ResponseEntity<String> removeFile(@PathVariable("name") String name, @PathVariable("id") UUID id) {
        Optional<BinaryFile> optionalBinaryFile = service.getFile(name, id);
        boolean deleted = service.removeFile(name, id);
        if (optionalBinaryFile.isPresent()) {
            return ResponseEntity.ok(id.toString());
        } else {
            return ResponseEntity.notFound().build();
        }

    }

    @GetMapping(value = "/applications/{nameOrId}/filesOnRepository/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<BinaryFile>> getFilesOnRepository(@PathVariable("nameOrId") String nameOrId,
                                                                 @PathVariable("dataType") String dataType,
                                                                 @RequestParam("repositoryId") String repositoryId) {
        BinaryFileDataset binaryFileDataset = deserialiseBinaryFileDatasetQuery(dataType, repositoryId);
        List<BinaryFile> files = service.getFilesOnRepository(nameOrId, dataType, binaryFileDataset, false);
        return ResponseEntity.ok(files);
    }

    @GetMapping(value = "/applications/{name}/file/{id}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getFile(@PathVariable("name") String name, @PathVariable("id") UUID id) {
        Optional<BinaryFile> optionalBinaryFile = service.getFile(name, id);
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
        return service.getApplications();
    }

    @PostMapping(value = "/validate-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConfigurationParsingResult> validateConfiguration(@RequestParam("file") MultipartFile file) throws IOException {
        ConfigurationParsingResult validationResult = service.validateConfiguration(file);
        return ResponseEntity.ok(validationResult);
    }

    @PostMapping(value = "/applications/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createApplication(@PathVariable("name") String name,
                                               @RequestParam("file") MultipartFile file) throws IOException, BadApplicationConfigurationException {
        if (INVALID_APPLICATION_NAME_PREDICATE.test(name)) {
            return ResponseEntity.badRequest().body("'" + name + "' n’est pas un nom d'application valide, seules les lettres minuscules sont acceptées");
        }
        UUID result = service.createApplication(name, file);
        String uri = UriUtils.encodePath("/applications/" + result, Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("id", result.toString()));
    }

    @GetMapping(value = "/applications/{nameOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApplicationResult> getApplication(@PathVariable("nameOrId") String nameOrId) {
        Application application = service.getApplication(nameOrId);
        TreeMultimap<String, String> childrenPerReferences = TreeMultimap.create();
        application.getConfiguration().getCompositeReferences().values().forEach(compositeReferenceDescription -> {
            ImmutableList<String> referenceTypes = compositeReferenceDescription.getComponents().stream()
                    .map(Configuration.CompositeReferenceComponentDescription::getReference)
                    .collect(ImmutableList.toImmutableList());
            ImmutableSortedSet<String> sortedReferenceTypes = ImmutableSortedSet.copyOf(Ordering.explicit(referenceTypes), referenceTypes);
            sortedReferenceTypes.forEach(reference -> {
                String child = sortedReferenceTypes.higher(reference);
                if (child == null) {
                    // on est sur le dernier élément de la hiérarchie, pas de descendant
                } else {
                    childrenPerReferences.put(reference, child);
                }
            });
        });
        Map<String, ApplicationResult.Reference> references = Maps.transformEntries(application.getConfiguration().getReferences(), (reference, referenceDescription) -> {
            Map<String, ApplicationResult.Reference.Column> columns = Maps.transformEntries(referenceDescription.getColumns(), (column, columnDescription) -> new ApplicationResult.Reference.Column(column, column, referenceDescription.getKeyColumns().contains(column), null));
            Set<String> children = childrenPerReferences.get(reference);
            return new ApplicationResult.Reference(reference, reference, children, columns);
        });
        Map<String, ApplicationResult.DataType> dataTypes = Maps.transformEntries(application.getConfiguration().getDataTypes(), (dataType, dataTypeDescription) -> {
            Map<String, ApplicationResult.DataType.Variable> variables = Maps.transformEntries(dataTypeDescription.getData(), (variable, variableDescription) -> {
                Map<String, ApplicationResult.DataType.Variable.Component> components = Maps.transformEntries(variableDescription.getComponents(), (component, componentDescription) -> {
                    return new ApplicationResult.DataType.Variable.Component(component, component);
                });
                return new ApplicationResult.DataType.Variable(variable, variable, components);
            });
            Map<String, String> repository = application.getConfiguration().getDataTypes().get(dataType).getRepository();
            return new ApplicationResult.DataType(dataType, dataType, variables, Optional.ofNullable(repository).filter(m->!m.isEmpty()).orElse(null));
        });
        ApplicationResult applicationResult = new ApplicationResult(application.getId().toString(), application.getName(), application.getConfiguration().getApplication().getName(), references, dataTypes);
        return ResponseEntity.ok(applicationResult);
    }

    @GetMapping(value = "/applications/{nameOrId}/configuration", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getConfiguration(@PathVariable("nameOrId") String nameOrId) {
        Application application = service.getApplication(nameOrId);
        UUID configFileId = application.getConfigFile();
        return getFile(nameOrId, configFileId);
    }

    @PostMapping(value = "/applications/{nameOrId}/configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> changeConfiguration(@PathVariable("nameOrId") String nameOrId, @RequestParam("file") MultipartFile file) throws IOException, BadApplicationConfigurationException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID result = service.changeApplicationConfiguration(nameOrId, file);
        String uri = UriUtils.encodePath(String.format("/applications/%s/configuration/%s", nameOrId, result), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("id", result.toString()));
    }

//    @PutMapping(value = "/applications/{nameOrId}/users/{role}/{userId}")
//    public ResponseEntity addUserForApplication(@PathVariable("nameOrId") String nameOrId,
//                                  @PathVariable("role") String role,
//                                  @PathVariable("userId") UUID userId,
//                                  @RequestBody(required = false) UUID[] excludedReference) {
//        Optional<Application> opt = repo.findApplication(nameOrId);
//        if (opt.isEmpty()) {
//            return ResponseEntity.notFound().build();
//        }
//        Application app = opt.get();
//        ApplicationRight appRole = ApplicationRight.valueOf(StringUtils.upperCase(role));
//        authRepo.addUserRight(userId, app.getId(), appRole, excludedReference);
//
//        return ResponseEntity.ok().build();
//    }

    /**
     * Liste les noms des types de referenciels disponible
     *
     * @param nameOrId l'id ou le nom de l'application
     * @return un tableau de chaine
     */
    @GetMapping(value = "/applications/{nameOrId}/references", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> listNameReferences(@PathVariable("nameOrId") String nameOrId) {
        Application application = service.getApplication(nameOrId);
        return ResponseEntity.ok(application.getReferenceType());
    }

    /**
     * Liste toutes les valeurs possibles pour un type de referenciel
     *
     * @param nameOrId l'id ou le nom de l'application
     * @param refType  le type du referenciel
     * @return un tableau de chaine
     */
    @GetMapping(value = "/applications/{nameOrId}/references/{refType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetReferenceResult> listReferences(
            @PathVariable("nameOrId") String nameOrId,
            @PathVariable("refType") String refType,
            @RequestParam MultiValueMap<String, String> params) {
        List<ReferenceValue> list = service.findReference(nameOrId, refType, params);


        ImmutableSet<GetReferenceResult.ReferenceValue> referenceValues = list.stream()
                .map(referenceValue ->
                        new GetReferenceResult.ReferenceValue(
                                referenceValue.getHierarchicalKey(),
                                referenceValue.getNaturalKey(),
                                referenceValue.getRefValues()
                        )
                )
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetReferenceResult.ReferenceValue::getHierarchicalKey)));
        return ResponseEntity.ok(new GetReferenceResult(referenceValues));
    }

    @GetMapping(value = "/applications/{nameOrId}/references/{refType}/csv", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> listReferencesCsv(
            @PathVariable("nameOrId") String nameOrId,
            @PathVariable("refType") String refType,
            @RequestParam MultiValueMap<String, String> params) {
        String csv = service.getReferenceValuesCsv(nameOrId, refType, params);
        return ResponseEntity.ok(csv);
    }

    @GetMapping(value = "/applications/{nameOrId}/references/{refType}/{column}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> listReferences(@PathVariable("nameOrId") String nameOrId, @PathVariable("refType") String refType, @PathVariable("column") String column) {
        Application application = service.getApplication(nameOrId);
        List<String> list = repo.getRepository(application).referenceValue().findReferenceValue(refType, column);
        return ResponseEntity.ok(list);
    }

    @PostMapping(value = "/applications/{nameOrId}/references/{refType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> createReference(@PathVariable("nameOrId") String nameOrId, @PathVariable("refType") String refType, @RequestParam("file") MultipartFile file) throws IOException {
        Preconditions.checkArgument(!file.isEmpty(), "le CSV téléversé pour le référentiel " + refType + " est vide");
        Application app = service.getApplication(nameOrId);
        UUID result = service.addReference(app, refType, file);
        String uri = UriUtils.encodePath(String.format("/applications/%s/references/%s", nameOrId, refType), Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("id", result.toString()));
    }

    @GetMapping(value = "/applications/{nameOrId}/data", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<String>> listDataType(@PathVariable("nameOrId") String nameOrId) {
        Application application = service.getApplication(nameOrId);
        return ResponseEntity.ok(application.getDataType());
    }

    /**
     * export as JSON
     */
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<GetDataResult> getAllDataJson(
            @PathVariable("nameOrId") String nameOrId,
            @PathVariable("dataType") String dataType,
            @RequestParam(value = "downloadDatasetQuery", required = false) String params) {
        LinkedHashSet<String> orderedVariables = buildOrderedVariables(nameOrId, dataType);
        DownloadDatasetQuery downloadDatasetQuery = deserialiseParamDownloadDatasetQuery(params);
        List<DataRow> list = service.findData(downloadDatasetQuery, nameOrId, dataType);
        ImmutableSet<String> variables = list.stream()
                .limit(1)
                .map(DataRow::getValues)
                .map(Map::keySet)
                .flatMap(Set::stream)
                .sorted((a, b) -> {
                    if (a.equals(b)) {
                        return 0;
                    }
                    return orderedVariables
                            .stream()
                            .dropWhile(i -> !i.equals(a) && !i.equals(b))
                            .findFirst()
                            .orElse("")
                            .equals(a) ? -1 : 1;
                })
                .collect(ImmutableSet.toImmutableSet());
        Long totalRows = list.stream().limit(1).map(dataRow -> dataRow.getTotalRows()).findFirst().orElse(-1L);
        Map<String, Map<String, LineChecker>> checkedFormatVariableComponents = service.getcheckedFormatVariableComponents(nameOrId, dataType);
        return ResponseEntity.ok(new GetDataResult(variables, list, totalRows, checkedFormatVariableComponents));
    }

    private LinkedHashSet<String> buildOrderedVariables(String nameOrId, String dataType) {
        Configuration.AuthorizationDescription authorization = service.getApplication(nameOrId).getConfiguration().getDataTypes().get(dataType).getAuthorization();
        LinkedHashSet<String> orderedVariableComponents = new LinkedHashSet<String>();
        orderedVariableComponents.add(authorization.getTimeScope().getVariable());
        authorization.getAuthorizationScopes().values()
                .stream()
                .filter(vc -> !orderedVariableComponents.contains(vc))
                .forEach(vc -> orderedVariableComponents.add(vc.getVariable()));
        authorization.getDataGroups()
                .values()
                .stream()
                .map(dg -> dg.getData())
                .flatMap(Set::stream)
                .filter(vc -> !orderedVariableComponents.contains(vc))
                .forEach(vc -> orderedVariableComponents.add(vc));
        return orderedVariableComponents;
    }

    /**
     * export as CSV
     */
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}/csv", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getAllDataCsvForce(
            @PathVariable("nameOrId") String nameOrId,
            @PathVariable("dataType") String dataType,
            @RequestParam(value = "downloadDatasetQuery", required = false) String params) {
        return getAllDataCsv(nameOrId, dataType, params);
    }

    /**
     * export as CSV
     */
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}", produces = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<String> getAllDataCsv(
            @PathVariable("nameOrId") String nameOrId,
            @PathVariable("dataType") String dataType,
            @RequestParam(value = "downloadDatasetQuery", required = false) String params) {
        DownloadDatasetQuery downloadDatasetQuery = deserialiseParamDownloadDatasetQuery(params);
        String result = service.getDataCsv(downloadDatasetQuery, nameOrId, dataType);
        return ResponseEntity.ok(result);
    }

    private DownloadDatasetQuery deserialiseParamDownloadDatasetQuery(String params) {
        try {
            return params != null ? new ObjectMapper().readValue(params, DownloadDatasetQuery.class) : null;
        } catch (IOException e) {
            throw new BadDownloadDatasetQuery(e.getMessage());
        }
    }

    private FileOrUUID deserialiseFileOrUUIDQuery(String datatype, String params) {
        try {
            FileOrUUID fileOrUUID = params != null ? new ObjectMapper().readValue(params, FileOrUUID.class) : null;
            if (fileOrUUID.binaryfiledataset.getDatatype() == null) {
                fileOrUUID.binaryfiledataset.setDatatype(datatype);
            }
            return fileOrUUID;
        } catch (IOException e) {
            throw new BadFileOrUUIDQuery(e.getMessage());
        }
    }

    private BinaryFileDataset deserialiseBinaryFileDatasetQuery(String datatype, String params) {
        try {
            BinaryFileDataset binaryFileDataset = params != null ? new ObjectMapper().readValue(params, BinaryFileDataset.class) : null;
            if (binaryFileDataset.getDatatype() == null) {
                binaryFileDataset.setDatatype(datatype);
            }
            return binaryFileDataset;
        } catch (IOException e) {
            throw new BadBinaryFileDatasetQuery(e.getMessage());
        }
    }

    @PostMapping(value = "/applications/{nameOrId}/data/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createData(@PathVariable("nameOrId") String nameOrId,
                                        @PathVariable("dataType") String dataType,
                                        @RequestParam(value = "file", required = false) MultipartFile file,
                                        @RequestParam(value = "params", required = false) String params) throws IOException {
        try {
            FileOrUUID binaryFiledataset = Strings.isNullOrEmpty(params) ? null : deserialiseFileOrUUIDQuery(dataType, params);
            Preconditions.checkArgument(file != null || (binaryFiledataset != null && binaryFiledataset.fileid != null), "le fichier ou params.fileid est requis");
            UUID fileId = service.addData(nameOrId, dataType, file, binaryFiledataset);
            String uri = UriUtils.encodePath(String.format("/applications/%s/file/%s", nameOrId, fileId), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(Map.of("fileId", fileId.toString()));
        } catch (InvalidDatasetContentException e) {
            List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }


}