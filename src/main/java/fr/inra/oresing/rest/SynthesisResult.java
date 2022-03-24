package fr.inra.oresing.rest;

import fr.inra.oresing.model.LocalDateTimeRange;
import fr.inra.oresing.model.chart.OreSiSynthesis;
import lombok.Value;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Value
public class SynthesisResult {
    private UUID application;
    private String datatype;
    private String variable;
    private Map<String, String> requiredauthorizations;
    private String aggregation;
    private List<LocalDateTimeRangeResult> ranges;
    public SynthesisResult(OreSiSynthesis synthesis) {
        this.application = synthesis.getApplication();
        this.datatype = synthesis.getDatatype();
        this.variable = synthesis.getVariable();
        this.requiredauthorizations = synthesis.getRequiredauthorizations();
        this.aggregation = synthesis.getAggregation();
        this.ranges = synthesis.getRanges().stream()
                .map(LocalDateTimeRangeResult::new)
                .collect(Collectors.toList());
    }

    @Value
    class LocalDateTimeRangeResult {
        List<String> range;

        public LocalDateTimeRangeResult(LocalDateTimeRange range) {
            this.range = List.of(range.getRange().lowerEndpoint().toString(), range.getRange().upperEndpoint().toString());
        }
    }
}