package fr.inra.oresing.rest.filesenderclient;

import fr.inra.oresing.domain.application.Application;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public record BuildBundleReport(
        Application application,
        List<String> referentielsAvecDonnees,
        Map<String, Set<String>> fichiersGeneres,
        List<String> referentielsAvecDonneesExemple,
        List<String> referentielsEnErreur,
        Locale locale) implements MessageInformations {
}