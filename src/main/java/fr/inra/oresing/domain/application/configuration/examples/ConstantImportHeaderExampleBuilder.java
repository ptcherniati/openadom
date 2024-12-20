package fr.inra.oresing.domain.application.configuration.examples;

import fr.inra.oresing.domain.application.configuration.ConfigurationSchemaNode;
import fr.inra.oresing.domain.application.configuration.type.*;

import java.util.LinkedHashMap;
import java.util.Map;

class ConstantImportHeaderExampleBuilder {
    protected static ConstantImportHeaderType buildExportHeader(final I18nType exportHeader) {
        return new ConstantImportHeaderType(Map.of(
                ConfigurationSchemaNode.OA_TITLE,
                exportHeader
        ));
    }

    protected static ConstantImportHeaderType buildConstante(final int rowNumber,
                                                             final int columnNumber,
                                                             final String columnName,
                                                             CollectionType.ArrayType<StringType> langRestriction) {
        return new ConstantImportHeaderType(new LinkedHashMap<>() {{
            put(ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_ROW_NUMBER, new IntegerType(rowNumber));
            if (columnName == null) {
                put(ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_COLUMN_NUMBER, new IntegerType(columnNumber));
            } else {
                put(ConfigurationSchemaNode.OA_CONSTANT_IMPORT_HEADER_COLUMN_NAME, new StringType(columnName));
            }
            if (langRestriction != null) {
                put(ConfigurationSchemaNode.OA_LANG_RESTRICTIONS, langRestriction);
            }
        }});
    }
}