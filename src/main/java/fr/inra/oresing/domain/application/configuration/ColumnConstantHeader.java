package fr.inra.oresing.domain.application.configuration;

public sealed interface ColumnConstantHeader extends ConstantImportHeader permits ColumnConstantHeaderByColumnNumber, ColumnConstantHeaderByHeaderName {
}
