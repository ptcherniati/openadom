package fr.inra.oresing.domain.application.configuration.type;



public record BooleanType(Boolean children,
                          boolean required) implements FinalType<Boolean> {
    public static BooleanType EMPTY_INSTANCE() {
        return new BooleanType(false);
    }

    public BooleanType(final Boolean children) {
        this(children, false);
    }
}
