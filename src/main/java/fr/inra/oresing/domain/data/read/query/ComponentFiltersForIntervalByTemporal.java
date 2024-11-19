package fr.inra.oresing.domain.data.read.query;

import java.util.List;

public sealed interface ComponentFiltersForIntervalByTemporal extends ComponentFilterForInterval
        permits ComponentFiltersForIntervalByDate, ComponentFiltersForIntervalByTime, ComponentFiltersForIntervalByDateTime {
    List<? extends WithFormatForIntervalDate> intervalsValues();
}
