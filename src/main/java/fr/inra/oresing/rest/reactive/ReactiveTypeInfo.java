package fr.inra.oresing.rest.reactive;

import java.time.LocalDateTime;
import java.util.Map;

public record ReactiveTypeInfo<String>(String result, Map<String, String> params, LocalDateTime time,
                                       ReactiveType type) implements ReactiveResult<String> {
    public ReactiveTypeInfo(final String result, final Map<String, String> params) {
        this(result, params, LocalDateTime.now(), ReactiveType.REACTIVE_INFO);
    }

    public ReactiveTypeInfo(final String result) {
        this(result, Map.of(), LocalDateTime.now(), ReactiveType.REACTIVE_INFO);
    }
}