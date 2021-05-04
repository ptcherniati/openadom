package fr.inra.oresing;

public enum ValidationLevel {
    SUCCESS, WARN, ERROR;

    public boolean isSuccess() {
        return this == SUCCESS;
    }
}
