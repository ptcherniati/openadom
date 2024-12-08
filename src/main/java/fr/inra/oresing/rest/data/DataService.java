package fr.inra.oresing.rest.data;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.GroovyDataInjectionConfiguration;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.checker.CheckerFactory;
import fr.inra.oresing.domain.checker.InvalidDatasetContentException;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.Multiplicity;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.checker.type.StringType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.DataImporter;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.deposit.context.ContextConstants;
import fr.inra.oresing.domain.data.deposit.context.DataImporterContext;
import fr.inra.oresing.domain.data.deposit.context.column.*;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.persistence.data.read.bundle.FileContent;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQueryNoFilter;
import fr.inra.oresing.domain.data.read.query.OutPut;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.file.DataFile;
import fr.inra.oresing.domain.file.FileOrUUID;
import fr.inra.oresing.domain.groovy.Expression;
import fr.inra.oresing.domain.groovy.GroovyContextHelper;
import fr.inra.oresing.domain.groovy.StringGroovyExpression;
import fr.inra.oresing.domain.groovy.StringSetGroovyExpression;
import fr.inra.oresing.domain.transformer.transformer.TransformationConfiguration;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.rest.data.extraction.DataCsvBuilder;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;
import fr.inra.oresing.rest.HierarchicalReferenceAsTree;
import fr.inra.oresing.rest.application.ApplicationService;
import fr.inra.oresing.rest.model.application.ApplicationResult;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Component
@Transactional(readOnly = true)
public class DataService {

    private final GroovyContextHelper groovyContextHelper = new GroovyContextHelper();
    @Autowired
    private ApplicationService applicationService;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private OreSiRepository repo;
    @Autowired
    private JsonRowMapper jsonRowMapper;

    private DataRepository dataRepository;

    private static ImmutableSet<Column> dynamicColumnDescriptionToColumns(final DataRepository referenceValueRepository, final DataColumn referenceColumn, final ReferenceDynamicColumnDescription referenceDynamicColumnDescription) {
        final String reference = referenceDynamicColumnDescription.reference();
        final DataColumn referenceColumnToLookForHeader = new DataColumn(referenceDynamicColumnDescription.referenceColumnToLookForHeader());
        final List<DataValue> allByReferenceType = referenceValueRepository.findAllByReferenceTypeStream(reference)
                .toList();
        final ImmutableSet<Column> valuedDynamicColumns = allByReferenceType.stream()
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
                            ComputedValueUsage.NOT_COMPUTED
                    ) {
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
        return valuedDynamicColumns;
    }

    @Transactional()
    public UUID addData(final Application application,
                        final String dataName,
                        final DataFile file) throws IOException {
        authenticationService.setRoleForClient();
        try (final InputStream csv = new ByteArrayInputStream(file.data())) {
            addData(application, dataName, csv, file.params());
        } catch (InvalidDatasetContentException invalidDatasetContentException) {
            throw invalidDatasetContentException;
        } catch (Exception exception) {
            throw exception;
        }
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
        Optional.ofNullable(compositeReferenceDescription.node())
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
                            ;
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
        final ImmutableSet<LineChecker> lineCheckers = checkerFactory.getCheckers(application, dataName,
                publishContextBuilder);
        ImmutableMap<DataValue.LineIdentityPatternColumnName, UUID> storedReferences = referenceValueRepository.getDataIdPerKeys(dataName);

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
                .map(m -> m.stream().collect(Collectors.toSet()))
                .orElseGet(HashSet::new);
        final Set<String> patternColumnsDescription = Optional.ofNullable(constants.displayPattern())
                .map(InternationalizationTitle::getDescription)
                .map(Map::values)
                .map(m -> m.stream().collect(Collectors.toSet()))
                .orElseGet(HashSet::new);
        Map<String, List<String>> referenceToColumnName = lineCheckers.stream()
                .filter(lc -> lc.underlyingType() instanceof ReferenceType)
                .collect(Collectors.groupingBy(
                                lc -> ((ReferenceType) lc.underlyingType()).getRefType(),
                                Collectors.mapping(ReferenceType -> ((DataColumn) ReferenceType.target()).column(), Collectors.toList())
                        )
                );
        Map<String, Map<String, Map<String, String>>> displayNamesByReferenceAndNaturalKey =
                lineCheckers.stream()
                        .filter(lc -> lc.underlyingType() instanceof ReferenceType)
                        .map(lc -> ((ReferenceType) lc.underlyingType()).getRefType())
                        .filter(rt -> patternColumnsNames.contains(rt))
                        .collect(Collectors.toMap(ref ->
                                        Optional.ofNullable(referenceToColumnName.getOrDefault(ref, null))
                                                .map(l -> l.get(0))
                                                .orElse(ref),
                                ref -> getReferenceValueRepository(application).findDisplayByNaturalKey(ref)));
        Map<String, Map<String, Map<String, String>>> displayDescriptionsByReferenceAndNaturalKey =
                lineCheckers.stream()
                        .filter(lc -> lc.underlyingType() instanceof ReferenceType)
                        .map(lc -> ((ReferenceType) lc.underlyingType()).getRefType())
                        .filter(rt -> patternColumnsDescription.contains(rt))
                        .collect(Collectors.toMap(ref ->
                                        Optional.ofNullable(referenceToColumnName.getOrDefault(ref, null))
                                                .map(l -> l.get(0))
                                                .orElse(ref),
                                ref -> getReferenceValueRepository(application).findDisplayByNaturalKey(ref)));
        List<ReferenceScope.NodeDescription> nodesForMenu = referenceValueRepository.getNodesForMenu(MenuType.authorization);
        DataImporterContext referenceImporterContext =
                new DataImporterContext(
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
        return referenceImporterContext;
    }


    private DataService.BuildColumns buildColumns(Map<Class<? extends ComponentDescription>, List<Map.Entry<String, ComponentDescription>>> componentDescriptionEntryByComputedType, DataRepository referenceValueRepository) {
        final ImmutableSet<Column> staticColumns = componentDescriptionEntryByComputedType
                .getOrDefault(
                        BasicComponent.class,
                        new LinkedList<>()
                ).stream()
                .map(entry -> {
                    final ComponentDescription basicComponent = (BasicComponent) entry.getValue();
                    final TransformationConfiguration defaultValue = Optional
                            .ofNullable(basicComponent.defaultValue())
                            .orElse(null);
                    final DataColumn referenceColumn = new DataColumn(entry.getKey());
                    final String headerForReferenceColumn = Optional.ofNullable(basicComponent)
                            .map(ComponentDescription::importHeader)
                            .orElse(entry.getKey());
                    final ComponentPresenceConstraint mandatory = Optional.ofNullable(basicComponent)
                            .map(ComponentDescription::mandatory)
                            .orElse(ComponentPresenceConstraint.MANDATORY);
                    final Set<? extends Tag> tags = Optional.ofNullable(basicComponent)
                            .map(ComponentDescription::tags)
                            .orElse(Set.of(Tag.NoTag.INSTANCE()));
                    final CheckerDescription checker = Optional.ofNullable(basicComponent)
                            .map(ComponentDescription::checker)
                            .orElse(null);
                    final Multiplicity multiplicity = Optional.ofNullable(basicComponent.checker()).map(CheckerDescription::multiplicity).orElse(Multiplicity.ONE);
                    final Column column = Optional.ofNullable(defaultValue)
                            .map(defaultValueConfiguration -> Column.staticColumnDescriptionToColumn(
                                    referenceColumn,
                                    headerForReferenceColumn,
                                    mandatory,
                                    multiplicity,
                                    referenceValueRepository,
                                    defaultValueConfiguration))
                            .orElseGet(() -> Column.staticColumnDescriptionToColumn(
                                    referenceColumn,
                                    headerForReferenceColumn,
                                    mandatory,
                                    multiplicity,
                                    referenceValueRepository,
                                    defaultValue));
                    return column;
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
                            .orElse(Set.of(Tag.NoTag.INSTANCE()));
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
                                    computedComponent.transformation());
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
                            .orElse(Set.of(Tag.NoTag.INSTANCE()));
                    final Multiplicity multiplicity = Optional.ofNullable(dynamicComponent.checker()).map(CheckerDescription::multiplicity).orElse(Multiplicity.ONE);
                    final ReferenceDynamicColumnDescription referenceDynamicColumnDescription =
                            new ReferenceDynamicColumnDescription(
                                    mandatory,
                                    tags,
                                    null,
                                    dynamicComponent.prefix(),
                                    dynamicComponent.reference(),
                                    dynamicComponent.referenceColumnToLookForHeader()
                            );
                    final ImmutableSet<Column> valuedDynamicColumns = dynamicColumnDescriptionToColumns(referenceValueRepository, referenceColumn, referenceDynamicColumnDescription);
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
        BuildColumns result = new BuildColumns(patternColumnFactory, columns);
        return result;
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
        return new ManyValuesStaticColumn(referenceColumn, referenceColumn.column(), ComponentPresenceConstraint.ABSENT, ComputedValueUsage.USE_COMPUTED_VALUE) {
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
                final Optional<DataColumnValue> computedValue = Optional.ofNullable(evaluate)
                        .map(l -> l.stream().map(StringType::getStringTypeFromStringValue)
                                .collect(Collectors.toCollection(LinkedList<FieldType>::new)))
                        .map(DataColumnMultipleValue::new);
                return computedValue;
            }
        };
    }

    private Column newComputedColumn(final DataColumn referenceColumn, final ReferenceStaticComputedColumnDescription referenceStaticComputedColumnDescription, final DataRepository referenceValueRepository) {
        final TransformationConfiguration computation = referenceStaticComputedColumnDescription.computation();
        final Map<String, Object> contextForExpression = computeGroovyContext(referenceValueRepository, computation);
        final Expression<String> computationExpression = StringGroovyExpression.forExpression(computation.expression(), computation.exceptionMessages());
        return new OneValueStaticColumn(referenceColumn, referenceColumn.column(), ComponentPresenceConstraint.ABSENT, ComputedValueUsage.USE_COMPUTED_VALUE) {
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
                final Optional<DataColumnValue> computedValue = Optional.ofNullable(evaluate)
                        .map(s -> StringUtils.isEmpty(s) ? "" : s)
                        .map(StringType::getStringTypeFromStringValue)
                        .map(DataColumnSingleValue::new);
                return computedValue;
            }
        };
    }

    private Map<String, Object> computeGroovyContext(final DataRepository referenceValueRepository, final GroovyDataInjectionConfiguration groovyDataInjectionConfiguration) {
        if (!Optional.ofNullable(groovyDataInjectionConfiguration)
                .map(GroovyDataInjectionConfiguration::getReferences).isPresent()) {
            return Map.of();
        }
        final Set<String> configurationReferences = groovyDataInjectionConfiguration.getReferences();
        final ImmutableMap<String, Object> contextForExpression = GroovyContextHelper.getGroovyContextForReferences(referenceValueRepository, configurationReferences, null);
        return contextForExpression;
    }

    public List<DataValue> findReferenceAccordingToRights(final Application application, final String refType, final MultiValueMap<String, String> params) {
        if (application.getConfiguration().getHiddenData().contains(refType)) {
            return List.of();
        }
        final Set<String> hiddenComponents = application.getConfiguration().getHiddenComponentsForData(refType);
        authenticationService.setRoleForClient();
        return getReferenceValueRepository(application)
                .findAllByReferenceTypeWithReferencingReferencesStream(refType, params)
                .peek(referenceValue -> referenceValue.setRefValues(referenceValue.getRefValues().filterHidden(hiddenComponents)))
                .toList();
    }

    private DataRepository getReferenceValueRepository(Application application) {
        return repo.getRepository(application).data();
    }

    public List<UUID> deleteDataAccordingToRights(final Application application, final String refType, final MultiValueMap<String, String> params) {
        authenticationService.setRoleForClient();
        final List<UUID> list = getReferenceValueRepository(application).deleteReferenceType(refType, params);
        return list;
    }

    public Flux<DataRow> findDataFlux(final DownloadDatasetQuery downloadDatasetQuery) {
        final Application application = downloadDatasetQuery.application();
        if (application.findData(downloadDatasetQuery.dataName())
                .map(StandardDataDescription::tags)
                .filter(Tag.HiddenTag.HAS_HIDDEN_TAG_PREDICATE)
                .isPresent()) {
            return Flux.empty();
        }
        dataRepository = getDataRepository(downloadDatasetQuery);
        return dataRepository.findAllByDataTypeFlux(downloadDatasetQuery)
                .map(dataRows -> DataRow.of(downloadDatasetQuery.application().findData(downloadDatasetQuery.dataName()), dataRows));
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
        final Application application = applicationService.getApplication(applicationNameOrId);
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

        DataCsvBuilder.getDataCsvBuilder((appOrName, referenceType) ->
                        getDataImporterContext(application, referenceType, null)
                )
                .withDownloadDatasetQuery(downloadDatasetQuery
                )
                .withReferenceService(this)
                .withOutputStream(outputStream)
                .onRepositories(new DataRepositoryWithBuffer(application, dataRepository), null)
                .addDatas(datas)
                .buildDataCsv(downloadDatasetQuery.getLanguage(), dataDescription);
    }

    public List<ApplicationResult.DataSynthesis> getReferenceSynthesis(final Application application) {
        return getReferenceValueRepository(application).buildReferenceSynthesis();
    }

    public Boolean getDataFromStoredCsvStream(ZipOutputStream zipOutputStream, String name, String reference, Application application, Locale locale) {
        SubmissionType submissionStrategy = application.findData(reference)
                .map(StandardDataDescription::submission)
                .map(Submission::strategy)
                .orElse(SubmissionType.OA_INSERTION);
        dataRepository = repo.getRepository(application).data();
        Flux<FileContent> storedData = dataRepository.getStoredData(reference, submissionStrategy);

        return storedData
                .flatMap(fileContent -> Mono.fromCallable(() -> {
                    String entryName = String.format("%s/%s", reference, fileContent.fileName());
                    ZipEntry zipEntry = new ZipEntry(entryName);
                    zipOutputStream.putNextEntry(zipEntry);
                    zipOutputStream.write(fileContent.fileContent().getBytes(StandardCharsets.UTF_8));
                    zipOutputStream.closeEntry();
                    return true;
                }))
                .any(b -> b)
                .onErrorResume(e -> {
                    log.error("Erreur lors du traitement des données stockées", e);
                    return Mono.just(false);
                })
                .block();
    }

    private record BuildColumns(PatternColumnFactory patternColumnFactory, ImmutableSet<Column> columns) {
    }
}