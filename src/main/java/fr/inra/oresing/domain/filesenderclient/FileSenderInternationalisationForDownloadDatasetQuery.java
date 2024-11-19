package fr.inra.oresing.domain.filesenderclient;

import fr.inra.oresing.domain.application.configuration.ApplicationDescription;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.internationalization.Internationalizations;
import fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record FileSenderInternationalisationForDownloadDatasetQuery(DownloadDatasetQuery downloadDatasetQuery)
implements FileSenderInternationalisation{
    private static final Map<Locale, String> SUBJECT_PATTERN = Map.of(
            Locale.FRENCH, "Chargement des données de l'application \"%s\"",
            Locale.ENGLISH, "Loading data of application \"%s\""
    );
    private static final Map<Locale, String> MESSAGE_PATTERN = Map.of(
            Locale.FRENCH, "Resultat de l'extraction des données de \"%s\"",
            Locale.ENGLISH, "Result data of data type \"%s\""
    );
    private static final Map<Locale, String> MESSAGE_PATTERN_FOR_MAIL = Map.of(
            Locale.FRENCH, """
                    %s
                    
                    Vous pourrez télécharger le résultat de votre requête en cliquant sur le lien ci dessus.
                    
                    Le lien expirera dans  %s jours
                    """,
            Locale.ENGLISH, """
                    %s
                    
                    You can download the result of your query by clicking on the upper link.
                    
                    The link will expire in %s days"""
    );
    public String subjectPattern(){
        return Optional.ofNullable(SUBJECT_PATTERN.get(downloadDatasetQuery.outPut().locale()))
                .orElse(SUBJECT_PATTERN.get(getDefaultLanguage()));
    }
    public String messagePattern(){
        return Optional.ofNullable(MESSAGE_PATTERN.get(downloadDatasetQuery.outPut().locale()))
                .orElse(MESSAGE_PATTERN.get(getDefaultLanguage()));
    }
    public String getInternationnalizedApplication(Locale locale) {
        return Optional.ofNullable(downloadDatasetQuery().application().getConfiguration())
                .map(Configuration::i18n)
                .map(Internationalizations::getApplication)
                .map(i18napplication -> i18napplication.getTitle().get(locale.getLanguage()))
                .orElse(null);
    }
    public String getInternationnalizedApplicationDescription(Locale locale) {
        return Optional.ofNullable(downloadDatasetQuery().application().getConfiguration())
                .map(Configuration::i18n)
                .map(Internationalizations::getApplication)
                .map(i18napplication -> i18napplication.getDescription().get(locale.getLanguage()))
                .orElse(null);
    }

    public String getInternationnalizedDataName(Locale locale, String dataName) {
        return Optional.ofNullable(downloadDatasetQuery().application().getConfiguration())
                .map(Configuration::i18n)
                .map(Internationalizations::getData)
                .map(data -> data.get(dataName))
                .map(internationalizationData -> internationalizationData.getI18n())
                .map(i18nData -> i18nData.getTitle().get(locale.getLanguage()))
                .orElse(dataName);
    }

    public String getInternationnalizedDataNameDescription(Locale locale, String dataName) {
        return Optional.ofNullable(downloadDatasetQuery().application().getConfiguration())
                .map(Configuration::i18n)
                .map(Internationalizations::getData)
                .map(data -> data.get(dataName))
                .map(internationalizationData -> internationalizationData.getI18n())
                .map(i18nData -> i18nData.getDescription().get(locale.getLanguage()))
                .orElse(dataName);
    }

    public Locale getDefaultLanguage() {
        return Optional.ofNullable(downloadDatasetQuery().application().getConfiguration())
                .map(Configuration::applicationDescription)
                .map(ApplicationDescription::defaultLanguage)
                .orElse(Locale.FRENCH);
    }

    public String mailMessagefor(String message, int expirationDelay) {
        String messageTemplate = Optional.ofNullable(MESSAGE_PATTERN_FOR_MAIL.get(downloadDatasetQuery.outPut().locale()))
                .orElse(MESSAGE_PATTERN_FOR_MAIL.get(getDefaultLanguage()));
        return messageTemplate.formatted(message, expirationDelay);
    }
}
