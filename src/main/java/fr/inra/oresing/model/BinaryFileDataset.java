package fr.inra.oresing.model;

import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.rest.OreSiService;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Getter
@Setter
@ToString(callSuper = true)
public class BinaryFileDataset {
    public final static BinaryFileDataset EMPTY_INSTANCE(){
        return new BinaryFileDataset();
    }
    private String datatype;
    private Map<String, Ltree> requiredauthorizations = new HashMap<>();
    private String from;
    private String to;
    private String comment;

    @Override
    public String toString() {
        String authorizationsString = requiredauthorizations.entrySet().stream()
                .map(ra -> String.format("%s : %s", ra.getKey(), ra.getValue().getSql()))
                .collect(Collectors.joining(",", "[", "]"));
        return String.format("%s -> [%s, %s]",
                authorizationsString, OreSiService.DATE_FORMATTER_DDMMYYYY.format(OreSiService.DATE_TIME_FORMATTER.parse(from)),
                authorizationsString, OreSiService.DATE_FORMATTER_DDMMYYYY.format(OreSiService.DATE_TIME_FORMATTER.parse(to))
        );
    }
}