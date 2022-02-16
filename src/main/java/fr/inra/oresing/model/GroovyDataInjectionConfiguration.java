package fr.inra.oresing.model;

import java.util.Set;

public interface GroovyDataInjectionConfiguration {
    Set<String> getReferences();
    Set<String> getDatatypes();
}