package fr.inra.oresing.domain.application.configuration;

public class DependsBuilder {

    public static DependsParentBuilder dependsParent() {
        return new DependsParentBuilder();
    }

    public static DependsRecursiveBuilder dependsRecursive() {
        return new DependsRecursiveBuilder();
    }

    public static DependsReferencesBuilder dependsReferences() {
        return new DependsReferencesBuilder();
    }

    public static class DependsParentBuilder {
        private String references;
        private String component;

        public DependsParentBuilder references(String references) {
            this.references = references;
            return this;
        }

        public DependsParentBuilder component(String component) {
            this.component = component;
            return this;
        }

        public DependsParent build() {
            return new DependsParent(Depends.DependsType.DependsParent, references, component);
        }
    }

    public static class DependsRecursiveBuilder {
        private String references;
        private String component;

        public DependsRecursiveBuilder references(String references) {
            this.references = references;
            return this;
        }

        public DependsRecursiveBuilder component(String component) {
            this.component = component;
            return this;
        }

        public DependsRecursive build() {
            return new DependsRecursive(Depends.DependsType.DependsRecursive, references, component);
        }
    }

    public static class DependsReferencesBuilder {
        private String references;
        private String component;

        public DependsReferencesBuilder references(String references) {
            this.references = references;
            return this;
        }

        public DependsReferencesBuilder component(String component) {
            this.component = component;
            return this;
        }

        public DependsReferences build() {
            return new DependsReferences(Depends.DependsType.DependsReferences, references, component);
        }
    }
}