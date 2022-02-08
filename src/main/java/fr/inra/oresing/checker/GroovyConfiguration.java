package fr.inra.oresing.checker;

import fr.inra.oresing.model.IGroovyDataInjectionConfiguration;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.LinkedList;
import java.util.List;

@Getter
@Setter
@ToString
public class GroovyConfiguration implements IGroovyDataInjectionConfiguration {
    String expression;
    List<String> references = new LinkedList<>();
    List<String> datatypes = new LinkedList<>();
}