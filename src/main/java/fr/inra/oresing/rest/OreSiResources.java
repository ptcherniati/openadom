package fr.inra.oresing.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import fr.inra.oresing.checker.InvalidDatasetContentException;
import fr.inra.oresing.checker.LineChecker;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.checker.ReferenceLineCheckerDisplay;
import fr.inra.oresing.model.*;
import fr.inra.oresing.model.chart.OreSiSynthesis;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.rest.exceptions.configuration.BadApplicationConfigurationException;
import org.assertj.core.util.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.i18n.LocaleContextHolder;
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
import java.util.stream.Collectors;

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
        Optional<BinaryFile> optionalBinaryFile = service.getFileWithData(name, id);
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
    public List<Application> getApplications(@RequestParam(required = false, defaultValue = "") String[] filter) {
        List<ApplicationInformation> filters = Arrays.stream(filter)
                .map(s -> ApplicationInformation.valueOf(s))
                .collect(Collectors.toList());
        return service.getApplications(filters);
    }

    @PostMapping(value = "/validate-configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ConfigurationParsingResult> validateConfiguration(@RequestParam("file") MultipartFile file) throws IOException {
        ConfigurationParsingResult validationResult = service.validateConfiguration(file);
        return ResponseEntity.ok(validationResult);
    }

    @PostMapping(value = "/applications/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createApplication(@PathVariable("name") String name, @RequestParam(name = "comment", defaultValue = "") String comment,
                                               @RequestParam("file") MultipartFile file) throws IOException, BadApplicationConfigurationException {
        if (INVALID_APPLICATION_NAME_PREDICATE.test(name)) {
            return ResponseEntity.badRequest().body("'" + name + "' n’est pas un nom d'application valide, seules les lettres minuscules sont acceptées");
        }
        UUID result = service.createApplication(name, file, comment);
        String uri = UriUtils.encodePath("/applications/" + result, Charset.defaultCharset());
        return ResponseEntity.created(URI.create(uri)).body(Map.of("id", result.toString()));
    }

    @GetMapping(value = "/applications/{nameOrId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApplicationResult> getApplication(@PathVariable("nameOrId") String nameOrId, @RequestParam(required = false, defaultValue = "") String[] filter) {
        Application application = service.getApplication(nameOrId);
        List<ApplicationInformation> filters = Arrays.stream(filter)
                .map(s -> ApplicationInformation.valueOf(s))
                .collect(Collectors.toList());
        boolean withSynthesis = filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.SYNTHESIS);
        boolean withDatatypes = filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.DATATYPE);
        boolean withReferenceType = filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.REFERENCETYPE);
        boolean withConfiguration = filters.contains(ApplicationInformation.ALL) || filters.contains(ApplicationInformation.CONFIGURATION);
        final List<ApplicationResult.ReferenceSynthesis> referenceSynthesis = withReferenceType ? service.getReferenceSynthesis(application) : List.of();
        TreeMultimap<String, String> childrenPerReferences = TreeMultimap.create();
        if (withReferenceType) {
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
        }
        Map<String, ApplicationResult.Reference> references = withReferenceType?Maps.transformEntries(
                application.getConfiguration().getReferences(),
                (reference, referenceDescription) -> {
                    Map<String, ApplicationResult.Reference.Column> columns = Maps.transformEntries(referenceDescription.doGetStaticColumnDescriptions(), (column, columnDescription) -> new ApplicationResult.Reference.Column(column, column, referenceDescription.getKeyColumns().contains(column), null));
                    Map<String, ApplicationResult.Reference.DynamicColumn> dynamicColumns = Maps.transformEntries(referenceDescription.getDynamicColumns(), (dynamicColumnName, dynamicColumnDescription) ->
                            new ApplicationResult.Reference.DynamicColumn(
                                    dynamicColumnName,
                                    dynamicColumnName,
                                    dynamicColumnDescription.getHeaderPrefix(),
                                    dynamicColumnDescription.getReference(),
                                    dynamicColumnDescription.getReferenceColumnToLookForHeader(),
                                    dynamicColumnDescription.getPresenceConstraint().isMandatory()));
                    Set<String> children = childrenPerReferences.get(reference);
                    final Set<String> tags = Optional.ofNullable(referenceDescription.getTags()).map(Map::keySet).orElse(Set.of());
                    return new ApplicationResult.Reference(reference, reference, children, columns, dynamicColumns, tags);
                }) : Map.of();
        Map<String, ApplicationResult.DataType> dataTypes = withDatatypes ? Maps.transformEntries(application.getConfiguration().getDataTypes(), (dataType, dataTypeDescription) -> {
            Map<String, ApplicationResult.DataType.Variable> variables = Maps.transformEntries(dataTypeDescription.getData(), (variable, variableDescription) -> {
                Map<String, ApplicationResult.DataType.Variable.Component> components = Maps.transformEntries(variableDescription.doGetAllComponentDescriptions(), (component, componentDescription) -> {
                    return new ApplicationResult.DataType.Variable.Component(component, component);
                });
                Configuration.Chart chartDescription = variableDescription.getChartDescription();
                ApplicationResult.DataType.Variable.Chart chartDescriptionResult = null;
                if (chartDescription != null) {
                    VariableComponentKey aggregation = chartDescription.getAggregation();
                    String value = chartDescription.getValue();
                    String gap = chartDescription.getGap();
                    String unit = chartDescription.getUnit();
                    String standardDeviation = chartDescription.getStandardDeviation();
                    chartDescriptionResult = new ApplicationResult.DataType.Variable.Chart(value, unit, gap, standardDeviation, aggregation);
                }
                return new ApplicationResult.DataType.Variable(variable, variable, components, chartDescriptionResult);
            });
            Configuration.RepositoryDescription repository = dataTypeDescription.getRepository();
            final boolean hasAuthorizations = repository != null;
            final ApplicationResult.DataType.Repository repositoryResult = Optional.ofNullable(repository)
                    .map(repositoryDescription -> {
                        final String filePattern = repositoryDescription.getFilePattern();
                        final Map<String, Integer> authorizationScope = repositoryDescription.getAuthorizationScope();
                        final ApplicationResult.DataType.TokenDateDescription startDate = Optional.ofNullable(repositoryDescription.getStartDate()).map(sd -> new ApplicationResult.DataType.TokenDateDescription(sd.getToken())).orElse(null);
                        final ApplicationResult.DataType.TokenDateDescription endDate = Optional.ofNullable(repositoryDescription.getEndDate()).map(ed -> new ApplicationResult.DataType.TokenDateDescription(ed.getToken())).orElse(null);
                        return new ApplicationResult.DataType.Repository(filePattern, authorizationScope, startDate, endDate);
                    })
                    .orElse(null);
            return new ApplicationResult.DataType(dataType, dataType, variables, repositoryResult, hasAuthorizations);
        }) : Map.of();
        Configuration configuration = withConfiguration ? application.getConfiguration() : null;
        ApplicationResult applicationResult = new ApplicationResult(application.getId().toString(), application.getName(), application.getConfiguration().getApplication().getName(), application.getComment(), application.getConfiguration().getInternationalization(), references, dataTypes, referenceSynthesis, configuration);
        return ResponseEntity.ok(applicationResult);
    }

    @GetMapping(value = "/applications/{nameOrId}/configuration", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> getConfiguration(@PathVariable("nameOrId") String nameOrId) {
        Application application = service.getApplication(nameOrId);
        UUID configFileId = application.getConfigFile();
        return getFile(nameOrId, configFileId);
    }

    @PostMapping(value = "/applications/{nameOrId}/configuration", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> changeConfiguration(@PathVariable("nameOrId") String nameOrId, @RequestParam("file") MultipartFile file, @RequestParam(name = "comment", defaultValue = "") String comment) throws IOException, BadApplicationConfigurationException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        UUID result = service.changeApplicationConfiguration(nameOrId, file, comment);
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
                                referenceValue.getHierarchicalKey().getSql(),
                                referenceValue.getHierarchicalReference().getSql(),
                                referenceValue.getNaturalKey().getSql(),
                                referenceValue.getRefValues().toJsonForFrontend()
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
    public ResponseEntity<List<List<String>>> listReferences(@PathVariable("nameOrId") String nameOrId, @PathVariable("refType") String refType, @PathVariable("column") String column) {
        Application application = service.getApplication(nameOrId);
        List<List<String>> list = repo.getRepository(application).referenceValue().findReferenceValue(refType, column);
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
        Locale locale = Optional.ofNullable(downloadDatasetQuery)
                .map(DownloadDatasetQuery::getLocale)
                .map(Locale::new)
                .orElseGet(LocaleContextHolder::getLocale);
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
        final Set<String> listOfDataIds = list.stream()
                .map(DataRow::getRowId)
                .collect(Collectors.toSet());
        Map<Ltree, List<ReferenceValue>> requiredreferencesValues = service.getReferenceDisplaysById(service.getApplication(nameOrId), listOfDataIds);
        final Map<String, LineChecker> referenceLineCheckers = checkedFormatVariableComponents.get(ReferenceLineChecker.class.getSimpleName());
        if (referenceLineCheckers == null) {
            //TODO on est dans le cas ou aucun checker reference n'est décrit : authorizationscope  n'est pas un referentiel
        } else {
            for (Map.Entry<String, LineChecker> referenceCheckersByVariableComponentKey : referenceLineCheckers.entrySet()) {
                String variableComponentKey = referenceCheckersByVariableComponentKey.getKey();
                ReferenceLineChecker referenceLineChecker = (ReferenceLineChecker) referenceCheckersByVariableComponentKey.getValue();
                if (referenceLineCheckers.get(variableComponentKey) instanceof ReferenceLineCheckerDisplay) {
                    continue;
                }
                referenceLineChecker.getReferenceValues().entrySet().stream()
                        .filter(e -> requiredreferencesValues.containsKey(e.getKey()))
                        .forEach(e ->
                                list.stream()
                                        .limit(1)
                                        .forEach(dataRow -> {
                                            UUID refId = dataRow.getRefsLinkedTo().get(((VariableComponentKey) referenceLineChecker.getTarget()).getVariable()).get(((VariableComponentKey) referenceLineChecker.getTarget()).getComponent());
                                            requiredreferencesValues.values().stream()
                                                    .map(l ->
                                                            l.stream()
                                                                    .filter(referenceValue -> referenceValue.getId().equals(refId))
                                                                    .findFirst())
                                                    .filter(Optional::isPresent)
                                                    .map(Optional::get)
                                                    .findFirst()
                                                    .ifPresent(referenceValue -> {
                                                        referenceLineCheckers.put(variableComponentKey, new ReferenceLineCheckerDisplay(referenceLineChecker, referenceValue));
                                                    });
                                        })
                        );
            }
        }
        return ResponseEntity.ok(new GetDataResult(variables, list, totalRows, checkedFormatVariableComponents));
    }

    private LinkedHashSet<String> buildOrderedVariables(String nameOrId, String dataType) {
        Configuration.AuthorizationDescription authorization = service.getApplication(nameOrId).getConfiguration().getDataTypes().get(dataType).getAuthorization();
        LinkedHashSet<String> orderedVariableComponents = new LinkedHashSet<String>();
        if (authorization != null && authorization.getTimeScope() != null) {
            orderedVariableComponents.add(authorization.getTimeScope().getVariable());
        }
        if (authorization != null && authorization.getAuthorizationScopes() != null) {
            authorization.getAuthorizationScopes().values()
                    .stream()
                    .filter(vc -> !orderedVariableComponents.contains(vc.getVariable()))
                    .forEach(vc -> orderedVariableComponents.add(vc.getVariable()));
        }
        if (authorization != null && authorization.getDataGroups() != null) {
            authorization.getDataGroups()
                    .values()
                    .stream()
                    .map(dg -> dg.getData())
                    .flatMap(Set::stream)
                    .filter(vc -> !orderedVariableComponents.contains(vc))
                    .forEach(vc -> orderedVariableComponents.add(vc));
        }
        return orderedVariableComponents;
    }

    /**
     * export as CSV
     */
    @GetMapping(value = "/applications/{nameOrId}/data/{dataType}/csv", produces = {MediaType.TEXT_PLAIN_VALUE, MediaType.APPLICATION_OCTET_STREAM_VALUE})
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
        String locale = downloadDatasetQuery != null && downloadDatasetQuery.getLocale() != null ? downloadDatasetQuery.getLocale() : LocaleContextHolder.getLocale().getLanguage();
        String result = service.getDataCsv(downloadDatasetQuery, nameOrId, dataType, locale);
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
            FileOrUUID fileOrUUID = params != null && params != "undefined" ? new ObjectMapper().readValue(params, FileOrUUID.class) : null;
            Optional<BinaryFileDataset> binaryFileDatasetOpt = Optional.ofNullable(fileOrUUID)
                    .map(fileOrUUID1 -> fileOrUUID.binaryfiledataset);
            if (
                    binaryFileDatasetOpt
                            .map(binaryFileDataset -> binaryFileDataset.getDatatype()).isPresent()) {
                binaryFileDatasetOpt
                        .ifPresent(binaryFileDataset -> binaryFileDataset.setDatatype(datatype));
            }
            return fileOrUUID;
        } catch (IOException e) {
            throw new BadFileOrUUIDQuery(e.getMessage());
        }
    }

    private BinaryFileDataset deserialiseBinaryFileDatasetQuery(String datatype, String params) {
        try {
            BinaryFileDataset binaryFileDataset = params != null ? new ObjectMapper().readValue(params, BinaryFileDataset.class) : null;
            Optional<BinaryFileDataset> binaryFileDatasetOpt = Optional.ofNullable(binaryFileDataset);
            if (binaryFileDatasetOpt.map(binaryFileDataset1 -> binaryFileDataset1.getDatatype()).isEmpty()) {
                binaryFileDatasetOpt.ifPresent(binaryFileDataset1 -> binaryFileDataset1.setDatatype(datatype));
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
            FileOrUUID binaryFiledataset = Strings.isNullOrEmpty(params) || "undefined".equals(params) ? null : deserialiseFileOrUUIDQuery(dataType, params);
            Preconditions.checkArgument(file != null || (binaryFiledataset != null && binaryFiledataset.fileid != null), "le fichier ou params.fileid est requis");
            UUID fileId = service.addData(nameOrId, dataType, file, binaryFiledataset);
            String uri = UriUtils.encodePath(String.format("/applications/%s/file/%s", nameOrId, fileId), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(Map.of("fileId", fileId.toString()));
        } catch (InvalidDatasetContentException e) {
            List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }

    @GetMapping(value = "/applications/{nameOrId}/synthesis/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSynthesis(@PathVariable("nameOrId") String nameOrId,
                                          @PathVariable("dataType") String dataType) throws IOException {
        try {
            Map<String, List<OreSiSynthesis>> synthesis = service.getSynthesis(nameOrId, dataType);
            String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s", nameOrId, dataType), Charset.defaultCharset());
            final Map<String, List<SynthesisResult>> synthesisResults = synthesis.entrySet()
                    .stream()
                    .collect(Collectors.toMap(
                                    e -> e.getKey(),
                                    e -> e.getValue().stream().collect(
                                            Collectors.mapping(SynthesisResult::new, Collectors.toList())
                                    )
                            )
                    );
            return ResponseEntity.created(URI.create(uri)).body(synthesisResults);
        } catch (InvalidDatasetContentException e) {
            List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }

    @GetMapping(value = "/applications/{nameOrId}/synthesis/{dataType}/{variable}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> getSynthesis(@PathVariable("nameOrId") String nameOrId,
                                          @PathVariable("dataType") String dataType,
                                          @PathVariable("variable") String variable) throws IOException {
        try {
            Map<String, List<OreSiSynthesis>> synthesis = service.getSynthesis(nameOrId, dataType, variable);
            String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s/%s", nameOrId, dataType, variable), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(synthesis);
        } catch (InvalidDatasetContentException e) {
            List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }

    @PutMapping(value = "/applications/{nameOrId}/synthesis/{dataType}/{variable}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> buidSynthesis(@PathVariable("nameOrId") String nameOrId,
                                           @PathVariable("dataType") String dataType,
                                           @PathVariable("variable") String variable) throws IOException {
        try {
            Map<String, List<OreSiSynthesis>> synthesis = service.buildSynthesis(nameOrId, dataType, variable);
            String uri = UriUtils.encodePath(String.format("/applications/%s/synthesis/%s%s", nameOrId, dataType, variable != null ? "/" + variable : ""), Charset.defaultCharset());
            return ResponseEntity.created(URI.create(uri)).body(synthesis);
        } catch (InvalidDatasetContentException e) {
            List<CsvRowValidationCheckResult> errors = e.getErrors();
            return ResponseEntity.badRequest().body(errors);
        }
    }

    @PutMapping(value = "/applications/{nameOrId}/synthesis/{dataType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> buidSynthesis(@PathVariable("nameOrId") String nameOrId,
                                           @PathVariable("dataType") String dataType) throws IOException {
        return buidSynthesis(nameOrId, dataType, null);
    }


}