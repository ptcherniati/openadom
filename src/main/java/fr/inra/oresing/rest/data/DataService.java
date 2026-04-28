package fr.inra.oresing.rest.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.checker.CheckerFactory;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.deposit.context.AsynchroneFileImporterContext;
import fr.inra.oresing.domain.data.deposit.context.ContextConstants;
import fr.inra.oresing.domain.data.rapport.BundleReport;
import fr.inra.oresing.domain.data.rapport.Manifest;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.file.DataFile;
import fr.inra.oresing.domain.file.FileBomResolver;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.workflow.cascade.CascadeImportPipeline;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisation;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisationForBuildBundleReport;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisationForDownloadDatasetQuery;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.persistence.data.read.bundle.FileContent;
import fr.inra.oresing.rest.HierarchicalReferenceAsTree;
import fr.inra.oresing.rest.data.extraction.DataCsvBuilder;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import fr.inra.oresing.rest.filesenderclient.*;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import fr.inra.oresing.rest.model.data.DefaultLineCheckerResult;
import fr.inra.oresing.rest.model.data.LineCheckerResult;
import fr.inra.oresing.rest.services.ServiceContainer;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Slf4j
@Component
public class DataService {
    public static final String README_FILE_NAME = "README";
    public static final String MANIFEST_JSON = "manifest.json";
    public static final String REFERENCES_JSON = "references.json";
    public static final String CONFIGURATION_FILE = "configuration.yaml";
    private static final String CSV_FILENAME_PATTERN = "%s.csv";
    private static final String REFERENCES_CSV_FILENAME_PATTERN = "references/%s.csv";


    public static final int MAX_CONCURRENCY = 6; // Ou ta limite pour contrôler la charge
    @Setter
    ServiceContainer serviceContainer;
    private final OreSiRepository repo;
    private final JsonRowMapper jsonRowMapper;
    private final OreSiRepository repository;
    private final FileRepository fileRepository;
    private final PlatformTransactionManager transactionManager;
    private final CascadeImportPipeline cascadeImportPipeline;
    Executor fastExecutor;
    Executor normalExecutor;
    Executor heavyExecutor;
    Executor backupExecutor;

    public DataService(
            OreSiRepository repo,
            JsonRowMapper jsonRowMapper,
            OreSiRepository repository,
            FileRepository fileRepository,
            ServiceContainer serviceContainer,
            PlatformTransactionManager transactionManager, CascadeImportPipeline cascadeImportPipeline,
            @Qualifier("fastServiceExecutor") Executor fastExecutor,      // ✅ Fast executor
            @Qualifier("normalServiceExecutor") Executor normalExecutor,  // ✅ Normal executor
            @Qualifier("heavyServiceExecutor") Executor heavyExecutor,    // ✅ Heavy executor
            @Qualifier("backupExecutor") Executor backupExecutor
    ) {
        this.repo = repo;
        this.jsonRowMapper = jsonRowMapper;
        this.repository = repository;
        this.fileRepository = fileRepository;
        this.serviceContainer = serviceContainer;
        this.transactionManager = transactionManager;
        this.cascadeImportPipeline = cascadeImportPipeline;
        this.fastExecutor = fastExecutor;
        this.normalExecutor = normalExecutor;
        this.heavyExecutor = heavyExecutor;
        this.backupExecutor = backupExecutor;
    }

    @Transactional()
    public UUID addData(final Application application,
                        final String dataName,
                        final DataFile file) throws IOException {
        serviceContainer.authenticationService().setRoleForClient();
        addData(application, dataName, file.inputData(), file.params());
        return file.params().fileid();
    }

    private void addData(final Application application,
                         final String refType,
                         final InputStream file,
                         final FileOrUUID fileOrUUID) throws IOException {
        final DataRepository referenceValueRepository = getReferenceValueRepository(application);
        AsynchroneFileImporterContext referenceImporterContext = getAsynchroneImporterContext(
                application,
                refType,
                fileOrUUID
        );

        final DataImporter referenceImporter = new DataImporter(referenceImporterContext);
        Path path = referenceImporter.prepareContextForDataTreatment(FileBomResolver.of(file));
        final String userId = serviceContainer.authenticationService().getCurrentUser().getId().toString();
        cascadeImportPipeline.execute(
                referenceImporter,
                referenceValueRepository,
                path,
                userId,
                application.getName(),
                refType
        );
        //final Path toMerge = referenceImporter.doDataTreatment(path, sharedContext, chunkInfo, workflowProperties, lifecycleManager);
        /*referenceImporter.treatErrors();
        referenceValueRepository.storeAll(toMerge);*/

    }

    public HierarchicalReferenceAsTree getHierarchicalReferenceAsTree(final Application application, final String lowestLevelReference) {
        HierarchicalNode compositeReferenceDescription = application.findNode(lowestLevelReference)
                .map(HierarchicalNode::new)
                .orElseThrow(() -> new OreSiTechnicalException("Can't find "));
        BiMap<Ltree, DataValue> indexedByHierarchicalKeyReferenceValues = HashBiMap.create();
        Map<DataValue, Ltree> parentHierarchicalKeys = new LinkedHashMap<>();
        ImmutableList<String> referenceTypes = compositeReferenceDescription.node().children().stream()
                .map(Node::nodeName)
                .collect(ImmutableList.toImmutableList());
        ImmutableSortedSet<String> sortedReferenceTypes = ImmutableSortedSet.copyOf(Ordering.explicit(referenceTypes), referenceTypes);
        ImmutableSortedSet<String> includedReferences = sortedReferenceTypes.headSet(lowestLevelReference, true);
        Optional.of(compositeReferenceDescription.node())
                //.filter(node -> includedReferences.contains(node.nodeName()))
                .ifPresent(compositeReferenceComponentDescription -> {
                    String reference = compositeReferenceComponentDescription.nodeName();
                    Optional<DataColumn> parentKeyColumn = Optional.ofNullable(compositeReferenceComponentDescription.componentKey())
                            .map(DataColumn::new);
                    getReferenceValueRepository(application).findAllByReferenceTypeStream(reference).forEach(referenceValue -> {
                        indexedByHierarchicalKeyReferenceValues.put(referenceValue.getNaturalKey(), referenceValue);
                        parentKeyColumn.ifPresent(presentParentKeyColumn -> {
                            DataDatum referenceDatum = referenceValue.getRefValues();
                            DataColumnValue referenceColumnValue = referenceDatum.get(presentParentKeyColumn);
                            Preconditions.checkState(referenceColumnValue instanceof DataColumnSingleValue);
                            String parentHierarchicalKeyAsString = ((DataColumnSingleValue) referenceColumnValue).getValue().toString();
                            if (!parentHierarchicalKeyAsString.isEmpty()) {
                                Ltree parentHierarchicalKey = Ltree.fromSql(parentHierarchicalKeyAsString);
                                parentHierarchicalKeys.put(referenceValue, parentHierarchicalKey);
                            }
                        });
                    });
                });
        Map<DataValue, DataValue> childToParents = Maps.transformValues(parentHierarchicalKeys, indexedByHierarchicalKeyReferenceValues::get);
        SetMultimap<DataValue, DataValue> tree = HashMultimap.create();
        childToParents.forEach((child, parent) -> tree.put(parent, child));
        ImmutableSet<DataValue> roots = Sets.difference(indexedByHierarchicalKeyReferenceValues.values(), parentHierarchicalKeys.keySet()).immutableCopy();
        return new HierarchicalReferenceAsTree(ImmutableSetMultimap.copyOf(tree), roots);
    }

    public AsynchroneFileImporterContext getAsynchroneImporterContext(final Application application, final String dataName, final FileOrUUID fileOrUUID) {
        final DataRepository referenceValueRepository = getReferenceValueRepository(application);
        final Configuration configuration = application.getConfiguration();
        final CheckerFactory checkerFactory = new CheckerFactory(referenceValueRepository);
        Function<String, List<DataValue>> getDatavaluesByReference = reference -> referenceValueRepository.findAllByReferenceType(reference);
        PublishContext.PublishContextBuilder publishContextBuilder = new PublishContext.PublishContextBuilder(application, dataName, fileOrUUID, getDatavaluesByReference);
        final ImmutableSet<LineChecker<? extends FieldType<?>>> lineCheckers = checkerFactory.getCheckers(application, dataName,
                publishContextBuilder);
        final ContextConstants contextConstants = ContextConstants.with(
                application,
                dataName);
        final Set<String> patternColumnsNames = Optional.ofNullable(contextConstants.displayPattern())
                .map(InternationalizationTitle::getTitle)
                .map(Map::values)
                .map(HashSet::new)
                .orElseGet(HashSet::new);
        final Set<String> patternColumnsDescription = Optional.ofNullable(contextConstants.displayPattern())
                .map(InternationalizationTitle::getDescription)
                .map(Map::values)
                .map(HashSet::new)
                .orElseGet(HashSet::new);
        Map<String, List<String>> referenceToColumnName = lineCheckers.stream()
                .filter(lc -> lc.underlyingType() instanceof ReferenceType)
                .collect(Collectors.groupingBy(
                                lc -> ((ReferenceType) lc.underlyingType()).getRefType(),
                                Collectors.mapping(ReferenceType -> ReferenceType.target().column(), Collectors.toList())
                        )
                );
        Map<String, Map<String, Map<String, String>>> displayNamesByReferenceAndNaturalKey =
                lineCheckers.stream()
                        .filter(lc -> lc.underlyingType() instanceof ReferenceType)
                        .map(lc -> ((ReferenceType) lc.underlyingType()).getRefType())
                        .filter(patternColumnsNames::contains)
                        .collect(Collectors.toMap(ref ->
                                        Optional.ofNullable(referenceToColumnName.getOrDefault(ref, null))
                                                .map(List::getFirst)
                                                .orElse(ref),
                                ref -> getReferenceValueRepository(application).findDisplayByNaturalKey(ref)));
        return AsynchroneFileImporterContext.of(
                contextConstants,
                new PublishContext.PublishContextBuilder(application, dataName, fileOrUUID, getDatavaluesByReference),
                lineCheckers,
                displayNamesByReferenceAndNaturalKey,
                jsonRowMapper,
                referenceValueRepository
        );
    }

    public List<DataValue> findReference(final String nameOrId, final String refType, final MultiValueMap<String, String> params) {
        Application application = serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(nameOrId);
        return serviceContainer.dataService()
                .findReferenceAccordingToRights(application, refType, params);
    }

    public List<DataValue> findReferenceAccordingToRights(final Application application, final String refType, final MultiValueMap<String, String> params) {
        if (application.getConfiguration().getHiddenData().contains(refType)) {
            return List.of();
        }
        final Set<String> hiddenComponents = application.getConfiguration().getHiddenComponentsForData(refType);
        serviceContainer.authenticationService().setRoleForClient();
        return getReferenceValueRepository(application)
                .findAllByReferenceTypeWithReferencingReferencesStream(refType, params)
                .map(referenceValue -> {
                    referenceValue.setRefValues(referenceValue.getRefValues().filterHidden(hiddenComponents));
                    return referenceValue;
                })
                .toList();
    }

    private DataRepository getReferenceValueRepository(Application application) {
        return repo.getRepository(application).data();
    }

    public List<UUID> deleteDataAccordingToRights(final Application application, final String refType, final MultiValueMap<String, String> params) {
        serviceContainer.authenticationService().setRoleForClient();
        return getReferenceValueRepository(application).deleteReferenceType(refType, params);
    }


    public Flux<DataRow> findDataFlux(final DownloadDatasetQuery downloadDatasetQuery) {
        final Application application = downloadDatasetQuery.application();
        if (application.findData(downloadDatasetQuery.dataName())
                .map(StandardDataDescription::tags)
                .filter(Tag.HiddenTag.HAS_HIDDEN_TAG_PREDICATE)
                .isPresent()) {
            return Flux.empty();
        }
        DataRepository dataRepository = getDataRepository(downloadDatasetQuery);
        serviceContainer.authenticationService().setRoleForClient();
        return dataRepository.findAllByDataTypeFlux(downloadDatasetQuery)
                .map(dataRows -> DataRow
                        .of(downloadDatasetQuery.application()
                                        .findData(downloadDatasetQuery.dataName()
                                        ).orElse(null)
                                , dataRows));
    }

    private DataRepository getDataRepository(DownloadDatasetQuery downloadDatasetQuery) {
        return Optional.ofNullable(downloadDatasetQuery)
                .map(DownloadDatasetQuery::application)
                .map(repo::getRepository)
                .map(OreSiRepository.RepositoryForApplication::data)
                .orElseThrow(() -> new IllegalArgumentException("no data repository"));
    }

    public void getDataCsvStream(
            final OutputStream outputStream,
            final String applicationNameOrId,
            final String dataName,
            Locale language,
            boolean horizontalDisplay) {
        final Application application = serviceContainer.applicationService().getApplication(applicationNameOrId);
        if (application.getConfiguration().getHiddenData().contains(dataName)) {
            return;
        }
        DownloadDatasetQueryNoFilter downloadDatasetQuery = new DownloadDatasetQueryNoFilter(
                application,
                dataName,
                new OutPut(
                        Optional.of(language)
                                .orElse(application.getConfiguration().applicationDescription().defaultLanguage()),
                        0L,
                        -1L
                ),
                Set.of(),
                Set.of(),
                horizontalDisplay
        );
        final Flux<DataRow> datas = findDataFlux(downloadDatasetQuery);
        Optional<StandardDataDescription> data = downloadDatasetQuery.application()
                .findData(downloadDatasetQuery.dataName());
        final StandardDataDescription dataDescription = data
                .orElseThrow(() -> new IllegalStateException("can't find application %s".formatted(downloadDatasetQuery.dataName())));
        final AtomicLong counter = new AtomicLong();
        DataCsvBuilder
                .getDataCsvBuilder((appOrName, referenceType) -> getAsynchroneImporterContext(application, referenceType, null))
                .withDownloadDatasetQuery(downloadDatasetQuery)
                .withReferenceService(this)
                .onRepositories(getDataRepository(application), null)
                .addDatas(datas)
                .buildDataCsv(outputStream, downloadDatasetQuery.getLanguage(), dataDescription, downloadDatasetQuery.horizontalDisplay());
    }

    public List<ApplicationResult.DataSynthesis> getReferenceSynthesis(final Application application) {
        return getReferenceValueRepository(application).buildReferenceSynthesis();
    }

    public Boolean getDataFromStoredCsvStream(
            Manifest manifest,
            Path tempZipDirectory,
            String name,
            String reference,
            Application application,
            Locale locale) {

        log.info("getDataFromStoredCsvStream {}", reference);

        DataRepository dataRepository = repo.getRepository(application).data();
        Flux<FileContent> storedData = dataRepository.getStoredData(application, reference);

        try {
            Boolean result = storedData
                    .map(fileContent -> {
                        log.info("reference is loading {} - file {}", reference, fileContent.fileName());
                        try {
                            String relativePath = String.format("%s/%s", reference, fileContent.fileName());
                            manifest.add(reference, fileContent);
                            Path targetFile = tempZipDirectory.resolve(relativePath);
                            Files.createDirectories(targetFile.getParent());
                            try (InputStream is = fileContent.fileContent()) {
                                Files.copy(is, targetFile, StandardCopyOption.REPLACE_EXISTING);
                                return true;
                            }
                        } catch (Exception e) {
                            log.error("Erreur traitement fichier {} pour référence {}", fileContent.fileName(), reference, e);
                            manifest.addError(reference, fileContent);
                            return false;
                        }
                    })
                    .reduce(false, (acc, current) -> acc || current) // Force la consommation et accumule
                    .block();

            log.info("Reference {} finished - hasData: {}", reference, result);
            return result != null ? result : false;

        } catch (Exception e) {
            log.error("Erreur lors du traitement des données stockées pour {}", reference, e);
            return false;
        }
    }


    public DataRepository getDataRepository(Application application) {
        return repository.getRepository(application).data();
    }

    public Flux<DownloadDatasetQueryByRowId> getDownloadDatasetQueriesAsync(
            long patternDefinitionCount,
            Application application,
            Locale locale,
            DataRepository dataRepository,
            Set<UUID> uuidsFromData,
            boolean horizontalDisplay) {
        return Flux.fromStream(dataRepository.getLinkedReferenceValuesStream(uuidsFromData))
                .map(dataValuesByDataType -> {
                    String dataType = dataValuesByDataType.dataType();
                    Set<DataRowIds> ids = dataValuesByDataType.ids();
                    return new DownloadDatasetQueryByRowId(
                            application,
                            dataType,
                            new OutPut(locale, 0L, null),
                            new HashSet<>(),
                            new HashSet<>(),
                            ids,
                            horizontalDisplay
                    );
                });
    }

    /**
     * Construit l'export ZIP : un CSV principal + N CSVs de references.
     *
     * <p>Phase 1c-bis ( issue #62 ) : le {@code @Transactional(readOnly=true)}
     * global a ete retire au profit d'une transaction par CSV ( cf. {@link
     * #addDatacsv} ). Cela permet de fermer le curseur JDBC entre chaque
     * fichier , ce qui evite de tenir une transaction longue ouverte sur le
     * pool {@code workflowDataSource} pendant toute la generation des N+1
     * CSVs ( risque OOM / saturation pool sur gros volumes ).
     */
    public void buildDataZip(
            Path zipOutputStream,
            DownloadDatasetQuery downloadDatasetQuery) {
        Application application = downloadDatasetQuery.application();
        DataRepository dataRepository = repository.getRepository(downloadDatasetQuery.application()).data();

        serviceContainer.authenticationService().setRoleForClient();

        // Passer par le proxy Spring ( serviceContainer.dataService() ) pour
        // que le @Transactional(readOnly=true) declare sur addDatacsv soit
        // effectivement applique - les appels intra-classe bypassent le proxy
        // et n'ouvrent aucune transaction.
        DataService self = serviceContainer.dataService();

        UUIDsfromData uuiDsfromData = self.addDatacsv(zipOutputStream, dataRepository, downloadDatasetQuery, "%s.csv");


        getDownloadDatasetQueriesAsync(
                downloadDatasetQuery.patternDefinitionCount(),
                application,
                downloadDatasetQuery.outPut().locale(),
                dataRepository,
                uuiDsfromData.uuidsfromData(),
                downloadDatasetQuery.horizontalDisplay()
        )
                .flatMap(downloadDatasetQueryByRowId -> Mono.fromCallable(() -> {
                    try {
                        return self.addDatacsv(zipOutputStream, dataRepository, downloadDatasetQueryByRowId, "references/%s.csv");
                    } catch (Exception e) {
                        throw new SiOreIllegalArgumentException("IOException", Map.of("message", Optional.ofNullable(e).map(Exception::getLocalizedMessage).orElse(OreSiTechnicalException.NO_MESSAGE)));
                    }
                }))
                .blockLast();
               /*  .subscribe(downloadDatasetQueries -> {
           for (DownloadDatasetQueryByRowId downloadDatasetQueryByRowId : downloadDatasetQueries) {
                try {
                    addDatacsv(zipOutputStream, dataRepository, downloadDatasetQueryByRowId, "references/%s.csv");
                } catch (Exception e) {
                    throw new SiOreIllegalArgumentException("IOException", Map.of("message", Optional.ofNullable(e).map(Exception::getLocalizedMessage).orElse(OreSiTechnicalException.NO_MESSAGE)));
                }
            }*/
        //TODO add additionalFiles

        /*Flux.fromStream(dataRepository.getLinkedReferenceValuesStream(uuiDsfromData.uuidsfromData()))
                //.filter(dataValuesByDataType -> "tr_metadata_agri_magri".equals(dataValuesByDataType.getDataType()))
                //.take(10)
                .doOnNext(dataValuesByDataType -> {
                    try {
                        addReferenceEntry(
                                downloadDatasetQuery.application(),
                                language,
                                separator,
                                dataValuesByDataType,
                                dataRepositoryWithBuffer,
                                zipOutputStream);
                    } catch (Exception e) {
                        throw new OreSiTechnicalException(e.getMessage(), e);
                    }
                })
                .doOnError(e -> {
                    // Gestion des erreurs
                    throw new SiOreIllegalArgumentException("IOException", Map.of("message", e.getLocalizedMessage()));
                })
                .doOnComplete(() -> {
                    try {
                        zipOutputStream.close();
                    } catch (IOException e) {
                        throw new SiOreIllegalArgumentException("IOException", Map.of("message", e.getLocalizedMessage()));
                    }
                })
                .subscribe();*/

        // 3. Construire la liste des fichiers additionnels
        //AdditionalFileRepository additionalFileRepository = repository.getRepository(downloadDatasetQuery.application()).additionalBinaryFile();
        /*for (String additionalFileType : downloadDatasetQuery.application().getConfiguration().additionalFiles()) {
            if (!additionalFileType.isEmpty()) {
                additionalFileRepository.getAssociatedAdditionalFilesStream(uuiDsfromData.getDatasIds())
                        .forEach(additionalFile -> {
                            try {
                                new AdditionalFileSearchHelper().addAdditionalFilesToZip(additionalFile, zipOutputStream, "additionalFiles/");
                            } catch (final IOException e) {
                                throw new OreSiTechnicalException("Erreur lors de l'ajout des fichiers additionnels", e);
                            }
                        });
            }
        }*/
    }

    /**
     * Variante streaming de {@link #buildDataZip} : ecrit toutes les entrees
     * CSV ( principal + references ) directement dans le {@link ZipOutputStream}
     * fourni. Aucun fichier intermediaire sur disque. Phase 1c-full ( issue #62 ).
     *
     * <p>Le {@code zipOutputStream} n'est pas ferme par cette methode :
     * l'appelant garde le controle ( typiquement via try-with-resources ).
     *
     * <p>L'ordre d'ecriture est sequentiel ( {@code concatMap} au lieu de
     * {@code flatMap} ) car {@link ZipOutputStream} n'est pas thread-safe.
     */
    public void streamDataZipTo(
            java.util.zip.ZipOutputStream zipOutputStream,
            DownloadDatasetQuery          downloadDatasetQuery) {
        Application application = downloadDatasetQuery.application();
        DataRepository dataRepository = repository.getRepository(application).data();

        serviceContainer.authenticationService().setRoleForClient();

        DataService self = serviceContainer.dataService();

        UUIDsfromData uuiDsfromData = self.addDatacsvEntry(
                zipOutputStream, dataRepository, downloadDatasetQuery, CSV_FILENAME_PATTERN);

        getDownloadDatasetQueriesAsync(
                downloadDatasetQuery.patternDefinitionCount(),
                application,
                downloadDatasetQuery.outPut().locale(),
                dataRepository,
                uuiDsfromData.uuidsfromData(),
                downloadDatasetQuery.horizontalDisplay()
        )
                .concatMap(subQuery -> Mono.fromCallable(() -> {
                    try {
                        return self.addDatacsvEntry(
                                zipOutputStream, dataRepository, subQuery, REFERENCES_CSV_FILENAME_PATTERN);
                    } catch (Exception e) {
                        throw new SiOreIllegalArgumentException("IOException",
                                Map.of("message", Optional.ofNullable(e)
                                        .map(Exception::getLocalizedMessage)
                                        .orElse(OreSiTechnicalException.NO_MESSAGE)));
                    }
                }))
                .blockLast();
    }

    /**
     * Variante streaming de {@link #addDatacsv} : ecrit le CSV dans une entree
     * du zip fourni au lieu d'un fichier dans un repertoire temporaire.
     */
    @Transactional(readOnly = true)
    public UUIDsfromData addDatacsvEntry(
            final java.util.zip.ZipOutputStream zipOutputStream,
            DataRepository                      dataRepository,
            final DownloadDatasetQuery          downloadDatasetQuery,
            String                              fileNamePattern) {
        final Flux<DataRow> datas = serviceContainer.dataService().findDataFlux(downloadDatasetQuery);
        try {
            AdditionalFileRepository additionalFileRepository = repository
                    .getRepository(downloadDatasetQuery.application()).additionalBinaryFile();
            return DataCsvBuilder.getDataCsvBuilder(
                            (appOrName, refType) -> serviceContainer.dataService()
                                    .getAsynchroneImporterContext(
                                            downloadDatasetQuery.application(), refType, null))
                    .withDownloadDatasetQuery(downloadDatasetQuery)
                    .withReferenceService(serviceContainer.dataService())
                    .onRepositories(dataRepository, additionalFileRepository)
                    .addDatas(datas)
                    .buildToZipEntry(zipOutputStream, fileNamePattern);
        } catch (IOException e) {
            throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
        }
    }

    @Transactional(readOnly = true)
    public UUIDsfromData addDatacsv(
            final Path zipRepository,
            DataRepository dataRepository,
            final DownloadDatasetQuery downloadDatasetQuery,
            String fileNamePattern) {
        final Flux<DataRow> datas = serviceContainer.dataService().findDataFlux(downloadDatasetQuery);
        try {
            AdditionalFileRepository additionalFileRepository = repository.getRepository(downloadDatasetQuery.application()).additionalBinaryFile();
            return DataCsvBuilder.getDataCsvBuilder((applicationNameOrId, referenceType) -> serviceContainer.dataService().getAsynchroneImporterContext(downloadDatasetQuery.application(), referenceType, null))
                    .withDownloadDatasetQuery(downloadDatasetQuery)
                    .withReferenceService(serviceContainer.dataService())
                    .withZipRepository(zipRepository)
                    .onRepositories(dataRepository, additionalFileRepository)
                    .addDatas(datas)
                    .build(fileNamePattern);
        } catch (IOException e) {
            throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
        }

    }

    public List<DataRow> findData(final DownloadDatasetQuery downloadDatasetQuery) {
        return serviceContainer.dataService().findDataFlux(downloadDatasetQuery).collectList().block();
    }

    public void sendZipLinkByMail(Path filePath, MessageInformations messageInformations, OreSiUser currentUser) {
        switch (messageInformations) {
            case DownloadDatasetQuery downloadDatasetQuery -> {
                try {
                    FileSenderInternationalisation fileSenderInternationalisation = new FileSenderInternationalisationForDownloadDatasetQuery(downloadDatasetQuery);
                    Locale locale = downloadDatasetQuery.outPut().locale();
                    String applicationName = Optional.ofNullable(
                                    fileSenderInternationalisation.getInternationnalizedApplication(locale)
                            )
                            .orElseGet(() -> Optional.ofNullable(fileSenderInternationalisation.getInternationnalizedApplication(fileSenderInternationalisation.getDefaultLanguage()))
                                    .orElse(downloadDatasetQuery.application().getName()));
                    String dataName = Optional.ofNullable(fileSenderInternationalisation.getInternationnalizedDataName(locale, downloadDatasetQuery.dataName()))
                            .orElseGet(() -> Optional.ofNullable(fileSenderInternationalisation.getInternationnalizedDataName(fileSenderInternationalisation.getDefaultLanguage(), downloadDatasetQuery.dataName()))
                                    .orElse(downloadDatasetQuery.dataName()));
                    String subject = fileSenderInternationalisation.subjectPattern();
                    String message = fileSenderInternationalisation.messagePattern();
                    String internationnalizedDataName = fileSenderInternationalisation.getInternationnalizedDataName(
                            Locale.of(downloadDatasetQuery.getLanguage()),
                            dataName
                    );

                    String messageWithReport = fileSenderInternationalisation.
                            mailMessagefor(message.formatted(internationnalizedDataName),
                                    FileSenderRepository.DEFAULT_TRANSFER_DAYS_VALID);
                    FileInfos fileInfos = new FileInfos(
                            applicationName,
                            dataName,
                            filePath,
                            currentUser.getEmail(),
                            subject.formatted(applicationName),
                            messageWithReport);
                    String downloadUrl = fileRepository.postTransfer(fileInfos);
                    log.info("Adresse de téléchargement : %s".formatted(downloadUrl));
                    /*sendUploadZipEmail(
                            currentUser.getEmail(),
                            subject.formatted(applicationName),
                            message.formatted(dataName),
                            downloadUrl,
                            fileSenderInternationalisation,
                            internationnalizedDataName
                    );*/
                } catch (Exception e) {
                    throw new OreSiTechnicalException(ExceptionMessage.IO_EXCEPTION.toMessage(), e);
                }
            }
            case BuildBundleReport buildBundleReport -> {
                try {
                    FileSenderInternationalisation fileSenderInternationalisation = new FileSenderInternationalisationForBuildBundleReport(buildBundleReport);
                    Locale locale = buildBundleReport.locale();

                    String applicationName = Optional.ofNullable(
                            fileSenderInternationalisation.getInternationnalizedApplication(locale)
                    ).orElseGet(() -> Optional.ofNullable(
                            fileSenderInternationalisation.getInternationnalizedApplication(fileSenderInternationalisation.getDefaultLanguage())
                    ).orElse(buildBundleReport.application().getName()));

                    String subject = fileSenderInternationalisation.subjectPattern().formatted(applicationName);
                    String message = fileSenderInternationalisation.messagePattern().formatted(applicationName);


                    String emailMessage = fileSenderInternationalisation.mailMessagefor(message, FileSenderRepository.DEFAULT_TRANSFER_DAYS_VALID);

                    /*sendUploadZipEmail(@Autowired
private PlatformTransactionManager transactionManager;

                            currentUser.getEmail(),
                            subject,
                            emailMessage,
                            FileSenderRepository.DEFAULT_TRANSFER_DAYS_VALID,
                            fileSenderInternationalisation,
                            applicationName
                    );*/
                    FileInfos fileInfos = new FileInfos(
                            applicationName,
                            "BulkUploadZIP",
                            filePath,
                            currentUser.getEmail(),
                            subject,
                            message
                    );
                    String downloadUrl = fileRepository.postTransfer(fileInfos);
                    log.info("Adresse de téléchargement du ZIP pour dépôt en masse : %s".formatted(downloadUrl));

                } catch (Exception e) {
                    log.error("Erreur lors de la création ou de l'envoi du ZIP pour dépôt en masse", e);
                    throw new OreSiTechnicalException("Erreur lors de la création ou de l'envoi du ZIP pour dépôt en masse", e);
                }
            }
            case BundleReport bundleReport -> {
                try {
                    Locale locale = bundleReport.locale();

                    String applicationName = bundleReport.application().getName();

                    String subject = bundleReport.title();
                    String emailMessage = bundleReport.message();

                    FileInfos fileInfos = new FileInfos(
                            applicationName,
                            "BulkUploadZIP",
                            filePath,
                            currentUser.getEmail(),
                            subject,
                            emailMessage
                    );
                    String downloadUrl = fileRepository.postTransfer(fileInfos);
                    log.info("Adresse de téléchargement du ZIP pour dépôt en masse : %s".formatted(downloadUrl));

                } catch (Exception e) {
                    log.error("Erreur lors de la création ou de l'envoi du rapport pour dépôt en masse", e);
                    throw new OreSiTechnicalException("Erreur lors de la création ou de l'envoi du rapport pour dépôt en masse", e);
                }
            }
            default -> throw new IllegalStateException("Unexpected value: " + messageInformations);
        }

    }

    @Transactional(readOnly = true)
    public BuildBundleReport writeUploadBundle(String instanceUrl, String nameOrId, boolean withData,
                                               Locale locale, Path tempZipDirectory) throws IOException {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        Scheduler virtualScheduler = Schedulers.fromExecutor(heavyExecutor);
        List<String> referentielsAvecDonnees = Collections.synchronizedList(new ArrayList<>());
        List<String> referentielsAvecDonneesExemple = Collections.synchronizedList(new ArrayList<>());
        List<String> referentielsEnErreur = Collections.synchronizedList(new ArrayList<>());

        Manifest manifest = new Manifest();

        // Capture le gestionnaire de transactions
        PlatformTransactionManager txManager = transactionManager;
        TransactionDefinition txDef = new DefaultTransactionDefinition(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        Flux.fromIterable(application.getConfiguration().dataDescription().keySet())
                .flatMap(reference ->
                                Mono.fromCallable(() -> {
                                            // Crée une NOUVELLE transaction pour ce thread
                                            TransactionStatus txStatus = txManager.getTransaction(txDef);
                                            try {
                                                Boolean dataFromStoredCsvStream = serviceContainer.dataService()
                                                        .getDataFromStoredCsvStream(
                                                                manifest,
                                                                tempZipDirectory,
                                                                application.getName(),
                                                                reference,
                                                                application,
                                                                locale);

                                                if (withData && dataFromStoredCsvStream) {
                                                    referentielsAvecDonnees.add(reference);
                                                } else {
                                                    String fileName = application.getConfiguration().findData(reference)
                                                            .map(StandardDataDescription::submission)
                                                            .map(Submission::fileNameParsing)
                                                            .map(Submission.SubmissionFileNameParsing::createExampleSubmissionFileName)
                                                            .orElse(CSV_FILENAME_PATTERN.formatted(reference));
                                                    String dataCsvFilePath = "%1$s/%2$s".formatted(reference, fileName);
                                                    Path filePath = tempZipDirectory.resolve(dataCsvFilePath);
                                                    Files.createDirectories(filePath.getParent());
                                                    Files.createFile(filePath);
                                                    referentielsAvecDonneesExemple.add(reference);
                                                }

                                                txManager.commit(txStatus);
                                                return reference;
                                            } catch (Exception e) {
                                                txManager.rollback(txStatus);
                                                throw e;
                                            }
                                        })
                                        .subscribeOn(virtualScheduler)
                                        .onErrorResume(e -> {
                                            log.error("Erreur lors du traitement du référentiel {}", reference, e);
                                            referentielsEnErreur.add(reference);
                                            return Mono.empty();
                                        }),
                        MAX_CONCURRENCY)
                .collectList()
                .block();

        addManifest(tempZipDirectory, manifest);
        return new BuildBundleReport(application, referentielsAvecDonnees, referentielsAvecDonneesExemple,
                referentielsEnErreur, locale);
    }


    private static void addManifest(Path directory, Manifest manifest) throws IOException {

        final Map<String, List<String>> orderedDependancies = addReferencesFile(directory, manifest);
        final LinkedHashMap<String, List<String>> orderedManifest = orderedDependancies.keySet()
                .stream()
                .collect(
                        Collectors.toMap(
                                Function.identity(),
                                referenceName -> manifest.referenceTypeFiles()
                                        .get(referenceName).stream()
                                        .map(FileContent::fileName)
                                        .toList(),
                                (v1, v2) -> v1,
                                LinkedHashMap::new
                        )
                );
        String manifestJson = new ObjectMapper().writeValueAsString(orderedManifest);
        Path manifestFile = directory.resolve(MANIFEST_JSON);
        Files.createDirectories(manifestFile.getParent());
        Files.writeString(manifestFile, manifestJson, StandardCharsets.UTF_8);
    }

    private static Map<String, List<String>> addReferencesFile(Path directory, Manifest manifest) throws IOException {
        Map<String, List<String>> referenceDeps = manifest.orderedReferenceTypes();
        String referencesJson = new ObjectMapper().writeValueAsString(referenceDeps);
        Path referencesFile = directory.resolve(REFERENCES_JSON);
        Files.createDirectories(referencesFile.getParent());
        Files.writeString(referencesFile, referencesJson, StandardCharsets.UTF_8);
        return referenceDeps;
    }

    @Transactional()
    public List<UUID> deleteData(final DownloadDatasetQuery downloadDatasetQuery) {
        serviceContainer.authenticationService().setRoleForClient();
        final Application application = downloadDatasetQuery.application();
        return repository.getRepository(application).data().delete(downloadDatasetQuery);
    }

    public Map<Ltree, List<DataValue>> getReferenceDisplaysById(final Application application, final Set<String> listOfDataIds) {
        return repository.getRepository(application).data().getReferenceDisplaysById(listOfDataIds);
    }

    public Map<String, Map<String, LineCheckerResult>> getCheckedFormatComponents(final String nameOrId, final String dataName) {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        return new CheckerFactory(repository.getRepository(application).data()).getCheckers(application, dataName, new PublishContext.PublishContextBuilder(application, dataName, null, r -> List.of())).stream()
                .filter(c -> (c.underlyingType() instanceof DateType) || (c.underlyingType() instanceof IntegerType) || (c.underlyingType() instanceof FloatType) || (c.underlyingType() instanceof ReferenceType)).collect(Collectors
                        .groupingBy(
                                c -> c.underlyingType().getClass().getSimpleName(),
                                Collectors.toMap(c -> {
                                            final DataColumn dataColumn = c.target();
                                            return dataColumn.toHumanReadableString();
                                        },
                                        DefaultLineCheckerResult::fromLineChecker)
                        )
                );
    }

    @Transactional(readOnly = true)
    public Map<String, Map<String, LineChecker>> getFormatChecked(final String nameOrId, final String references) {
        final DataRepository dataRepository = repository.getRepository(serviceContainer.applicationService().getApplication(nameOrId)).data();
        return new CheckerFactory(dataRepository)
                .getCheckers(
                        serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(nameOrId),
                        references,
                        null
                ).stream()
                .filter(c -> (c.underlyingType() instanceof DateType) || (c.underlyingType() instanceof IntegerType) || (c.underlyingType() instanceof FloatType) || (c.underlyingType() instanceof ReferenceType)).collect(Collectors
                        .groupingBy(
                                c -> c.fieldTypeForOne().getClass().getSimpleName(),
                                Collectors.toMap(
                                        c -> {
                                            final DataColumn vc = c.target();
                                            return vc.asString();
                                        },
                                        c -> c)
                        )
                );
    }

    public List<List<String>> getDataColumn(final Application application, final String refType, final String column) {
        List<List<String>> list = List.of();
        if (application.findData(refType)
                .map(StandardDataDescription::tags)
                .filter(Tag.HiddenTag.HAS_HIDDEN_TAG_PREDICATE)
                .isPresent()) {
            list = repository.getRepository(application).data().findDataColumn(refType, column);
        }
        return list;
    }

    // PERF #465 - Cache en mémoire pour les résultats de la requête filterList.
    //
    // Raison ?
    //   La requête SQL getFilterList() dans DataRepository.java est très coûteuse ( ~54 secondes
    //   pour 100K lignes ). Le résultat ne change QUE lors d'un import/suppression de données.
    //   On cache le résultat en mémoire Java pour éviter de re-exécuter la requête.
    //
    // Fonctionnement :
    //   - Clé du cache : "nomApplication::nomDataType" (ex: "bmks_sandbox::t_soil_analysis_sana")
    //   - Valeur : le JSON sérialisé des FilterList + le timestamp ( pour l'éviction LRU )
    //     On stocke le JSON sérialisé (String) au lieu des objets Java pour éviter la re-sérialisation
    //     Jackson (~700ms) à chaque appel GET /filters. Le endpoint retourne le JSON directement.
    //   - Pas de TTL : le cache n'expire jamais automatiquement
    //   - Reconstruction : après un dépôt/suppression de données réussi, le cache est reconstruit
    //     en asynchrone via refreshFilterListCache(). L'ancien cache reste lisible pendant la
    //     reconstruction — il n'est jamais supprimé, seulement remplacé par le nouveau résultat.
    //   - Taille max : 50 entrées — au-delà, l'entrée la plus ancienne est supprimée (LRU)
    //   - Thread-safety : ConcurrentHashMap pour supporter les accès multi-utilisateurs
    //   - Rechargement manuel : GET /filters?refresh=true
    //
    // Mémoire utilisée :
    //   - Pour un jeu de 105K lignes : le résultat fait ~1.7 MB de JSON
    //   - 50 entrées max = ~85 MB worst case ( en pratique beaucoup moins )
    private record FilterListCacheEntry(String json, long timestamp) {}
    private static final java.util.concurrent.ConcurrentHashMap<String, FilterListCacheEntry> filterListCache = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int FILTER_LIST_CACHE_MAX_ENTRIES = 50;
    private static final ObjectMapper cacheObjectMapper = new ObjectMapper();

    /**
     * Retourne le JSON sérialisé des filtres, depuis le cache si disponible.
     * Si le cache est vide (premier appel ou après refresh=true), exécute la requête SQL,
     * sérialise le résultat en JSON, et le stocke en cache pour les appels suivants.
     *
     * @return le JSON sérialisé prêt à être retourné directement par le endpoint, ou null si aucune donnée
     */
    public String filterListAsJson(final Application application, final String refType) {
        String cacheKey = application.getName() + "::" + refType;
        FilterListCacheEntry cached = filterListCache.get(cacheKey);

        // Cache hit : retourner le JSON déjà sérialisé (0ms, pas de re-sérialisation Jackson)
        if (cached != null) {
            log.debug("filterList cache hit for {}", cacheKey);
            return cached.json();
        }

        // Cache miss : exécuter la requête SQL, sérialiser en JSON, et stocker
        log.info("filterList cache miss for {}, loading from database", cacheKey);
        List<FilterListEntry> entries = computeFilterListEntries(application, refType);
        return serializeAndCache(cacheKey, entries);
    }

    /**
     * Calcule la liste complète des entrées du payload {@code /filters} pour un
     * couple {@code (application, refType)} :
     * <ul>
     *   <li>les {@link FilterList} produits par la requête historique
     *       ( valeurs de {@code ReferenceChecker} liées au datatype ) ;
     *   <li>les {@link ColumnDistinctValues} pour chaque colonne marquée
     *       {@code __FILTER_LIST__} dans le YAML.
     * </ul>
     *
     * <p>Les deux types cohabitent dans le même {@link FilterListEntry} ; le
     * frontend les distingue via la propriété {@code @class} du payload JSON.
     *
     * <p>Conçu pour ne jamais lever : en cas d'erreur SQL sur une colonne
     * particulière , on logge et on saute la colonne plutôt que de faire
     * échouer l'endpoint complet ( on préfère afficher la dropdown vide à
     * un blocage UI ).
     */
    private List<FilterListEntry> computeFilterListEntries(
            final Application application, final String refType) {
        final List<FilterListEntry> result = new java.util.ArrayList<>();
        final var dataRepo = repository.getRepository(application).data();

        // 1. Filtres référence ( historique - inchangé )
        final List<FilterList> filterLists = dataRepo.getFilterList(refType)
                .collectList()
                .block();
        if (filterLists != null) {
            result.addAll(filterLists);
        }

        // 2. Pour chaque colonne filtrable opt-in :
        //    - __FILTER_LIST__  -> valeurs distinctes complètes ( DISTINCT )
        //    - __FILTER_TEXT__  -> uniquement le drapeau hasEmpty ( EXISTS ) ,
        //      values reste vide. Permet au front de conditionner le bouton
        //      "(vide)" sans payer le coût d'un DISTINCT inutile.
        application.findData(refType).ifPresent(dataDescription ->
                dataDescription.componentDescriptions().values().stream()
                        .filter(c -> c.isFilterableAsList() || c.isFilterableAsText())
                        .forEach(component -> {
                            try {
                                final var multiplicity = component.checker() != null
                                        ? component.checker().multiplicity()
                                        : fr.inra.oresing.domain.checker.Multiplicity.ONE;
                                if (component.isFilterableAsList()) {
                                    result.add(dataRepo.getColumnDistinctValues(
                                            refType, component.componentKey(), multiplicity));
                                } else {
                                    result.add(dataRepo.getColumnHasEmpty(
                                            refType, component.componentKey(), multiplicity));
                                }
                            } catch (Exception e) {
                                log.warn("Failed to load filter metadata for {}::{} - dropdown / hasEmpty defaults",
                                        refType, component.componentKey(), e);
                            }
                        }));

        return result;
    }

    /**
     * Sérialise la liste d'entrées en JSON et la stocke dans le cache.
     */
    private String serializeAndCache(String cacheKey, List<FilterListEntry> list) {
        try {
            String json = cacheObjectMapper.writeValueAsString(list);
            if (filterListCache.size() >= FILTER_LIST_CACHE_MAX_ENTRIES) {
                filterListCache.entrySet().stream()
                        .min(java.util.Comparator.comparingLong(e -> e.getValue().timestamp()))
                        .ifPresent(oldest -> filterListCache.remove(oldest.getKey()));
            }
            filterListCache.put(cacheKey, new FilterListCacheEntry(json, System.currentTimeMillis()));
            return json;
        } catch (Exception e) {
            log.error("Failed to serialize filterList for {}", cacheKey, e);
            return "[]";
        }
    }

    /**
     * Reconstruit le cache des filtres pour un dataType donné, en asynchrone.
     * À appeler après un dépôt ou une suppression de données RÉUSSIE.
     *
     * L'ancien cache reste lisible pendant la reconstruction (pas de suppression préalable).
     * Le nouveau résultat remplace l'ancien atomiquement via ConcurrentHashMap.put().
     * En cas d'erreur SQL, l'ancien cache reste en place — pas de perte de service.
     */
    public void refreshFilterListCache(final Application application, final String refType) {
        log.info("filterList cache refresh started for {}::{}", application.getName(), refType);
        String cacheKey = application.getName() + "::" + refType;
        // Réutilise computeFilterListEntries() qui agrège les FilterList ( SQL
        // historique ) et les ColumnDistinctValues ( colonnes __FILTER_LIST__ ).
        // L'opération reste asynchrone via Mono.fromCallable + boundedElastic.
        reactor.core.publisher.Mono.fromCallable(() -> computeFilterListEntries(application, refType))
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .doOnNext(entries -> {
                    serializeAndCache(cacheKey, entries);
                    log.info("filterList cache refreshed for {}", cacheKey);
                })
                .doOnError(error -> log.warn(
                        "Failed to refresh filterList cache for {}::{}",
                        application.getName(), refType, error))
                .subscribe();
    }

    /**
     * Invalide le cache filterList pour une application et un dataType donnés.
     * Utilisé par le endpoint GET /filters?refresh=true pour forcer un rechargement manuel.
     */
    public void invalidateFilterListCache(final Application application, final String refType) {
        String cacheKey = application.getName() + "::" + refType;
        filterListCache.remove(cacheKey);
        log.info("filterList cache invalidated for {}", cacheKey);
    }

    /**
     * Invalide tout le cache filterList (toutes les applications, tous les dataTypes).
     */
    public void invalidateAllFilterListCaches() {
        filterListCache.clear();
        log.info("All filterList caches invalidated");
    }

    public void readEntry(File zipBundleFile, String entryName, Consumer<InputStream> consumer) throws IOException {
        try (ZipFile zipFile = new ZipFile(zipBundleFile)) {
            ZipEntry entry = zipFile.getEntry(entryName);
            if (entry == null) throw new FileNotFoundException("Entrée absente: " + entryName);
            try (InputStream is = zipFile.getInputStream(entry)) {
                consumer.accept(is); // tout traitement doit être fait ici
            }
        }
    }


}