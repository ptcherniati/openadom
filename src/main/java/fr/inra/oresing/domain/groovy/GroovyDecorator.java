package fr.inra.oresing.domain.groovy;

import java.util.Map;

public interface GroovyDecorator {
    String getHierarchicalKey();

    String getNaturalKey();

    Map<String, Object> getRefValues();
}
