package fr.inra.oresing.domain.application.configuration;

public record ColumnConstantHeaderByColumnNumber(ConstantImportHeaderType type, int rowNumber,
                                                 int columnNumber) implements ColumnConstantHeader {
}
