package fr.inra.oresing.domain.application.configuration;

import java.util.List;

interface WithDepends {
    List<String> depends();

    String nodeName();
}
