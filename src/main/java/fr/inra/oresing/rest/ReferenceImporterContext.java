package fr.inra.oresing.rest;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.MoreCollectors;
import fr.inra.oresing.checker.LineChecker;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.ReferenceColumn;
import fr.inra.oresing.model.ReferenceColumnIndexedValue;
import fr.inra.oresing.model.ReferenceColumnMultipleValue;
import fr.inra.oresing.model.ReferenceColumnSingleValue;
import fr.inra.oresing.model.ReferenceColumnValue;
import fr.inra.oresing.model.ReferenceDatum;
import fr.inra.oresing.model.internationalization.Internationalization;
import fr.inra.oresing.model.internationalization.InternationalizationDisplay;
import fr.inra.oresing.model.internationalization.InternationalizationMap;
import fr.inra.oresing.model.internationalization.InternationalizationReferenceMap;
import fr.inra.oresing.persistence.Ltree;
import lombok.AllArgsConstructor;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Toutes les informations nécessaires à l'import d'un référentiel donné.
 *
 * C'est un objet immuable, toutes ces informations sont constantes tout au long de l'import;
 */
@AllArgsConstructor
public class ReferenceImporterContext {

    private static final String COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR = "__";

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

    /**
     * Tous les {@link LineChecker} qui s'appliquent sur chaque ligne à importer
     */
    private final ImmutableSet<LineChecker> lineCheckers;

    /**
     * Les clés techniques de chaque clé naturelle hiérarchique de toutes les lignes existantes en base (avant l'import)
     */
    private final ImmutableMap<Ltree, UUID> storedReferences;

    private final ImmutableMap<String, Column> columnsPerHeader;

    private Optional<InternationalizationReferenceMap> getInternationalizationReferenceMap() {
        Optional<InternationalizationReferenceMap> internationalizationReferenceMap = Optional.ofNullable(conf)
                .map(Configuration::getInternationalization)
                .map(InternationalizationMap::getReferences)
                .map(references -> references.getOrDefault(refType, null));
        return internationalizationReferenceMap;
    }

    public Map<String, Internationalization> getDisplayColumns() {
        Optional<InternationalizationReferenceMap> internationalizationReferenceMap = getInternationalizationReferenceMap();
        return internationalizationReferenceMap
                .map(InternationalizationReferenceMap::getInternationalizedColumns)
                .orElseGet(HashMap::new);
    }

    public Optional<Map<String, String>> getDisplayPattern() {
        Optional<InternationalizationReferenceMap> internationalizationReferenceMap = getInternationalizationReferenceMap();
        return internationalizationReferenceMap
                .map(InternationalizationReferenceMap::getInternationalizationDisplay)
                .map(InternationalizationDisplay::getPattern);
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

    private HierarchicalKeyFactory getHierarchicalKeyFactory() {
        HierarchicalKeyFactory hierarchicalKeyFactory = HierarchicalKeyFactory.build(conf, refType);
        return hierarchicalKeyFactory;
    }

    public String getRefType() {
        return refType;
    }

    public Ltree getRefTypeAsLabel() {
        return Ltree.fromUnescapedString(refType);
    }

    /**
     * Crée une clé hiérarchique
     */
    public Ltree newHierarchicalKey(Ltree recursiveNaturalKey, ReferenceDatum referenceDatum) {
        return getHierarchicalKeyFactory().newHierarchicalKey(recursiveNaturalKey, referenceDatum);
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
        Configuration.ReferenceDescription ref = conf.getReferences().get(refType);
        return ref;
    }

    private Optional<Configuration.CompositeReferenceComponentDescription> getRecursiveComponentDescription() {
        return conf.getCompositeReferences().values().stream()
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
        return applicationId;
    }

    public Optional<UUID> getIdForSameHierarchicalKeyInDatabase(Ltree hierarchicalKey) {
        return Optional.ofNullable(storedReferences.get(hierarchicalKey));
    }

    public void pushValue(ReferenceDatum referenceDatum, String header, String cellContent) {
        Column column = columnsPerHeader.get(header);
        column.pushValue(referenceDatum, cellContent);
    }

    public ImmutableSet<String> getExpectedHeaders() {
        return columnsPerHeader.keySet();
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

        private final String expectedHeader;

        private final ReferenceColumn referenceColumn;

        public Column(ReferenceColumn referenceColumn, String expectedHeader) {
            this.referenceColumn = referenceColumn;
            this.expectedHeader = expectedHeader;
        }

        public boolean canHandle(String header) {
            return expectedHeader.equals(header);
        }

        abstract void pushValue(ReferenceDatum referenceDatum, String cellContent);

        public ReferenceColumn getReferenceColumn() {
            return referenceColumn;
        }

        public String getExpectedHeader() {
            return expectedHeader;
        }
    }

    public static class OneValueStaticColumn extends Column {

        public OneValueStaticColumn(ReferenceColumn referenceColumn) {
            super(referenceColumn, referenceColumn.getColumn());
        }

        @Override
        public void pushValue(ReferenceDatum referenceDatum, String cellContent) {
            ReferenceColumnValue referenceColumnValue = new ReferenceColumnSingleValue(cellContent);
            referenceDatum.put(getReferenceColumn(), referenceColumnValue);
        }
    }

    public static class ManyValuesStaticColumn extends Column {

        public ManyValuesStaticColumn(ReferenceColumn referenceColumn) {
            super(referenceColumn, referenceColumn.getColumn());
        }

        @Override
        public void pushValue(ReferenceDatum referenceDatum, String cellContent) {
            Set<String> values = Splitter.on(ReferenceColumnMultipleValue.CSV_CELL_SEPARATOR)
                    .splitToStream(cellContent)
                    .collect(Collectors.toSet());
            ReferenceColumnValue referenceColumnValue = new ReferenceColumnMultipleValue(values);
            referenceDatum.put(getReferenceColumn(), referenceColumnValue);
        }
    }

    public static class DynamicColumn extends Column {

        private final Ltree expectedHierarchicalKey;

        public DynamicColumn(ReferenceColumn referenceColumn, String expectedHeader, Ltree expectedHierarchicalKey) {
            super(referenceColumn, expectedHeader);
            this.expectedHierarchicalKey = expectedHierarchicalKey;
        }

        @Override
        public void pushValue(ReferenceDatum referenceDatum, String cellContent) {
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
        }
    }
}
