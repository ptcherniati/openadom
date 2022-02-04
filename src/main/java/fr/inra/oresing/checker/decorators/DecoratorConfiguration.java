package fr.inra.oresing.checker.decorators;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableSet;
import org.assertj.core.util.Streams;

import java.util.Set;

public interface DecoratorConfiguration {
    boolean isCodify();
    boolean isRequired();
    String getGroovy();
    String getReferences();

    default Set<String> doGetReferencesAsCollection() {
        return Streams.stream(Splitter.on(",").split(getReferences())).collect(ImmutableSet.toImmutableSet());
    }
}
