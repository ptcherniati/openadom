package fr.inra.oresing.domain.exceptions.application;

import lombok.Getter;

@Getter
public class NoSuchApplicationException extends RuntimeException {

    public static final String APPLICATION_INCONNUE_PATTERN = "application inconnue '%s'";
    private final String nameOrId;

    public NoSuchApplicationException(final String nameOrId) {
        super(APPLICATION_INCONNUE_PATTERN.formatted(nameOrId));
        this.nameOrId = nameOrId;
    }

}