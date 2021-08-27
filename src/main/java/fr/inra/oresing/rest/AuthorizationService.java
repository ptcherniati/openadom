package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Range;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.OreSiAuthorization;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.AuthorizationRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.SqlPolicy;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.SqlService;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.shaded.com.google.common.base.Preconditions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OreSiService oreSiService;

    @Autowired
    private CheckerFactory checkerFactory;

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

    public GetGrantableResult getGrantable(String applicationNameOrId, String dataType) {
        Application application = repository.application().findApplication(applicationNameOrId);
        Configuration configuration = application.getConfiguration();
        Preconditions.checkArgument(configuration.getDataTypes().containsKey(dataType));
        ImmutableSortedSet<GetGrantableResult.User> users = getGrantableUsers();
        ImmutableSortedSet<GetGrantableResult.DataGroup> dataGroups = getDataGroups(application, dataType);
        ImmutableSortedSet<GetGrantableResult.AuthorizationScope> authorizationScopes = getAuthorizationScopes(application, dataType);
        return new GetGrantableResult(users, dataGroups, authorizationScopes);
    }

    private ImmutableSortedSet<GetGrantableResult.DataGroup> getDataGroups(Application application, String dataType) {
        ImmutableSortedSet<GetGrantableResult.DataGroup> dataGroups = application.getConfiguration().getDataTypes().get(dataType).getAuthorization().getDataGroups().entrySet().stream()
                .map(dataGroupEntry -> new GetGrantableResult.DataGroup(dataGroupEntry.getKey(), dataGroupEntry.getValue().getLabel()))
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetGrantableResult.DataGroup::getId)));
        return dataGroups;
    }

    private ImmutableSortedSet<GetGrantableResult.User> getGrantableUsers() {
        List<OreSiUser> allUsers = userRepository.findAll();
        ImmutableSortedSet<GetGrantableResult.User> users = allUsers.stream()
                .map(oreSiUserEntity -> new GetGrantableResult.User(oreSiUserEntity.getId(), oreSiUserEntity.getLogin()))
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetGrantableResult.User::getId)));
        return users;
    }

    private ImmutableSortedSet<GetGrantableResult.AuthorizationScope> getAuthorizationScopes(Application application, String dataType) {
        ImmutableMap<VariableComponentKey, ReferenceLineChecker> referenceLineCheckers = checkerFactory.getReferenceLineCheckers(application, dataType);
        Configuration.AuthorizationDescription authorizationDescription = application.getConfiguration().getDataTypes().get(dataType).getAuthorization();
        ImmutableSortedSet<GetGrantableResult.AuthorizationScope> authorizationScopes = authorizationDescription.getAuthorizationScopes().entrySet().stream()
                .map(authorizationScopeEntry -> {
                    String variable = authorizationScopeEntry.getValue().getVariable();
                    String component = authorizationScopeEntry.getValue().getComponent();
                    VariableComponentKey variableComponentKey = new VariableComponentKey(variable, component);
                    ReferenceLineChecker referenceLineChecker = referenceLineCheckers.get(variableComponentKey);
                    String lowestLevelReference = referenceLineChecker.getRefType();
                    HierarchicalReferenceAsTree hierarchicalReferenceAsTree = oreSiService.getHierarchicalReferenceAsTree(application, lowestLevelReference);
                    ImmutableSortedSet<GetGrantableResult.AuthorizationScope.Option> rootOptions = hierarchicalReferenceAsTree.getRoots().stream()
                            .map(rootReferenceValue -> toOption(hierarchicalReferenceAsTree, rootReferenceValue))
                            .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetGrantableResult.AuthorizationScope.Option::getId)));
                    String authorizationScopeId = authorizationScopeEntry.getKey();
                    return new GetGrantableResult.AuthorizationScope(authorizationScopeId, authorizationScopeId, rootOptions);
                })
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetGrantableResult.AuthorizationScope::getId)));
        return authorizationScopes;
    }

    private GetGrantableResult.AuthorizationScope.Option toOption(HierarchicalReferenceAsTree tree, ReferenceValue referenceValue) {
        ImmutableSortedSet<GetGrantableResult.AuthorizationScope.Option> options = tree.getChildren(referenceValue).stream()
                .map(child -> toOption(tree, child))
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetGrantableResult.AuthorizationScope.Option::getId)));
        return new GetGrantableResult.AuthorizationScope.Option(referenceValue.getHierarchicalKey(), referenceValue.getHierarchicalKey(), options);
    }
}
