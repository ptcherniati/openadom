package fr.inra.oresing.domain.data.deposit.context;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.HierarchicalNode;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.data.deposit.context.hierarchicalkey.HierarchicalKeyFactory;
import fr.inra.oresing.domain.data.deposit.recursion.RecursionStrategy;
import fr.inra.oresing.domain.data.deposit.storage.KeysAndReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.RowWithReferenceDatum;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.data.read.DataHeaderReader;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.repository.data.DataRepository;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.rest.data.BuildColumns;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public record AsynchroneFileImporterContext(
        ContextConstants contextConstants,
        PublishContext.PublishContextBuilder publishContextBuilder,
        ImmutableSet<LineChecker<? extends FieldType<?>>> lineCheckers,
        Set<LineChecker<? extends FieldType<?>>> transformedLineCheckers,
        JsonRowMapper jsonRowMapper,
        ConcurrentHashMap<DataValue.LineIdentityColumnName, UUID> afterPreloadReferenceUuids,
        ConcurrentHashMap<Ltree, List<RowWithReferenceDatum>> missingParentLine,
        ImmutableMap<DataValue.LineIdentityColumnName, UUID> storedReferences,
        SetMultimap<Ltree, Long> encounteredHierarchicalKeysForConflictDetection, //asynchronous
        Set<Column> columnsWithPatternColumns,
        Map<String, Map<String, Map<String, String>>> displayNamesByReferenceAndNaturalKey,
        List<ReferenceScope.NodeDescription> nodesForMenu,
        BuildColumns buildColumns,
        ReportErrors allErrors,
        DataHeaderReader dataHeaderReader) {
    public static final String COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR = "__";

    /**
     * Séparateur pour les clés naturelles composites.
     */
    public static String getCompositeNaturalKeyComponentsSeparator() {
        return COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR;
    }

    public static AsynchroneFileImporterContext of(
            ContextConstants constants,
            PublishContext.PublishContextBuilder publishContextBuilder,
            ImmutableSet<LineChecker<? extends FieldType<?>>> lineCheckers,
            Map<String, Map<String, Map<String, String>>> displayNamesByReferenceAndNaturalKey,
            JsonRowMapper jsonRowMapper,
            DataRepository referenceValueRepository) {

        final StandardDataDescription referenceDescription = constants.dataConfiguration();
        final Map<Class<? extends ComponentDescription>, List<Map.Entry<String, ComponentDescription>>> componentDescriptionEntryByComputedType = referenceDescription.componentDescriptions()
                .entrySet().stream()
                .collect(Collectors.groupingBy(entry -> (entry.getValue().getClass())));

        BuildColumns result = BuildColumns.buildColumns(componentDescriptionEntryByComputedType, referenceValueRepository);

        return new AsynchroneFileImporterContext(
                constants,
                publishContextBuilder,
                lineCheckers,
                new HashSet<>(),
                jsonRowMapper,
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                referenceValueRepository.getDataIdPerKeys(constants.refType()),
                Multimaps.synchronizedSetMultimap(HashMultimap.create()),
                new HashSet<>(),
                displayNamesByReferenceAndNaturalKey,
                referenceValueRepository.getNodesForMenu(MenuType.authorization),
                result,
                new ReportErrors(jsonRowMapper),
                new DataHeaderReader(result, publishContextBuilder, constants.dataConfiguration())
        );
    }

    /**
     * Si le référentiel contient des colonnes qui font références à d'autres lignes de ce même référentiel
     */
    public boolean isRecursive() {
        return getRecursiveComponentDescription().isPresent();
    }

    private Optional<HierarchicalNode> getRecursiveComponentDescription() {
        return contextConstants().application().getConfiguration().findCompositeReferencesUsing(contextConstants().refType())
                .filter(HierarchicalNode::isRecursive);
    }

    public LinkedHashSet<String> getNaturalKeyColumns() {
        return contextConstants().dataConfiguration().naturalKey();

    }

    public List<String> getNaturalKeyColumnsImportHeaders() {
        Function<String, String> getimportHeader = component -> contextConstants().dataConfiguration().componentDescriptions().get(component).importHeader();
        return getNaturalKeyColumns()
                .stream()
                .map(getimportHeader)
                .toList();
    }

    public Optional<UUID> getKnownId(final Ltree naturalKey, String patternColumnName) {
        return afterPreloadReferenceUuids().entrySet().stream()
                .filter(entry -> entry.getKey().naturalKey().equals(naturalKey) &&
                                 entry.getKey().patternColomnName().equals(patternColumnName)
                )
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public <F extends FieldType<?>> void setTransformedLineCheckers(RecursionStrategy recursionStrategy, Set<? extends LineChecker<?>> transformedLineCheckers) {
        ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues = transformedLineCheckers.stream()
                .map(LineChecker::fieldTypeForOne)
                .filter(ReferenceType.class::isInstance)
                .map(ReferenceType.class::cast)
                .filter(referenceType -> referenceType.getRefType().equals(contextConstants().refType()))
                .findAny()
                .map(ReferenceType::getReferenceValues)
                .orElseGet(ImmutableMap::of);
        referenceValues.entrySet()
                .forEach(entry -> recursionStrategy.addReferenceValuesForSelfType(entry.getKey(), entry.getValue()));

        transformedLineCheckers().addAll(lineCheckers);
    }

    public void withPatternColumn() {
        columnsWithPatternColumns().addAll(
                new ImmutableSet.Builder<Column>()
                        .addAll(buildColumns().columns())
                        .addAll(buildColumns().patternColumnFactory().getExpectedPatternColumns())
                        .build()
        );
    }

    public boolean existsColumn(final DataColumn column, Map<DataColumn, DataColumnValue> constantColumnsValues) {
        return columnsWithPatternColumns().stream()
                       .map(registeredColumn -> registeredColumn.as(column.column()))
                       .anyMatch(Objects::nonNull) ||
               constantColumnsValues.keySet().stream()
                       .map(DataColumn::column)
                       .anyMatch(c -> c.equals(column.column()));
    }

    public boolean pushValue(final DataDatum referenceDatum, final String header, final String cellContent, final Map<String, Map<String, Map<String, LinkedLines>>> refsLinkedTo) {
        final Column column = buildColumns().getExpectedColumnsPerHeaders().get(header);
        if (column == null) {
            return true;
        }
        column.pushValue(cellContent, referenceDatum, refsLinkedTo);
        return false;
    }

    public Optional<InternationalizationTitle> getDisplayPattern() {
        return Optional.ofNullable(contextConstants().displayPattern());
    }

    public Optional<UUID> getIdForSameHierarchicalKeyInDatabase(final Ltree hierarchicalKey, String patternColumnName) {
        if (storedReferences() == null) {
            return Optional.empty();
        }
        return storedReferences().entrySet().stream()
                .filter(entry ->
                        entry.getKey().hierarchicalKey().equals(hierarchicalKey) &&
                        entry.getKey().patternColomnName().equals(patternColumnName)
                )
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public String getDisplayNamesByReferenceAndNaturalKey(final String referencedColumn, final String naturalKey, final String locale) {
        return displayNamesByReferenceAndNaturalKey().getOrDefault(referencedColumn, new HashMap<>())
                .getOrDefault(naturalKey, new HashMap<>())
                .getOrDefault(locale, naturalKey);
    }

    public void registerMissingLine(Ltree hierarchicalParentKey, RowWithReferenceDatum rowWithReferenceDatum) {
        this.missingParentLine()
                .computeIfAbsent(hierarchicalParentKey, k -> new LinkedList<>())
                .add(rowWithReferenceDatum);
    }

    public DatePattern getDatepattern() {
        return contextConstants().application().findSubmissionDatePattern(contextConstants().refType());
    }

    /**
     * Les colonnes dont les valeurs composent la clé naturelle composite de chaque ligne pour ce référentiel
     */
    public ImmutableList<DataColumn> getKeyColumns() {
        Preconditions.checkState(CollectionUtils.isNotEmpty(contextConstants().dataConfiguration().naturalKey()), ExceptionMessage.MISSING_PRIMARY_KEY_COMPONENT.toMessage(), contextConstants().refType());
        return contextConstants().dataConfiguration().naturalKey().stream()
                .map(DataColumn::new)
                .collect(ImmutableList.toImmutableList());
    }

    /**
     * Crée une clé hiérarchique
     */
    public Ltree newHierarchicalKey(final Ltree recursiveNaturalKey, final DataDatum referenceDatum) {
        return getHierarchicalKeyFactory().newHierarchicalKey(recursiveNaturalKey, referenceDatum);
    }

    private HierarchicalKeyFactory getHierarchicalKeyFactory() {
        return contextConstants().hierarchicalKeyFactory();
    }

    public KeysAndReferenceDatumAfterChecking storeHierarchicalKeyForConflictDetection(
            KeysAndReferenceDatumAfterChecking keysAndReferenceDatumAfterChecking) {

        final long lineNumber = keysAndReferenceDatumAfterChecking.getLineNumber();
        final Ltree hierarchicalKey = keysAndReferenceDatumAfterChecking.hierarchicalKey();
        encounteredHierarchicalKeysForConflictDetection().put(hierarchicalKey, lineNumber);
        return keysAndReferenceDatumAfterChecking;
    }
}