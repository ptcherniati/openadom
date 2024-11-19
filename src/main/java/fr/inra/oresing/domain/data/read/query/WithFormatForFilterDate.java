package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.checker.type.DateType;
import org.apache.commons.collections4.CollectionUtils;

import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

public sealed interface WithFormatForFilterDate extends WithFormat, ComponentFilterSimpleSearch permits ComponentFiltersByDate, ComponentFiltersByDateTime, ComponentFiltersByTime {
    List<String> filters();

    default List<Timestamp> getTimeStamps() {
        if(CollectionUtils.isEmpty(filters())){
            return null;
        }
        return filters().stream()
                .map(f -> DateType.valueToDate(DateTimeFormatter.ofPattern(format()), f))
                .map(Timestamp::valueOf)
                .toList();
    }

    default List<String> getIsoStrings() {
        if(CollectionUtils.isEmpty(filters())){
            return null;
        }
        return getTimeStamps().stream()
                .map(Timestamp::toLocalDateTime)
                .map(DateTimeFormatter.ISO_LOCAL_DATE_TIME::format)
                .toList();
    }
  }
