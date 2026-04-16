package fr.inra.oresing.domain.application.configuration;

import java.util.List;

public class SubmissionBuilder {

    public static SubmissionReferenceScopeBuilder submissionReferenceScope() {
        return new SubmissionReferenceScopeBuilder();
    }

    public static TimeScopeBuilder timeScope() {
        return new TimeScopeBuilder();
    }

    public static SubmissionFileNameParsingBuilder submissionFileNameParsing() {
        return new SubmissionFileNameParsingBuilder();
    }

    public static SubmissionScopeBuilder submissionScope() {
        return new SubmissionScopeBuilder();
    }

    public static class SubmissionReferenceScopeBuilder {
        private String reference;
        private String component;

        public SubmissionReferenceScopeBuilder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public SubmissionReferenceScopeBuilder component(String component) {
            this.component = component;
            return this;
        }

        public Submission.SubmissionScope.SubmissionReferenceScope build() {
            return new Submission.SubmissionScope.SubmissionReferenceScope(reference, component);
        }
    }

    public static class TimeScopeBuilder {
        private String component;

        public TimeScopeBuilder component(String component) {
            this.component = component;
            return this;
        }

        public Submission.SubmissionScope.TimeScope build() {
            return new Submission.SubmissionScope.TimeScope(component);
        }
    }

    public static class SubmissionFileNameParsingBuilder {
        private String pattern;
        private List<String> authorizationScopes = List.of();
        private Integer startDate;
        private Integer endDate;

        public SubmissionFileNameParsingBuilder pattern(String pattern) {
            this.pattern = pattern;
            return this;
        }

        public SubmissionFileNameParsingBuilder authorizationScopes(List<String> authorizationScopes) {
            this.authorizationScopes = authorizationScopes;
            return this;
        }

        public SubmissionFileNameParsingBuilder startDate(Integer startDate) {
            this.startDate = startDate;
            return this;
        }

        public SubmissionFileNameParsingBuilder endDate(Integer endDate) {
            this.endDate = endDate;
            return this;
        }

        public Submission.SubmissionFileNameParsing build() {
            return new Submission.SubmissionFileNameParsing(pattern, authorizationScopes, startDate, endDate);
        }
    }

    public static class SubmissionScopeBuilder {
        private List<Submission.SubmissionScope.SubmissionReferenceScope> referenceScopes = List.of();
        private Submission.SubmissionScope.TimeScope timescope;

        public SubmissionScopeBuilder referenceScopes(List<Submission.SubmissionScope.SubmissionReferenceScope> referenceScopes) {
            this.referenceScopes = referenceScopes;
            return this;
        }

        public SubmissionScopeBuilder timescope(Submission.SubmissionScope.TimeScope timescope) {
            this.timescope = timescope;
            return this;
        }

        public Submission.SubmissionScope build() {
            return new Submission.SubmissionScope(referenceScopes, timescope);
        }
    }

    public static class SubmissionMainBuilder {
        private SubmissionType strategy;
        private Submission.SubmissionFileNameParsing fileNameParsing;
        private Submission.SubmissionScope submissionScope;

        public SubmissionMainBuilder strategy(SubmissionType strategy) {
            this.strategy = strategy;
            return this;
        }

        public SubmissionMainBuilder fileNameParsing(Submission.SubmissionFileNameParsing fileNameParsing) {
            this.fileNameParsing = fileNameParsing;
            return this;
        }

        public SubmissionMainBuilder submissionScope(Submission.SubmissionScope submissionScope) {
            this.submissionScope = submissionScope;
            return this;
        }

        public Submission build() {
            return new Submission(strategy, fileNameParsing, submissionScope);
        }
    }
}