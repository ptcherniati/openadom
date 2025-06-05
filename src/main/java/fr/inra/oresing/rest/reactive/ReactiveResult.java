package fr.inra.oresing.rest.reactive;

import java.time.LocalDateTime;

public sealed interface ReactiveResult<T> permits ReactiveTypeResult, ReactiveTypeInfo, ReactiveTypeError, ReactiveTypeProgress {
    default LocalDateTime time() {
        return LocalDateTime.now();
    }

    T result();

    ReactiveType type();

}