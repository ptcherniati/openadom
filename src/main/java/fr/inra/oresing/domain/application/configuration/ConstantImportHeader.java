package fr.inra.oresing.domain.application.configuration;

public sealed interface ConstantImportHeader extends ConstantImport permits FileColumnConstantHeader, ColumnConstantHeader {
    ConstantImportHeaderType type();

    int rowNumber();

    enum ConstantImportHeaderType {
        MissingConstantImportHeader,
        SubmissionComponent,
        FileConstantHeader,
        ColumnConstantHeaderByColumnNumber,
        ColumnConstantHeaderByHeaderName
    }
}
