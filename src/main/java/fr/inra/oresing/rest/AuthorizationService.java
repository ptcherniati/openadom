package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Range;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
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

    public UUID addAuthorization(CreateAuthorizationRequest authorization) {
        OreSiUserRole userRole = authenticationService.getUserRole(authorization.getUserId());

        Application application = repository.application().findApplication(authorization.getApplicationNameOrId());

        String dataType = authorization.getDataType();
        String dataGroup = authorization.getDataGroup();

        Preconditions.checkArgument(application.getConfiguration().getDataTypes().containsKey(dataType));

        Configuration.AuthorizationDescription authorizationDescription = application.getConfiguration().getDataTypes().get(dataType).getAuthorization();

        Preconditions.checkArgument(authorizationDescription.getDataGroups().containsKey(dataGroup));

        Preconditions.checkArgument(authorization.getAuthorizedScopes().keySet().equals(authorizationDescription.getAuthorizationScopes().keySet()));

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

        return entity.getId();
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
                OreSiAuthorization.class.getSimpleName() + "_" + authorization.getId().toString(),
                SqlSchema.forApplication(application).data(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                SqlPolicy.Statement.SELECT,
                userRole,
                usingExpression
        );

        return sqlPolicy;
    }

    public void revoke(AuthorizationRequest revokeAuthorizationRequest) {
        Application application = repository.application().findApplication(revokeAuthorizationRequest.getApplicationNameOrId());
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        UUID authorizationId = revokeAuthorizationRequest.getAuthorizationId();
        OreSiAuthorization oreSiAuthorization = authorizationRepository.findById(authorizationId);
        SqlPolicy sqlPolicy = toPolicy(oreSiAuthorization);
        db.dropPolicy(sqlPolicy);
        authorizationRepository.delete(authorizationId);
    }

    public ImmutableSet<GetAuthorizationResult> getAuthorizations(String applicationNameOrId, String dataType) {
        Application application = repository.application().findApplication(applicationNameOrId);
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        ImmutableSet<GetAuthorizationResult> authorizations = authorizationRepository.findByDataType(dataType).stream()
                .map(this::toGetAuthorizationResult)
                .collect(ImmutableSet.toImmutableSet());
        return authorizations;
    }

    public GetAuthorizationResult getAuthorization(AuthorizationRequest authorizationRequest) {
        Application application = repository.application().findApplication(authorizationRequest.getApplicationNameOrId());
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        UUID authorizationId = authorizationRequest.getAuthorizationId();
        OreSiAuthorization oreSiAuthorization = authorizationRepository.findById(authorizationId);
        return toGetAuthorizationResult(oreSiAuthorization);
    }

    private GetAuthorizationResult toGetAuthorizationResult(OreSiAuthorization oreSiAuthorization) {
        Range<LocalDateTime> timeScopeRange = oreSiAuthorization.getTimeScope().getRange();
        LocalDate fromDay;
        if (timeScopeRange.hasLowerBound()) {
            fromDay = timeScopeRange.lowerEndpoint().toLocalDate();
        } else {
            fromDay = null;
        }
        LocalDate toDay;
        if (timeScopeRange.hasUpperBound()) {
            toDay = timeScopeRange.upperEndpoint().toLocalDate();
        } else {
            toDay = null;
        }
        return new GetAuthorizationResult(
            oreSiAuthorization.getId(),
            oreSiAuthorization.getOreSiUser(),
            oreSiAuthorization.getApplication(),
            oreSiAuthorization.getDataType(),
            oreSiAuthorization.getDataGroup(),
            oreSiAuthorization.getAuthorizedScopes(),
            fromDay,
            toDay
        );
    }
}
