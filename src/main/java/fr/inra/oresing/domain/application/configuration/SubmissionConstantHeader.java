package fr.inra.oresing.domain.application.configuration;

public record SubmissionConstantHeader(
        ConstantImportHeader.ConstantImportHeaderType type) implements ConstantImport {
}
