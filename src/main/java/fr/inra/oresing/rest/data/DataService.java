package fr.inra.oresing.rest.data;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import com.google.common.io.Resources;
import fr.inra.oresing.client.Client;
import fr.inra.oresing.domain.BinaryFile;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.GroovyDataInjectionConfiguration;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.checker.CheckerFactory;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.*;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.deposit.context.ContextConstants;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import fr.inra.oresing.domain.data.deposit.context.column.*;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.data.rapport.BundleReport;
import fr.inra.oresing.domain.data.rapport.Manifest;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.file.DataFile;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisation;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisationForBuildBundleReport;
import fr.inra.oresing.domain.filesenderclient.FileSenderInternationalisationForDownloadDatasetQuery;
import fr.inra.oresing.domain.groovy.Expression;
import fr.inra.oresing.domain.groovy.GroovyContextHelper;
import fr.inra.oresing.domain.groovy.StringGroovyExpression;
import fr.inra.oresing.domain.groovy.StringSetGroovyExpression;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;
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
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
public class DataService {
    public static final String OPEN_ADOM_CLIENT_GROOVY = "OpenAdomClient.groovy";
    public static final String OPEN_ADOM_CLIENT_JS = "bundle.js";
    public static final String OPEN_ADOM_CLIENT_HTML = "index.html";
    public static final String OPEN_ADOM_CLIENT_CONFIGURATION_JSON = "openAdom-client-configuration.json";
    public static final String README_FILE_NAME = "README";
    public static final String SCRIPTS = "Scripts";
    public static final String SETUP_SCRIPT_NAME = "setup.sh";
    public static final String MANIFEST_JSON = "manifest.json";
    public static final String CONFIGURATION = "Configuration";
    public static final String CONFIGURATION_FILE = "configuration.yaml";
    public static final String COMPOSE = "compose/";
    @Setter
    ServiceContainer serviceContainer;
    private final OreSiRepository repo;
    private final JsonRowMapper jsonRowMapper;
    private final OreSiRepository repository;
    private final FileRepository fileRepository;

    public DataService(
            OreSiRepository repo,
            JsonRowMapper jsonRowMapper,
            OreSiRepository repository,
            FileRepository fileRepository,
            ServiceContainer serviceContainer) {
        this.repo = repo;
        this.jsonRowMapper = jsonRowMapper;
        this.repository = repository;
        this.fileRepository = fileRepository;
        this.serviceContainer = serviceContainer;
    }

    private static ImmutableSet<Column> dynamicColumnDescriptionToColumns(final DataRepository referenceValueRepository, final DataColumn referenceColumn, final ReferenceDynamicColumnDescription referenceDynamicColumnDescription, TransformationConfiguration defaultValue) {
        final String reference = referenceDynamicColumnDescription.reference();
        final DataColumn referenceColumnToLookForHeader = new DataColumn(referenceDynamicColumnDescription.referenceColumnToLookForHeader());
        final List<DataValue> allByReferenceType = referenceValueRepository.findAllByReferenceTypeStream(reference)
                .toList();
        return allByReferenceType.stream()
                .map(referenceValue -> {
                    final DataDatum referenceDatum = referenceValue.getRefValues();
                    final Ltree naturalKey = referenceValue.getNaturalKey();
                    final DataColumnSingleValue referenceColumnValue = (DataColumnSingleValue) referenceDatum.get(referenceColumnToLookForHeader);
                    final String header = referenceColumnValue.getValue().toString();
                    final String fullHeader = referenceDynamicColumnDescription.headerPrefix() + header;
                    final ComponentPresenceConstraint presenceConstraint = referenceDynamicColumnDescription.presenceConstraint();
                    return new DynamicColumn(
                            referenceColumn,
                            presenceConstraint,
                            naturalKey,
                            Map.entry(reference, new RefsLinkedToValue(
                                            Set.of(referenceValue.getId()),
                                            naturalKey
                                    )
                            ),
                            defaultValue == null ? ComputedValueUsage.NOT_COMPUTED : ComputedValueUsage.USE_COMPUTED_AS_DEFAULT_VALUE,
                            defaultValue) {
                        @Override
                        public String getExpectedHeader() {
                            return fullHeader;
                        }

                        @Override
                        public Optional<DataColumnValue> computeValue(final DataDatum referenceDatum) {
                            throw new UnsupportedOperationException("pas de valeur calculable pour " + referenceColumn);
                        }
                    };
                }).collect(ImmutableSet.toImmutableSet());
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
        DataImporterContext referenceImporterContext = getDataImporterContext(application, refType, fileOrUUID);
        final Consumer<Stream<DataValue>> storeAll = (final Stream<DataValue> referenceValueStream) -> {
            List<UUID> uuids = referenceValueRepository.storeAll(
                    referenceValueStream
            );
            referenceValueRepository.updateConstraintForeignReferences(uuids);
        };
        final DataImporter referenceImporter = new DataImporter(referenceImporterContext, storeAll);
        referenceImporter.doImport(file, fileOrUUID.fileid());
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

    public DataImporterContext getDataImporterContext(final Application application, final String dataName, final FileOrUUID fileOrUUID) {
        final DataRepository referenceValueRepository = getReferenceValueRepository(application);
        final Configuration configuration = application.getConfiguration();
        final CheckerFactory checkerFactory = new CheckerFactory(referenceValueRepository);
        Function<String, List<DataValue>> getDatavaluesByReference = reference -> referenceValueRepository.findAllByReferenceTypeStream(reference).toList();
        PublishContext.PublishContextBuilder publishContextBuilder = new PublishContext.PublishContextBuilder(application, dataName, fileOrUUID, getDatavaluesByReference);
        final ImmutableSet<LineChecker<FieldType<?>>> lineCheckers = checkerFactory.getCheckers(application, dataName,
                publishContextBuilder);
        ImmutableMap<DataValue.LineIdentityColumnName, UUID> storedReferences = referenceValueRepository.getDataIdPerKeys(dataName);

        final StandardDataDescription referenceDescription = configuration.dataDescription().get(dataName);
        final boolean allowUnexpectedColumns = referenceDescription.allowUnexpectedColumns();
        final Map<Class<? extends ComponentDescription>, List<Map.Entry<String, ComponentDescription>>> componentDescriptionEntryByComputedType = referenceDescription.componentDescriptions()
                .entrySet().stream()
                .collect(Collectors.groupingBy(entry -> (entry.getValue().getClass())));

        BuildColumns result = buildColumns(componentDescriptionEntryByComputedType, referenceValueRepository);

        ContextConstants constants = ContextConstants.with(
                application,
                dataName);
        final Set<String> patternColumnsNames = Optional.ofNullable(constants.displayPattern())
                .map(InternationalizationTitle::getTitle)
                .map(Map::values)
                .map(HashSet::new)
                .orElseGet(HashSet::new);
        final Set<String> patternColumnsDescription = Optional.ofNullable(constants.displayPattern())
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
        Map<String, Map<String, Map<String, String>>> displayDescriptionsByReferenceAndNaturalKey =
                lineCheckers.stream()
                        .filter(lc -> lc.underlyingType() instanceof ReferenceType)
                        .map(lc -> ((ReferenceType) lc.underlyingType()).getRefType())
                        .filter(patternColumnsDescription::contains)
                        .collect(Collectors.toMap(ref ->
                                        Optional.ofNullable(referenceToColumnName.getOrDefault(ref, null))
                                                .map(List::getFirst)
                                                .orElse(ref),
                                ref -> getReferenceValueRepository(application).findDisplayByNaturalKey(ref)));
        List<ReferenceScope.NodeDescription> nodesForMenu = referenceValueRepository.getNodesForMenu(MenuType.authorization);
        return new DataImporterContext(
                constants,
                lineCheckers,
                storedReferences,
                result.columns(),
                result.patternColumnFactory(),
                jsonRowMapper,
                displayNamesByReferenceAndNaturalKey,
                displayDescriptionsByReferenceAndNaturalKey,
                allowUnexpectedColumns,
                dataName,
                publishContextBuilder,
                nodesForMenu
        );
    }


    private DataService.BuildColumns buildColumns(Map<Class<? extends ComponentDescription>, List<Map.Entry<String, ComponentDescription>>> componentDescriptionEntryByComputedType, DataRepository referenceValueRepository) {
        final ImmutableSet<Column> staticColumns = componentDescriptionEntryByComputedType
                .getOrDefault(
                        BasicComponent.class,
                        new LinkedList<>()
                ).stream()
                .map(entry -> {
                    final ComponentDescription basicComponent = entry.getValue();
                    final TransformationConfiguration defaultValue = basicComponent.defaultValue();
                    final DataColumn referenceColumn = new DataColumn(entry.getKey());
                    final String headerForReferenceColumn = Optional.of(basicComponent)
                            .map(ComponentDescription::importHeader)
                            .orElse(entry.getKey());
                    final ComponentPresenceConstraint mandatory = Optional.of(basicComponent)
                            .map(ComponentDescription::mandatory)
                            .orElse(ComponentPresenceConstraint.MANDATORY);
                    final Set<? extends Tag> tags = Optional.of(basicComponent)
                            .map(ComponentDescription::tags)
                            .orElse(Set.of(Tag.NoTag.instance()));
                    final CheckerDescription checker = Optional.of(basicComponent)
                            .map(ComponentDescription::checker)
                            .orElse(null);
                    final Multiplicity multiplicity = Optional.ofNullable(basicComponent.checker()).map(CheckerDescription::multiplicity).orElse(Multiplicity.ONE);
                    return Optional.ofNullable(defaultValue)
                            .map(defaultValueConfiguration -> Column.staticColumnDescriptionToColumn(
                                    referenceColumn,
                                    headerForReferenceColumn,
                                    mandatory,
                                    multiplicity,
                                    defaultValueConfiguration))
                            .orElseGet(() -> Column.staticColumnDescriptionToColumn(
                                    referenceColumn,
                                    headerForReferenceColumn,
                                    mandatory,
                                    multiplicity,
                                    defaultValue));
                }).collect(ImmutableSet.toImmutableSet());

        final ImmutableSet<Column> computedColumns = componentDescriptionEntryByComputedType
                .getOrDefault(
                        ComputedComponent.class,
                        new LinkedList<>()
                ).stream()
                .map(entry -> {
                    final DataColumn referenceColumn = new DataColumn(entry.getKey());
                    final ComputedComponent computedComponent = (ComputedComponent) entry.getValue();
                    final Multiplicity multiplicity = Optional.ofNullable(computedComponent)
                            .map(ComputedComponent::checker)
                            .map(CheckerDescription::multiplicity)
                            .orElse(Multiplicity.ONE);

                    final ComponentPresenceConstraint mandatory = Optional.ofNullable(computedComponent)
                            .map(ComponentDescription::mandatory)
                            .orElse(ComponentPresenceConstraint.MANDATORY);
                    final Set<? extends Tag> tags = Optional.ofNullable(computedComponent)
                            .map(ComponentDescription::tags)
                            .orElse(Set.of(Tag.NoTag.instance()));
                    final CheckerDescription checker = Optional.ofNullable(computedComponent)
                            .map(ComputedComponent::computationChecker)
                            .orElse(null);
                    final String headerForReferenceColumn = Optional.ofNullable(entry.getValue())
                            .map(ComponentDescription::importHeader)
                            .orElse(entry.getKey());
                    final ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription =
                            new ReferenceStaticComputedColumnDescription(
                                    mandatory,
                                    tags,
                                    checker,
                                    headerForReferenceColumn,
                                    Objects.requireNonNull(computedComponent).transformation());
                    return computedColumnDescriptionToColumn(referenceValueRepository, referenceColumn, multiplicity, referenceStaticComputedColumnDescription);
                }).collect(ImmutableSet.toImmutableSet());

        final ImmutableSet<Column> dynamicColumns = componentDescriptionEntryByComputedType
                .getOrDefault(
                        DynamicComponent.class,
                        new LinkedList<>()).stream()
                .flatMap(entry -> {
                    final DataColumn referenceColumn = new DataColumn(entry.getKey());
                    final DynamicComponent dynamicComponent = (DynamicComponent) entry.getValue();
                    final ComponentPresenceConstraint mandatory = Optional.ofNullable(dynamicComponent)
                            .map(ComponentDescription::mandatory)
                            .orElse(ComponentPresenceConstraint.MANDATORY);
                    final Set<? extends Tag> tags = Optional.ofNullable(dynamicComponent)
                            .map(ComponentDescription::tags)
                            .orElse(Set.of(Tag.NoTag.instance()));
                    final Multiplicity multiplicity = Optional.ofNullable(Objects.requireNonNull(dynamicComponent).checker()).map(CheckerDescription::multiplicity).orElse(Multiplicity.ONE);
                    final ReferenceDynamicColumnDescription referenceDynamicColumnDescription =
                            new ReferenceDynamicColumnDescription(
                                    mandatory,
                                    tags,
                                    null,
                                    dynamicComponent.prefix(),
                                    dynamicComponent.reference(),
                                    dynamicComponent.referenceColumnToLookForHeader()
                            );
                    final ImmutableSet<Column> valuedDynamicColumns = dynamicColumnDescriptionToColumns(referenceValueRepository, referenceColumn, referenceDynamicColumnDescription, dynamicComponent.defaultValue());
                    return valuedDynamicColumns.stream();
                }).collect(ImmutableSet.toImmutableSet());

        final PatternColumnFactory patternColumnFactory = PatternColumnFactory.of(
                referenceValueRepository,
                componentDescriptionEntryByComputedType.getOrDefault(
                                PatternComponent.class,
                                new LinkedList<>())
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> (PatternComponent) e.getValue()))
        );

        final ImmutableSet<Column> columns = ImmutableSet.<Column>builder()
                .addAll(staticColumns)
                .addAll(computedColumns)
                .addAll(dynamicColumns)
                .build();
        return new BuildColumns(patternColumnFactory, columns);
    }

    private Column computedColumnDescriptionToColumn(final DataRepository referenceValueRepository,
                                                     final DataColumn referenceColumn,
                                                     final Multiplicity multiplicity,
                                                     final ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription) {
        Column column = null;
        if (multiplicity == Multiplicity.ONE) {
            column = newComputedColumn(referenceColumn, referenceStaticComputedColumnDescription, referenceValueRepository);
        } else if (multiplicity == Multiplicity.MANY) {
            column = newComputedManyColumn(referenceColumn, referenceStaticComputedColumnDescription, referenceValueRepository);
        } else {
            //TODO throw Multiplicity.getError(multiplicity);
        }
        return column;
    }

    private Column newComputedManyColumn(final DataColumn referenceColumn, final ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription, final DataRepository referenceValueRepository) {
        final TransformationConfiguration computation = referenceStaticComputedColumnDescription.computation();
        final Map<String, Object> contextForExpression = computeGroovyContext(referenceValueRepository, computation);
        final Expression<Set<String>> computationExpression = StringSetGroovyExpression.forExpression(computation.expression());
        return new ManyValuesStaticColumn(referenceColumn, referenceColumn.column(), ComponentPresenceConstraint.ABSENT, ComputedValueUsage.USE_COMPUTED_VALUE, null) {
            @Override
            public String getExpectedHeader() {
                throw new UnsupportedOperationException("la colonne " + referenceColumn + " est calculée, il n'y a pas d'entête spécifié car elle ne doit pas être dans le CSV");
            }

            @Override
            public Optional<DataColumnValue> computeValue(final DataDatum referenceDatum) {
                final ImmutableMap<String, Object> evaluationContext = ImmutableMap.<String, Object>builder()
                        .putAll(contextForExpression)
                        .putAll(referenceDatum.getEvaluationContext())
                        .build();
                final Set<String> evaluate = computationExpression.evaluate(evaluationContext);
                return Optional.ofNullable(evaluate)
                        .map(l -> l.stream().map(StringType::getStringTypeFromStringValue)
                                .collect(Collectors.toCollection(LinkedList<FieldType<?>>::new)))
                        .map(DataColumnMultipleValue::new);
            }
        };
    }

    private Column newComputedColumn(final DataColumn referenceColumn, final ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription, final DataRepository referenceValueRepository) {
        final TransformationConfiguration computation = referenceStaticComputedColumnDescription.computation();
        final Map<String, Object> contextForExpression = computeGroovyContext(referenceValueRepository, computation);
        final Expression<String> computationExpression = StringGroovyExpression.forExpression(computation.expression(), computation.exceptionMessages());
        return new OneValueStaticColumn(referenceColumn, referenceColumn.column(), ComponentPresenceConstraint.ABSENT, ComputedValueUsage.USE_COMPUTED_VALUE, null) {
            @Override
            public String getExpectedHeader() {
                throw new UnsupportedOperationException("la colonne " + referenceColumn + " est calculée, il n'y a pas d'entête spécifié");
            }

            @Override
            public Optional<DataColumnValue> computeValue(final DataDatum referenceDatum) {
                final ImmutableMap<String, Object> evaluationContext = ImmutableMap.<String, Object>builder()
                        .putAll(contextForExpression)
                        .putAll(referenceDatum.getEvaluationContext())
                        .build();
                final String evaluate = computationExpression.evaluate(evaluationContext);
                return Optional.ofNullable(evaluate)
                        .map(s -> StringUtils.isEmpty(s) ? "" : s)
                        .map(StringType::getStringTypeFromStringValue)
                        .map(DataColumnSingleValue::new);
            }
        };
    }

    private Map<String, Object> computeGroovyContext(final DataRepository referenceValueRepository, final GroovyDataInjectionConfiguration groovyDataInjectionConfiguration) {
        if (Optional.ofNullable(groovyDataInjectionConfiguration)
                .map(GroovyDataInjectionConfiguration::getReferences).isEmpty()) {
            return Map.of();
        }
        final Set<String> configurationReferences = groovyDataInjectionConfiguration.getReferences();
        return GroovyContextHelper.getGroovyContextForReferences(referenceValueRepository, configurationReferences, null);
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
                .getDataCsvBuilder((appOrName, referenceType) -> getDataImporterContext(application, referenceType, null))
                .withDownloadDatasetQuery(downloadDatasetQuery)
                .withReferenceService(this)
                .withOutputStream(outputStream)
                .onRepositories(getDataRepository(application), null)
                .addDatas(datas)
                .buildDataCsv(downloadDatasetQuery.getLanguage(), dataDescription, downloadDatasetQuery.horizontalDisplay());
    }

    public List<ApplicationResult.DataSynthesis> getReferenceSynthesis(final Application application) {
        return getReferenceValueRepository(application).buildReferenceSynthesis();
    }

    public Boolean getDataFromStoredCsvStream(Manifest manifest, ZipOutputStream zipOutputStream, String name, String reference, Application application, Locale locale) {
        DataRepository dataRepository = repo.getRepository(application).data();
        Flux<FileContent> storedData = dataRepository.getStoredData(application, reference);

        return storedData
                .flatMap(fileContent -> Mono.fromCallable(() -> {
                    String entryName = String.format("%s/%s", reference, fileContent.fileName());
                    manifest.add(reference, fileContent);
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zipOutputStream.putNextEntry(zipEntry);
                    try (InputStream is = fileContent.fileContent()) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = is.read(buffer)) >= 0) {
                            zipOutputStream.write(buffer, 0, length);
                        }
                    }catch (Exception e){
                        manifest.addError(reference, fileContent);
                    }
                    zipOutputStream.closeEntry();
                    return true;
                }))
                .collectList() // attend la fin du flux
                .map(list -> list.stream().anyMatch(b -> b)) // si tu dois renvoyer true si au moins un élément a réussi
                .onErrorResume(e -> {
                    log.error("Erreur lors du traitement des données stockées", e);
                    return Mono.just(false);
                })
                .block();
    }

    public DataRepository getDataRepository(Application application) {
        return repository.getRepository(application).data();
    }

    public Mono<List<DownloadDatasetQueryByRowId>> getDownloadDatasetQueriesAsync(
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
                })
                .collectList();
    }

    @Transactional(readOnly = true)
    public void buildDataZip(
            ZipOutputStream zipOutputStream,
            DownloadDatasetQuery downloadDatasetQuery) {
        Application application = downloadDatasetQuery.application();
        DataRepository dataRepository = repository.getRepository(downloadDatasetQuery.application()).data();
        //DataRepositoryForBuffer dataRepositoryWithBuffer = getDataRepositoryWithBuffer(application);

        serviceContainer.authenticationService().setRoleForClient();

        UUIDsfromData uuiDsfromData = addDatacsv(zipOutputStream, dataRepository, downloadDatasetQuery, "%s.csv");


        getDownloadDatasetQueriesAsync(
                downloadDatasetQuery.patternDefinitionCount(),
                application,
                downloadDatasetQuery.outPut().locale(),
                dataRepository,
                uuiDsfromData.uuidsfromData(),
                downloadDatasetQuery.horizontalDisplay()
        )
                .subscribe(downloadDatasetQueries -> {
                    for (DownloadDatasetQueryByRowId downloadDatasetQueryByRowId : downloadDatasetQueries) {
                        try {
                            addDatacsv(zipOutputStream, dataRepository, downloadDatasetQueryByRowId, "references/%s.csv");
                        } catch (Exception e) {
                            throw new SiOreIllegalArgumentException("IOException", Map.of("message", Optional.ofNullable(e).map(Exception::getLocalizedMessage).orElse(OreSiTechnicalException.NO_MESSAGE)));
                        }
                    }
                    try {
                        zipOutputStream.close();
                    } catch (IOException e) {
                        throw new OreSiTechnicalException(Optional.ofNullable(ExceptionMessage.IO_EXCEPTION).map(ExceptionMessage::toMessage).orElse(OreSiTechnicalException.NO_MESSAGE), e);
                    }
                });
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

    public UUIDsfromData addDatacsv(
            final ZipOutputStream zipOutputStream,
            DataRepository dataRepository,
            final DownloadDatasetQuery downloadDatasetQuery,
            String fileNamePattern) {
        final Flux<DataRow> datas = serviceContainer.dataService().findDataFlux(downloadDatasetQuery);
        try {
            AdditionalFileRepository additionalFileRepository = repository.getRepository(downloadDatasetQuery.application()).additionalBinaryFile();
            return DataCsvBuilder.getDataCsvBuilder((applicationNameOrId, referenceType) -> serviceContainer.dataService().getDataImporterContext(downloadDatasetQuery.application(), referenceType, null))
                    .withDownloadDatasetQuery(downloadDatasetQuery)
                    .withReferenceService(serviceContainer.dataService())
                    .withOutputStream(zipOutputStream)
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

                    /*sendUploadZipEmail(
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
    public BuildBundleReport writeUploadBundle(String instanceUrl, String nameOrId, boolean withData, Locale locale, ZipOutputStream zipOutputStream) {
        Application application = serviceContainer.applicationService().getApplication(nameOrId);
        String applicationName = application.getName();
        List<String> referentielsAvecDonnees = new ArrayList<>();
        Map<String, Set<String>> fichiersGeneres = new HashMap<>();
        List<String> referentielsAvecDonneesExemple = new ArrayList<>();
        List<String> referentielsEnErreur = new ArrayList<>();
        Optional.of(locale)
                .orElseGet(application.getConfiguration().applicationDescription()::defaultLanguage);

        try (zipOutputStream) {
            writeJsClient(zipOutputStream, fichiersGeneres);
            writeHtml(zipOutputStream, fichiersGeneres);
            writeConfiguration(zipOutputStream, fichiersGeneres, instanceUrl, nameOrId);
            writeConfigurationFile(zipOutputStream, fichiersGeneres, application);
            writeReadMe(zipOutputStream, fichiersGeneres);
            writeDirectoryToZip(zipOutputStream, fichiersGeneres);
            Manifest manifest = new Manifest();

            // Traiter chaque référentiel
            for (String reference : application.getConfiguration().dataDescription().keySet()) {
                String fileName = application.getConfiguration().findData(reference)
                        .map(StandardDataDescription::submission)
                        .map(Submission::fileNameParsing)
                        .map(Submission.SubmissionFileNameParsing::createExampleSubmissionFileName)
                        .orElse("%s.csv".formatted(reference));
                String dataCsvFilePath = "%1$s/%2$s".formatted(reference, fileName);

                try {

                    final Boolean dataFromStoredCsvStream = serviceContainer.dataService()
                            .getDataFromStoredCsvStream(
                                    manifest,
                                    zipOutputStream,
                                    application.getName(),
                                    reference,
                                    application,
                                    locale);
                    if (withData && dataFromStoredCsvStream) {
                        referentielsAvecDonnees.add(reference);
                        fichiersGeneres.computeIfAbsent(reference, k -> new LinkedHashSet<>()).add(dataCsvFilePath);
                    } else {
                        zipOutputStream.putNextEntry(new ZipEntry(dataCsvFilePath));
                        application.getConfiguration().dataDescription().get(reference).buildEmptyFile(zipOutputStream);
                        zipOutputStream.flush();
                        zipOutputStream.closeEntry();
                        referentielsAvecDonneesExemple.add(reference);
                        fichiersGeneres.computeIfAbsent(reference, k -> new LinkedHashSet<>()).add(dataCsvFilePath);
                    }
                } catch (Exception e) {
                    log.error("Erreur lors du traitement du référentiel {}", reference, e);
                    referentielsEnErreur.add(reference);
                }
            }
            addManifest(zipOutputStream, manifest);
        } catch (Exception e) {
            log.error("Erreur générale lors de la création du bundle", e);
            referentielsEnErreur.add("ERREUR_GENERALE");
        }

        return new BuildBundleReport(application, referentielsAvecDonnees, fichiersGeneres, referentielsAvecDonneesExemple, referentielsEnErreur, locale);
    }

    private static void addManifest(ZipOutputStream zipOutputStream, Manifest manifest) throws IOException {
        Map<String, List<String>> orderedManifest = new LinkedHashMap<>();
        orderedManifest = manifest.orderedReferenceTypes();

        String manifestJson = new ObjectMapper().writeValueAsString(orderedManifest);
        zipOutputStream.putNextEntry(new ZipEntry(MANIFEST_JSON));
        zipOutputStream.write(manifestJson.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.flush();
        zipOutputStream.closeEntry();
    }

    private void writeGroovyClient(ZipOutputStream zipOutputStream, Map<String, Set<String>> fichiersGeneres) throws IOException {
        writeFileToZip(zipOutputStream, OPEN_ADOM_CLIENT_GROOVY, Resources.getResource(Client.class, OPEN_ADOM_CLIENT_GROOVY));
        fichiersGeneres.getOrDefault(SCRIPTS, new LinkedHashSet<>())
                .add(OPEN_ADOM_CLIENT_GROOVY);
    }

    private void writeJsClient(ZipOutputStream zipOutputStream, Map<String, Set<String>> fichiersGeneres) throws IOException {
        writeFileToZip(zipOutputStream, OPEN_ADOM_CLIENT_JS, Resources.getResource(Client.class, OPEN_ADOM_CLIENT_JS));
        fichiersGeneres.getOrDefault(SCRIPTS, new LinkedHashSet<>())
                .add(OPEN_ADOM_CLIENT_JS);
    }

    private void writeHtml(ZipOutputStream zipOutputStream, Map<String, Set<String>> fichiersGeneres) throws IOException {
        writeFileToZip(zipOutputStream, OPEN_ADOM_CLIENT_HTML, Resources.getResource(Client.class, OPEN_ADOM_CLIENT_HTML));
        fichiersGeneres.getOrDefault(SCRIPTS, new LinkedHashSet<>())
                .add(OPEN_ADOM_CLIENT_HTML);
    }

    private void writeConfiguration(ZipOutputStream zipOutputStream, Map<String, Set<String>> fichiersGeneres, String instanceUrl, String dataName) throws IOException {
        String configurationJson = """
                {
                  "instanceUrl": "%s",
                  "applicationName": "%s"
                }
                """.formatted(instanceUrl, dataName);
        writeStringToZip(zipOutputStream, OPEN_ADOM_CLIENT_CONFIGURATION_JSON, configurationJson);
        fichiersGeneres.computeIfAbsent(CONFIGURATION, k -> new LinkedHashSet<>())
                .add(OPEN_ADOM_CLIENT_CONFIGURATION_JSON);
    }

    private void writeReadMe(ZipOutputStream zipOutputStream, Map<String, Set<String>> fichiersGeneres) throws IOException {
        writeFileToZip(zipOutputStream, README_FILE_NAME, Resources.getResource(Client.class, README_FILE_NAME));
        fichiersGeneres.getOrDefault(README_FILE_NAME, new LinkedHashSet<>())
                .add(README_FILE_NAME);

    }


    private void writeConfigurationFile(ZipOutputStream zipOutputStream, Map<String, Set<String>> fichiersGeneres, Application application) throws IOException {
        final String configurationString = serviceContainer.binaryFileService().getFileWithData(application.getName(), application.getConfigFile())
                .map(BinaryFile::getFileData)
                .map(inputStream -> {
                    try {
                        return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .orElse(null);
        writeStringToZip(zipOutputStream, CONFIGURATION_FILE, configurationString);
        fichiersGeneres.computeIfAbsent(CONFIGURATION, k -> new LinkedHashSet<>())
                .add(CONFIGURATION_FILE);
    }

    private void writeDirectoryToZip(ZipOutputStream zipOutputStream, Map<String, Set<String>> fichiersGeneres) throws IOException, URISyntaxException {
        List<String> allFiles = listAllFilesFromResources(COMPOSE); // à implémenter selon ton contexte

        for (String resourcePath : allFiles) {
            String zipEntryName = COMPOSE + resourcePath; // ajoute dans le zip sous compose/
            writeFileToZip(zipOutputStream, resourcePath, Resources.getResource(Client.class, zipEntryName));
            fichiersGeneres.computeIfAbsent(COMPOSE, k -> new LinkedHashSet<>()).add(zipEntryName);
        }
    }

    private List<String> listAllFilesFromResources(String baseDirPath) throws IOException, URISyntaxException {
        List<String> fileList = new ArrayList<>();
        URL resourceUrl = Resources.getResource(Client.class, baseDirPath);  // baseDirPath = "compose"
        Path baseDir = Paths.get(resourceUrl.toURI());
        Files.walk(baseDir)
                .filter(Files::isRegularFile)
                .forEach(path -> fileList.add(baseDir.relativize(path).toString().replace("\\", "/")));
        return fileList;
    }


    private void writeFileToZip(ZipOutputStream zipOutputStream, String fileName, URL resourceUrl) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(fileName));
        byte[] fileBytes = Resources.toByteArray(resourceUrl);
        zipOutputStream.write(fileBytes);
        zipOutputStream.closeEntry();
    }

    private void writeStringToZip(ZipOutputStream zipOutputStream, String fileName, String content) throws IOException {
        zipOutputStream.putNextEntry(new ZipEntry(fileName));
        zipOutputStream.write(content.getBytes(StandardCharsets.UTF_8));
        zipOutputStream.closeEntry();
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

    public Flux<FilterList> filterList(final Application application, final String refType) {
        return repository.getRepository(application).data().getFilterList(refType);
    }

    private record BuildColumns(PatternColumnFactory patternColumnFactory, ImmutableSet<Column> columns) {
    }

    public InputStream readEntry(File zipBundleFile, String entryName) throws IOException {
        ZipFile zipFile = new ZipFile(zipBundleFile);
        ZipEntry entry = zipFile.getEntry(entryName);
        if (entry == null) throw new FileNotFoundException("Entrée absente: " + entryName);
        return zipFile.getInputStream(entry);
    }

}