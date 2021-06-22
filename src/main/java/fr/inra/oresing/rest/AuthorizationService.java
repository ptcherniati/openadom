package fr.inra.oresing.rest;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.OreSiAuthorization;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.AuthorizationRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.SqlPolicy;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.SqlService;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.shaded.com.google.common.base.Preconditions;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@Transactional
public class AuthorizationService {

    @Autowired
    private SqlService db;

    @Autowired
    private AuthenticationService authenticationService;

    @Autowired
    private OreSiRepository repository;

    public CreateAuthorizationRequest addAuthorization(CreateAuthorizationRequest authorization) {
        OreSiUserRole userRole = authenticationService.getUserRole(authorization.getUserId());

        Application application = repository.application().findApplication(authorization.getApplicationNameOrId());

        String dataType = authorization.getDataType();
        String dataGroup = authorization.getDataGroup();

        Preconditions.checkArgument(application.getConfiguration().getDataTypes().containsKey(dataType));

        Configuration.AuthorizationDescription authorizationDescription = application.getConfiguration().getDataTypes().get(dataType).getAuthorization();

        Preconditions.checkArgument(authorizationDescription.getDataGroups().containsKey(dataGroup));

        OreSiAuthorization entity = new OreSiAuthorization();
        entity.setOreSiUser(authorization.getUserId());
        entity.setApplication(application.getId());
        entity.setDataType(dataType);
        entity.setDataGroup(dataGroup);
        entity.setAuthorizedScopes(authorization.getAuthorizedScopes());
        entity.setTimeScope(authorization.getTimeScope());

        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        authorizationRepository.store(entity);

        SqlPolicy sqlPolicy = toPolicy(entity);
        db.addUserInRole(userRole, OreSiRightOnApplicationRole.readerOn(application));
        db.createPolicy(sqlPolicy);

        return authorization;
    }

    private SqlPolicy toPolicy(OreSiAuthorization authorization) {
        Set<String> usingExpressionElements = new LinkedHashSet<>();

        String dataType = authorization.getDataType();

        usingExpressionElements.add("application = '" + authorization.getApplication() + "'::uuid");
        usingExpressionElements.add("dataType = '" + dataType + "'");
        usingExpressionElements.add("dataGroup = '" + authorization.getDataGroup() + "'");

        String timeScopeSqlExpression = authorization.getTimeScope().toSqlExpression();
        usingExpressionElements.add("timeScope <@ '" + timeScopeSqlExpression + "'");

        authorization.getAuthorizedScopes().entrySet().stream()
                .map(authorizationEntry -> {
                    String authorizationScope = authorizationEntry.getKey();
                    String authorizedScope = authorizationEntry.getValue();
                    String usingElement = "jsonb_extract_path_text(requiredAuthorizations, '" + authorizationScope + "')::ltree <@ '" + authorizedScope + "'::ltree";
                    return usingElement;
                })
                .forEach(usingExpressionElements::add);

        String usingExpression = usingExpressionElements.stream()
                .map(statement -> "(" + statement + ")")
                .collect(Collectors.joining(" AND "));

        OreSiUserRole userRole = authenticationService.getUserRole(authorization.getOreSiUser());

        Application application = repository.application().findApplication(authorization.getApplication());

        SqlPolicy sqlPolicy = new SqlPolicy(
                SqlSchema.forApplication(application).data(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                SqlPolicy.Statement.SELECT,
                userRole,
                usingExpression
        );

        return sqlPolicy;
    }

}
