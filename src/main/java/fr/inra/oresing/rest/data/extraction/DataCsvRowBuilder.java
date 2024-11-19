package fr.inra.oresing.rest.data.extraction;

import com.opencsv.CSVWriter;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.DynamicComponent;
import fr.inra.oresing.domain.application.configuration.PatternComponent;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.checker.type.FieldType;
import fr.inra.oresing.domain.checker.type.ListType;
import fr.inra.oresing.domain.checker.type.MapType;
import fr.inra.oresing.domain.data.DataColumn;
import fr.inra.oresing.domain.data.DataColumnValue;
import fr.inra.oresing.domain.data.DataValue;
import fr.inra.oresing.domain.data.deposit.context.column.Column;
import fr.inra.oresing.domain.data.read.query.*;
import fr.inra.oresing.persistence.DataRepository;
import fr.inra.oresing.persistence.DataRow;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record DataCsvRowBuilder(String language, DataRepositoryWithBuffer dataRepositoryWithBuffer, StandardDataDescription dataDescription) {
    public List<String> getCsvRow(Map<String, FieldType> dataRowValues,
                                         List<ComponentOrderByForExport> columns) {
        try {
            Function<ComponentOrderByForExport, Stream<String>> toValue = componentOrderBy -> componentOrderBy.toValue(language(), dataRepositoryWithBuffer(), dataRowValues, dataDescription());
            List<String> rowAsRecord = columns
                    .stream()
                    .flatMap(toValue)
                    .toList();

            return rowAsRecord;
        } catch (Exception e) {
            throw e;
        }
    }
}
