package fr.inra.oresing.rest;

import fr.inra.oresing.model.LocalDateTimeRange;
import fr.inra.oresing.model.chart.OreSiSynthesis;
import lombok.Value;

import java.util.*;
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
        this.ranges = Optional.of(synthesis)
                .map(OreSiSynthesis::getRanges)
                .map(ranges->ranges.stream()
                        .map(LocalDateTimeRangeResult::new)
                        .collect(Collectors.toList())
                )
                .orElseGet(LinkedList::new);
    }

    @Value
    class LocalDateTimeRangeResult {
        List<String> range;

        public LocalDateTimeRangeResult(LocalDateTimeRange range) {
            this.range = List.of(
                    range.getRange().hasLowerBound()?range.getRange().lowerEndpoint().toString():"",
                    range.getRange().hasUpperBound()?range.getRange().upperEndpoint().toString():""
                    );
        }
    }
}