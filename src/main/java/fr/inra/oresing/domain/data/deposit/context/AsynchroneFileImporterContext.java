package fr.inra.oresing.domain.data.deposit.context;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.Mapper;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.checker.ReferenceChecker;
import fr.inra.oresing.domain.application.configuration.date.DatePattern;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.BuildColumns;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.data.deposit.context.hierarchicalkey.HierarchicalKeyFactory;
import fr.inra.oresing.domain.data.deposit.recursion.RecursionStrategy;
import fr.inra.oresing.domain.data.deposit.storage.KeysAndReferenceDatumAfterChecking;
import fr.inra.oresing.domain.data.deposit.validation.transformer.data.RowWithReferenceDatum;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.data.read.DataHeaderReader;
import fr.inra.oresing.domain.exceptions.ExceptionMessage;
import fr.inra.oresing.domain.exceptions.ReportErrors;
import fr.inra.oresing.domain.repository.data.DataRepository;
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
        DataHeaderReader dataHeaderReader,
        // Axe A.3 plan resilience : loader lazy + batch coalescing pour
        // les parents recursifs non pre-charges par Axe B . Quand le
        // context est construit via {@link #ofWithNaturalKeysHint} sur un
        // refType recursif , le loader est pre-populated avec les rows
        // chargees ; sur cache miss dans {@link #getKnownId} , le loader
        // fait une query bulk SQL lazy ( par batch de naturalkeys ) au
        // lieu d'echouer . Couvre le cas incremental imports ou un row
        // CSV reference un parent qui existe deja en BDD mais n'etait
        // pas dans le prescan . Null si non-recursif ou hint absent
        // ( fallback legacy full preload ) .
        fr.inra.oresing.domain.data.deposit.prescan.LazyParentLoader lazyParentLoader) {

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
        return ofInternal(constants, publishContextBuilder, lineCheckers,
                displayNamesByReferenceAndNaturalKey, jsonRowMapper,
                referenceValueRepository, /* naturalKeysHint */ null);
    }

    /**
     * Variante du factory {@link #of(ContextConstants, PublishContext.PublishContextBuilder, ImmutableSet, Map, Mapper, DataRepository)}
     * qui accepte un hint pre-scanne des naturalkeys reellement
     * referencees par le CSV en cours d'import . Sur un refType recursif ,
     * le hint declenche un chargement <i>lazy</i> de
     * {@code storedReferences} via
     * {@link DataRepository#getDataIdPerKeysByNaturalKeys} au lieu du
     * full preload via {@link DataRepository#getDataIdPerKeys} , bornant
     * la consommation memoire a {@code O(|naturalKeysHint|)} au lieu
     * de {@code O(N_ref_size)} .
     *
     * <h2>Pourquoi</h2>
     *
     * <p>Le full preload materialise TOUTES les rows {@code referencevalue}
     * du refType en RAM Java ( 10 MB pour 100k rows , 1 GB pour 10M rows ,
     * OOM au-dela ) . Pour des refs recursifs de 10M+ rows c'est
     * inutilisable . Cette variante charge uniquement le sous-ensemble
     * effectivement reference par le CSV soumis a publication
     * ( typiquement 50-5000 valeurs distinctes ) .
     *
     * <h2>Iso-resultat garanti</h2>
     *
     * <p>Sous l'invariant {@code naturalKeysHint contient toutes les
     * naturalkeys lues par le caller au cours de l'import } , la map
     * resultante est un sous-ensemble strict de celle retournee par le
     * full preload limite aux rows reellement utilisees . Les indexes
     * {@code hkIndex} / {@code nkIndex} sont construits sur ce
     * sous-ensemble et restent semantiquement identiques pour les
     * lookups effectivement effectues par {@code WithRecursion} et
     * {@code DataTransformer} .
     *
     * <h2>Effet sur les non-recursifs</h2>
     *
     * <p>Identique a la variante legacy : {@code storedReferences =
     * ImmutableMap.of()} ( refacto B ; les ids existants sont recuperes
     * en SQL via JOIN post-UPSERT cote {@code DataRepository.storeAll} ) .
     * Le hint est ignore .
     *
     * <h2>Fallback</h2>
     *
     * <p>Si {@code naturalKeysHint} est {@code null} ou vide , la
     * methode retombe sur le chemin legacy ( full preload ) pour
     * preserver la coherence : un hint absent signifie que le caller
     * n'a pas pu pre-scanner ( CSV non disponible , prescan disable
     * par config , erreur transient ) , donc on prefere un coup couteux
     * mais sur a une perte silencieuse de rows .
     *
     * @param naturalKeysHint  set des naturalkeys composees pre-scannees
     *                         depuis le CSV ; format texte compatible
     *                         ltree . {@code null} ou vide -> fallback
     *                         legacy ( full preload ) .
     * @since openadom plan resilience Axe B
     */
    public static AsynchroneFileImporterContext ofWithNaturalKeysHint(
            ContextConstants constants,
            PublishContext.PublishContextBuilder publishContextBuilder,
            ImmutableSet<LineChecker<? extends FieldType<?>>> lineCheckers,
            Map<String, Map<String, Map<String, String>>> displayNamesByReferenceAndNaturalKey,
            Mapper jsonRowMapper,
            DataRepository referenceValueRepository,
            Set<String> naturalKeysHint) {
        return ofInternal(constants, publishContextBuilder, lineCheckers,
                displayNamesByReferenceAndNaturalKey, jsonRowMapper,
                referenceValueRepository, naturalKeysHint);
    }

    /**
     * Construction effective partagee entre les deux factories publiques .
     *
     * @param naturalKeysHint  {@code null} pour le chemin legacy ( full
     *                         preload sur recursif , {@code ImmutableMap.of()}
     *                         sur non-recursif ) ; non-vide pour le chemin
     *                         lazy ( charge uniquement les naturalkeys
     *                         requestees ) .
     */
    private static AsynchroneFileImporterContext ofInternal(
            ContextConstants constants,
            PublishContext.PublishContextBuilder publishContextBuilder,
            ImmutableSet<LineChecker<? extends FieldType<?>>> lineCheckers,
            Map<String, Map<String, Map<String, String>>> displayNamesByReferenceAndNaturalKey,
            Mapper jsonRowMapper,
            DataRepository referenceValueRepository,
            Set<String> naturalKeysHint) {

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

        // Axe B : sur refType recursif , si le caller a fourni un hint
        // pre-scanne des naturalkeys reellement referencees par le CSV ,
        // utilise getDataIdPerKeysByNaturalKeys ( O(|hint|) RAM + index
        // btree nk_patternColumnNam_type ) au lieu de getDataIdPerKeys
        // ( O(N_ref_size) RAM ) . Sur des refs 10M+ rows cela evite l'OOM .
        // Sans hint -> fallback legacy ( full preload ) pour preserver la
        // coherence quand le pre-scan n'est pas disponible .
        ImmutableMap<DataValue.LineIdentityColumnName, UUID> storedReferences;
        if (!isRecursive) {
            storedReferences = ImmutableMap.of();
        } else if (naturalKeysHint != null && !naturalKeysHint.isEmpty()) {
            storedReferences = referenceValueRepository.getDataIdPerKeysByNaturalKeys(
                    constants.refType(), naturalKeysHint);
        } else {
            storedReferences = referenceValueRepository.getDataIdPerKeys(constants.refType());
        }

        // B4 / #5 : index O(1) pour {@link #getIdForSameHierarchicalKeyInDatabase}.
        // Cette methode est appelee par ligne par DataTransformer ; sur un
        // referentiel deja peuple de N lignes et un import de M lignes ,
        // l'ancien stream().filter() faisait O(N x M) operations. L'index
        // ramene chaque lookup a O(1) au prix d'un seul parcours initial.
        // En cas de collisions ( meme HK + pattern , peu probable mais
        // possible historiquement ) on garde la PREMIERE occurrence ,
        // identique au .findFirst() de l'ancien stream.
        Map<HkPatternKey, UUID> hkIndexTmp = HashMap.newHashMap(storedReferences.size() * 2);
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

        // Axe A.3 : si recursif + hint fourni , pre-populated lazy loader
        // pour permettre fallback sur cache miss ( ex : import incremental
        // ou un row CSV reference un parent deja en BDD non present dans
        // le prescan ) . Le loader est pre-warmed avec les rows deja
        // chargees ( storedReferences ) pour eviter de re-query celles-ci .
        // Null pour non-recursif ou si hint absent ( comportement legacy ) .
        final fr.inra.oresing.domain.data.deposit.prescan.LazyParentLoader lazyLoader;
        if (isRecursive && naturalKeysHint != null && !naturalKeysHint.isEmpty()) {
            lazyLoader = new fr.inra.oresing.domain.data.deposit.prescan.LazyParentLoader(
                    referenceValueRepository, constants.refType());
            for (Map.Entry<DataValue.LineIdentityColumnName, UUID> entry : storedReferences.entrySet()) {
                lazyLoader.putKnown(entry.getKey().naturalKey(), entry.getValue());
            }
        } else {
            lazyLoader = null;
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
                new DataHeaderReader(result, publishContextBuilder, constants.dataConfiguration()),
                lazyLoader
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
        Optional<UUID> scanHit = afterPreloadReferenceUuids().entrySet().stream()
                .filter(entry -> entry.getKey().naturalKey().equals(naturalKey) &&
                                 entry.getKey().patternColomnName().equals(patternColumnName)
                )
                .map(Map.Entry::getValue)
                .findFirst();
        if (scanHit.isPresent()) {
            return scanHit;
        }
        // Axe A.3 : fallback lazy DB lookup pour le cas incremental
        // import ou un parent existe deja en BDD mais n'etait pas dans
        // le prescan ( hint Axe B ) . Si lazyParentLoader present
        // ( recursif + hint ) , on declenche une query lazy bornee par
        // negative cache pour ne pas re-query les naturalkeys deja
        // confirmees absentes . Si null ( non-recursif ou full preload
        // legacy ) , semantique inchangee .
        if (lazyParentLoader != null && naturalKey != null) {
            UUID cached = lazyParentLoader.getCachedId(naturalKey);
            if (cached != null) {
                // Promote dans l'index local pour eviter de retraverser
                // le loader sur les acces ulterieurs ( WithRecursion
                // peut iterer plusieurs fois sur la meme naturalkey ) .
                if (idx != null) {
                    idx.putIfAbsent(new NaturalKeyPattern(naturalKey, patternColumnName), cached);
                }
                return Optional.of(cached);
            }
            lazyParentLoader.request(naturalKey);
            lazyParentLoader.flush();
            UUID loaded = lazyParentLoader.getCachedId(naturalKey);
            if (loaded != null) {
                if (idx != null) {
                    idx.putIfAbsent(new NaturalKeyPattern(naturalKey, patternColumnName), loaded);
                }
                return Optional.of(loaded);
            }
        }
        return Optional.empty();
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