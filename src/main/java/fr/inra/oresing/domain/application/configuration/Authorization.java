package fr.inra.oresing.domain.application.configuration;

import java.util.List;

public record Authorization(List<AuthorizationScopeComponentData> authorizationScope, String timeScope) {
}
