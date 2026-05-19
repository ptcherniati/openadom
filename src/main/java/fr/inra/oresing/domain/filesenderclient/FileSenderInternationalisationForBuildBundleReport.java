package fr.inra.oresing.domain.filesenderclient;

import fr.inra.oresing.domain.application.configuration.ApplicationDescription;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.filesenderclient.BuildBundleReport;
import org.apache.commons.lang3.NotImplementedException;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record FileSenderInternationalisationForBuildBundleReport(BuildBundleReport buildBundleReport)
        implements FileSenderInternationalisation {
    private static final Map<Locale, String> SUBJECT_PATTERN = Map.of(
            Locale.FRENCH, "Fichier ZIP pour le dépôt des données de l'application \"%s\"",
            Locale.ENGLISH, "ZIP file for bulk data submission of application \"%s\""
    );

    private static final Map<Locale, String> MESSAGE_PATTERN = Map.of(
            Locale.FRENCH, "Résultat de la création du fichier ZIP pour l'application \"%s\"",
            Locale.ENGLISH, "Result of ZIP file creation for application \"%s\""
    );

    private static final Map<Locale, String> MESSAGE_PATTERN_FOR_MAIL = Map.of(
            Locale.FRENCH, """
                    %s
                    
                    Le lien expirera dans %s jours.
                    
                    Résumé du contenu du ZIP :
                    - Référentiels avec données : %s
                    - Référentiels avec données d'exemple : %s
                    - Référentiels en erreur : %s
                    """,
            Locale.ENGLISH, """
                    %s
                    
                    The link will expire in %s days.
                    
                    Summary of ZIP content:
                    - Referentials with data: %s
                    - Referentials with example data: %s
                    - Referentials with errors: %s
                    """
    );

    public String subjectPattern() {
        return Optional.ofNullable(SUBJECT_PATTERN.get(buildBundleReport.locale()))
                .orElse(SUBJECT_PATTERN.get(getDefaultLanguage()));
    }

    public String messagePattern() {
        return Optional.ofNullable(MESSAGE_PATTERN.get(buildBundleReport.locale()))
                .orElse(MESSAGE_PATTERN.get(getDefaultLanguage()));
    }

    @Override
    public String getInternationnalizedDataName(Locale locale, String s) {
        throw new NotImplementedException("getInternationnalizedDataName");
    }

    @Override
    public String getInternationnalizedApplication(Locale locale) {
        return Optional.ofNullable(buildBundleReport.application().getConfiguration())
                .map(Configuration::i18n)
                .map(Internationalizations::getApplication)
                .map(i18nApplication -> i18nApplication.getTitle().get(Locale.of(locale.getLanguage())))
                .orElse(buildBundleReport.application().getName());
    }

    @Override
    public String getInternationnalizedApplicationDescription(Locale locale) {
        return Optional.ofNullable(buildBundleReport.application().getConfiguration())
                .map(Configuration::i18n)
                .map(Internationalizations::getApplication)
                .map(i18nApplication -> i18nApplication.getDescription().get(Locale.of(locale.getLanguage())))
                .orElse(buildBundleReport.application().getName());
    }

    public Locale getDefaultLanguage() {
        return Optional.ofNullable(buildBundleReport.application().getConfiguration())
                .map(Configuration::applicationDescription)
                .map(ApplicationDescription::defaultLanguage)
                .orElse(Locale.FRENCH);
    }

    @Override
    public String mailMessagefor(String message, int expirationDelay) {
        String messageTemplate = Optional.ofNullable(MESSAGE_PATTERN_FOR_MAIL.get(buildBundleReport.locale()))
                .orElse(MESSAGE_PATTERN_FOR_MAIL.get(getDefaultLanguage()));

        String referentielsAvecDonnees = String.join(", ", buildBundleReport.referentielsAvecDonnees());
        String referentielsAvecDonneesExemple = String.join(", ", buildBundleReport.referentielsAvecDonneesExemple());
        String referentielsEnErreur = String.join(", ", buildBundleReport.referentielsEnErreur());

        return messageTemplate.formatted(
                message,
                expirationDelay,
                referentielsAvecDonnees,
                referentielsAvecDonneesExemple,
                referentielsEnErreur
        );
    }
}