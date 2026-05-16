package fr.inra.oresing.domain.data.deposit.context;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.HierarchicalNode;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Tag;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
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
import fr.inra.oresing.domain.Mapper;
import fr.inra.oresing.domain.data.deposit.BuildColumns;
import fr.inra.oresing.domain.exceptions.ExceptionMessage;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public record AsynchroneFileImporterContext(
        ContextConstants contextConstants,
        PublishContext.PublishContextBuilder publishContextBuilder,
        ImmutableSet<LineChecker<? extends FieldType<?>>> lineCheckers,
        Set<LineChecker<? extends FieldType<?>>> transformedLineCheckers,
        Mapper jsonRowMapper,
        ConcurrentHashMap<DataValue.LineIdentityColumnName, UUID> afterPreloadReferenceUuids,
        ConcurrentHashMap<Ltree, List<RowWithReferenceDatum>> missingParentLine,
        ImmutableMap<DataValue.LineIdentityColumnName, UUID> storedReferences,
        ImmutableMap<HkPatternKey, UUID> storedReferencesByHkPattern,
        Map<NaturalKeyPattern, UUID> naturalKeyPatternIndex,
        // PERF : prev type was {@code SetMultimap} backed by
        // {@code Multimaps.synchronizedSetMultimap(HashMultimap.create())} which
        // serialized all N parallel transform workers on a global mutex for
        // every {@code put(key, lineNumber)} - audit shows 15-20% throughput
        // loss on 4+ workers . ConcurrentHashMap uses bucket locking ( 16+
        // segments ) + {@code computeIfAbsent} for atomic get-or-create + the
        // inner Set is {@code ConcurrentHashMap.newKeySet()} = lock-free add .
        // Net : near-linear scaling vs parallelism vs global-lock SetMultimap .
        ConcurrentMap<Ltree, Set<Long>> encounteredHierarchicalKeysForConflictDetection, //asynchronous
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

    /**
     * Cle composite ( naturalKey , patternColumnName ) pour le lookup O(1)
     * dans {@link #getKnownId} . Pendant la recursion ,
     * {@link fr.inra.oresing.domain.data.deposit.recursion.WithRecursion}
     * doit passer par {@link #putAfterPreload} pour maintenir l'index .
     * Cf AUDIT 06-05-26 #2 .
     */
    public record NaturalKeyPattern(Ltree naturalKey, String patternColumnName) {}

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
            Mapper jsonRowMapper,
            DataRepository referenceValueRepository) {

        final StandardDataDescription referenceDescription = constants.dataConfiguration();
        final Map<Class<? extends ComponentDescription>, List<Map.Entry<String, ComponentDescription>>> componentDescriptionEntryByComputedType = referenceDescription.componentDescriptions()
                .entrySet().stream()
                .collect(Collectors.groupingBy(entry -> (entry.getValue().getClass())));

        BuildColumns result = BuildColumns.buildColumns(componentDescriptionEntryByComputedType, referenceValueRepository);

        // Refacto B ( perf : skip ~110s + ~2.1 GB heap sur datatypes
        // non-recursifs gros , 16-05-26 ) : la map nk->id n'est plus
        // necessaire en RAM cote Java pour les datatypes non recursifs .
        // Les ids existants seront retrouves directement en SQL via JOIN
        // post-UPSERT dans {@link DataRepository#storeAll} ( refref_pending
        // build par JOIN sur referencevalue plutot que via s.data->>'id' ) .
        //
        // Pour le NON-recursif : id reste random ( generation
        // {@code UUID.randomUUID()} cote {@code OreSiEntity} ) , puis
        // l'UPSERT preserve l'id existant via ON CONFLICT DO UPDATE pour
        // les rows en collision , INSERT l'id NEW pour les nouvelles .
        // Le JOIN SQL post-UPSERT recupere rv.id correct dans tous les cas .
        //
        // Pour le RECURSIF : la map est OBLIGATOIRE car WithRecursion chaine
        // les UUIDs parent->child au sein du run via
        // {@code addKnownIdToReferenceValues} . Garder le path actuel .
        boolean isRecursive = constants.application().getConfiguration()
                .findCompositeReferencesUsing(constants.refType())
                .filter(HierarchicalNode::isRecursive)
                .isPresent();

        ImmutableMap<DataValue.LineIdentityColumnName, UUID> storedReferences =
                isRecursive
                        ? referenceValueRepository.getDataIdPerKeys(constants.refType())
                        : ImmutableMap.of();

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

        // AUDIT 06-05-26 #2 : index secondaire ( naturalKey , patternColumnName )
        // -> UUID pour ramener getKnownId d'un scan O(N) a un lookup O(1) .
        // Mutable ConcurrentHashMap car WithRecursion ajoute des entries
        // pendant la phase transform via putAfterPreload .
        Map<NaturalKeyPattern, UUID> nkIndex = new ConcurrentHashMap<>(storedReferences.size() * 2);
        for (Map.Entry<DataValue.LineIdentityColumnName, UUID> entry : storedReferences.entrySet()) {
            DataValue.LineIdentityColumnName k = entry.getKey();
            nkIndex.putIfAbsent(
                    new NaturalKeyPattern(k.naturalKey(), k.patternColomnName()),
                    entry.getValue());
        }

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
                nkIndex,
                new ConcurrentHashMap<>(),
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

    /**
     * Retourne {@code true} si la validation portant le checker récursif est annotée
     * avec le tag {@link Tag.OrderStrictTag} ({@code __ORDER_STRICT__}).
     *
     * <p>Ce tag indique que le fichier CSV est garanti ordonné (parents avant enfants),
     * ce qui permet d'activer le mode à récursion ordonnée sans configuration Spring Boot
     * globale.
     */
    public boolean isOrderStrictTaggedOnRecursiveValidation() {
        return contextConstants().dataConfiguration().validations().values().stream()
                .filter(vd -> vd.checkers().values().stream()
                        .anyMatch(cd -> cd instanceof ReferenceChecker rc && rc.isRecursive()))
                .anyMatch(vd -> vd.tags() != null
                        && vd.tags().stream().anyMatch(Tag.OrderStrictTag.class::isInstance));
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
        // AUDIT 06-05-26 #2 : index secondaire O(1) sur ( naturalKey ,
        // patternColumnName ) -> UUID , maintenu en parallele de
        // afterPreloadReferenceUuids . Pre-rempli dans of(...) a partir
        // de storedReferences ; les puts ulterieurs ( WithRecursion )
        // doivent passer par putAfterPreload pour rester coherents .
        // Si l'index miss ( workflows pre-fix qui appellent .put direct
        // sans maintenir l'index ) , fallback sur scan O(N) defensif .
        Map<NaturalKeyPattern, UUID> idx = naturalKeyPatternIndex();
        if (idx != null) {
            UUID hit = idx.get(new NaturalKeyPattern(naturalKey, patternColumnName));
            if (hit != null) return Optional.of(hit);
        }
        return afterPreloadReferenceUuids().entrySet().stream()
                .filter(entry -> entry.getKey().naturalKey().equals(naturalKey) &&
                                 entry.getKey().patternColomnName().equals(patternColumnName)
                )
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * Wrapper de mutation a utiliser au lieu de
     * {@code afterPreloadReferenceUuids().put(...)} pour garantir la
     * coherence avec l'index secondaire {@link #naturalKeyPatternIndex} .
     * Cf AUDIT 06-05-26 #2 .
     */
    public void putAfterPreload(DataValue.LineIdentityColumnName key, UUID value) {
        afterPreloadReferenceUuids().put(key, value);
        Map<NaturalKeyPattern, UUID> idx = naturalKeyPatternIndex();
        if (idx != null) {
            idx.put(new NaturalKeyPattern(key.naturalKey(), key.patternColomnName()), value);
        }
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
        // computeIfAbsent atomic = bucket-level lock only ; add on
        // ConcurrentHashMap.newKeySet() is lock-free .
        encounteredHierarchicalKeysForConflictDetection()
                .computeIfAbsent(hierarchicalKey, k -> ConcurrentHashMap.newKeySet())
                .add(lineNumber);
        return keysAndReferenceDatumAfterChecking;
    }
}