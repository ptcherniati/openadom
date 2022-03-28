package fr.inra.oresing.model;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import fr.inra.oresing.checker.*;
import fr.inra.oresing.model.internationalization.*;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.assertj.core.util.Streams;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Nullable;
import java.util.*;

@Getter
@Setter
@ToString
public class Configuration {
    @ApiModelProperty(notes = "the set of requiredAuthorization of data.authorization section", required = false, hidden = true)
    private List<String> requiredAuthorizationsAttributes;
    @ApiModelProperty(notes = "the version number of the yaml schema used to read the deposited yaml",required = true)
    private int version;
    @ApiModelProperty(notes = "The internationalization description from other sections", required = false, hidden = true)
    private InternationalizationMap internationalization;
    @ApiModelProperty(notes = "a comment about this yaml",required = false)
    private String comment;
    @ApiModelProperty(notes = "The Application description",required = true)
    private ApplicationDescription application;
    @ApiModelProperty(notes = "A list of references indexed by name. A reference is used to describe other references or data..",required = true)
    private LinkedHashMap<String, ReferenceDescription> references = new LinkedHashMap<>();
   @ApiModelProperty(notes = "A composite reference allows you to link references according to an ''is in'' link. For example between a city and country reference.\n" +
           "You can define several composite references, and a composite reference can contain only one reference or contain a recursion.\n" +
           "All references used in a datatype.authorization.authorizationscope section must be composite.",required = true)
     private LinkedHashMap<String, CompositeReferenceDescription> compositeReferences = new LinkedHashMap<>();
    @ApiModelProperty(notes = "A data type describes a set of data representing a cohesive set of measurements or observations. (stored in one csv file format).",required = false)
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
        LinkedHashMap<String, LineValidationRuleDescription> validations = reference.getValue().getValidations();
        if (!CollectionUtils.isEmpty(validations)) {
            for (Map.Entry<String, LineValidationRuleDescription> validation : validations.entrySet()) {
                CheckerDescription checker = validation.getValue().getChecker();
                if (checker != null) {
                    String refType = Optional.of(checker)
                            .map(CheckerDescription::getParams)
                            .map(CheckerConfigurationDescription::getRefType)
                            .orElse(null);
                    if ("Reference".equals(checker.getName()) && refType != null) {
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
        private LinkedHashMap<String, ReferenceColumnDescription> columns = new LinkedHashMap<>();
        @ApiModelProperty(notes = "The list of dynamic columns descriptions.", required = true)
        private LinkedHashMap<String, ReferenceDynamicColumnDescription> dynamicColumns = new LinkedHashMap<>();
        @ApiModelProperty(notes = "The list des validations à effectuer.", required = false)
        private LinkedHashMap<String, LineValidationRuleDescription> validations = new LinkedHashMap<>();

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
    @ToString
    public static class ReferenceColumnDescription {
        @ApiModelProperty(notes = "If the column is or not mandatory", required = true, example = "MANDATORY", allowableValues = "MANDATORY,OPTIONAL")
        private ColumnPresenceConstraint presenceConstraint = ColumnPresenceConstraint.MANDATORY;
    }

    @Getter
    @Setter
    @ToString
    public static class ReferenceDynamicColumnDescription {
        @ApiModelProperty(notes = "The header prefix. All culumnsthat startswith this prefix use this description", required = true)
        private String headerPrefix = "";
        @ApiModelProperty(notes = "The reference that contains the column names", required = true)
        private String reference;
        @ApiModelProperty(notes = "The column in this reference that contains the column names", required = true)
        private String referenceColumnToLookForHeader;
        @ApiModelProperty(notes = "If the column is or not mandatory", required = true, example = "MANDATORY", allowableValues = "MANDATORY,OPTIONAL")
        private ColumnPresenceConstraint presenceConstraint = ColumnPresenceConstraint.MANDATORY;
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
        @ApiModelProperty(notes = "the reference composing the composite reference", required = true)
        String reference;
        @ApiModelProperty(notes = "the reference where this reference is in", required = false)
        String parentKeyColumn;
        @ApiModelProperty(notes = "for recursive composite reference :the reference where this reference column that's contains parent key", required = false)
        String parentRecursiveKey;
    }

    @Getter
    @Setter
    @ToString
    public static class DataTypeDescription extends InternationalizationMapDisplayImpl {
        @ApiModelProperty(notes = "this section describes the binding between a file and the data", required = true)
        FormatDescription format;
        @ApiModelProperty(notes = "this section describes the data model", required = true)
        LinkedHashMap<String, ColumnDescription> data = new LinkedHashMap<>();
        @ApiModelProperty(notes = "this section validate the data format", required = true)
        LinkedHashMap<String, LineValidationRuleDescription> validations = new LinkedHashMap<>();
        @ApiModelProperty(notes = "this section defines the natural key of a line", required = false)
        List<VariableComponentKey> uniqueness = new LinkedList<>();
        @ApiModelProperty(notes = "this section defines how to migrate the data when a new version of yaml is registred", required = false)
        TreeMap<Integer, List<MigrationDescription>> migrations = new TreeMap<>();
        @ApiModelProperty(notes = "this section defines the autorizations for this dataType", required = false)
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
    @ToString
    public static class LineValidationRuleDescription {
        @ApiModelProperty(notes = "A description of the validation", required = false)
        String description;
        @ApiModelProperty(notes = "A checker that can validate a column", required = true)
        CheckerDescription checker;
    }

    @Getter
    @Setter
    @ToString
    public static class AuthorizationDescription {
        @ApiModelProperty(notes = "The variable component with checker date that identify the time scope of the line", required = true)
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
        @ApiModelProperty(notes = "The variable name", required = true)
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
        @ApiModelProperty(notes = "The line with columns names", required = true)
        private int headerLine = 1;
        @ApiModelProperty(notes = "The first line withdata", required = true)
        private int firstRowLine = 2;
        @ApiModelProperty(notes = "The csv separator", required = false)
        private char separator = ';';
        @ApiModelProperty(notes = "The description for binding columns content to variable component", required = true)
        private List<ColumnBindingDescription> columns = new LinkedList<>();
        @ApiModelProperty(notes = "The description for binding repeated columns content to variable component", required = false)
        private List<RepeatedColumnBindingDescription> repeatedColumns = new LinkedList<>();
        @ApiModelProperty(notes = "The description of some values in header to bind to varaible component", required = false)
        private List<HeaderConstantDescription> constants = new LinkedList<>();
    }

    @Getter
    @Setter
    @ToString
    public static class HeaderConstantDescription {
        @ApiModelProperty(notes = "The row where is the constant value", required = true)
        int rowNumber;
        @ApiModelProperty(notes = "The column where is the constant value. Id empty headerName is required", required = false)
        int columnNumber;
        @ApiModelProperty(notes = "The header column name of column where is the constant value. Id empty columnNumber is required", required = false)
        String headerName;
        @ApiModelProperty(notes = "The variable component to bound to", required = true)
        VariableComponentKey boundTo;
        @ApiModelProperty(notes = "The export header name", required = true)
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
        @ApiModelProperty(notes = "The  header name of column that contains the value to bind", required = true)
        String header;
        @ApiModelProperty(notes = "The  variable component to bind to", required = true)
        VariableComponentKey boundTo;
    }

    @Getter
    @Setter
    @ToString
    public static class RepeatedColumnBindingDescription {
        @ApiModelProperty(notes = "The regexp pattern to find repeated columns to bind", required = true)
        String headerPattern;
        @ApiModelProperty(notes = "The export header of these columns", required = true)
        String exportHeader;
        @ApiModelProperty(notes = "How bind the result of regexp parenthesis. $1 to first pattern ...", required = false)
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
        @ApiModelProperty(notes = "The export header name", required = true)
        String exportHeader;
    }

    @Getter
    @Setter
    @ToString
    public static class ColumnDescription {
        @ApiModelProperty(notes = "A description to create disponibility charts", required = false)
        Chart chartDescription;
        @ApiModelProperty(notes = "A list of variable component", required = true)
        LinkedHashMap<String, VariableComponentDescription> components = new LinkedHashMap<>();
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
    @ToString
    public static class VariableComponentDescription {
        @ApiModelProperty(notes = "A checker description", required = false)
        CheckerDescription checker;
        @Nullable
        @ApiModelProperty(notes = "A default value if ciolumn is empty. This is a groovy expression", required = false)
        String defaultValue;
        @ApiModelProperty(notes = "the params of the  checker. Required for some checkers", required = false)
        VariableComponentDescriptionConfiguration params;
    }

    @Getter
    @Setter
    @ToString
    public static class VariableComponentDescriptionConfiguration implements GroovyDataInjectionConfiguration {
        @ApiModelProperty(notes = "the list of references values to add in groovy context", required = false)
        Set<String> references = new LinkedHashSet<>();
        @ApiModelProperty(notes = "the list of datatypes values to add in groovy context", required = false)
        Set<String> datatypes = new LinkedHashSet<>();
        @ApiModelProperty(notes = "if true the result of grrovy expression replace the value of the column", required = false)
        boolean replace;
    }

    @Getter
    @Setter
    @ToString
    public static class CheckerDescription {
        @ApiModelProperty(notes = "The name of the checker that must be used", required = true, allowableValues = "RegularExpression,Reference,Float,Integer,Date,GroovyExpression")
        String name;
        @ApiModelProperty(notes = "the params of the checker to configure it. Required for some checkers", required = false)
        CheckerConfigurationDescription params;
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
        @ApiModelProperty(notes = "the pattern of a regular expression for RegularExpression checker\nthe pattern of a date for Date checker", required = false)
        String pattern;
        @ApiModelProperty(notes = "the name of the reference for Reference checker", required = false)
        String refType;
        @ApiModelProperty(notes = "A groovy expression for Reference checker, GroovyChecker", required = false)
        GroovyConfiguration groovy;
        @ApiModelProperty(notes = "The list of columns to build natural key of reference for Reference checker", required = false)
        String columns;
        @ApiModelProperty(hidden = true)
        String variableComponentKey;
        String duration;
        @ApiModelProperty(notes = "If true codifies the column value", required = false)
        boolean codify;
        @ApiModelProperty(notes = "If true the value can't be null", required = false)
        boolean required;
        @ApiModelProperty(notes = "If MANY the value is a list of references for Reference checker", required = false, example ="MANY", allowableValues = "MANY,ONE")
        Multiplicity multiplicity = Multiplicity.ONE;

        public ImmutableSet<String> doGetColumnsAsCollection() {
            if (StringUtils.isEmpty(getColumns())) {
                return ImmutableSet.of();
            }
            return Streams.stream(Splitter.on(",").split(getColumns())).collect(ImmutableSet.toImmutableSet());
        }
    }

    @Getter
    @Setter
    @ToString
    public static class GroovyConfiguration implements fr.inra.oresing.checker.GroovyConfiguration {
        @ApiModelProperty(notes = "a groovy expression", required = false)
        String expression;
        @ApiModelProperty(notes = "the list of references values to add in groovy context", required = false)
        Set<String> references = new LinkedHashSet<>();
        @ApiModelProperty(notes = "the list of datatypes values to add in groovy context", required = false)
        Set<String> datatypes = new LinkedHashSet<>();
    }

    @Getter
    @Setter
    @ToString
    public static class DataGroupDescription extends InternationalizationImpl {
        @ApiModelProperty(notes = "the description of internationalization of the datagroup", required = false)
        Internationalization internationalizationName;
        @ApiModelProperty(notes = "the name of the datagroup", required = true)
        String label;
        @ApiModelProperty(notes = "the list of variable in this datagroup", required = true)
        Set<String> data = new LinkedHashSet<>();
    }

    @Getter
    @Setter
    @ToString
    public static class ApplicationDescription extends InternationalizationImpl {
        @ApiModelProperty(notes = "The unique name of the application",required = true)
        String name;
        @ApiModelProperty(notes = "The version incremental version number of this yaml description of this application",required = true)
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
        @ApiModelProperty(notes = "the migration strategy", required = true, example = "ADD_VARIABLE", allowableValues = "ADD_VARIABLE")
        MigrationStrategy strategy;
        @ApiModelProperty(notes = "a datagroup nalme", required = true)
        String dataGroup;
        @ApiModelProperty(notes = "a variable in this datagroup", required = true)
        String variable;
        @ApiModelProperty(notes = "a list of component migration description for this variable", required = true)
        Map<String, AddVariableMigrationDescription> components = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    @ToString
    public static class AddVariableMigrationDescription {
        @ApiModelProperty(notes = "the value by default if the variable component is empty after migration", required = true)
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