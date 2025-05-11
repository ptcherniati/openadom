package fr.inra.oresing.domain.chart;

import fr.inra.oresing.domain.OreSiEntity;
import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
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