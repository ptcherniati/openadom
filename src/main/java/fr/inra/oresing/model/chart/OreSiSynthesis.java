package fr.inra.oresing.model.chart;

import fr.inra.oresing.model.LocalDateTimeRange;
import fr.inra.oresing.model.OreSiEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Getter
@Setter
@ToString(callSuper = true)
public class OreSiSynthesis extends OreSiEntity {
    private UUID application;
    private String datatype;
    private String variable;
    private Map<String, String> requiredAuthorizations;
    private String Aggregation;
    private List<LocalDateTimeRange> ranges;
}