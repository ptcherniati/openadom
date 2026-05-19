package fr.inra.oresing.rest.usecases.security.authorization;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.authorization.GetGrantableResult;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.rest.services.DefaultAuthorizationService;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class GetAuthorizationScopesUseCase {
    private final DefaultAuthorizationService authorizationService;

    public GetAuthorizationScopesUseCase(DefaultAuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    public Map<String, List<GetGrantableResult.ReferenceScope>> execute(Application application, MenuType menuType) {
        return authorizationService.getAuthorizationScopes(application, menuType);
    }
}