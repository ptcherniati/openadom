package fr.inra.oresing.domain.data.read.query;

public record IntervalValuesDefault(
        String from,
        String to) implements WithIntervalValues {
}
