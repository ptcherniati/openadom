package fr.inra.oresing.domain.application.configuration;

import java.util.List;
import java.util.Locale;

public class ConstantImportBuilder {

    public static SubmissionConstantHeaderBuilder submissionConstantHeader() {
        return new SubmissionConstantHeaderBuilder();
    }

    public static FileColumnConstantHeaderBuilder fileColumnConstantHeader() {
        return new FileColumnConstantHeaderBuilder();
    }

    public static ColumnConstantHeaderByColumnNumberBuilder columnConstantHeaderByColumnNumber() {
        return new ColumnConstantHeaderByColumnNumberBuilder();
    }

    public static ColumnConstantHeaderByHeaderNameBuilder columnConstantHeaderByHeaderName() {
        return new ColumnConstantHeaderByHeaderNameBuilder();
    }

    public static class SubmissionConstantHeaderBuilder {
        public SubmissionConstantHeader build() {
            return new SubmissionConstantHeader(ConstantImportHeader.ConstantImportHeaderType.SubmissionComponent);
        }
    }

    public static class FileColumnConstantHeaderBuilder {
        private int rowNumber;
        private int columnNumber;

        public FileColumnConstantHeaderBuilder rowNumber(int rowNumber) {
            this.rowNumber = rowNumber;
            return this;
        }

        public FileColumnConstantHeaderBuilder columnNumber(int columnNumber) {
            this.columnNumber = columnNumber;
            return this;
        }

        public FileColumnConstantHeader build() {
            return new FileColumnConstantHeader(ConstantImportHeader.ConstantImportHeaderType.FileConstantHeader, rowNumber, columnNumber);
        }
    }

    public static class ColumnConstantHeaderByColumnNumberBuilder {
        private int rowNumber;
        private int columnNumber;

        public ColumnConstantHeaderByColumnNumberBuilder rowNumber(int rowNumber) {
            this.rowNumber = rowNumber;
            return this;
        }

        public ColumnConstantHeaderByColumnNumberBuilder columnNumber(int columnNumber) {
            this.columnNumber = columnNumber;
            return this;
        }

        public ColumnConstantHeaderByColumnNumber build() {
            return new ColumnConstantHeaderByColumnNumber(ConstantImportHeader.ConstantImportHeaderType.ColumnConstantHeaderByColumnNumber, rowNumber, columnNumber);
        }
    }

    public static class ColumnConstantHeaderByHeaderNameBuilder {
        private int rowNumber;
        private String headerName;
        private List<Locale> langRestrictions = List.of();

        public ColumnConstantHeaderByHeaderNameBuilder rowNumber(int rowNumber) {
            this.rowNumber = rowNumber;
            return this;
        }

        public ColumnConstantHeaderByHeaderNameBuilder headerName(String headerName) {
            this.headerName = headerName;
            return this;
        }

        public ColumnConstantHeaderByHeaderNameBuilder langRestrictions(List<Locale> langRestrictions) {
            this.langRestrictions = langRestrictions;
            return this;
        }

        public ColumnConstantHeaderByHeaderName build() {
            return new ColumnConstantHeaderByHeaderName(ConstantImportHeader.ConstantImportHeaderType.ColumnConstantHeaderByHeaderName, rowNumber, headerName, langRestrictions);
        }
    }
}