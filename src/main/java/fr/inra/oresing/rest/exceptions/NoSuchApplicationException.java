package fr.inra.oresing.rest.exceptions;

public class NoSuchApplicationException extends RuntimeException {

    private final String nameOrId;

    public NoSuchApplicationException(String nameOrId) {
        super("application inconnue '" + nameOrId + "'");
        this.nameOrId = nameOrId;
    }

    public String getNameOrId() {
        return nameOrId;
    }
}