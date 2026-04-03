package fr.inra.oresing.domain.application.configuration.internationalization;

import java.util.Locale;
import java.util.Map;

public class InternationalizationsBuilder {

    public static InternationalizationsMainBuilder internationalizations() {
        return new InternationalizationsMainBuilder();
    }

    public static InternationalizationTitleBuilder internationalizationTitle() {
        return new InternationalizationTitleBuilder();
    }

    public static InternationalizationDataBuilder internationalizationData() {
        return new InternationalizationDataBuilder();
    }

    public static InternationalizationComponentBuilder internationalizationComponent() {
        return new InternationalizationComponentBuilder();
    }

    public static InternationalizationSubmissionComponentBuilder internationalizationSubmissionComponent() {
        return new InternationalizationSubmissionComponentBuilder();
    }

    public static InternationalizationRightrequestBuilder internationalizationRightrequest() {
        return new InternationalizationRightrequestBuilder();
    }

    public static InternationalizationAdditionalFileBuilder internationalizationAdditionalFile() {
        return new InternationalizationAdditionalFileBuilder();
    }

    public static class InternationalizationsMainBuilder {
        private Map<String, Map<Locale, String>> tags = Map.of();
        private InternationalizationTitle application;
        private Map<String, InternationalizationData> data = Map.of();
        private InternationalizationRightrequest rightsrequest = new InternationalizationRightrequest();
        private Map<String, InternationalizationAdditionalFile> additionalFiles = Map.of();

        public InternationalizationsMainBuilder tags(Map<String, Map<Locale, String>> tags) {
            this.tags = tags;
            return this;
        }

        public InternationalizationsMainBuilder application(InternationalizationTitle application) {
            this.application = application;
            return this;
        }

        public InternationalizationsMainBuilder data(Map<String, InternationalizationData> data) {
            this.data = data;
            return this;
        }

        public InternationalizationsMainBuilder rightsrequest(InternationalizationRightrequest rightsrequest) {
            this.rightsrequest = rightsrequest;
            return this;
        }

        public InternationalizationsMainBuilder additionalFiles(Map<String, InternationalizationAdditionalFile> additionalFiles) {
            this.additionalFiles = additionalFiles;
            return this;
        }

        public Internationalizations build() {
            Internationalizations internationalizations = new Internationalizations();
            internationalizations.setTags(tags);
            internationalizations.setApplication(application);
            internationalizations.setData(data);
            internationalizations.setRightsrequest(rightsrequest);
            internationalizations.setAdditionalFiles(additionalFiles);
            return internationalizations;
        }
    }

    public static class InternationalizationTitleBuilder {
        private Map<Locale, String> title = Map.of();
        private Map<Locale, String> description = Map.of();

        public InternationalizationTitleBuilder title(Map<Locale, String> title) {
            this.title = title;
            return this;
        }

        public InternationalizationTitleBuilder description(Map<Locale, String> description) {
            this.description = description;
            return this;
        }

        public InternationalizationTitle build() {
            InternationalizationTitle internationalizationTitle = new InternationalizationTitle();
            internationalizationTitle.setTitle(title);
            internationalizationTitle.setDescription(description);
            return internationalizationTitle;
        }
    }

    public static class InternationalizationDataBuilder {
        private Map<String, Map<Locale, String>> validations = Map.of();
        private Map<String, Map<String, Map<Locale, String>>> exceptions = Map.of();
        private Map<String, InternationalizationComponent> components = Map.of();
        private InternationalizationSubmissionComponent submissions = new InternationalizationSubmissionComponent();
        private InternationalizationTitle i18nDisplayPattern;
        private InternationalizationTitle i18n;

        public InternationalizationDataBuilder validations(Map<String, Map<Locale, String>> validations) {
            this.validations = validations;
            return this;
        }

        public InternationalizationDataBuilder exceptions(Map<String, Map<String, Map<Locale, String>>> exceptions) {
            this.exceptions = exceptions;
            return this;
        }

        public InternationalizationDataBuilder components(Map<String, InternationalizationComponent> components) {
            this.components = components;
            return this;
        }

        public InternationalizationDataBuilder submissions(InternationalizationSubmissionComponent submissions) {
            this.submissions = submissions;
            return this;
        }

        public InternationalizationDataBuilder i18nDisplayPattern(InternationalizationTitle i18nDisplayPattern) {
            this.i18nDisplayPattern = i18nDisplayPattern;
            return this;
        }

        public InternationalizationDataBuilder i18n(InternationalizationTitle i18n) {
            this.i18n = i18n;
            return this;
        }

        public InternationalizationData build() {
            InternationalizationData internationalizationData = new InternationalizationData();
            internationalizationData.setValidations(validations);
            internationalizationData.setExceptions(exceptions);
            internationalizationData.setComponents(components);
            internationalizationData.setSubmissions(submissions);
            internationalizationData.setI18nDisplayPattern(i18nDisplayPattern);
            internationalizationData.setI18n(i18n);
            return internationalizationData;
        }
    }

    public static class InternationalizationComponentBuilder {
        private InternationalizationTitle exportHeader;

        public InternationalizationComponentBuilder exportHeader(InternationalizationTitle exportHeader) {
            this.exportHeader = exportHeader;
            return this;
        }

        public InternationalizationComponent build() {
            InternationalizationComponent internationalizationComponent = new InternationalizationComponent();
            internationalizationComponent.setExportHeader(exportHeader);
            return internationalizationComponent;
        }
    }

    public static class InternationalizationSubmissionComponentBuilder {
        private Map<String, InternationalizationTitle> referenceScopes = Map.of();

        public InternationalizationSubmissionComponentBuilder referenceScopes(Map<String, InternationalizationTitle> referenceScopes) {
            this.referenceScopes = referenceScopes;
            return this;
        }

        public InternationalizationSubmissionComponent build() {
            InternationalizationSubmissionComponent internationalizationSubmissionComponent = new InternationalizationSubmissionComponent();
            internationalizationSubmissionComponent.setReferenceScopes(referenceScopes);
            return internationalizationSubmissionComponent;
        }
    }

    public static class InternationalizationRightrequestBuilder {
        private Map<String, InternationalizationTitle> fields = Map.of();
        private InternationalizationTitle i18n;

        public InternationalizationRightrequestBuilder fields(Map<String, InternationalizationTitle> fields) {
            this.fields = fields;
            return this;
        }

        public InternationalizationRightrequestBuilder i18n(InternationalizationTitle i18n) {
            this.i18n = i18n;
            return this;
        }

        public InternationalizationRightrequest build() {
            InternationalizationRightrequest internationalizationRightrequest = new InternationalizationRightrequest();
            internationalizationRightrequest.setFields(fields);
            internationalizationRightrequest.setI18n(i18n);
            return internationalizationRightrequest;
        }
    }

    public static class InternationalizationAdditionalFileBuilder {
        private InternationalizationTitle i18n;
        private Map<String, InternationalizationTitle> fields = Map.of();

        public InternationalizationAdditionalFileBuilder i18n(InternationalizationTitle i18n) {
            this.i18n = i18n;
            return this;
        }

        public InternationalizationAdditionalFileBuilder fields(Map<String, InternationalizationTitle> fields) {
            this.fields = fields;
            return this;
        }

        public InternationalizationAdditionalFile build() {
            InternationalizationAdditionalFile internationalizationAdditionalFile = new InternationalizationAdditionalFile();
            internationalizationAdditionalFile.setI18n(i18n);
            internationalizationAdditionalFile.setFields(fields);
            return internationalizationAdditionalFile;
        }
    }
}