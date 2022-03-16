package fr.inra.oresing.model;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.MoreCollectors;
import fr.inra.oresing.checker.*;
import fr.inra.oresing.model.internationalization.*;
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
        private char separator = ';';
        private List<String> keyColumns = new LinkedList<>();
        private LinkedHashMap<String, ReferenceColumnDescription> columns = new LinkedHashMap<>();
        private LinkedHashMap<String, ReferenceDynamicColumnDescription> dynamicColumns = new LinkedHashMap<>();
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
        private ColumnPresenceConstraint presenceConstraint = ColumnPresenceConstraint.MANDATORY;
    }

    @Getter
    @Setter
    @ToString
    public static class ReferenceDynamicColumnDescription {
        private String headerPrefix = "";
        private String reference;
        private String referenceColumnToLookForHeader;
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
        VariableComponentKey boundTo;
        String exportHeader;
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
        LinkedHashMap<String, VariableComponentDescription> components = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    @ToString
    public static class VariableComponentDescription {
        CheckerDescription checker;
        @Nullable
        String defaultValue;
        VariableComponentDescriptionConfiguration params;
    }

    @Getter
    @Setter
    @ToString
    public static class VariableComponentDescriptionConfiguration implements GroovyDataInjectionConfiguration {
        Set<String> references = new LinkedHashSet<>();
        Set<String> datatypes = new LinkedHashSet<>();
        boolean replace;
    }

    @Getter
    @Setter
    @ToString
    public static class CheckerDescription {
        String name;
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
        String pattern;
        String refType;
        GroovyConfiguration groovy;
        String columns;
        String variableComponentKey;
        String duration;
        boolean codify;
        boolean required;
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