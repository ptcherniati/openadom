package fr.inra.oresing.model;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import com.google.common.collect.Sets;
import fr.inra.oresing.checker.CheckerTarget;
import fr.inra.oresing.checker.DateLineCheckerConfiguration;
import fr.inra.oresing.checker.FloatCheckerConfiguration;
import fr.inra.oresing.checker.GroovyLineCheckerConfiguration;
import fr.inra.oresing.checker.IntegerCheckerConfiguration;
import fr.inra.oresing.checker.Multiplicity;
import fr.inra.oresing.checker.ReferenceLineCheckerConfiguration;
import fr.inra.oresing.checker.RegularExpressionCheckerConfiguration;
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
import io.swagger.annotations.ApiModelProperty;
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

    @ApiModelProperty(notes = "The set of requiredAuthorization of data.authorization section. Fill by aplication", required = false, hidden = true)
    private List<String> requiredAuthorizationsAttributes;

    @ApiModelProperty(notes = "The version number of the yaml schema used to read the deposited yaml",required = true, example = "1")
    private int version;

    @ApiModelProperty(notes = "The internationalization description from other sections. Fill by application", required = false, hidden = true)
    private InternationalizationMap internationalization;

    @ApiModelProperty(notes = "A comment about this yaml",required = false, example = "Adding sites section")
    private String comment;

    @ApiModelProperty(notes = "An Application description",required = true)
    private ApplicationDescription application;

    @ApiModelProperty(notes = "A list of references indexed by name. A reference is used to describe other references or data..",required = true)
    private LinkedHashMap<String, ReferenceDescription> references = new LinkedHashMap<>();

    @ApiModelProperty(notes = "A composite reference allows you to link references according to an ''is in'' link. For example between a city and country reference.\n" +
           "You can define several composite references, and a composite reference can contain only one reference or contain a recursion.\n" +
           "All references used in a datatype.authorization.authorizationscope section must be composite.",required = true)
    private LinkedHashMap<String, CompositeReferenceDescription> compositeReferences = new LinkedHashMap<>();

    @ApiModelProperty(notes = "A data type describes a set of data representing a cohesive set of measurements or observations. (values can be stored in one csv file format).",required = false)
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

        @ApiModelProperty(notes = "The separator in csv files", required = false)
        private char separator = ';';

        @ApiModelProperty(notes = "The list of columns composing the natural key of a row.", required = true)
        private List<String> keyColumns = new LinkedList<>();

        @ApiModelProperty(notes = "The list of columns descriptions.", required = true)
        private LinkedHashMap<String, ReferenceStaticNotComputedColumnDescription> columns = new LinkedHashMap<>();
        private LinkedHashMap<String, ReferenceStaticComputedColumnDescription> computedColumns = new LinkedHashMap<>();

        @ApiModelProperty(notes = "The list of dynamic columns descriptions. Dynamic columns names reffers to an other reference.", required = true)
        private LinkedHashMap<String, ReferenceDynamicColumnDescription> dynamicColumns = new LinkedHashMap<>();

        @ApiModelProperty(notes = "The list of validations to perform on this reference.", required = false)
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

        @ApiModelProperty(notes = "If the column is or not mandatory", required = true, example = "MANDATORY", allowableValues = "MANDATORY,OPTIONAL")
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

        @ApiModelProperty(notes = "The header prefix. All culumnsthat startswith this prefix use this description", example = "rt_", required = true)
        private String headerPrefix = "";

        @ApiModelProperty(notes = "The reference that contains the column names", required = true, example = "proprietes_taxon")
        private String reference;

        @ApiModelProperty(notes = "The column in this reference that contains the column names", required = true, example = "name")
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

        @ApiModelProperty(notes = "The reference composing the composite reference", required = true, example = "types_sites")
        String reference;

        @ApiModelProperty(notes = "The reference where this reference refers to", required = false, example = "name")
        String parentKeyColumn;

        @ApiModelProperty(notes = "For recursive composite reference : the reference column that's contains parent key", required = false, example = "parent_key")
        String parentRecursiveKey;
    }

    @Getter
    @Setter
    @ToString
    public static class DataTypeDescription extends InternationalizationMapDisplayImpl {

        @ApiModelProperty(notes = "This section describes a binding between a file and the data", required = true)
        FormatDescription format;

        @ApiModelProperty(notes = "This section describes the data model", required = true)
        LinkedHashMap<String, ColumnDescription> data = new LinkedHashMap<>();

        @ApiModelProperty(notes = "This section validate the data format", required = true)
        LinkedHashMap<String, LineValidationRuleWithVariableComponentsDescription> validations = new LinkedHashMap<>();

        @ApiModelProperty(notes = "This section defines the natural key of a line", required = false)
        List<VariableComponentKey> uniqueness = new LinkedList<>();

        @ApiModelProperty(notes = "This section defines how to migrate the data when a new version of yaml is registred", required = false)
        TreeMap<Integer, List<MigrationDescription>> migrations = new TreeMap<>();

        @ApiModelProperty(notes = "This section defines the autorizations for this dataType", required = true)
        AuthorizationDescription authorization;

        @ApiModelProperty(notes = "If this section existe, the data file will be store on a repository tree", required = false)
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
    public static abstract class LineValidationRuleDescription {

        @ApiModelProperty(notes = "A description of the validation", required = false)
        String description;

        @ApiModelProperty(notes = "A checker that can validate one or some columns. Can also build new values from other values.", required = true)
        CheckerDescription checker;
        public abstract Set<CheckerTarget> doGetCheckerTargets();
    }

    @Getter
    @Setter
    @ToString
    public static class LineValidationRuleWithColumnsDescription extends LineValidationRuleDescription {

        @ApiModelProperty(notes = "The list of columns to build natural key of reference for Reference checker", required = false)
        Set<String> columns;

        @Override
        public Set<CheckerTarget> doGetCheckerTargets() {
            return columns.stream()
                    .map(ReferenceColumn::new)
                    .collect(Collectors.toUnmodifiableSet());
        }
    }

    @Getter
    @Setter
    @ToString
    public static class LineValidationRuleWithVariableComponentsDescription extends LineValidationRuleDescription {

        @ApiModelProperty(notes = "the variable component key for this checkern filled by application", required = false, hidden = true)
        Set<VariableComponentKey> variableComponents;

        @Override
        public Set<CheckerTarget> doGetCheckerTargets() {
            return Set.copyOf(variableComponents);
        }
    }

    @Getter
    @Setter
    @ToString
    public static class AuthorizationDescription {

        @ApiModelProperty(notes = "The variable component with a checker date that identify the time scope of the line", required = true)
        VariableComponentKey timeScope;

        @ApiModelProperty(notes = "A list of authorization scopes. An authorization scope is for example a an authorization on a location", required = true)
        LinkedHashMap<String, AuthorizationScopeDescription> authorizationScopes = new LinkedHashMap<>();

        @ApiModelProperty(notes = "A list of datagroups. A line wil be split into as many lines as there are data groups. Datagroups is partition of variables", required = true)
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

        @ApiModelProperty(notes = "The variable name", required = true, example = "temperature")
        String variable;

        @ApiModelProperty(notes = "The component name. A component is an information about a variable", required = true, example = "unit")
        String component;

        public VariableComponentKey getVariableComponentKey() {
            return new VariableComponentKey(variable, component);
        }
    }

    @Getter
    @Setter
    @ToString
    public static class FormatDescription {

        @ApiModelProperty(notes = "The line with columns names", required = true, example = "1")
        private int headerLine = 1;

        @ApiModelProperty(notes = "The first line with data", required = true, example = "2")
        private int firstRowLine = 2;

        @ApiModelProperty(notes = "The csv separator", required = false, example = ";")
        private char separator = ';';

        @ApiModelProperty(notes = "The description for binding columns content to variable component", required = true)
        private List<ColumnBindingDescription> columns = new LinkedList<>();

        @ApiModelProperty(notes = "The description for binding repeated columns content to variable component", required = false)
        private List<RepeatedColumnBindingDescription> repeatedColumns = new LinkedList<>();

        @ApiModelProperty(notes = "The description of some values in header to bind to variable component", required = false)
        private List<HeaderConstantDescription> constants = new LinkedList<>();
    }

    @Getter
    @Setter
    @ToString
    public static class HeaderConstantDescription {

        @ApiModelProperty(notes = "The row where is the constant value", required = true, example = "1")
        int rowNumber;

        @ApiModelProperty(notes = "The column where is the constant value. Id empty headerName is required", required = false, example = "2")
        int columnNumber;

        @ApiModelProperty(notes = "The header column name of column where is the constant value. Id empty columnNumber is required", required = false, example = "CO2")
        String headerName;

        @ApiModelProperty(notes = "The variable component to bound to", required = true)
        VariableComponentKey boundTo;

        @ApiModelProperty(notes = "The export header name", required = true, example = "CO2_unit")
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

        @ApiModelProperty(notes = "The  header name of column that contains the value to bind", required = true, example = "CO2")
        String header;

        @ApiModelProperty(notes = "The  variable component to bind to", required = true)
        VariableComponentKey boundTo;
    }

    @Getter
    @Setter
    @ToString
    public static class RepeatedColumnBindingDescription {

        @ApiModelProperty(notes = "The regexp pattern to find repeated columns to bind", required = true, example = "(.*)_([0-9]*)_([0-9]*)")
        String headerPattern;

        @ApiModelProperty(notes = "The export header (for value) of these columns", required = true, example = "SMP")
        String exportHeader;

        @ApiModelProperty(notes = "How bind the result of regexp parenthesis. $1 to first pattern, $2 is the second ...", required = false)
        List<HeaderPatternToken> tokens = new LinkedList<>();

        @ApiModelProperty(notes = "How bind the value column", required = true)
        VariableComponentKey boundTo;
    }

    @Getter
    @Setter
    @ToString
    public static class HeaderPatternToken {

        @ApiModelProperty(notes = "The variable component to bind to", required = true)
        VariableComponentKey boundTo;

        @ApiModelProperty(notes = "The export header(for pattern) name", required = true, example = "profondeur")
        String exportHeader;
    }

    @Getter
    @Setter
    @ToString
    public static class ColumnDescription {

        @ApiModelProperty(notes = "A description to create disponibility charts", required = false)
        Chart chartDescription;

        @ApiModelProperty(notes = "A list of variable component", required = true)
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

        @ApiModelProperty(notes = "The component contening value", required = true)
        String value;

        @ApiModelProperty(notes = "A variable component for aggregate values", required = false)
        VariableComponentKey aggregation = null;

        @ApiModelProperty(notes = "A variable component for unit", required = false)
        String unit = null;

        @ApiModelProperty(notes = "An sql expression for max gap between consecutives values", required = false)
        String gap = null;

        @ApiModelProperty(notes = "A component for standardDeviation", required = false)
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

        @ApiModelProperty(notes = "A checker description", required = false)
        CheckerDescription checker;
    }

    @Getter
    @Setter
    @ToString
    public static class VariableComponentWithDefaultValueDescription extends VariableComponentDescription {

        @ApiModelProperty(notes = "A default value if ciolumn is empty. This is a groovy expression", required = false, example = "-9999")
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

        @ApiModelProperty(notes = "The name of the checker that must be used", required = true, allowableValues = "RegularExpression,Reference,Float,Integer,Date,GroovyExpression")
        String name;

        @ApiModelProperty(notes = "The params of the checker to configure it. Required for some checkers", required = false)
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

        @ApiModelProperty(notes = "The pattern of a regular expression for RegularExpression checker\nthe pattern of a date for Date checker", required = false, example = "dd/MM/yyyy")
        String pattern;

        @ApiModelProperty(notes = "the name of the reference for Reference checker", required = false, example = "units")
        String refType;

        @ApiModelProperty(notes = "A groovy expression for Reference checker, GroovyChecker", required = false)
        GroovyConfiguration groovy;
        String duration;
        TransformationConfigurationDescription transformation = new TransformationConfigurationDescription();

        @ApiModelProperty(notes = "If true the value can't be null", required = false, example = "true", allowableValues = "true,false")
        boolean required = true;

        @ApiModelProperty(notes = "If MANY the value is a list of references for Reference checker", required = false, example ="MANY", allowableValues = "MANY,ONE")
        Multiplicity multiplicity = Multiplicity.ONE;
    }

    @Getter
    @Setter
    @ToString
    public static class TransformationConfigurationDescription implements TransformationConfiguration {

        @ApiModelProperty(notes = "If true codifies the column value", required = false, example = "true", allowableValues = "true,false")
        boolean codify;
        GroovyConfiguration groovy;
    }

    @Getter
    @Setter
    @ToString
    public static class GroovyConfiguration implements fr.inra.oresing.checker.GroovyConfiguration {

        @ApiModelProperty(notes = "A groovy expression", required = false, example = ">\n" +
                "                String dataType = Arrays.stream(datum.dataType)\n" +
                "                  .split(\"_\"))\n" +
                "                  .collect{it.substring(0, 1)}\n" +
                "                  .join(); " +
                "                return application.dataType.contains(dataType);")
        String expression;

        @ApiModelProperty(notes = "The list of references values in database to add to groovy context", required = false)
        Set<String> references = new LinkedHashSet<>();

        @ApiModelProperty(notes = "The list of datatypes values in database to add to groovy context", required = false)
        Set<String> datatypes = new LinkedHashSet<>();
    }

    @Getter
    @Setter
    @ToString
    public static class DataGroupDescription extends InternationalizationImpl {

        @ApiModelProperty(notes = "The name of the datagroup", required = true, example = "localizations")
        String label;

        @ApiModelProperty(notes = "The list of variable in this datagroup", required = true)
        Set<String> data = new LinkedHashSet<>();
    }

    @Getter
    @Setter
    @ToString
    public static class ApplicationDescription extends InternationalizationImpl {

        @ApiModelProperty(notes = "The unique name of the application",required = true, example = "ACBB")
        String name;

        @ApiModelProperty(notes = "The version incremental version number of this yaml description of this application",required = true, example = "1")
        int version;

        @ApiModelProperty(notes = "The default language if none is provided",required = false, example = "fr")
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

        @ApiModelProperty(notes = "The migration strategy", required = true, example = "ADD_VARIABLE", allowableValues = "ADD_VARIABLE")
        MigrationStrategy strategy;

        @ApiModelProperty(notes = "A datagroup name", required = true, example = "variables")
        String dataGroup;

        @ApiModelProperty(notes = "A variable in this datagroup", required = true, example = "CO2")
        String variable;

        @ApiModelProperty(notes = "A list of component migration description for this variable", required = true)
        Map<String, AddVariableMigrationDescription> components = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    @ToString
    public static class AddVariableMigrationDescription {

        @ApiModelProperty(notes = "The value by default if the variable component is empty after migration", required = true, example = "-9999")
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