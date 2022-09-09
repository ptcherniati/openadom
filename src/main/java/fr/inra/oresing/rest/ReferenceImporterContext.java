package fr.inra.oresing.rest;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.*;
import fr.inra.oresing.checker.LineChecker;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.model.*;
import fr.inra.oresing.model.internationalization.Internationalization;
import fr.inra.oresing.model.internationalization.InternationalizationDisplay;
import fr.inra.oresing.model.internationalization.InternationalizationMap;
import fr.inra.oresing.model.internationalization.InternationalizationReferenceMap;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.persistence.ReferenceValueRepository;
import lombok.AllArgsConstructor;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Toutes les informations nécessaires à l'import d'un référentiel donné.
 *
 * C'est un objet immuable, toutes ces informations sont constantes tout au long de l'import;
 */
@AllArgsConstructor
public class ReferenceImporterContext {

    private static final String COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR = "__";
    private final Constants constants;
    /**
     * Tous les {@link LineChecker} qui s'appliquent sur chaque ligne à importer
     */
    private final ImmutableSet<LineChecker> lineCheckers;

    /**
     * Les clés techniques de chaque clé naturelle hiérarchique de toutes les lignes existantes en base (avant l'import)
     */
    private final ImmutableMap<Ltree, UUID> storedReferences;

    private final ImmutableSet<Column> columns;

    Map<String, Map<String, Map<String, String>>> displayByReferenceAndNaturalKey;

    boolean allowUnexpectedColumns = false;

    public String getDisplayByReferenceAndNaturalKey(String referencedColumn, String naturalKey, String locale){
        return this.displayByReferenceAndNaturalKey.getOrDefault(referencedColumn, new HashMap<>())
                .getOrDefault(naturalKey, new HashMap<>())
                .getOrDefault(locale, naturalKey);
    }

    /**
     * Séparateur pour les clés naturelles composites.
     * <p>
     * Lorsqu'une clé naturelle est composite, c'est à dire composée de plusieurs {@link Configuration.ReferenceDescription#getKeyColumns()},
     * les valeurs qui composent la clé sont séparées avec ce séparateur.
     */
    public String getCompositeNaturalKeyComponentsSeparator() {
        return COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR;
    }

    public String getRefType() {
        return constants.getRefType();
    }

    public Ltree getRefTypeAsLabel() {
        return Ltree.fromUnescapedString(getRefType());
    }

    /**
     * Crée une clé hiérarchique
     */
    public Ltree newHierarchicalKey(Ltree recursiveNaturalKey, ReferenceDatum referenceDatum) {
        return getHierarchicalKeyFactory().newHierarchicalKey(recursiveNaturalKey, referenceDatum);
    }

    private HierarchicalKeyFactory getHierarchicalKeyFactory() {
        return constants.getHierarchicalKeyFactory();
    }

    /**
     * Crée une nom de référentiel hiérarchique
     */
    public Ltree newHierarchicalReference(Ltree selfHierarchicalReference) {
        return getHierarchicalKeyFactory().newHierarchicalReference(selfHierarchicalReference);
    }

    /**
     * Les colonnes dont les valeurs composent la clé naturelle composite de chaque ligne pour ce référentiel
     */
    public ImmutableList<ReferenceColumn> getKeyColumns() {
        Preconditions.checkState(!getRef().getKeyColumns().isEmpty(), "aucune colonne désignée comme clé naturelle pour le référentiel " + getRefType());
        return getRef().getKeyColumns().stream()
                .map(ReferenceColumn::new)
                .collect(ImmutableList.toImmutableList());
    }

    private Configuration.ReferenceDescription getRef() {
        return constants.getRef();
    }

    private Optional<Configuration.CompositeReferenceComponentDescription> getRecursiveComponentDescription() {
        return constants.getConf().getCompositeReferences().values().stream()
                .map(compositeReferenceDescription -> compositeReferenceDescription.getComponents().stream().filter(compositeReferenceComponentDescription -> getRefType().equals(compositeReferenceComponentDescription.getReference()) && compositeReferenceComponentDescription.getParentRecursiveKey() != null).findFirst().orElse(null))
                .filter(e -> e != null)
                .findFirst();
    }

    /**
     * Si le référentiel contient des colonnes qui font références à d'autres lignes de ce même référentiel
     */
    public boolean isRecursive() {
        return getRecursiveComponentDescription().isPresent();
    }

    /**
     * Pour un référentiel récursif, indique la colonne dans laquelle la valeur est la clé vers le parent de la ligne courante
     */
    public ReferenceColumn getColumnToLookForParentKey() {
        Preconditions.checkState(isRecursive());
        return getRecursiveComponentDescription()
                .map(Configuration.CompositeReferenceComponentDescription::getParentRecursiveKey)
                .map(ReferenceColumn::new)
                .orElseThrow(() -> new IllegalStateException("ne devrait jamais arriver (?)"));
    }

    /**
     * Le séparateur à utiliser pour distinguer les cellules du fichier CSV
     */
    public char getCsvSeparator() {
        return getRef().getSeparator();
    }

    public ImmutableSet<LineChecker> getLineCheckers() {
        return lineCheckers;
    }

    /**
     * Dans le cas d'un référentiel récursif, le {@link ReferenceLineChecker} qui porte sur la colonne contenant des valeurs faisant référence à d'autres lignes du référentiel.
     */
    public ReferenceLineChecker getReferenceLineChecker() {
        Preconditions.checkState(isRecursive());
        return getLineCheckers().stream()
                .filter(lineChecker -> lineChecker instanceof ReferenceLineChecker && ((ReferenceLineChecker) lineChecker).getRefType().equals(getRefType()))
                .map(lineChecker -> ((ReferenceLineChecker) lineChecker))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("pas de checker sur " + getRefType() + " alors qu'on est sur un référentiel récursif"));
    }

    public UUID getApplicationId() {
        return constants.getApplicationId();
    }

    public Optional<UUID> getIdForSameHierarchicalKeyInDatabase(Ltree hierarchicalKey) {
        return Optional.ofNullable(storedReferences.get(hierarchicalKey));
    }

    /**
     * @deprecated ne devrait pas être exposé
     */
    @Deprecated
    public ImmutableSet<Column> getColumns() {
        return columns;
    }

    private ImmutableMap<String, Column> getExpectedColumnsPerHeaders() {
        ImmutableMap<String, ReferenceImporterContext.Column> expectedColumnsPerHeaders = columns.stream()
                .filter(Column::isExpected)
                .collect(ImmutableMap.toImmutableMap(
                        ReferenceImporterContext.Column::getExpectedHeader,
                        Function.identity()
                ));
        return expectedColumnsPerHeaders;
    }

    public void pushValue(ReferenceDatum referenceDatum, String header, String cellContent, SetMultimap<String, UUID> refsLinkedTo) {
        Column column = getExpectedColumnsPerHeaders().get(header);
        column.pushValue(cellContent, referenceDatum, refsLinkedTo);
    }

    public ImmutableSet<String> getExpectedHeaders() {
        return getExpectedColumnsPerHeaders().keySet();
    }

    public ImmutableSet<String> getMandatoryHeaders() {
        return getExpectedColumnsPerHeaders().values().stream()
                .filter(Column::isMandatory)
                .map(Column::getExpectedHeader)
                .collect(ImmutableSet.toImmutableSet());
    }

    public String getCsvCellContent(ReferenceDatum referenceDatum, String header) {
        Column column = getExpectedColumnsPerHeaders().get(header);
        return column.getCsvCellContent(referenceDatum);
    }

    public Optional<Map<Locale, String>> getDisplayPattern() {
        return constants.getDisplayPattern();
    }

    public Map<String, Internationalization> getDisplayColumns() {
        return constants.getDisplayColumns();
    }

    public static class Constants {
        /**
         * Identifiant de l'application à laquelle le référentiel importé appartient
         */
        private final UUID applicationId;
        /**
         * La configuration de l'application qui contient le référentiel mais aussi les utilisations de ce référentiel
         */
        private final Configuration conf;
        /**
         * Le nom du référentiel
         */
        private final String refType;
        private final Optional<InternationalizationReferenceMap> internationalizationReferenceMap;
        private final Map<String, Internationalization> displayColumns;
        private final Optional<Map<Locale, String>> displayPattern;
        private final HierarchicalKeyFactory hierarchicalKeyFactory;
        private final Optional<Map<Locale, List<String>>> patternColumns;
        private final Optional<Map<Locale, List<InternationalizationDisplay.PatternSection>>> patternSection;
        Constants constants;

        public Constants(UUID applicationId, Configuration conf, String refType, ReferenceValueRepository referenceValueRepository) {
            this.applicationId = applicationId;
            this.conf = conf;
            this.refType = refType;
            this.internationalizationReferenceMap = buildInternationalizationReferenceMap(conf, refType);
            this.displayColumns = buildDisplayColumns();
            this.displayPattern = buildDisplayPattern();
            this.hierarchicalKeyFactory = buildHierarchicalKeyFactory();
            this.patternColumns = this.buildPatternColumns();
            this.patternSection = this.buildPatternSection();
        }

        public Configuration.ReferenceDescription getRef() {
            return conf.getReferences().get(refType);
        }

        private Optional<InternationalizationReferenceMap> buildInternationalizationReferenceMap(Configuration conf, String refType) {
            Optional<InternationalizationReferenceMap> internationalizationReferenceMap = Optional.ofNullable(conf)
                    .map(Configuration::getInternationalization)
                    .map(InternationalizationMap::getReferences)
                    .map(references -> references.getOrDefault(refType, null));
            return internationalizationReferenceMap;
        }

        private Map<String, Internationalization> buildDisplayColumns() {
            return this.internationalizationReferenceMap
                    .map(InternationalizationReferenceMap::getInternationalizedColumns)
                    .orElseGet(HashMap::new);
        }

        private Optional<Map<Locale, String>> buildDisplayPattern() {
            return this.internationalizationReferenceMap
                    .map(InternationalizationReferenceMap::getInternationalizationDisplay)
                    .map(InternationalizationDisplay::getPattern);
        }


        private HierarchicalKeyFactory buildHierarchicalKeyFactory() {
            HierarchicalKeyFactory hierarchicalKeyFactory = HierarchicalKeyFactory.build(conf, refType);
            return hierarchicalKeyFactory;
        }

        private Optional<Map<Locale, List<InternationalizationDisplay.PatternSection>>> buildPatternSection() {
            return displayPattern
                    .map(dp -> dp.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, k -> InternationalizationDisplay.parsePattern(k.getValue()))));
        }

        private Optional<Map<Locale, List<String>>> buildPatternColumns() {
            return displayPattern
                    .map(dp -> dp.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, k -> InternationalizationDisplay.getPatternColumns(k.getValue()))));
        }

        public UUID getApplicationId() {
            return applicationId;
        }

        public Configuration getConf() {
            return conf;
        }

        public String getRefType() {
            return refType;
        }

        public Optional<InternationalizationReferenceMap> getInternationalizationReferenceMap() {
            return internationalizationReferenceMap;
        }

        public Map<String, Internationalization> getDisplayColumns() {
            return displayColumns;
        }

        public Optional<Map<Locale, String>> getDisplayPattern() {
            return displayPattern;
        }

        public HierarchicalKeyFactory getHierarchicalKeyFactory() {
            return hierarchicalKeyFactory;
        }

        public Optional<Map<Locale, List<String>>> getPatternColumns() {
            return patternColumns;
        }

        public Optional<Map<Locale, List<InternationalizationDisplay.PatternSection>>> getPatternSection() {
            return patternSection;
        }
    }

    /**
     * Contrat permettant de créer pour chaque ligne de référentiel sa clé hiérarchique.
     * <p>
     * Comme la création de cette clé dépend de l'appartenance ou non à un référentiel hiérarchique, on gère
     * ça par héritage.
     */
    private static abstract class HierarchicalKeyFactory {

        static HierarchicalKeyFactory build(Configuration conf, String refType) {
            HierarchicalKeyFactory hierarchicalKeyFactory;
            Optional<Configuration.CompositeReferenceDescription> toUpdateCompositeReference = conf.getCompositeReferencesUsing(refType);
            if (toUpdateCompositeReference.isPresent()) {
                Configuration.CompositeReferenceDescription compositeReferenceDescription = toUpdateCompositeReference.get();
                boolean root = Iterables.get(compositeReferenceDescription.getComponents(), 0).getReference().equals(refType);
                if (root) {
                    hierarchicalKeyFactory = new HierarchicalKeyFactory.ForCompositeReferenceRoot();
                } else {
                    Configuration.CompositeReferenceComponentDescription referenceComponentDescription = compositeReferenceDescription.getComponents().stream()
                            .filter(compositeReferenceComponentDescription -> compositeReferenceComponentDescription.getReference().equals(refType))
                            .collect(MoreCollectors.onlyElement());
                    ReferenceColumn parentHierarchicalKeyColumn = new ReferenceColumn(referenceComponentDescription.getParentKeyColumn());
                    String parentHierarchicalParentReference = compositeReferenceDescription.getComponents().get(compositeReferenceDescription.getComponents().indexOf(referenceComponentDescription) - 1).getReference();
                    hierarchicalKeyFactory = new HierarchicalKeyFactory.ForCompositeReferenceChild(parentHierarchicalKeyColumn, parentHierarchicalParentReference);
                }
            } else {
                hierarchicalKeyFactory = new HierarchicalKeyFactory.ForNotCompositeReference();
            }
            return hierarchicalKeyFactory;
        }

        public abstract Ltree newHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceValues);

        public abstract Ltree newHierarchicalReference(Ltree reference);

        /**
         * Pour un référentiel qui est la racine d'un référentiel hiérarchique
         */
        private static class ForCompositeReferenceRoot extends HierarchicalKeyFactory {

            /**
             * On est sur un référentiel qui est à la racine de la hiérarchie donc sa clé hiérarchique est simplement sa clé naturelle (pas de parent)
             */
            @Override
            public Ltree newHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceValues) {
                return naturalKey;
            }

            @Override
            public Ltree newHierarchicalReference(Ltree reference) {
                return reference;
            }
        }

        /**
         * Pour un référentiel qui n'appartient pas à un référentiel hiérarchique
         */
        private static class ForNotCompositeReference extends HierarchicalKeyFactory {

            /**
             * On est sur un référentiel qui n'appartient pas à une hiérarchie donc la clé naturelle se suffit à elle-même
             */
            @Override
            public Ltree newHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceValues) {
                return naturalKey;
            }

            @Override
            public Ltree newHierarchicalReference(Ltree reference) {
                return reference;
            }
        }

        /**
         * Pour un référentiel qui appartient à un référentiel hiérarchique mais qui est un enfant (= pas la racine).
         */
        private static class ForCompositeReferenceChild extends HierarchicalKeyFactory {

            /**
             * La colonne dans laquelle on va chercher la clé hiérachique du parent.
             */
            private final ReferenceColumn parentHierarchicalKeyColumn;

            private final String parentHierarchicalParentReference;

            public ForCompositeReferenceChild(ReferenceColumn parentHierarchicalKeyColumn, String parentHierarchicalParentReference) {
                this.parentHierarchicalKeyColumn = parentHierarchicalKeyColumn;
                this.parentHierarchicalParentReference = parentHierarchicalParentReference;
            }

            /**
             * On calcule la clé hiérachique en préfixant la clé naturelle avec la clé hiérarchique du parent.
             */
            @Override
            public Ltree newHierarchicalKey(Ltree naturalKey, ReferenceDatum referenceDatum) {
                ReferenceColumnValue parentHierarchicalKeyColumnValue = referenceDatum.get(parentHierarchicalKeyColumn);
                Preconditions.checkState(parentHierarchicalKeyColumnValue instanceof ReferenceColumnSingleValue);
                String parentHierarchicalKeyAsString = ((ReferenceColumnSingleValue) parentHierarchicalKeyColumnValue).getValue();
                Ltree parentHierarchicalKey = Ltree.fromUnescapedString(parentHierarchicalKeyAsString);
                return Ltree.join(parentHierarchicalKey, naturalKey);
            }

            @Override
            public Ltree newHierarchicalReference(Ltree reference) {
                return Ltree.join(Ltree.fromUnescapedString(parentHierarchicalParentReference), reference);
            }
        }
    }

    public abstract static class Column {

        private final ReferenceColumn referenceColumn;

        private final ColumnPresenceConstraint presenceConstraint;

        private final ComputedValueUsage computedValueUsage;

        public Column(ReferenceColumn referenceColumn, ColumnPresenceConstraint presenceConstraint, ComputedValueUsage computedValueUsage) {
            this.referenceColumn = referenceColumn;
            this.presenceConstraint = presenceConstraint;
            this.computedValueUsage = computedValueUsage;
        }

        public boolean canHandle(String header) {
            return isExpected() && getExpectedHeader().equals(header);
        }

        abstract void pushValue(String cellContent, ReferenceDatum referenceDatum, SetMultimap<String, UUID> refsLinkedTo);

        abstract String getCsvCellContent(ReferenceDatum referenceDatum);

        public ReferenceColumn getReferenceColumn() {
            return referenceColumn;
        }

        abstract String getExpectedHeader();

        public ColumnPresenceConstraint getPresenceConstraint() {
            return presenceConstraint;
        }

        public boolean isMandatory() {
            return getPresenceConstraint().isMandatory();
        }

        public boolean isExpected() {
            return getPresenceConstraint().isExpected();
        }

        abstract Optional<ReferenceColumnValue> computeValue(ReferenceDatum referenceDatum);

        public ComputedValueUsage getComputedValueUsage() {
            return computedValueUsage;
        }
    }

    public static abstract class OneValueStaticColumn extends Column {

        public OneValueStaticColumn(ReferenceColumn referenceColumn, ColumnPresenceConstraint presenceConstraint, ComputedValueUsage computedValueUsage) {
            super(referenceColumn, presenceConstraint, computedValueUsage);
        }

        @Override
        public void pushValue(String cellContent, ReferenceDatum referenceDatum, SetMultimap<String, UUID> refsLinkedTo) {
            ReferenceColumnValue referenceColumnValue = new ReferenceColumnSingleValue(cellContent);
            referenceDatum.put(getReferenceColumn(), referenceColumnValue);
        }

        @Override
        public String getCsvCellContent(ReferenceDatum referenceDatum) {
            ReferenceColumnSingleValue referenceColumnSingleValue = (ReferenceColumnSingleValue) referenceDatum.get(getReferenceColumn());
            return referenceColumnSingleValue.getValue();
        }
    }

    public static abstract class ManyValuesStaticColumn extends Column {

        private static final String CSV_CELL_SEPARATOR = ",";

        public ManyValuesStaticColumn(ReferenceColumn referenceColumn, ColumnPresenceConstraint presenceConstraint, ComputedValueUsage computedValueUsage) {
            super(referenceColumn, presenceConstraint, computedValueUsage);
        }

        @Override
        public void pushValue(String cellContent, ReferenceDatum referenceDatum, SetMultimap<String, UUID> refsLinkedTo) {
            Set<String> values = Splitter.on(CSV_CELL_SEPARATOR)
                    .splitToStream(cellContent)
                    .collect(Collectors.toSet());
            ReferenceColumnValue referenceColumnValue = new ReferenceColumnMultipleValue(values);
            referenceDatum.put(getReferenceColumn(), referenceColumnValue);
        }

        @Override
        public String getCsvCellContent(ReferenceDatum referenceDatum) {
            ReferenceColumnMultipleValue referenceColumnMultipleValue = (ReferenceColumnMultipleValue) referenceDatum.get(getReferenceColumn());
            String csvCellContent = referenceColumnMultipleValue.getValues().stream()
                    .peek(value -> Preconditions.checkState(!value.contains(CSV_CELL_SEPARATOR), value + " contient " + CSV_CELL_SEPARATOR))
                    .collect(Collectors.joining(CSV_CELL_SEPARATOR));
            return csvCellContent;
        }
    }

    public static abstract class DynamicColumn extends Column {

        /**
         * Les colonnes dynamiques sont représentées sous forme de Map dont la clé est la clé hiérarchique correspondant au référentiel qui décrit cette colonne dynamique
         */
        private final Ltree expectedHierarchicalKey;

        /**
         * Cette colonne dynamique a été générée par une ligne de référentiel, donc il faut lier la donnée à ce référentiel
         */
        private final Map.Entry<String, UUID> refsLinkedToEntryToAdd;

        public DynamicColumn(ReferenceColumn referenceColumn, ColumnPresenceConstraint presenceConstraint, Ltree expectedHierarchicalKey, Map.Entry<String, UUID> refsLinkedToEntryToAdd, ComputedValueUsage computedValueUsage) {
            super(referenceColumn, presenceConstraint, computedValueUsage);
            this.expectedHierarchicalKey = expectedHierarchicalKey;
            this.refsLinkedToEntryToAdd = refsLinkedToEntryToAdd;
        }

        @Override
        public void pushValue(String cellContent, ReferenceDatum referenceDatum, SetMultimap<String, UUID> refsLinkedTo) {
            ReferenceColumnIndexedValue existingReferenceColumnIndexedValue;
            final Map<Ltree, String> values;
            if (referenceDatum.contains(getReferenceColumn())) {
                existingReferenceColumnIndexedValue = (ReferenceColumnIndexedValue) referenceDatum.get(getReferenceColumn());
                final Map<Ltree, String> existingValues = existingReferenceColumnIndexedValue.getValues();
                values = new LinkedHashMap<>(existingValues);
            } else {
                values = new LinkedHashMap<>();
            }
            values.put(expectedHierarchicalKey, cellContent);
            ReferenceColumnIndexedValue newReferenceColumnIndexedValue = new ReferenceColumnIndexedValue(values);
            referenceDatum.put(getReferenceColumn(), newReferenceColumnIndexedValue);
            refsLinkedTo.put(refsLinkedToEntryToAdd.getKey(), refsLinkedToEntryToAdd.getValue());
        }

        @Override
        public String getCsvCellContent(ReferenceDatum referenceDatum) {
            ReferenceColumnIndexedValue referenceColumnIndexedValue = (ReferenceColumnIndexedValue) referenceDatum.get(getReferenceColumn());
            return referenceColumnIndexedValue.getValues().get(expectedHierarchicalKey);
        }
    }
}