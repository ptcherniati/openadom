package fr.inra.oresing.domain.application.configuration;

public record FileColumnConstantHeader(ConstantImportHeaderType type,
                                       int rowNumber,
                                       int columnNumber) implements ConstantImportHeader {
}
