package fr.inra.oresing.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import fr.inra.oresing.checker.DateLineCheckerConfiguration;
import fr.inra.oresing.checker.FloatCheckerConfiguration;
import fr.inra.oresing.checker.GroovyLineCheckerConfiguration;
import fr.inra.oresing.checker.IntegerCheckerConfiguration;
import fr.inra.oresing.checker.Multiplicity;
import fr.inra.oresing.checker.ReferenceLineCheckerConfiguration;
import fr.inra.oresing.checker.RegularExpressionCheckerConfiguration;
import fr.inra.oresing.model.internationalization.Internationalization;
import fr.inra.oresing.model.internationalization.InternationalizationApplicationMap;
import fr.inra.oresing.model.internationalization.InternationalizationAuthorisationMap;
import fr.inra.oresing.model.internationalization.InternationalizationAuthorisationName;
import fr.inra.oresing.model.internationalization.InternationalizationDataTypeMap;
import fr.inra.oresing.model.internationalization.InternationalizationDisplayImpl;
import fr.inra.oresing.model.internationalization.InternationalizationImpl;
import fr.inra.oresing.model.internationalization.InternationalizationMap;
import fr.inra.oresing.model.internationalization.InternationalizationMapDisplayImpl;
import fr.inra.oresing.model.internationalization.InternationalizationReferenceMap;
import fr.inra.oresing.transformer.TransformationConfiguration;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString
public class Configuration {
    private String defaultLanguage;
    private List<String> requiredAuthorizationsAttributes;
    private InternationalizationMap internationalization;
    private int version;
    private String comment;
    private ApplicationDescription application;
    private LinkedHashMap<String, ReferenceDescription> references = new LinkedHashMap<>();
    private LinkedHashMap<String, CompositeReferenceDescription> compositeReferences = new LinkedHashMap<>();
    private LinkedHashMap<String, DataTypeDescription> dataTypes = new LinkedHashMap<>();

    public InternationalizationMap getInternationalization() {
        final InternationalizationMap internationalizationMap = new InternationalizationMap();
        internationalizationMap.setApplication(Optional.ofNullable(application).map(ApplicationDescription::getInternationalization).orElse(null));
        internationalizationMap.setDataTypes(Optional.ofNullable(dataTypes).map(DataTypeDescription::getInternationalization).orElse(null));
        internationalizationMap.setReferences(Optional.ofNullable(references).map(ReferenceDescription::getInternationalization).orElse(null));
        return internationalizationMap;
    }

    public Optional<CompositeReferenceDescription> getCompositeReferencesUsing(String reference) {
        return getCompositeReferences().values().stream()
                .filter(compositeReferenceDescription -> compositeReferenceDescription.isDependentOfReference(reference))
                .collect(MoreCollectors.toOptional());
    }

    public LinkedHashMap<String, ReferenceDescription> getReferences() {
        Map<String, Set<String>> dependsOf = new HashMap<>();
        Map<String, DependencyNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, ReferenceDescription> reference : references.entrySet()) {
            addDependencyNodesForReference(nodes, reference);
        }
        LinkedHashMap<String, ReferenceDescription> sortedReferences = new LinkedHashMap<>();
        nodes.values().stream()
                .filter(node -> node.isLeaf || node.dependsOn.contains(node))
                .sorted((a, b) -> -1)
                .forEach(node -> addRecursively(node, sortedReferences, references));
        return sortedReferences;
    }

    private void addDependencyNodesForReference(Map<String, DependencyNode> nodes, Map.Entry<String, ReferenceDescription> reference) {
        DependencyNode dependencyNode = nodes.computeIfAbsent(reference.getKey(), k -> new DependencyNode(reference.getKey()));
        LinkedHashMap<String, LineValidationRuleWithColumnsDescription> validations = reference.getValue().getValidations();
        if (!CollectionUtils.isEmpty(validations)) {
            for (Map.Entry<String, LineValidationRuleWithColumnsDescription> validation : validations.entrySet()) {
                CheckerDescription checker = validation.getValue().getChecker();
                if (checker != null) {
                    String refType = checker.getParams().getRefType();
                    if ("Reference".equals(checker.getName()) && StringUtils.isNotEmpty(refType)) {
                        DependencyNode node = nodes.computeIfAbsent(refType, k -> new DependencyNode(refType));
                        dependencyNode.addDependance(node);
                    }
                }
            }
        }
    }

    private void addRecursively(DependencyNode node, LinkedHashMap<String, ReferenceDescription> sortedReferences, LinkedHashMap<String, ReferenceDescription> references) {
        if (!node.dependsOn.isEmpty()) {
            node.dependsOn
                    .stream().filter(n -> !n.dependsOn.contains(node))
                    .forEach(dependencyNode -> addRecursively(dependencyNode, sortedReferences, references));
        }
        sortedReferences.put(node.value, references.get(node.value));


    }

    public enum MigrationStrategy {
        ADD_VARIABLE
    }

    @Getter
    @Setter
    @ToString
    public static class ReferenceDescription extends InternationalizationDisplayImpl {
        private char separator = ';';
        private List<String> keyColumns = new LinkedList<>();
        private LinkedHashMap<String, ReferenceStaticNotComputedColumnDescription> columns = new LinkedHashMap<>();
        private LinkedHashMap<String, ReferenceStaticComputedColumnDescription> computedColumns = new LinkedHashMap<>();
        private LinkedHashMap<String, ReferenceDynamicColumnDescription> dynamicColumns = new LinkedHashMap<>();
        private LinkedHashMap<String, LineValidationRuleWithColumnsDescription> validations = new LinkedHashMap<>();

        public Set<String> doGetAllColumns() {
            return doGetAllColumnDescriptions().keySet();
        }

        public Set<String> doGetStaticColumns() {
            return doGetStaticColumnDescriptions().keySet();
        }

        public Map<String, ReferenceColumnDescription> doGetAllColumnDescriptions() {
            Map<String, ReferenceColumnDescription> allColumnDescriptions = new LinkedHashMap<>();
            allColumnDescriptions.putAll(doGetStaticColumnDescriptions());
            allColumnDescriptions.putAll(dynamicColumns);
            return allColumnDescriptions;
        }

        public Map<String, ReferenceStaticColumnDescription> doGetStaticColumnDescriptions() {
            Map<String, ReferenceStaticColumnDescription> staticColumnDescriptions = new LinkedHashMap<>();
            staticColumnDescriptions.putAll(columns);
            staticColumnDescriptions.putAll(computedColumns);
            return staticColumnDescriptions;
        }

        public boolean hasStaticColumn(String column) {
            return doGetStaticColumns().contains(column);
        }

        public boolean hasColumn(String column) {
            return doGetAllColumns().contains(column);
        }

        public ImmutableSet<ReferenceColumn> doGetComputedColumns() {
            Set<ReferenceColumn> usedInTransformationColumns = validations.values().stream()
                    .map(LineValidationRuleWithColumnsDescription::getColumns)
                    .flatMap(Collection::stream)
                    .map(ReferenceColumn::new)
                    .collect(Collectors.toUnmodifiableSet());
            ImmutableSet<ReferenceColumn> computedColumns = Sets.difference(usedInTransformationColumns, doGetStaticColumns()).immutableCopy();
            return computedColumns;
        }

        public static Map<String, InternationalizationReferenceMap>  getInternationalization(LinkedHashMap<String, ReferenceDescription> referenceDescriptionMap) {
            Map<String, InternationalizationReferenceMap> internationalizationReferenceMap = new HashMap<>();
            for (Map.Entry<String, ReferenceDescription> entry : referenceDescriptionMap.entrySet()) {
                final String reference = entry.getKey();
                final ReferenceDescription referenceDescription = entry.getValue();
                final InternationalizationReferenceMap internationalizationReference = new InternationalizationReferenceMap();
                internationalizationReference.setInternationalizationDisplay(referenceDescription.getInternationalizationDisplay());
                internationalizationReference.setInternationalizationName(referenceDescription.getInternationalizationName());
                internationalizationReference.setInternationalizedColumns(referenceDescription.getInternationalizedColumns());
                internationalizationReferenceMap.put(reference, internationalizationReference);
            }
            return internationalizationReferenceMap;
        }
    }

    @Getter
    @Setter
    public static abstract class ReferenceColumnDescription {
        ColumnPresenceConstraint presenceConstraint = ColumnPresenceConstraint.MANDATORY;
    }

    @Getter
    @Setter
    public static abstract class ReferenceStaticColumnDescription extends ReferenceColumnDescription {
        CheckerDescription checker;
    }

    @Getter
    @Setter
    @ToString
    public static class ReferenceStaticNotComputedColumnDescription extends ReferenceStaticColumnDescription {
        @Nullable
        GroovyConfiguration defaultValue;
    }

    @Getter
    @Setter
    @ToString
    public static class ReferenceStaticComputedColumnDescription extends ReferenceStaticColumnDescription {
        GroovyConfiguration computation;
    }

    @Getter
    @Setter
    @ToString
    public static class ReferenceDynamicColumnDescription extends ReferenceColumnDescription {
        private String headerPrefix = "";
        private String reference;
        private String referenceColumnToLookForHeader;
    }

    @Getter
    @Setter
    @ToString
    public static class CompositeReferenceDescription extends InternationalizationImpl {
        List<CompositeReferenceComponentDescription> components = new LinkedList<>();

        public boolean isDependentOfReference(String reference) {
            return components.stream()
                    .map(CompositeReferenceComponentDescription::getReference)
                    .anyMatch(reference::equals);
        }
    }

    @Getter
    @Setter
    @ToString
    public static class CompositeReferenceComponentDescription extends InternationalizationImpl {
        String reference;
        String parentKeyColumn;
        String parentRecursiveKey;
    }

    @Getter
    @Setter
    @ToString
    public static class DataTypeDescription extends InternationalizationMapDisplayImpl {
        FormatDescription format;
        LinkedHashMap<String, ColumnDescription> data = new LinkedHashMap<>();
        LinkedHashMap<String, LineValidationRuleDescription> validations = new LinkedHashMap<>();
        List<VariableComponentKey> uniqueness = new LinkedList<>();
        TreeMap<Integer, List<MigrationDescription>> migrations = new TreeMap<>();
        AuthorizationDescription authorization;
        LinkedHashMap<String, String> repository = null;

        public static Map<String, InternationalizationDataTypeMap> getInternationalization(LinkedHashMap<String, DataTypeDescription> dataTypeDescriptionMap) {
            Map<String, InternationalizationDataTypeMap> internationalizationDataTypeMapMap = new HashMap<>();
            for (Map.Entry<String, DataTypeDescription> entry : dataTypeDescriptionMap.entrySet()) {
                final String datatype = entry.getKey();
                final DataTypeDescription dataTypeDescription = entry.getValue();
                final InternationalizationDataTypeMap internationalizationDataTypeMap = new InternationalizationDataTypeMap();
                internationalizationDataTypeMap.setInternationalizationDisplay(dataTypeDescription.getInternationalizationDisplays());
                internationalizationDataTypeMap.setInternationalizationName(dataTypeDescription.getInternationalizationName());
                internationalizationDataTypeMap.setInternationalizedColumns(dataTypeDescription.getInternationalizedColumns());
                internationalizationDataTypeMap.setAuthorization(Optional.ofNullable(dataTypeDescription.getAuthorization()).map(AuthorizationDescription::getInternationalization).orElse(null));
                internationalizationDataTypeMapMap.put(datatype, internationalizationDataTypeMap);
            }
            return internationalizationDataTypeMapMap;
        }
    }

    @Getter
    @Setter
    @ToString
    public static class LineValidationRuleDescription {
        String description;
        CheckerDescription checker;
    }

    @Getter
    @Setter
    @ToString
    public static class LineValidationRuleWithColumnsDescription extends LineValidationRuleDescription {
        Set<String> columns;
    }

    @Getter
    @Setter
    @ToString
    public static class AuthorizationDescription {
        VariableComponentKey timeScope;
        LinkedHashMap<String, AuthorizationScopeDescription> authorizationScopes = new LinkedHashMap<>();
        LinkedHashMap<String, DataGroupDescription> dataGroups = new LinkedHashMap<>();

        public InternationalizationAuthorisationMap getInternationalization() {
            final InternationalizationAuthorisationMap internationalizationAuthorisationMap = new InternationalizationAuthorisationMap();
            Map<String, InternationalizationAuthorisationName> authorizationScopesLocalization = new HashMap<>();
            for (Map.Entry<String, AuthorizationScopeDescription> entry : authorizationScopes.entrySet()) {
                final InternationalizationAuthorisationName internationalizationAuthorisationName = new InternationalizationAuthorisationName();
                internationalizationAuthorisationName.setInternationalizationName(entry.getValue().getInternationalizationName());
                authorizationScopesLocalization.put(entry.getKey(), internationalizationAuthorisationName);
            }
            internationalizationAuthorisationMap.setAuthorizationScopes(authorizationScopesLocalization);
            Map<String, InternationalizationAuthorisationName> datagroupsLocalization = new HashMap<>();
            for (Map.Entry<String, DataGroupDescription> entry : dataGroups.entrySet()) {
                final InternationalizationAuthorisationName internationalizationAuthorisationName = new InternationalizationAuthorisationName();
                internationalizationAuthorisationName.setInternationalizationName(entry.getValue().getInternationalizationName());
                datagroupsLocalization.put(entry.getKey(), internationalizationAuthorisationName);
            }
            internationalizationAuthorisationMap.setDataGroups(datagroupsLocalization);
            return internationalizationAuthorisationMap;
        }
    }

    @Getter
    @Setter
    @ToString
    public static class AuthorizationScopeDescription extends InternationalizationImpl {
        String variable;
        String component;

        public VariableComponentKey getVariableComponentKey() {
            return new VariableComponentKey(variable, component);
        }
    }

    @Getter
    @Setter
    @ToString
    public static class FormatDescription {
        private int headerLine = 1;
        private int firstRowLine = 2;
        private char separator = ';';
        private List<ColumnBindingDescription> columns = new LinkedList<>();
        private List<RepeatedColumnBindingDescription> repeatedColumns = new LinkedList<>();
        private List<HeaderConstantDescription> constants = new LinkedList<>();
    }

    @Getter
    @Setter
    @ToString
    public static class HeaderConstantDescription {
        int rowNumber;
        int columnNumber;
        String headerName;
        VariableComponentKey boundTo;
        String exportHeader;

        public int getColumnNumber(ImmutableList<String> headerRows) {
            if (headerName != null && headerRows.contains(headerName)) {
                return headerRows.indexOf(headerName) + 1;
            }
            return columnNumber;
        }
    }


    @Getter
    @Setter
    @ToString
    public static class ColumnBindingDescription {
        String header;
        VariableComponentKey boundTo;
    }

    @Getter
    @Setter
    @ToString
    public static class RepeatedColumnBindingDescription {
        String headerPattern;
        String exportHeader;
        List<HeaderPatternToken> tokens = new LinkedList<>();
        VariableComponentKey boundTo;
    }

    @Getter
    @Setter
    @ToString
    public static class HeaderPatternToken {
        VariableComponentKey boundTo;
        String exportHeader;
    }

    @Getter
    @Setter
    @ToString
    public static class ColumnDescription {
        Chart chartDescription;
        LinkedHashMap<String, VariableComponentWithDefaultValueDescription> components = new LinkedHashMap<>();
        LinkedHashMap<String, ComputedVariableComponentDescription> computedComponents = new LinkedHashMap<>();

        public Set<String> doGetAllComponents() {
            return doGetAllComponentDescriptions().keySet();
        }

        public Map<String, VariableComponentDescription> doGetAllComponentDescriptions() {
            Map<String, VariableComponentDescription> allComponentDescriptions = new LinkedHashMap<>();
            allComponentDescriptions.putAll(components);
            allComponentDescriptions.putAll(computedComponents);
            return allComponentDescriptions;
        }

        public boolean hasComponent(String component) {
            return doGetAllComponents().contains(component);
        }
    }

    @Getter
    @Setter
    @ToString
    public static class Chart {
        public static String VAR_SQL_TEMPLATE =  "(\n" +
                "\t   Array['%1$s','%2$s'],-- aggrégation\n" +
                "\t   Array['%3$s','%4$s'], -- value\n" +
                "\t   '%5$s',-- datatype\n" +
                "\t   '%6$s'::interval -- gap\n" +
                "   )\n";
        public static String VAR_SQL_DEFAULT_TEMPLATE = " (\n" +
                "\t   '%s' -- datatype\n" +
                "   )\n";
        String value;
        VariableComponentKey aggregation = null;
        String unit = null;
        String gap = null;
        String standardDeviation = null;

        public String toSQL(String variableName, String dataType) {
            String sql = String.format(
                    VAR_SQL_TEMPLATE,
                    aggregation == null ? "" : aggregation.getVariable(),
                    aggregation == null ? "" : aggregation.getComponent(),
                    variableName,
                    value,
                    dataType,
                    gap == null ? "0" : gap
            );
            return sql;
        }
        public static String toSQL( String dataType) {
            String sql = String.format(
                    VAR_SQL_DEFAULT_TEMPLATE,
                    dataType
            );
            return sql;
        }
    }

    @Getter
    @Setter
    public abstract static class VariableComponentDescription {
        CheckerDescription checker;
    }

    @Getter
    @Setter
    @ToString
    public static class VariableComponentWithDefaultValueDescription extends VariableComponentDescription {
        @Nullable
        GroovyConfiguration defaultValue;
    }

    @Getter
    @Setter
    @ToString
    public static class ComputedVariableComponentDescription extends VariableComponentDescription {
        GroovyConfiguration computation;
    }

    @Getter
    @Setter
    @ToString
    public static class CheckerDescription {
        String name;
        CheckerConfigurationDescription params = new CheckerConfigurationDescription();
    }

    @Getter
    @Setter
    @ToString
    public static class CheckerConfigurationDescription implements
            RegularExpressionCheckerConfiguration,
            FloatCheckerConfiguration,
            IntegerCheckerConfiguration,
            DateLineCheckerConfiguration,
            ReferenceLineCheckerConfiguration,
            GroovyLineCheckerConfiguration {
        String pattern;
        String refType;
        GroovyConfiguration groovy;
        String duration;
        TransformationConfigurationDescription transformation = new TransformationConfigurationDescription();
        boolean required = true;
        Multiplicity multiplicity = Multiplicity.ONE;
    }

    @Getter
    @Setter
    @ToString
    public static class TransformationConfigurationDescription implements TransformationConfiguration {
        boolean codify;
        GroovyConfiguration groovy;
    }

    @Getter
    @Setter
    @ToString
    public static class GroovyConfiguration implements fr.inra.oresing.checker.GroovyConfiguration {
        String expression;
        Set<String> references = new LinkedHashSet<>();
        Set<String> datatypes = new LinkedHashSet<>();
    }

    @Getter
    @Setter
    @ToString
    public static class DataGroupDescription extends InternationalizationImpl {
        Internationalization internationalizationName;
        String label;
        Set<String> data = new LinkedHashSet<>();
    }

    @Getter
    @Setter
    @ToString
    public static class ApplicationDescription extends InternationalizationImpl {
        String name;
        int version;
        Locale defaultLanguage;

        public InternationalizationApplicationMap getInternationalization() {
            final InternationalizationApplicationMap internationalizationApplicationMap = new InternationalizationApplicationMap();
            internationalizationApplicationMap.setInternationalizationName(getInternationalizationName());
            return internationalizationApplicationMap;
        }
    }

    @Getter
    @Setter
    @ToString
    public static class MigrationDescription {
        MigrationStrategy strategy;
        String dataGroup;
        String variable;
        Map<String, AddVariableMigrationDescription> components = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    @ToString
    public static class AddVariableMigrationDescription {
        String defaultValue;
    }

    class DependencyNode {
        String value;
        boolean isLeaf = true;
        Set<DependencyNode> dependsOn = new HashSet<>();

        public DependencyNode(String value) {
            this.value = value;
        }

        void addDependance(DependencyNode dependencyNode) {
            dependencyNode.isLeaf = false;
            this.dependsOn.add(dependencyNode);

        }
    }
}