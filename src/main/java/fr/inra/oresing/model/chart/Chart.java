package fr.inra.oresing.model.chart;

import fr.inra.oresing.model.VariableComponentKey;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@ToString(callSuper = true)
public class Chart {
    String value;
    VariableComponentKey aggregation;
    String unit;
    String standardDeviation;
    String title;
}