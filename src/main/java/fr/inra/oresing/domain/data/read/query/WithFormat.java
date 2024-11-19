package fr.inra.oresing.domain.data.read.query;

public sealed interface WithFormat permits WithFormatForFilterDate, WithFormatForIntervalDate {
    String format();
}
