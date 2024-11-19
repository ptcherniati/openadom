package fr.inra.oresing.rest.model.data;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.domain.application.configuration.Submission;
import fr.inra.oresing.persistence.JsonRowMapper;
import fr.inra.oresing.persistence.requestBuilder.data.DataRequestBuilder;
import fr.inra.oresing.persistence.requestBuilder.data.SqlRequest;
import fr.inra.oresing.rest.model.data.query.DownloadDatasetQuery;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.util.FileCopyUtils;
import org.testcontainers.shaded.com.google.common.collect.ImmutableSet;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Tag("MODEL_REQUEST_TEST")
class DownloadDatasetQueryTest {
    final String simpleSearchJson = """
            {
               "limit": 10,
               "componentSelects": [{
                     "variable": "date",
                     "component": "value"
                   },
                   {
                     "variable": "site",
                     "component": "plateforme"
                   }
                 ],
               "authorizationDescriptions": [
                 {
                   "timeScope": {
                     "from": "1984-01-01",
                     "to": "1984-01-02"
                   },
                   "requiredAuthorizations": [
                     {
                       "projet": "projet_manche",
                       "localization": "plateforme"
                     }
                   ]
                 },
                 {
                   "timeScope": {
                     "from": "1984-01-03",
                     "to": "1984-01-04"
                   },
                   "requiredAuthorizations": [
                     {
                       "projet": "atlantique",
                       "localization": "plateforme"
                     }
                   ]
                 }
               ],
                "componentOrderBy": [
                  {
                      "componentKey": {
                           "variable": "site",
                           "component": "plateforme"
                      },
                      "order": "ASC",
                      "type": null,
                      "format": null
                  }
              ]
             }
            """;

    final String searchByIds = """
                       {
                            "limit": 10,
                            "componentSelects": [{
                                 "variable": "date",
                                 "component": "value"
                               },
                               {
                                 "variable": "site",
                                 "component": "plateforme"
                               }
                             ],
                            "rowIds" : [
                                "b5e086ee-0462-41b0-a136-d6e0604fe6c8",
                                "2386667c-6929-4981-9e8a-72c1d090272a"
                            ],
                            "componentOrderBy": [
                              {
                                  "componentKey": {
                                       "variable": "site",
                                       "component": "plateforme"
                                  },
                                  "order": "ASC",
                                  "type": null,
                                  "format": null
                              }
                          ]
                       }
            """;

    final String searchNoFilter = """
                       {
                            "limit": 10,
                            "componentSelects": [{
                                 "variable": "date",
                                 "component": "value"
                               },
                               {
                                 "variable": "site",
                                 "component": "plateforme"
                               }
                             ],
                            "componentOrderBy": [
                              {
                                  "componentKey": {
                                       "variable": "site",
                                       "component": "plateforme"
                                  },
                                  "order": "ASC",
                                  "type": null,
                                  "format": null
                              }
                          ]
                       }
            """;

    final String advancedSearchJson = """
            {
              "application": null,
              "applicationNameOrId": null,
              "dataType": null,
              "offset": null,
              "limit": 15,
               "componentSelects": [{
                     "variable": "date",
                     "component": "value"
                   },
                   {
                     "variable": "site",
                     "component": "plateforme"
                   }
                 ],
              "componentFilters": [
                 {
                   "componentKey": {
                     "variable": "date",
                     "component": "value"
                   },
                   "type": "date",
                   "format": "dd/MM/yyyy",
                   "intervalValues": {
                     "from": "01/01/1984",
                     "to": "04/01/1984"
                   }
                 },
                 {
                   "componentKey": {
                     "variable": "Nombre d'individus",
                     "component": "value"
                   },
                   "type": "numeric",
                   "intervalValues": {
                     "from": "5",
                     "to": "40"
                   },
                   "isRegExp": null
                 },
                 {
                   "componentKey": {
                     "variable": "site",
                     "component": "bassin"
                   },
                   "filter": "lebassin"
                 },
                 {
                   "componentKey": {
                     "variable": "site",
                     "component": "chemin"
                   },
                   "filter": "l[ae]bassine+",
                   "intervalValues": null,
                   "isRegExp": true
                 },
                 {
                   "componentKey": {
                     "variable": "projet",
                     "component": "value"
                   },
                   "filter": "projet_manche",
                   "type": "reference"
                 }
               ],
              "componentOrderBy": [
                {
                  "componentKey": {
                    "variable": "site",
                    "component": "plateforme"
                  },
                  "order": "ASC",
                  "type": null,
                  "format": null
                }
              ]
            }""";
    final Resource yaml = new ClassPathResource("data/monsore/monsore-with-repository.yaml");

    @Test
    public void BuildSQLBySimpleSearchByRequest() {
        try {
            final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery build = before(simpleSearchJson);
            final SqlRequest sql = DataRequestBuilder.buildSelectRequest(build);
            System.out.println(sql);
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    public void BuildSQLBySimpleSearchByRowIds() {
        try {
            final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery build = before(searchByIds);
            final SqlRequest request = DataRequestBuilder.buildSelectRequest(build);
            System.out.println(request.sql());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    public void BuildSQLNoFilter() {
        try {
            final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery build = before(searchNoFilter);
            final SqlRequest request = DataRequestBuilder.buildSelectRequest(build);
            System.out.println(request.sql());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

    }

    @Test
    public void BuildSQLAdvancedSearch() {
        try {
            final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery build = before(advancedSearchJson);
            final SqlRequest request = DataRequestBuilder.buildSelectRequest(build);
            System.out.println(request.sql());
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }

    }

    
    private fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery before(final String search) throws IOException {
        final byte[] yamlContent = FileCopyUtils.copyToByteArray(yaml.getInputStream());
        final YAMLMapper mapper = new YAMLMapper();
        final Configuration configuration = mapper.readValue(yamlContent, Configuration.class);
        final ImmutableSet.Builder<String> requiredAuthorizationsAttributesBuilder = ImmutableSet.builder();

        for (final Map.Entry<String, StandardDataDescription> dataTypeEntry : configuration.dataDescription().entrySet()) {
            Optional.ofNullable(dataTypeEntry.getValue())
                    .map(StandardDataDescription::submission)
                    .map(Submission::submissionScope)
                    .ifPresent(authorization-> {
                requiredAuthorizationsAttributesBuilder.addAll(authorization.componentNames());
            });
        }
        configuration.requiredAuthorizationsAttributes().clear();
        configuration.requiredAuthorizationsAttributes().addAll(List.copyOf(requiredAuthorizationsAttributesBuilder.build()));
        final DownloadDatasetQuery downloadDatasetQuerySearch = (DownloadDatasetQuery) new JsonRowMapper().toObject(search, DownloadDatasetQuery.class);
        final Application application = new Application();
        application.setConfiguration(configuration);
        application.setName("monsores");
        downloadDatasetQuerySearch.setApplication(application);
        downloadDatasetQuerySearch.setDataName("pem");
        final fr.inra.oresing.domain.data.read.query.DownloadDatasetQuery build = DownloadDatasetQuery.build(downloadDatasetQuerySearch);
        return build;
    }

}