package fr.inra.oresing;

import org.apache.commons.lang3.builder.ToStringBuilder;

public enum OreSiAnonymousRequestClient implements OreSiRequestClient {

    ANONYMOUS;

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("role", role())
                .toString();
    }
}