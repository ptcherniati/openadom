package fr.inra.oresing.domain.data.read.query;


import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Ltree;
import org.apache.logging.log4j.util.Strings;

import java.util.Objects;
import java.util.Set;

public record DownloadDatasetQueryByNaturalKey(
        boolean hasPatternDefinition,
        Application application,
        String dataName,
        OutPut outPut,
        Set<String> componentSelects,
        Set<ComponentOrderBy> componentOrderBy,

        Set<Ltree> naturalOrHierarchicalKey) implements DownloadDatasetQuery {
    public DownloadDatasetQueryByNaturalKey {
        Objects.requireNonNull(application, "You must provide a valide application");
        if(!Strings.isNotEmpty(dataName)){
            throw new IllegalArgumentException("You must provide a valide datatype");
        }
        assert application.existsData(dataName) : "Datatype must be declared in configuration";
    }

}