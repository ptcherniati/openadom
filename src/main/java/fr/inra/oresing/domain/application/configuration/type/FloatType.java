package fr.inra.oresing.domain.application.configuration.type;



public record FloatType(Float children,
                        boolean required) implements FinalType<Float> {

    public FloatType(final Float children) {
        this(children, false);
    }
}
