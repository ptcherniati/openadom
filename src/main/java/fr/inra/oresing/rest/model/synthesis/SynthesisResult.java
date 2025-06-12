package fr.inra.oresing.rest.model.synthesis;

import fr.inra.oresing.domain.application.configuration.date.LocalDateTimeRange;
import fr.inra.oresing.domain.chart.OreSiSynthesis;
import lombok.Value;

import java.util.*;

@Value
public class SynthesisResult {
    UUID application;
    String datatype;
    String variable;
    Map<String, String> requiredAuthorizations;
    String aggregation;
    List<LocalDateTimeRangeResult> ranges;

    public SynthesisResult(final OreSiSynthesis synthesis) {
        super();
        application = synthesis.getApplication();
        datatype = synthesis.getDatatype();
        variable = synthesis.getVariable();
        requiredAuthorizations = synthesis.getRequiredAuthorizations();
        aggregation = synthesis.getAggregation();
        ranges = Optional.of(synthesis)
                .map(OreSiSynthesis::getRanges)
                .map(ranges -> ranges.stream()
                        .map(LocalDateTimeRangeResult::new)
                        .toList()
                )
                .orElseGet(LinkedList::new);
    }

    @Value
    static class LocalDateTimeRangeResult {
        List<String> range;

        public LocalDateTimeRangeResult(final LocalDateTimeRange range) {
            super();
            this.range = List.of(
                    range.getRange().hasLowerBound() ? range.getRange().lowerEndpoint().toString() : "",
                    range.getRange().hasUpperBound() ? range.getRange().upperEndpoint().toString() : ""
            );
        }
    }
}