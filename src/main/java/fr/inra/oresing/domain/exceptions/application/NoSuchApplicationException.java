package fr.inra.oresing.domain.exceptions.application;

import lombok.Getter;

@Getter
public class NoSuchApplicationException extends RuntimeException {

    private final String nameOrId;

    public NoSuchApplicationException(final String nameOrId) {
        super("application inconnue '" + nameOrId + "'");
        this.nameOrId = nameOrId;
    }

}