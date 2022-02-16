package fr.inra.oresing.checker;

import fr.inra.oresing.model.IGroovyDataInjectionConfiguration;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedHashSet;
import java.util.Set;

@Getter
@Setter
@ToString
public class GroovyConfiguration implements IGroovyDataInjectionConfiguration {
    String expression;
    Set<String> references = new LinkedHashSet<>();
    Set<String> datatypes = new LinkedHashSet<>();
}