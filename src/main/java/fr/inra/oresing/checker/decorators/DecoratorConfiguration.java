package fr.inra.oresing.checker.decorators;

public interface DecoratorConfiguration {
    boolean isCodify();
    boolean isRequired();
    String getGroovy();
    String getReferences();
}
