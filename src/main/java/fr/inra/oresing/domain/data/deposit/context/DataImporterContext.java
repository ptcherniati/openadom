package fr.inra.oresing.domain.data.deposit.context;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import fr.inra.oresing.domain.Mapper;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.internationalization.InternationalizationTitle;
import fr.inra.oresing.domain.checker.LineChecker;
import fr.inra.oresing.domain.checker.type.ReferenceType;
import fr.inra.oresing.domain.data.*;
import fr.inra.oresing.domain.data.deposit.PublishContext;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.data.deposit.context.column.PatternColumnFactory;
import fr.inra.oresing.domain.data.deposit.context.hierarchicalkey.HierarchicalKeyFactory;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.data.read.query.ComponentOrderBy;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.rest.exceptions.ExceptionMessage;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Toutes les informations nécessaires à l'import d'un référentiel donné.
 * <p>
 * C'est un objet immuable, toutes ces informations sont constantes tout au long de l'import;
 */

public class DataImporterContext {
    public static final String COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR = "__";
    private final ContextConstants constants;
    /**
     *
     */
    private final ImmutableSet<LineChecker> lineCheckers;
    /**
     * Les clés techniques de chaque clé naturelle hiérarchique de toutes les lignes existantes en base (avant l'import)
     */
    private final ImmutableMap<DataValue.LineIdentityPatternColumnName, UUID> storedReferences;
    private final ImmutableSet<Column> columns;

    public List<ReferenceScope.NodeDescription> getNodesForMenu() {
        return nodesForMenu;
    }

    private final List<ReferenceScope.NodeDescription> nodesForMenu;

    public ImmutableSet<Column> getColumnsWithPatternColumns() {
        return columnsWithPatternColumns;
    }

    private ImmutableSet<Column> columnsWithPatternColumns;
    @Getter
    private final PatternColumnFactory patternColumnFactory;
    @Getter
    private final Mapper jsonRowMapper;
    final Map<String, Map<String, Map<String, String>>> displayNamesByReferenceAndNaturalKey;
    final Map<String, Map<String, Map<String, String>>> displayDescriptionsByReferenceAndNaturalKey;
    final boolean allowUnexpectedColumns;
    private final PublishContext.PublishContextBuilder publishContextBuilder;

    public DataImporterContext(final ContextConstants constants,
                               final ImmutableSet<LineChecker> lineCheckers,
                               final ImmutableMap<DataValue.LineIdentityPatternColumnName, UUID> storedReferences,
                               final ImmutableSet<Column> columns,
                               final PatternColumnFactory patternColumnFactory,
                               final Mapper jsonRowMapper,
                               final Map<String, Map<String, Map<String, String>>> displayNamesByReferenceAndNaturalKey,
                               final Map<String, Map<String, Map<String, String>>> displayDescriptionsByReferenceAndNaturalKey,
                               final boolean allowUnexpectedColumns,
                               final String reftype,
                               PublishContext.PublishContextBuilder publishContextBuilder,
                               List<ReferenceScope.NodeDescription> nodesForMenu) {
        super();
        this.constants = constants;
        this.lineCheckers = lineCheckers;
        this.storedReferences = storedReferences;
        this.columns = columns;
        this.patternColumnFactory = patternColumnFactory;
        this.jsonRowMapper = jsonRowMapper;
        this.displayNamesByReferenceAndNaturalKey = displayNamesByReferenceAndNaturalKey;
        this.displayDescriptionsByReferenceAndNaturalKey = displayDescriptionsByReferenceAndNaturalKey;
        this.allowUnexpectedColumns = allowUnexpectedColumns;
        this.publishContextBuilder = publishContextBuilder;
        this.nodesForMenu = nodesForMenu;
    }

    public String getDisplayNamesByReferenceAndNaturalKey(final String referencedColumn, final String naturalKey, final String locale) {
        return displayNamesByReferenceAndNaturalKey.getOrDefault(referencedColumn, new HashMap<>())
                .getOrDefault(naturalKey, new HashMap<>())
                .getOrDefault(locale, naturalKey);
    }

    public String getDisplayDescriptionsByReferenceAndNaturalKey(final String referencedColumn, final String naturalKey, final String locale) {
        return displayDescriptionsByReferenceAndNaturalKey.getOrDefault(referencedColumn, new HashMap<>())
                .getOrDefault(naturalKey, new HashMap<>())
                .getOrDefault(locale, naturalKey);
    }

    /**
     * Séparateur pour les clés naturelles composites.
     *
     */
    public static String getCompositeNaturalKeyComponentsSeparator() {
        return COMPOSITE_NATURAL_KEY_COMPONENTS_SEPARATOR;
    }

    public String getRefType() {
        return constants.refType();
    }

    /**
     * Crée une clé hiérarchique
     *
     */
    public Ltree newHierarchicalKey(final Ltree recursiveNaturalKey, final DataDatum referenceDatum) {
        return getHierarchicalKeyFactory().newHierarchicalKey(recursiveNaturalKey, referenceDatum);
    }

    private HierarchicalKeyFactory getHierarchicalKeyFactory() {
        return constants.hierarchicalKeyFactory();
    }

    /**
     * Les colonnes dont les valeurs composent la clé naturelle composite de chaque ligne pour ce référentiel
     *
     */
    public ImmutableList<DataColumn> getKeyColumns() {
        Preconditions.checkState(CollectionUtils.isNotEmpty(getDataDescription().naturalKey()), ExceptionMessage.MISSING_PRIMARY_KEY_COMPONENT.toMessage(), getRefType());
        return getDataDescription().naturalKey().stream()
                .map(DataColumn::new)
                .collect(ImmutableList.toImmutableList());
    }

    public StandardDataDescription getDataDescription() {
        return constants.dataConfiguration();
    }

    private Optional<HierarchicalNode> getRecursiveComponentDescription() {
        return getApplication().getConfiguration().findCompositeReferencesUsing(getRefType())
                .filter(HierarchicalNode::isRecursive);

    }

    /**
     * Si le référentiel contient des colonnes qui font références à d'autres lignes de ce même référentiel
     *
     */
    public boolean isRecursive() {
        return getRecursiveComponentDescription().isPresent();
    }

    /**
     * Pour un référentiel récursif, indique la colonne dans laquelle la valeur est la clé vers le parent de la ligne courante
     *
     */
    public DataColumn getColumnToLookForParentKey() {
        Preconditions.checkState(isRecursive());
        return getRecursiveComponentDescription()
                .map(HierarchicalNode::node)
                .map(Node::columnToLookUpForRecursive)
                .map(DataColumn::new)
                .orElseThrow(() -> new IllegalStateException("ne devrait jamais arriver (?)"));
    }

    /**
     * Le séparateur à utiliser pour distinguer les cellules du fichier CSV
     *
     */
    public char getCsvSeparator() {
        return getDataDescription().separator();
    }

    public ImmutableSet<LineChecker> getLineCheckers() {
        return ImmutableSet.copyOf(lineCheckers);
    }

    /**
     * Dans le cas d'un référentiel récursif, le {@link ReferenceType} qui porte sur la colonne contenant des valeurs faisant référence à d'autres lignes du référentiel.
     *
     */
    public LineChecker getReferenceLineChecker() {
        Preconditions.checkState(isRecursive());
        return getLineCheckers().stream()
                .filter(lineChecker -> lineChecker.underlyingType() instanceof ReferenceType &&

                        ((ReferenceType) lineChecker.underlyingType()).getRefType().equals(getRefType()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("pas de computationChecker sur " + getRefType() + " alors qu'on est sur un référentiel récursif"));
    }

    public Application getApplication() {
        return constants.application();
    }

    public Optional<UUID> getIdForSameHierarchicalKeyInDatabase(final Ltree hierarchicalKey) {
        if(storedReferences==null){
            return Optional.empty();
        }
        return storedReferences.entrySet().stream()
                .filter(entry -> entry.getKey().identity().hierarchicalKey().equals(hierarchicalKey))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    /**
     * @deprecated ne devrait pas être exposé
     */
    @Deprecated
    public ImmutableSet<Column> getColumns() {
        return columns;
    }

    public ImmutableMap<String, Column> getExpectedColumnsPerHeaders() {
        return columns.stream()
                .filter(Column::isExpected)
                .collect(ImmutableMap.toImmutableMap(
                        Column::getExpectedHeader,
                        Function.identity()
                ));
    }

    public Map<String, ComponentOrderBy> getExpectedComponentOrderByPerHeaders(String language) {
        return columns.stream()
                .filter(Column::isExpected)
                .map(Column::getReferenceColumn)
                .map(DataColumn::column)
                .collect(Collectors.toMap(
                        componentName -> getApplication()
                                .internationalizeHeader(getRefType(), componentName, language),
                        componentName -> new ComponentOrderBy(componentName, DataRepository.Order.ASC, getDataDescription().getTypeForComponentKey(componentName))
                ));
    }

    public boolean pushValue(final DataDatum referenceDatum, final String header, final String cellContent, final Map<String, Map<String, RefsLinkedToValue>> refsLinkedTo) {
        final Column column = getExpectedColumnsPerHeaders().get(header);
        if (column == null) {
            return true;
        }
        column.pushValue(cellContent, referenceDatum, refsLinkedTo);
        return false;
    }

    public ImmutableSet<String> getExpectedHeaders() {
        return getExpectedColumnsPerHeaders().keySet();
    }

    public ImmutableSet<String> getMandatoryHeaders() {
        return getExpectedColumnsPerHeaders().values().stream()
                .map(Column::getExpectedHeader)
                .collect(ImmutableSet.toImmutableSet());
    }

    public String getCsvCellContent(final DataDatum referenceDatum, final String header) {
        final Column column = getExpectedColumnsPerHeaders().get(header);
        return Objects.requireNonNull(column).getCsvCellContent(referenceDatum);
    }

    public Optional<InternationalizationTitle> getDisplayPattern() {
        return Optional.ofNullable(constants.displayPattern());
    }

       public String getParent() {
        return constants.hierarchicalKeyFactory().parent();
    }

    public boolean existsColumn(final DataColumn column, Map<DataColumn, DataColumnValue> constantColumnsValues) {
        return columnsWithPatternColumns.stream()
                .map(registeredColumn->registeredColumn.as(column.column()))
                .anyMatch(Objects::nonNull)||
                constantColumnsValues.keySet().stream()
                        .map(DataColumn::column)
                        .anyMatch(c -> c.equals(column.column()));
    }

    public void withPatternColumn() {
        columnsWithPatternColumns = new ImmutableSet.Builder<Column>()
                .addAll(columns)
                .addAll(getPatternColumnFactory().getExpectedPatternColumns())
                .build();
    }

    public LinkedHashSet<String> getNaturalKeyColumns() {
        return constants.dataConfiguration().naturalKey();
    }

    public PublishContext.PublishContextBuilder getPublishContextBuilder() {
        return this.publishContextBuilder;
    }

    public Authorization getAuthorization() {
        return constants.dataConfiguration().authorization();
    }

    public List<String> getNaturalKeyColumnsImportHeaders() {
        Function<String, String> getimportHeader = component -> getDataDescription().componentDescriptions().get(component).importHeader();
        return getNaturalKeyColumns()
                .stream()
                .map(getimportHeader)
                .toList();
    }
}