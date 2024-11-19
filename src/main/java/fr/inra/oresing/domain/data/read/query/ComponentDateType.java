package fr.inra.oresing.domain.data.read.query;

public record ComponentDateType(String format,
                                DownloadDatasetQueryAdvancedSearch.FieldType fieldType) implements ComponentType {
}
