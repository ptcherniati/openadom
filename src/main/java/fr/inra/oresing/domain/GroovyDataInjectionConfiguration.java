package fr.inra.oresing.domain;

import java.util.Set;

public interface GroovyDataInjectionConfiguration {
    Set<String> getReferences();
    Set<String> getData();
}