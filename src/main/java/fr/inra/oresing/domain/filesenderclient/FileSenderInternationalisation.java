package fr.inra.oresing.domain.filesenderclient;

import java.util.Locale;

public interface FileSenderInternationalisation {
    String mailMessagefor(String message, int expirationDelay);

    String getInternationnalizedApplication(Locale locale);
    String getInternationnalizedApplicationDescription(Locale locale);

    String getInternationnalizedDataName(Locale locale, String s);

    String subjectPattern();

    String messagePattern();

    Locale getDefaultLanguage();
}
