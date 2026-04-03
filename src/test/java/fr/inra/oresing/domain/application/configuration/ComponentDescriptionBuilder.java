package fr.inra.oresing.domain.application.configuration;

import fr.inra.oresing.domain.ComponentPresenceConstraint;
import fr.inra.oresing.domain.application.configuration.checker.CheckerDescription;
import fr.inra.oresing.domain.application.configuration.checker.ComputationChecker;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class ComponentDescriptionBuilder {

    public static BasicComponentBuilder basicComponent() {
        return new BasicComponentBuilder();
    }

    public static ComputedComponentBuilder computedComponent() {
        return new ComputedComponentBuilder();
    }

    public static ConstantComponentBuilder constantComponent() {
        return new ConstantComponentBuilder();
    }

    public static DynamicComponentBuilder dynamicComponent() {
        return new DynamicComponentBuilder();
    }

    public static FilteredDescriptionComponentBuilder filteredDescriptionComponent() {
        return new FilteredDescriptionComponentBuilder();
    }

    public static PatternComponentBuilder patternComponent() {
        return new PatternComponentBuilder();
    }

    public static PatternComponentQualifiersBuilder patternComponentQualifiers() {
        return new PatternComponentQualifiersBuilder();
    }

    public static PatternComponentAdjacentsBuilder patternComponentAdjacents() {
        return new PatternComponentAdjacentsBuilder();
    }

    public static ReferenceScopeComponentBuilder referenceScopeComponent() {
        return new ReferenceScopeComponentBuilder();
    }

    public static class BasicComponentBuilder {
        private String componentKey;
        private ComputationChecker defaultValue;
        private Set<Tag> tags = Set.of();
        private String importHeader;
        private String exportHeaderName;
        private List<Locale> langRestrictions = List.of();
        private boolean required = false;
        private ComponentPresenceConstraint mandatory = ComponentPresenceConstraint.OPTIONAL;
        private CheckerDescription checker;
        private String submissionAuthorizationScope;

        public BasicComponentBuilder componentKey(String componentKey) {
            this.componentKey = componentKey;
            return this;
        }

        public BasicComponentBuilder defaultValue(ComputationChecker defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public BasicComponentBuilder tags(Set<Tag> tags) {
            this.tags = tags;
            return this;
        }

        public BasicComponentBuilder importHeader(String importHeader) {
            this.importHeader = importHeader;
            return this;
        }

        public BasicComponentBuilder exportHeaderName(String exportHeaderName) {
            this.exportHeaderName = exportHeaderName;
            return this;
        }

        public BasicComponentBuilder langRestrictions(List<Locale> langRestrictions) {
            this.langRestrictions = langRestrictions;
            return this;
        }

        public BasicComponentBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public BasicComponentBuilder mandatory(ComponentPresenceConstraint mandatory) {
            this.mandatory = mandatory;
            return this;
        }

        public BasicComponentBuilder checker(CheckerDescription checker) {
            this.checker = checker;
            return this;
        }

        public BasicComponentBuilder submissionAuthorizationScope(String submissionAuthorizationScope) {
            this.submissionAuthorizationScope = submissionAuthorizationScope;
            return this;
        }

        public BasicComponent build() {
            return new BasicComponent(
                    ComponentDescription.ComponentDescriptionType.BasicComponent,
                    componentKey,
                    defaultValue,
                    tags,
                    importHeader,
                    exportHeaderName,
                    langRestrictions,
                    required,
                    mandatory,
                    checker,
                    submissionAuthorizationScope
            );
        }
    }

    public static class ComputedComponentBuilder {
        private String componentKey;
        private Set<Tag> tags = Set.of();
        private String exportHeaderName;
        private List<Locale> langRestrictions = List.of();
        private boolean required = false;
        private ComponentPresenceConstraint mandatory = ComponentPresenceConstraint.OPTIONAL;
        private CheckerDescription checker;
        private ComputationChecker computationChecker;
        private String submissionAuthorizationScope;

        public ComputedComponentBuilder componentKey(String componentKey) {
            this.componentKey = componentKey;
            return this;
        }

        public ComputedComponentBuilder tags(Set<Tag> tags) {
            this.tags = tags;
            return this;
        }

        public ComputedComponentBuilder exportHeaderName(String exportHeaderName) {
            this.exportHeaderName = exportHeaderName;
            return this;
        }

        public ComputedComponentBuilder langRestrictions(List<Locale> langRestrictions) {
            this.langRestrictions = langRestrictions;
            return this;
        }

        public ComputedComponentBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public ComputedComponentBuilder mandatory(ComponentPresenceConstraint mandatory) {
            this.mandatory = mandatory;
            return this;
        }

        public ComputedComponentBuilder checker(CheckerDescription checker) {
            this.checker = checker;
            return this;
        }

        public ComputedComponentBuilder computationChecker(ComputationChecker computationChecker) {
            this.computationChecker = computationChecker;
            return this;
        }

        public ComputedComponentBuilder submissionAuthorizationScope(String submissionAuthorizationScope) {
            this.submissionAuthorizationScope = submissionAuthorizationScope;
            return this;
        }

        public ComputedComponent build() {
            return new ComputedComponent(
                    ComponentDescription.ComponentDescriptionType.ComputedComponent,
                    componentKey,
                    tags,
                    exportHeaderName,
                    langRestrictions,
                    required,
                    mandatory,
                    checker,
                    computationChecker,
                    submissionAuthorizationScope
            );
        }
    }

    public static class ConstantComponentBuilder {
        private String componentKey;
        private ComputationChecker defaultValue;
        private Set<Tag> tags = Set.of();
        private boolean required = false;
        private ComponentPresenceConstraint mandatory = ComponentPresenceConstraint.OPTIONAL;
        private CheckerDescription checker;
        private ConstantImport constantImportHeader;
        private String exportHeaderName;
        private String submissionAuthorizationScope;

        public ConstantComponentBuilder componentKey(String componentKey) {
            this.componentKey = componentKey;
            return this;
        }

        public ConstantComponentBuilder defaultValue(ComputationChecker defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public ConstantComponentBuilder tags(Set<Tag> tags) {
            this.tags = tags;
            return this;
        }

        public ConstantComponentBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public ConstantComponentBuilder mandatory(ComponentPresenceConstraint mandatory) {
            this.mandatory = mandatory;
            return this;
        }

        public ConstantComponentBuilder checker(CheckerDescription checker) {
            this.checker = checker;
            return this;
        }

        public ConstantComponentBuilder constantImportHeader(ConstantImport constantImportHeader) {
            this.constantImportHeader = constantImportHeader;
            return this;
        }

        public ConstantComponentBuilder exportHeaderName(String exportHeaderName) {
            this.exportHeaderName = exportHeaderName;
            return this;
        }

        public ConstantComponentBuilder submissionAuthorizationScope(String submissionAuthorizationScope) {
            this.submissionAuthorizationScope = submissionAuthorizationScope;
            return this;
        }

        public ConstantComponent build() {
            return new ConstantComponent(
                    ComponentDescription.ComponentDescriptionType.ConstantComponent,
                    componentKey,
                    defaultValue,
                    tags,
                    required,
                    mandatory,
                    checker,
                    constantImportHeader,
                    exportHeaderName,
                    submissionAuthorizationScope
            );
        }
    }

    public static class DynamicComponentBuilder {
        private String componentKey;
        private ComputationChecker defaultValue;
        private String exportHeaderName;
        private List<Locale> langRestrictions = List.of();
        private Set<Tag> tags = Set.of();
        private boolean required = false;
        private ComponentPresenceConstraint mandatory = ComponentPresenceConstraint.OPTIONAL;
        private CheckerDescription checker;
        private String prefix;
        private String reference;
        private String referenceColumnToLookForHeader;
        private String submissionAuthorizationScope;

        public DynamicComponentBuilder componentKey(String componentKey) {
            this.componentKey = componentKey;
            return this;
        }

        public DynamicComponentBuilder defaultValue(ComputationChecker defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public DynamicComponentBuilder exportHeaderName(String exportHeaderName) {
            this.exportHeaderName = exportHeaderName;
            return this;
        }

        public DynamicComponentBuilder langRestrictions(List<Locale> langRestrictions) {
            this.langRestrictions = langRestrictions;
            return this;
        }

        public DynamicComponentBuilder tags(Set<Tag> tags) {
            this.tags = tags;
            return this;
        }

        public DynamicComponentBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public DynamicComponentBuilder mandatory(ComponentPresenceConstraint mandatory) {
            this.mandatory = mandatory;
            return this;
        }

        public DynamicComponentBuilder checker(CheckerDescription checker) {
            this.checker = checker;
            return this;
        }

        public DynamicComponentBuilder prefix(String prefix) {
            this.prefix = prefix;
            return this;
        }

        public DynamicComponentBuilder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public DynamicComponentBuilder referenceColumnToLookForHeader(String referenceColumnToLookForHeader) {
            this.referenceColumnToLookForHeader = referenceColumnToLookForHeader;
            return this;
        }

        public DynamicComponentBuilder submissionAuthorizationScope(String submissionAuthorizationScope) {
            this.submissionAuthorizationScope = submissionAuthorizationScope;
            return this;
        }

        public DynamicComponent build() {
            return new DynamicComponent(
                    ComponentDescription.ComponentDescriptionType.DynamicComponent,
                    componentKey,
                    defaultValue,
                    exportHeaderName,
                    langRestrictions,
                    tags,
                    required,
                    mandatory,
                    checker,
                    prefix,
                    reference,
                    referenceColumnToLookForHeader,
                    submissionAuthorizationScope
            );
        }
    }

    public static class FilteredDescriptionComponentBuilder {
        private String componentKey;
        private Set<Tag> tags = Set.of();
        private String submissionAuthorizationScope;

        public FilteredDescriptionComponentBuilder componentKey(String componentKey) {
            this.componentKey = componentKey;
            return this;
        }

        public FilteredDescriptionComponentBuilder tags(Set<Tag> tags) {
            this.tags = tags;
            return this;
        }

        public FilteredDescriptionComponentBuilder submissionAuthorizationScope(String submissionAuthorizationScope) {
            this.submissionAuthorizationScope = submissionAuthorizationScope;
            return this;
        }

        public FilteredDescriptionComponent build() {
            return new FilteredDescriptionComponent(
                    ComponentDescription.ComponentDescriptionType.TagsDescription,
                    componentKey,
                    tags,
                    submissionAuthorizationScope
            );
        }
    }

    public static class PatternComponentBuilder {
        private String componentKey;
        private ComputationChecker defaultValue;
        private String exportHeaderName;
        private List<Locale> langRestrictions = List.of();
        private Set<Tag> tags = Set.of();
        private boolean required = false;
        private ComponentPresenceConstraint mandatory = ComponentPresenceConstraint.OPTIONAL;
        private CheckerDescription checker;
        private String patternForComponents;
        private Map<String, PatternComponentQualifiers> patternComponentQualifiers = Map.of();
        private Map<String, PatternComponentAdjacents> patternComponentAdjacents = Map.of();
        private String submissionAuthorizationScope;

        public PatternComponentBuilder componentKey(String componentKey) {
            this.componentKey = componentKey;
            return this;
        }

        public PatternComponentBuilder defaultValue(ComputationChecker defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public PatternComponentBuilder exportHeaderName(String exportHeaderName) {
            this.exportHeaderName = exportHeaderName;
            return this;
        }

        public PatternComponentBuilder langRestrictions(List<Locale> langRestrictions) {
            this.langRestrictions = langRestrictions;
            return this;
        }

        public PatternComponentBuilder tags(Set<Tag> tags) {
            this.tags = tags;
            return this;
        }

        public PatternComponentBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public PatternComponentBuilder mandatory(ComponentPresenceConstraint mandatory) {
            this.mandatory = mandatory;
            return this;
        }

        public PatternComponentBuilder checker(CheckerDescription checker) {
            this.checker = checker;
            return this;
        }

        public PatternComponentBuilder patternForComponents(String patternForComponents) {
            this.patternForComponents = patternForComponents;
            return this;
        }

        public PatternComponentBuilder patternComponentQualifiers(Map<String, PatternComponentQualifiers> patternComponentQualifiers) {
            this.patternComponentQualifiers = patternComponentQualifiers;
            return this;
        }

        public PatternComponentBuilder patternComponentAdjacents(Map<String, PatternComponentAdjacents> patternComponentAdjacents) {
            this.patternComponentAdjacents = patternComponentAdjacents;
            return this;
        }

        public PatternComponentBuilder submissionAuthorizationScope(String submissionAuthorizationScope) {
            this.submissionAuthorizationScope = submissionAuthorizationScope;
            return this;
        }

        public PatternComponent build() {
            return new PatternComponent(
                    ComponentDescription.ComponentDescriptionType.PatternComponent,
                    componentKey,
                    defaultValue,
                    exportHeaderName,
                    langRestrictions,
                    tags,
                    required,
                    mandatory,
                    checker,
                    patternForComponents,
                    patternComponentQualifiers,
                    patternComponentAdjacents,
                    submissionAuthorizationScope
            );
        }
    }

    public static class PatternComponentQualifiersBuilder {
        private String componentKey;
        private ComputationChecker defaultValue;
        private Set<Tag> tags = Set.of();
        private String exportHeaderName;
        private List<Locale> langRestrictions = List.of();
        private int patternNumber;
        private CheckerDescription checker;
        private String submissionAuthorizationScope;

        public PatternComponentQualifiersBuilder componentKey(String componentKey) {
            this.componentKey = componentKey;
            return this;
        }

        public PatternComponentQualifiersBuilder defaultValue(ComputationChecker defaultValue) {
            this.defaultValue = defaultValue;
            return this;
        }

        public PatternComponentQualifiersBuilder tags(Set<Tag> tags) {
            this.tags = tags;
            return this;
        }

        public PatternComponentQualifiersBuilder exportHeaderName(String exportHeaderName) {
            this.exportHeaderName = exportHeaderName;
            return this;
        }

        public PatternComponentQualifiersBuilder langRestrictions(List<Locale> langRestrictions) {
            this.langRestrictions = langRestrictions;
            return this;
        }

        public PatternComponentQualifiersBuilder patternNumber(int patternNumber) {
            this.patternNumber = patternNumber;
            return this;
        }

        public PatternComponentQualifiersBuilder checker(CheckerDescription checker) {
            this.checker = checker;
            return this;
        }

        public PatternComponentQualifiersBuilder submissionAuthorizationScope(String submissionAuthorizationScope) {
            this.submissionAuthorizationScope = submissionAuthorizationScope;
            return this;
        }

        public PatternComponentQualifiers build() {
            return new PatternComponentQualifiers(
                    ComponentDescription.ComponentDescriptionType.PatternComponentQualifiers,
                    componentKey,
                    defaultValue,
                    tags,
                    exportHeaderName,
                    langRestrictions,
                    patternNumber,
                    checker,
                    submissionAuthorizationScope
            );
        }
    }

    public static class PatternComponentAdjacentsBuilder {
        private String componentKey;
        private String importHeaderPattern;
        private Set<Tag> tags = Set.of();
        private String exportHeaderName;
        private boolean required = false;
        private ComponentPresenceConstraint mandatory = ComponentPresenceConstraint.OPTIONAL;
        private List<Locale> langRestrictions = List.of();
        private CheckerDescription checker;

        public PatternComponentAdjacentsBuilder componentKey(String componentKey) {
            this.componentKey = componentKey;
            return this;
        }

        public PatternComponentAdjacentsBuilder importHeaderPattern(String importHeaderPattern) {
            this.importHeaderPattern = importHeaderPattern;
            return this;
        }

        public PatternComponentAdjacentsBuilder tags(Set<Tag> tags) {
            this.tags = tags;
            return this;
        }

        public PatternComponentAdjacentsBuilder exportHeaderName(String exportHeaderName) {
            this.exportHeaderName = exportHeaderName;
            return this;
        }

        public PatternComponentAdjacentsBuilder required(boolean required) {
            this.required = required;
            return this;
        }

        public PatternComponentAdjacentsBuilder mandatory(ComponentPresenceConstraint mandatory) {
            this.mandatory = mandatory;
            return this;
        }

        public PatternComponentAdjacentsBuilder langRestrictions(List<Locale> langRestrictions) {
            this.langRestrictions = langRestrictions;
            return this;
        }

        public PatternComponentAdjacentsBuilder checker(CheckerDescription checker) {
            this.checker = checker;
            return this;
        }

        public PatternComponentAdjacents build() {
            return new PatternComponentAdjacents(
                    ComponentDescription.ComponentDescriptionType.PatternComponentAdjacents,
                    componentKey,
                    importHeaderPattern,
                    tags,
                    exportHeaderName,
                    required,
                    mandatory,
                    langRestrictions,
                    checker
            );
        }
    }

    public static class ReferenceScopeComponentBuilder {
        private String componentKey;
        private String authorizationScopeName;
        private String references;
        private String component;
        private String exportHeaderName;
        private String submissionAuthorizationScope;

        public ReferenceScopeComponentBuilder componentKey(String componentKey) {
            this.componentKey = componentKey;
            return this;
        }

        public ReferenceScopeComponentBuilder authorizationScopeName(String authorizationScopeName) {
            this.authorizationScopeName = authorizationScopeName;
            return this;
        }

        public ReferenceScopeComponentBuilder references(String references) {
            this.references = references;
            return this;
        }

        public ReferenceScopeComponentBuilder component(String component) {
            this.component = component;
            return this;
        }

        public ReferenceScopeComponentBuilder exportHeaderName(String exportHeaderName) {
            this.exportHeaderName = exportHeaderName;
            return this;
        }

        public ReferenceScopeComponentBuilder submissionAuthorizationScope(String submissionAuthorizationScope) {
            this.submissionAuthorizationScope = submissionAuthorizationScope;
            return this;
        }

        public ReferenceScopeComponent build() {
            return new ReferenceScopeComponent(
                    ComponentDescription.ComponentDescriptionType.AuthorizationScopeComponent,
                    componentKey,
                    authorizationScopeName,
                    references,
                    component,
                    exportHeaderName,
                    submissionAuthorizationScope
            );
        }
    }
}