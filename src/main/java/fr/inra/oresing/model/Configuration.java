package fr.inra.oresing.model;

import com.google.common.collect.MoreCollectors;
import fr.inra.oresing.checker.ReferenceLineChecker;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.springframework.util.CollectionUtils;

import javax.annotation.Nullable;
import java.util.*;

@Getter
@Setter
@ToString
public class Configuration {

    public Optional<CompositeReferenceDescription> getCompositeReferencesUsing(String reference) {
        return getCompositeReferences().values().stream()
                .filter(compositeReferenceDescription -> compositeReferenceDescription.isDependentOfReference(reference))
                .collect(MoreCollectors.toOptional());
    }

    private int version;
    private ApplicationDescription application;

    class DependencyNode {
        String value;
        boolean isRoot = true;
        Set<DependencyNode> children = new HashSet<>();

        void addChild(DependencyNode childNode) {
            childNode.isRoot = false;
            this.children.add(childNode);

        }

        public DependencyNode(String value) {
            this.value = value;
        }
    }

    public LinkedHashMap<String, ReferenceDescription> getReferences() {
        Map<String, Set<String>> dependsOf = new HashMap<>();
        Map<String, DependencyNode> nodes = new LinkedHashMap<>();
        for (Map.Entry<String, ReferenceDescription> reference : references.entrySet()) {
            DependencyNode node = nodes.computeIfAbsent(reference.getKey(), k -> new DependencyNode(reference.getKey()));
            LinkedHashMap<String, LineValidationRuleDescription> validations = reference.getValue().getValidations();
            if (!CollectionUtils.isEmpty(validations)) {
                for (Map.Entry<String, LineValidationRuleDescription> validation : validations.entrySet()) {
                    CheckerDescription checker = validation.getValue().getChecker();
                    if (checker != null) {
                        String refType = Optional.ofNullable(checker)
                                .map(c -> c.getParams())
                                .map(param -> param.get(ReferenceLineChecker.PARAM_REFTYPE))
                                .orElse(null);
                        if ("Reference".equals(checker.getName()) && refType != null) {
                            DependencyNode dependencyNode = nodes.computeIfAbsent(refType, k -> new DependencyNode(refType));
                            dependencyNode.addChild(node);
                        }
                    }
                }
            }
        }
        LinkedHashMap<String, ReferenceDescription> sortedReferences = new LinkedHashMap<>();
        nodes.values().stream().filter(node->node.isRoot).forEach(node -> addRecursively(node, sortedReferences, references));
        return sortedReferences;
    }

    private void addRecursively(DependencyNode node, LinkedHashMap<String, ReferenceDescription> sortedReferences, LinkedHashMap<String, ReferenceDescription> references) {
        sortedReferences.put(node.value, references.get(node.value));
        if (!node.children.isEmpty()) {
            node.children.forEach(child-> addRecursively(child, sortedReferences,references));
        }

    }

    private LinkedHashMap<String, ReferenceDescription> references = new LinkedHashMap<>();
    private LinkedHashMap<String, CompositeReferenceDescription> compositeReferences = new LinkedHashMap<>();
    private LinkedHashMap<String, DataTypeDescription> dataTypes = new LinkedHashMap<>();

    @Getter
    @Setter
    @ToString
    public static class ReferenceDescription {
        private char separator = ';';
        private List<String> keyColumns = new LinkedList<>();
        private LinkedHashMap<String, ColumnDescription> columns;
        private LinkedHashMap<String, LineValidationRuleDescription> validations = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    @ToString
    public static class CompositeReferenceDescription {
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
    public static class CompositeReferenceComponentDescription {
        String reference;
        String parentKeyColumn;
        String recursive;
    }

    @Getter
    @Setter
    @ToString
    public static class DataTypeDescription {
        FormatDescription format;
        LinkedHashMap<String, ColumnDescription> data = new LinkedHashMap<>();
        LinkedHashMap<String, LineValidationRuleDescription> validations = new LinkedHashMap<>();
        TreeMap<Integer, List<MigrationDescription>> migrations = new TreeMap<>();
        AuthorizationDescription authorization;
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
        LinkedHashMap<String, VariableComponentKey> authorizationScopes = new LinkedHashMap<>();
        LinkedHashMap<String, DataGroupDescription> dataGroups = new LinkedHashMap<>();
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
    }

    @Getter
    @Setter
    @ToString
    public static class CheckerDescription {
        String name;
        Map<String, String> params = new LinkedHashMap<>();
    }

    @Getter
    @Setter
    @ToString
    public static class DataGroupDescription {
        String label;
        Set<String> data = new LinkedHashSet<>();
    }

    @Getter
    @Setter
    @ToString
    public static class ApplicationDescription {
        String name;
        int version;
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

    public enum MigrationStrategy {
        ADD_VARIABLE
    }
}
