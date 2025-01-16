package fr.inra.oresing.domain;

import com.google.common.base.Strings;
import fr.inra.oresing.domain.application.configuration.Ltree;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
public class BinaryFileDataset {
    public static BinaryFileDataset EMPTY_INSTANCE(){
        return new BinaryFileDataset();
    }
    private String datatype;
    private Map<String, List<Ltree>> requiredAuthorizations = new HashMap<>();
    private String from;
    private String to;
    private String comment;

    @Override
    public String toString() {
        final String authorizationsString =requiredAuthorizations.entrySet().stream()
                .map(ra -> String.format("%s : %s", ra.getKey(), ra.getValue().getFirst().getSql()))
                .collect(Collectors.joining(",", "[", "]"));
        return String.format("%s -> [%s, %s]",
                authorizationsString, Strings.isNullOrEmpty(from) ?"": LocalDateTimeRange.DATE_FORMATTER_DDMMYYYY.format(LocalDateTimeRange.DATE_TIME_FORMATTER.parse(from)),
                Strings.isNullOrEmpty(to)?"": LocalDateTimeRange.DATE_FORMATTER_DDMMYYYY.format(LocalDateTimeRange.DATE_TIME_FORMATTER.parse(to))
        );
    }
}