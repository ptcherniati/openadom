package fr.inra.oresing.rest.model.data.query;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class IntervalValues {
    public String from;
    public String to;

    public IntervalValues() {
        super();
    }

    public IntervalValues(String from, String to) {
        this.from = from;
        this.to = to;
    }
}