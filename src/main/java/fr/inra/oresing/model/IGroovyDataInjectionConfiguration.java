package fr.inra.oresing.model;

import java.util.Set;

public interface IGroovyDataInjectionConfiguration {
    Set<String> getReferences();
    Set<String> getDatatypes();
}