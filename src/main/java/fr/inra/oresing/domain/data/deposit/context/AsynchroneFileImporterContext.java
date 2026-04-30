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
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public record AsynchroneFileImporterContext(
        ContextConstants contextConstants,
        PublishContext.PublishContextBuilder publishContextBuilder,
        ImmutableSet<LineChecker<? extends FieldType<?>>> lineCheckers,
        Set<LineChecker<? extends FieldType<?>>> transformedLineCheckers,
        JsonRowMapper<Object> jsonRowMapper,
        ConcurrentHashMap<DataValue.LineIdentityColumnName, UUID> afterPreloadReferenceUuids,
        ConcurrentHashMap<Ltree, List<RowWithReferenceDatum>> missingParentLine,
        ImmutableMap<DataValue.LineIdentityColumnName, UUID> storedReferences,
        ImmutableMap<HkPatternKey, UUID> storedReferencesByHkPattern,
        SetMultimap<Ltree, Long> encounteredHierarchicalKeysForConflictDetection, //asynchronous
        Set<Column> columnsWithPatternColumns,
        Map<String, Map<String, Map<String, String>>> displayNamesByReferenceAndNaturalKey,
        List<ReferenceScope.NodeDescription> nodesForMenu,
        BuildColumns buildColumns,
        ReportErrors allErrors,
        DataHeaderReader dataHeaderReader) {

    /**
     * Cle composite ( hierarchicalKey , patternColumnName ) pour le lookup
     * O(1) dans {@link #getIdForSameHierarchicalKeyInDatabase} . Avant ce
     * fix , chaque appel parcourait l'ImmutableMap entier en O(N) ; sur un
     * referentiel deja peuple de 100k lignes + un import 274k lignes , c'etait
     * l'un des hot path les plus chers du pipeline.
     */
    public record HkPatternKey(Ltree hierarchicalKey, String patternColumnName) {}
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
            JsonRowMapper<Object> jsonRowMapper,
            DataRepository referenceValueRepository) {

        final StandardDataDescription referenceDescription = constants.dataConfiguration();
        final Map<Class<? extends ComponentDescription>, List<Map.Entry<String, ComponentDescription>>> componentDescriptionEntryByComputedType = referenceDescription.componentDescriptions()
                .entrySet().stream()
                .collect(Collectors.groupingBy(entry -> (entry.getValue().getClass())));

        BuildColumns result = BuildColumns.buildColumns(componentDescriptionEntryByComputedType, referenceValueRepository);

        ImmutableMap<DataValue.LineIdentityColumnName, UUID> storedReferences =
                referenceValueRepository.getDataIdPerKeys(constants.refType());

        // B4 / #5 : index O(1) pour {@link #getIdForSameHierarchicalKeyInDatabase}.
        // Cette methode est appelee par ligne par DataTransformer ; sur un
        // referentiel deja peuple de N lignes et un import de M lignes ,
        // l'ancien stream().filter() faisait O(N x M) operations. L'index
        // ramene chaque lookup a O(1) au prix d'un seul parcours initial.
        // En cas de collisions ( meme HK + pattern , peu probable mais
        // possible historiquement ) on garde la PREMIERE occurrence ,
        // identique au .findFirst() de l'ancien stream.
        Map<HkPatternKey, UUID> hkIndexTmp = new HashMap<>(storedReferences.size() * 2);
        for (Map.Entry<DataValue.LineIdentityColumnName, UUID> entry : storedReferences.entrySet()) {
            DataValue.LineIdentityColumnName k = entry.getKey();
            hkIndexTmp.putIfAbsent(
                    new HkPatternKey(k.hierarchicalKey(), k.patternColomnName()),
                    entry.getValue());
        }
        ImmutableMap<HkPatternKey, UUID> hkIndex = ImmutableMap.copyOf(hkIndexTmp);

        return new AsynchroneFileImporterContext(
                constants,
                publishContextBuilder,
                lineCheckers,
                new HashSet<>(),
                jsonRowMapper,
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                storedReferences,
                hkIndex,
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

    public Set<String> getNaturalKeyColumns() {
        return contextConstants().dataConfiguration().naturalKey();
    }

    public List<String> getNaturalKeyColumnsImportHeaders() {
        UnaryOperator<String> getImportHeader = component -> contextConstants().dataConfiguration().componentDescriptions().get(component).importHeader();
        return getNaturalKeyColumns()
                .stream()
                .map(getImportHeader)
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

    public void setTransformedLineCheckers(RecursionStrategy recursionStrategy, Set<? extends LineChecker<?>> transformedLineCheckers) {
        ImmutableMap<DataValue.LineIdentityColumnName, ImmutableSet<UUID>> referenceValues = transformedLineCheckers.stream()
                .map(LineChecker::fieldTypeForOne)
                .filter(ReferenceType.class::isInstance)
                .map(ReferenceType.class::cast)
                .filter(referenceType -> referenceType.getRefType().equals(contextConstants().refType()))
                .findAny()
                .map(ReferenceType::getReferenceValues)
                .orElseGet(ImmutableMap::of);
        referenceValues.forEach(recursionStrategy::addReferenceValuesForSelfType);

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

    @SuppressWarnings("java:S3740")
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
        // O(1) lookup via storedReferencesByHkPattern ( index pre-calcule
        // dans of(...) ) au lieu de l'ancien stream().filter() O(N) execute
        // par ligne du CSV importe. Voir B4 / #5 dans PERF_AUDIT_LOCAL.md.
        if (storedReferencesByHkPattern() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                storedReferencesByHkPattern().get(new HkPatternKey(hierarchicalKey, patternColumnName)));
    }

    public String getDisplayNamesByReferenceAndNaturalKey(final String referencedColumn, final String naturalKey, final String locale) {
        return displayNamesByReferenceAndNaturalKey().getOrDefault(referencedColumn, new HashMap<>())
                .getOrDefault(naturalKey, new HashMap<>())
                .getOrDefault(locale, naturalKey);
    }

    public void registerMissingLine(Ltree hierarchicalParentKey, RowWithReferenceDatum rowWithReferenceDatum) {
        this.missingParentLine()
                .computeIfAbsent(hierarchicalParentKey, _ -> new LinkedList<>())
                .add(rowWithReferenceDatum);
    }

    @SuppressWarnings("java:S1452") // DatePattern peut être de différents types temporels selon la config
    public DatePattern<?> getDatepattern() {
        return contextConstants().application().findSubmissionDatePattern(contextConstants().refType());
    }

    /**
     * Les colonnes dont les valeurs composent la clé naturelle composite de chaque ligne pour ce référentiel
     */
    public ImmutableList<DataColumn> getKeyColumns() {
        @SuppressWarnings("java:S2629")
        String missingPrimaryKeyMsg = ExceptionMessage.MISSING_PRIMARY_KEY_COMPONENT.toMessage();
        Preconditions.checkState(CollectionUtils.isNotEmpty(contextConstants().dataConfiguration().naturalKey()), missingPrimaryKeyMsg, contextConstants().refType());
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