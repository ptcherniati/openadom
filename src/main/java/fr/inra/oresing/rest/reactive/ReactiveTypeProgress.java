package fr.inra.oresing.rest.reactive;

import java.time.LocalDateTime;

public record ReactiveTypeProgress<Long>(Long result, LocalDateTime time,
                                         ReactiveType type) implements ReactiveResult<Long> {
    public ReactiveTypeProgress(final Long result) {
        this(result, LocalDateTime.now(), ReactiveType.REACTIVE_PROGRESS);
    }
}