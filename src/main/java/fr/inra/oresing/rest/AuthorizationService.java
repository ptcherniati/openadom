package fr.inra.oresing.rest;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Range;
import com.google.common.collect.Sets;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.Authorization;
import fr.inra.oresing.model.Configuration;
import fr.inra.oresing.model.OreSiAuthorization;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.model.ReferenceValue;
import fr.inra.oresing.model.VariableComponentKey;
import fr.inra.oresing.persistence.AuthenticationService;
import fr.inra.oresing.persistence.AuthorizationRepository;
import fr.inra.oresing.persistence.Ltree;
import fr.inra.oresing.persistence.OperationType;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.SqlPolicy;
import fr.inra.oresing.persistence.SqlSchema;
import fr.inra.oresing.persistence.SqlSchemaForApplication;
import fr.inra.oresing.persistence.SqlService;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.shaded.com.google.common.base.Preconditions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
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

    public void
    updateRoleForManagement(Set<UUID> previousUsers, OreSiAuthorization modifiedAuthorization) {
        Set<UUID> newUsers = modifiedAuthorization.getOreSiUsers();
        Application application = repository.application().findApplication(modifiedAuthorization.getApplication());
        OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, modifiedAuthorization.getId());
        db.addUserInRole(oreSiRightOnApplicationRole, OreSiRightOnApplicationRole.readerOn(application));
        addOrRemoveAuthorizationForUsers(previousUsers, newUsers, oreSiRightOnApplicationRole);
        if (modifiedAuthorization.getAuthorizations().containsKey(OperationType.publication)) {
            db.addUserInRole(oreSiRightOnApplicationRole, OreSiRightOnApplicationRole.writerOn(application));
            SqlPolicy publishPolicy = toDatatypePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.publication, SqlPolicy.Statement.INSERT);
            db.createPolicy(publishPolicy);
        }
        if (modifiedAuthorization.getAuthorizations().containsKey(OperationType.extraction)) {
            SqlPolicy extractPolicy = toDatatypePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.extraction, SqlPolicy.Statement.SELECT);
            db.createPolicy(extractPolicy);
        }
        if (modifiedAuthorization.getAuthorizations().containsKey(OperationType.delete)) {
            db.addUserInRole(oreSiRightOnApplicationRole, OreSiRightOnApplicationRole.writerOn(application));
            SqlPolicy extractPolicy = toDatatypePolicy(modifiedAuthorization, oreSiRightOnApplicationRole, OperationType.delete, SqlPolicy.Statement.DELETE);
            db.createPolicy(extractPolicy);
        }
    }

    public OreSiRightOnApplicationRole createRoleForAuthorization(CreateAuthorizationRequest previousAuthorization, OreSiAuthorization modifiedAuthorization) {
        UUID created = modifiedAuthorization.getId();
        Application application = repository.application().findApplication(previousAuthorization.getApplicationNameOrId());
        OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, created);
        db.createRole(oreSiRightOnApplicationRole);
        return oreSiRightOnApplicationRole;
    }


    public OreSiAuthorization addAuthorization(CreateAuthorizationRequest authorizations) {
        Application application = repository.application().findApplication(authorizations.getApplicationNameOrId());
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        OreSiAuthorization entity = authorizations.getUuid() == null ?
                new OreSiAuthorization()
                : authorizationRepository.findById(authorizations.getUuid());

        String dataType = authorizations.getDataType();
        Map<OperationType, List<Authorization>> authorizationsByType = authorizations.getAuthorizations();

        Preconditions.checkArgument(application.getConfiguration().getDataTypes().containsKey(dataType));

        Configuration.AuthorizationDescription authorizationDescription = application.getConfiguration().getDataTypes().get(dataType).getAuthorization();

        authorizationsByType.values()
                .forEach(authByType -> {
                    authByType.forEach(authorization -> {
                        authorization.getDataGroup()
                                .forEach(datagroup -> Preconditions.checkArgument(authorizationDescription.getDataGroups().containsKey(datagroup)));
                        Set<String> labels = authorizationDescription.getAuthorizationScopes().keySet();
                        Preconditions.checkArgument(
                                labels.containsAll(authorization.getRequiredauthorizations().keySet())
                        );
                    });
                });
        entity.setName(authorizations.getName());
        entity.setOreSiUsers(authorizations.getUsersId());
        entity.setApplication(application.getId());
        entity.setDataType(dataType);
        entity.setAuthorizations(authorizations.getAuthorizations());
        authorizationRepository.store(entity);
        return entity;
    }

    private void addOrRemoveAuthorizationForUsers(Set<UUID> previousUsers, Set<UUID> newUsers, OreSiRightOnApplicationRole oreSiRightOnApplicationRole) {
        Set<UUID> usersNotChanged = Sets.difference(previousUsers, newUsers);
        previousUsers.stream()
                .filter(user -> !usersNotChanged.contains(user))
                .map(authenticationService::getUserRole)
                .forEach(user -> db.removeUserInRole(user, oreSiRightOnApplicationRole));
        newUsers.stream()
                .filter(user -> !usersNotChanged.contains(user))
                .map(authenticationService::getUserRole)
                .forEach(user -> db.addUserInRole(user, oreSiRightOnApplicationRole));
    }


    private SqlPolicy toDatatypePolicy(OreSiAuthorization authorization, OreSiRightOnApplicationRole oreSiRightOnApplicationRole, OperationType operation, SqlPolicy.Statement statement) {
        Set<String> usingExpressionElements = new LinkedHashSet<>();
        Application application = repository.application().findApplication(authorization.getApplication());
        SqlSchemaForApplication sqlSchemaForApplication = SqlSchema.forApplication(application);

        String dataType = authorization.getDataType();
        SqlPolicy sqlPolicy = null;
        usingExpressionElements.add("application = '" + authorization.getApplication() + "'::uuid");
        usingExpressionElements.add("dataType = '" + dataType + "'");
        String usingExpression = createUsingExpression(authorization, usingExpressionElements, application, sqlSchemaForApplication, operation);

        sqlPolicy = new SqlPolicy(
                OreSiAuthorization.class.getSimpleName() + "_" + authorization.getId().toString(),
                sqlSchemaForApplication.data(),
                SqlPolicy.PermissiveOrRestrictive.PERMISSIVE,
                statement,
                oreSiRightOnApplicationRole,
                usingExpression
        );
        return sqlPolicy;
    }

    private String createUsingExpression(OreSiAuthorization authorization, Set<String> usingExpressionElements, Application application, SqlSchemaForApplication sqlSchemaForApplication, OperationType operation) {
        if (authorization.getAuthorizations().containsKey(operation) &&
                !authorization.getAuthorizations().get(operation).isEmpty()) {
            usingExpressionElements.add("\"authorization\" @> " +
                    authorization.getAuthorizations().get(operation).stream()
                            .map(auth -> auth.toSQL(application.getConfiguration().getRequiredAuthorizationsAttributes()))
                            .filter(auth -> auth != null)
                            .map(sql -> String.format(sql, sqlSchemaForApplication.getName()))
                            .collect(Collectors.joining(",", "ARRAY[", "]::" + sqlSchemaForApplication.getName() + ".authorization[]"))


            );
        }

        String usingExpression = usingExpressionElements.stream()
                .map(statement -> "(" + statement + ")")
                .collect(Collectors.joining(" AND "));
        return usingExpression;
    }

    public void revoke(AuthorizationRequest revokeAuthorizationRequest) {
        Application application = repository.application().findApplication(revokeAuthorizationRequest.getApplicationNameOrId());
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        UUID authorizationId = revokeAuthorizationRequest.getAuthorizationId();
        OreSiAuthorization oreSiAuthorization = authorizationRepository.findById(authorizationId);
        OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, authorizationId);
        if (oreSiAuthorization.getAuthorizations().containsKey(OperationType.publication)) {
            db.addUserInRole(oreSiRightOnApplicationRole, OreSiRightOnApplicationRole.writerOn(application));
            SqlPolicy publishPolicy = toDatatypePolicy(oreSiAuthorization, oreSiRightOnApplicationRole, OperationType.publication, SqlPolicy.Statement.INSERT);
            db.dropPolicy(publishPolicy);
        }
        if (oreSiAuthorization.getAuthorizations().containsKey(OperationType.extraction)) {
            SqlPolicy extractPolicy = toDatatypePolicy(oreSiAuthorization, oreSiRightOnApplicationRole, OperationType.extraction, SqlPolicy.Statement.SELECT);
            db.dropPolicy(extractPolicy);
        }
        if (oreSiAuthorization.getAuthorizations().containsKey(OperationType.delete)) {
            SqlPolicy extractPolicy = toDatatypePolicy(oreSiAuthorization, oreSiRightOnApplicationRole, OperationType.delete, SqlPolicy.Statement.DELETE);
            db.dropPolicy(extractPolicy);
        }
        authorizationRepository.delete(authorizationId);
    }

    public ImmutableSet<GetAuthorizationResult> getAuthorizations(String applicationNameOrId, String dataType) {
        Application application = repository.application().findApplication(applicationNameOrId);
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        Preconditions.checkArgument(application.getConfiguration().getDataTypes().containsKey(dataType));
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
        List<OreSiUser> all = userRepository.findAll();
        return new GetAuthorizationResult(
                oreSiAuthorization.getId(),
                oreSiAuthorization.getName(),
                getOreSIUSers(all, oreSiAuthorization.getOreSiUsers()),
                oreSiAuthorization.getApplication(),
                oreSiAuthorization.getDataType(),
                extractTimeRangeToFromAndTo(oreSiAuthorization.getAuthorizations())
        );
    }

    private Set<OreSiUser> getOreSIUSers(List<OreSiUser> users, Set<UUID> usersId) {
        return users.stream()
                .filter(oreSiUser -> usersId.contains(oreSiUser.getId()))
                .collect(Collectors.toSet());
    }


    private Map<OperationType, List<AuthorizationParsed>> extractTimeRangeToFromAndTo(Map<OperationType, List<Authorization>> authorizations) {
        Map<OperationType, List<AuthorizationParsed>> transformedAuthorizations = new HashMap<>();
        for (Map.Entry<OperationType, List<Authorization>> operationTypeListEntry : authorizations.entrySet()) {
            List<AuthorizationParsed> authorizationsParsed = new LinkedList<>();
            for (Authorization authorization : operationTypeListEntry.getValue()) {
                Range<LocalDateTime> timeScopeRange = authorization.getTimeScope().getRange();
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
                authorizationsParsed.add(new AuthorizationParsed(authorization.getDataGroup(), Maps.transformValues(authorization.getRequiredauthorizations(), Ltree::getSql), fromDay, toDay));
            }
            transformedAuthorizations.put(operationTypeListEntry.getKey(), authorizationsParsed);
        }
        return transformedAuthorizations;
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
        return new GetGrantableResult.AuthorizationScope.Option(referenceValue.getHierarchicalKey().getSql(), referenceValue.getHierarchicalKey().getSql(), options);
    }
}