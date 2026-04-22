package fr.inra.oresing.rest.monitoring;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.server.observation.ServerRequestObservationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerMapping;

import java.util.Map;

/**
 * Enrichit automatiquement les observations HTTP ( metric
 * {@code http.server.requests} ) avec deux tags metier de cardinalite
 * basse :
 *
 * <ul>
 *   <li>{@code app_name} : nom ou UUID de l'application ( path variable
 *       {@code {nameOrId}} )</li>
 *   <li>{@code data_type} : type de donnee ou reference ( path variable
 *       {@code {dataType}} ou {@code {dataName}} )</li>
 * </ul>
 *
 * <p>Ces tags permettent de decouper les dashboards Grafana par
 * application et par type de donnees sans ajouter de metric custom
 * ( reutilisation de la metric HTTP standard Spring Boot ).
 *
 * <p>Filter passif : lit uniquement les path variables deja parsees par
 * Spring MVC. Aucun cout CPU significatif , aucune interference avec
 * le traitement de la requete.
 *
 * <p>Phase 1 observabilite (issue #62).
 */
@Component
public class OreSiWebMvcTagsContributor implements ObservationFilter {

    @Override
    public Observation.Context map(Observation.Context context) {
        if (context instanceof ServerRequestObservationContext serverContext) {
            HttpServletRequest request = serverContext.getCarrier();
            @SuppressWarnings("unchecked")
            Map<String, String> pathVariables = (Map<String, String>)
                    request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);

            if (pathVariables != null) {
                String appName = pathVariables.get("nameOrId");
                if (appName != null) {
                    context.addLowCardinalityKeyValue(KeyValue.of("app_name", appName));
                }

                String dataType = pathVariables.get("dataType");
                if (dataType == null) {
                    dataType = pathVariables.get("dataName");
                }
                if (dataType != null) {
                    context.addLowCardinalityKeyValue(KeyValue.of("data_type", dataType));
                }
            }
        }
        return context;
    }
}
