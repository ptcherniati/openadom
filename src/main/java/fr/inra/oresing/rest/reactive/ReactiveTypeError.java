package fr.inra.oresing.rest.reactive;

import java.time.LocalDateTime;
import java.util.Optional;

public record ReactiveTypeError<T>(String errorType, T result, LocalDateTime time,
                                   ReactiveType type) implements ReactiveResult<T> {

    public ReactiveTypeError(final T result) {
        this(
                Optional.ofNullable(result)
                        .map(Object::getClass)
                        .map(Class::getSimpleName)
                        .orElse("null"),
                result, LocalDateTime.now(), ReactiveType.REACTIVE_ERROR);
    }

    @Override
    public ReactiveType type() {
        return ReactiveType.REACTIVE_ERROR;
    }
}