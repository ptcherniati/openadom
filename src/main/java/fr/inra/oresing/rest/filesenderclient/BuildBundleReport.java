package fr.inra.oresing.rest.filesenderclient;

import fr.inra.oresing.domain.application.Application;

import java.util.List;
import java.util.Locale;

public record BuildBundleReport(
        Application application,
        List<String> referentielsAvecDonnees,
        List<String> referentielsAvecDonneesExemple,
        List<String> referentielsEnErreur,
        Locale locale) implements MessageInformations {
}