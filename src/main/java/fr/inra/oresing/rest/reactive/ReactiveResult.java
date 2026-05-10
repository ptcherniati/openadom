package fr.inra.oresing.rest.reactive;

import fr.inra.oresing.domain.event.ImportProgressEvent;

import java.time.LocalDateTime;

/**
 * Interface scellée de l'infrastructure réactive REST.
 * Implémente {@link ImportProgressEvent} afin que le domaine puisse
 * référencer l'abstraction sans connaître les classes REST.
 */
public sealed interface ReactiveResult<T> extends ImportProgressEvent permits ReactiveTypeResult, ReactiveTypeInfo, ReactiveTypeError, ReactiveTypeProgress {
    default LocalDateTime time() {
        return LocalDateTime.now();
    }

    T result();

    ReactiveType type();

}