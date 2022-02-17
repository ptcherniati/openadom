package fr.inra.oresing.model;

import com.google.common.collect.ImmutableMap;

public interface SomethingThatCanProvideEvaluationContext {
    ImmutableMap<String, Object> getEvaluationContext();
}
