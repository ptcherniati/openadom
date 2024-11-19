package fr.inra.oresing.domain.data.read.query;


import fr.inra.oresing.domain.application.Application;

import java.util.*;

public record DownloadDatasetQuerySimpleSearch(

        Application application,
        String dataName,
        OutPut outPut,
        Set<String> componentSelects,
        Set<ComponentOrderBy> componentOrderBy,

        Set<AuthorizationDescription>authorizationDescriptions) /*implements DownloadDatasetQuery*/ {

}