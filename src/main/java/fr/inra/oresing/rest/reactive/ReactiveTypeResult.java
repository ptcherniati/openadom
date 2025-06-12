package fr.inra.oresing.rest.reactive;

import java.time.LocalDateTime;

public record ReactiveTypeResult<T>(T result, LocalDateTime time, ReactiveType type) implements ReactiveResult<T> {

    public ReactiveTypeResult(final T result) {
        this(result, LocalDateTime.now(), ReactiveType.REACTIVE_RESULT);
    }
}