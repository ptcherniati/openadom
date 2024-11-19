package fr.inra.oresing.domain.application.configuration.type;


public record StringType(String children,
                         boolean required) implements FinalType<String> {
    public static final StringType FR = new StringType("fr", false);
    public static final StringType EN = new StringType("en", false);

    public static StringType  EMPTY_INSTANCE() {
        return new StringType("");
    }

    public StringType(final String children) {
        this(children, false);
    }
}
