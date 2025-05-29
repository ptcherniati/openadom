package fr.inra.oresing.rest.model.data.query;

import fr.inra.oresing.domain.application.configuration.Ltree;
import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;

@Getter
@Setter
/*

 */
public class AuthorizationDescription {
    private IntervalValues timeScope;
    private Map<String, Ltree> requiredAuthorizations = new HashMap<>();

    public AuthorizationDescription(IntervalValues timeScope, Map<String, Ltree> requiredAuthorizations) {
        this.timeScope = timeScope;
        this.requiredAuthorizations = requiredAuthorizations;
    }
}