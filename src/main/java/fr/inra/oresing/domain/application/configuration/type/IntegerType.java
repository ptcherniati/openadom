package fr.inra.oresing.domain.application.configuration.type;



public record IntegerType(Integer children,
                          boolean required) implements FinalType<Integer> {
    public static IntegerType  EMPTY_INSTANCE(){
        return new IntegerType(0);
    }

    public IntegerType(final Integer children) {
        this(children, false);
    }
}
