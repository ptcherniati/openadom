package fr.inra.oresing.rest.filesenderclient;

import fr.inra.oresing.domain.application.Application;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public record BuildBundleReport(
    Application applicationName,
    List<String> referentielsAvecDonnees,
    Map<String, List<String>> fichiersGeneres,
    List<String> referentielsAvecDonneesExemple,
    List<String> referentielsEnErreur,
    Locale locale) implements MessageInformations {
}
