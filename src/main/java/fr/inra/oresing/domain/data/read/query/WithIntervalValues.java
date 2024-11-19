package fr.inra.oresing.domain.data.read.query;

public sealed interface WithIntervalValues permits IntervalValuesDefault, IntervalValuesNumeric, WithFormatForIntervalDate {
    String from();

    String to();
}
