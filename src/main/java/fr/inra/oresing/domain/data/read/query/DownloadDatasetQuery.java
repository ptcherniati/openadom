package fr.inra.oresing.domain.data.read.query;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.ApplicationDescription;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.rest.OreSiResources;
import fr.inra.oresing.rest.filesenderclient.MessageInformations;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public sealed interface DownloadDatasetQuery extends MessageInformations
        permits DownloadDatasetQueryAdvancedSearch, DownloadDatasetQueryByNaturalKey, DownloadDatasetQueryByRowId, DownloadDatasetQueryNoFilter/*, DownloadDatasetQuerySimpleSearch*/ {

    Set<ComponentOrderBy> componentOrderBy();

    Set<String> componentSelects();

    Application application();

    String dataName();

    OutPut outPut();
    boolean horizontalDisplay();

    default long patternDefinitionCount(){
        return Optional.ofNullable(application())
                .map(Application::getConfiguration)
                .flatMap(configuration -> configuration.findData(dataName()))
                .map(StandardDataDescription::patternDefinitionCount)
                .orElse(0L);
    };

    default StandardDataDescription getDataConfiguration() {
        return application().getConfiguration().dataDescription().get(dataName());
    }

    default String getLanguage() {
        return getLocale().getLanguage();
    }

    default Locale getLocale() {
        return Optional.ofNullable(outPut())
                .map(OutPut::locale)
                .orElse(
                        Optional.ofNullable(application())
                                .map(Application::getConfiguration)
                                .map(Configuration::applicationDescription)
                                .map(ApplicationDescription::defaultLanguage)
                                .orElseGet(OreSiResources::getDefaultLocale)
                );
    }
}
