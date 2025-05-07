package fr.inra.oresing.domain.application.configuration;

import java.util.Set;

interface WithDepends {
    Set<String> depends();
    String nodeName();
}