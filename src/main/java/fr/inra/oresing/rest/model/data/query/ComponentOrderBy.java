package fr.inra.oresing.rest.model.data.query;

import fr.inra.oresing.domain.application.configuration.ComponentDescription;
import fr.inra.oresing.domain.application.configuration.StandardDataDescription;
import fr.inra.oresing.persistence.DataRepository;
import lombok.Getter;

public class ComponentOrderBy {
    @Getter
    public String componentKey;
    public DataRepository.Order order;

    public ComponentOrderBy() {
        super();
    }

    public ComponentOrderBy(final String componentKey, final DataRepository.Order order) {
        super();
        this.componentKey = componentKey;
        this.order = order;
    }

    public static fr.inra.oresing.domain.data.read.query.ComponentOrderBy build(
            final ComponentOrderBy componentOrderBy,
            final StandardDataDescription dataTypeDescription
    ) {

        ComponentDescription componentDescription = dataTypeDescription.componentDescriptions().get(componentOrderBy.componentKey);
        return new fr.inra.oresing.domain.data.read.query.ComponentOrderBy(
                componentOrderBy.componentKey,
                componentOrderBy.order,
                ComponentType.getComponentType(componentDescription)

        );
    }

    public String getOrder() {
        return order != null ? order.name() : "ASC";
    }
}