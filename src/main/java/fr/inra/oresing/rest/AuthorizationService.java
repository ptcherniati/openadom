package fr.inra.oresing.rest;

import com.google.common.collect.*;
import fr.inra.oresing.checker.CheckerFactory;
import fr.inra.oresing.checker.ReferenceLineChecker;
import fr.inra.oresing.model.*;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.persistence.roles.CurrentUserRoles;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import fr.inra.oresing.persistence.roles.OreSiRole;
import fr.inra.oresing.rest.exceptions.SiOreIllegalArgumentException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.shaded.com.google.common.base.Preconditions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    @Autowired
    private OreSiApiRequestContext request;

    public void updateRoleForManagement(Set<UUID> previousUsers, OreSiAuthorization modifiedAuthorization) {
        final UpdateRolesOnManagement updateRolesOnManagement = new UpdateRolesOnManagement(repository, db, authenticationService);
        updateRolesOnManagement.init(previousUsers, modifiedAuthorization);
        updateRolesOnManagement.updateRoleForManagement();
    }

    /**
     * create a role as a reader on application
     *
     * @param previousAuthorization The authorization that does not yet have an identifier
     * @param modifiedAuthorization The new authorization created from the previous Authorization information
     * @return the existing role for modifiedAuthorization
     */
    public OreSiRightOnApplicationRole createRoleForAuthorization(CreateAuthorizationRequest previousAuthorization, OreSiAuthorization modifiedAuthorization) {
        UUID created = modifiedAuthorization.getId();
        Application application = repository.application().findApplication(previousAuthorization.getApplicationNameOrId());
        OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, created);
        OreSiRightOnApplicationRole readerRoleOnApplication = OreSiRightOnApplicationRole.readerOn(application);
        db.createRole(oreSiRightOnApplicationRole);
        db.addUserInRole(oreSiRightOnApplicationRole, readerRoleOnApplication);
        return oreSiRightOnApplicationRole;
    }

    public List<OreSiAuthorization> findUserAuthorizationsForApplicationAndDataType(Application application, String dataType) {
        final UUID currentUserId = request.getRequestClient().getId();
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        return authorizationRepository.findAuthorizations(currentUserId, application, dataType);
    }

    public OreSiAuthorization addAuthorization(Application application, String dataType, CreateAuthorizationRequest authorizations, List<OreSiAuthorization> authorizationsForCurrentUser, boolean isApplicationCreator) {
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        OreSiAuthorization entity = authorizations.getUuid() == null ?
                new OreSiAuthorization()
                : authorizationRepository.findById(authorizations.getUuid());

        Map<OperationType, List<Authorization>> authorizationsByType = authorizations.getAuthorizations();

        Preconditions.checkArgument(application.getConfiguration().getDataTypes().containsKey(dataType));

        Configuration.AuthorizationDescription authorizationDescription = application.getConfiguration().getDataTypes().get(dataType).getAuthorization();

        final List<Authorization> authorizationListForCurrentUser = authorizationsForCurrentUser.stream()
                .map(oreSiAuthorization -> oreSiAuthorization.getAuthorizations())
                .filter(operationTypeListMap -> operationTypeListMap.containsKey(OperationType.admin))
                .map(operationTypeListMap -> operationTypeListMap.get(OperationType.admin))
                .flatMap(List::stream)
                .collect(Collectors.toList());

        final Map<OperationType, List<Authorization>> modifiedAuthorizations = authorizationsByType.entrySet().stream()
                .map(authByTypeEntry -> {
                    if (!isApplicationCreator) {
                        removeAuthorizationThatCantBeModified(authByTypeEntry, authorizationListForCurrentUser);
                    }
                    return authByTypeEntry;
                })
                .filter(authByType -> {
                    try {
                        testAuthorizationArguments(authorizationDescription, authByType);
                        return true;
                    } catch (IllegalArgumentException e) {
                        return false;
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!isApplicationCreator) {
            addStoredAuthorizationThatCantBeModified(entity, authorizationListForCurrentUser, modifiedAuthorizations);
        }
        entity.setName(authorizations.getName());
        entity.setOreSiUsers(authorizations.getUsersId());
        entity.setApplication(application.getId());
        entity.setDataType(dataType);
        entity.setAuthorizations(authorizations.getAuthorizations());
        authorizationRepository.store(entity);
        return entity;
    }

    private static void testAuthorizationArguments(Configuration.AuthorizationDescription authorizationDescription, Map.Entry<OperationType, List<Authorization>> authByType) {
        authByType.getValue().forEach(authorization -> {
            authorization.getDataGroups()
                    .forEach(datagroup -> Preconditions.checkArgument(authorizationDescription.getDataGroups().containsKey(datagroup)));
            Set<String> labels = Optional.ofNullable(authorizationDescription)
                    .map(Configuration.AuthorizationDescription::getAuthorizationScopes)
                    .map(Map::keySet)
                    .orElseGet(Set::of);
            Preconditions.checkArgument(
                    labels.containsAll(authorization.getRequiredAuthorizations().keySet())
            );
        });
    }

    private void removeAuthorizationThatCantBeModified(Map.Entry<OperationType, List<Authorization>> authByTypeEntry, List<Authorization> authorizationListForCurrentUser) {
        final List<Authorization> collect = authByTypeEntry.getValue().stream()
                .filter(authorization -> {
                    return testCanSetAuthorization(authorization, authorizationListForCurrentUser);
                })
                .collect(Collectors.toList());
        authByTypeEntry.setValue(collect);
    }

    private void addStoredAuthorizationThatCantBeModified(OreSiAuthorization entity, List<Authorization> authorizationListForCurrentUser, Map<OperationType, List<Authorization>> modifiedAuthorizations) {
        Optional.ofNullable(entity)
                .map(e -> e.getAuthorizations())
                .ifPresent(a -> a.entrySet().stream()
                        .forEach(authByTypeEntry -> {
                            final List<Authorization> collect = authByTypeEntry.getValue().stream()
                                    .filter(authorization -> {
                                        return !testCanSetAuthorization(authorization, authorizationListForCurrentUser);
                                    })
                                    .collect(Collectors.toList());
                            modifiedAuthorizations
                                    .computeIfAbsent(authByTypeEntry.getKey(), k -> new LinkedList<>())
                                    .addAll(collect);
                        })
                );
    }

    private boolean testCanSetAuthorization(Authorization authorization, List<Authorization> authorizationStream) {
        return authorizationStream.stream()
                .anyMatch(authorizationAdmin -> testCanSetAuthorization(authorization, authorizationAdmin));
    }

    private boolean testCanSetAuthorization(Authorization authorization, Authorization authorizationAdmin) {
        final Map<String, Ltree> requiredAuthorizationsAdmin = authorizationAdmin.getRequiredAuthorizations();
        if (requiredAuthorizationsAdmin.isEmpty()) {
            return false;
        }
        return authorization.getRequiredAuthorizations().entrySet().stream().allMatch(
                ltreeEntry -> {
                    if (!authorization.getRequiredAuthorizations().containsKey(ltreeEntry.getKey())) {
                        return true;
                    } else {
                        return authorization.getRequiredAuthorizations().get(ltreeEntry.getKey()).getSql().startsWith(ltreeEntry.getValue().getSql());
                    }
                }
        );
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

    public UUID revoke(String applicationNameOrid, AuthorizationRequest revokeAuthorizationRequest) {
        final UpdateRolesOnManagement updateRolesOnManagement = new UpdateRolesOnManagement(repository, db, authenticationService);
        final Application application = oreSiService.getApplication(applicationNameOrid);
        final CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForCurrentUser();
        final boolean isApplicationCreator = rolesForCurrentUser.getMemberOf().contains(OreSiRightOnApplicationRole.adminOn(application).getAsSqlRole());
        final String dataType = revokeAuthorizationRequest.getDataType();
        List<OreSiAuthorization> authorizationsForCurrentUser = findUserAuthorizationsForApplicationAndDataType(application, dataType);
        if (!isApplicationCreator && !authorizationsForCurrentUser.stream().anyMatch(
                a -> !a.getAuthorizations().get(OperationType.admin).isEmpty()
        )) {
            throw new NotApplicationCanSetRightsException(application.getName(), dataType);
        }
        OreSiAuthorization oreSiAuthorization = repository.getRepository(application).authorization().findById(revokeAuthorizationRequest.getAuthorizationId());
        final List<Authorization> authorizationListForCurrentUser = authorizationsForCurrentUser.stream()
                .map(authorization -> authorization.getAuthorizations())
                .filter(operationTypeListMap -> operationTypeListMap.containsKey(OperationType.admin))
                .map(operationTypeListMap -> operationTypeListMap.get(OperationType.admin))
                .flatMap(List::stream)
                .collect(Collectors.toList());
        Configuration.AuthorizationDescription authorizationDescription = application.getConfiguration().getDataTypes().get(dataType).getAuthorization();


        final Map<OperationType, List<Authorization>> filteredAuthorizations = oreSiAuthorization.getAuthorizations().entrySet().stream()
                .map(authByTypeEntry -> {
                    if (!isApplicationCreator) {
                        final boolean canRemoveEntry = authByTypeEntry.getValue().stream()
                                .allMatch(authorization -> testCanSetAuthorization(authorization, authorizationListForCurrentUser));
                        if (!canRemoveEntry) {
                            throw new NotApplicationCanDeleteRightsException(application.getName(), dataType);
                        }
                    }
                    return authByTypeEntry;
                })
                .map(authByType -> {
                    try {
                        testAuthorizationArguments(authorizationDescription, authByType);
                    } catch (IllegalArgumentException e) {
                        throw new NotApplicationCanDeleteRightsException(application.getName(), dataType);
                    }
                    return authByType;
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (filteredAuthorizations.isEmpty()) {
            return null;
        }
        return updateRolesOnManagement.revoke(revokeAuthorizationRequest);
    }

    public ImmutableSet<GetAuthorizationResult> getAuthorizations(String applicationNameOrId, String dataType, AuthorizationsResult authorizationsForUser) {
        Application application = repository.application().findApplication(applicationNameOrId);
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        Preconditions.checkArgument(application.getConfiguration().getDataTypes().containsKey(dataType));
        final List<OreSiAuthorization> publicAuthorizations = authorizationRepository.findPublicAuthorizations();
        ImmutableSet<GetAuthorizationResult> authorizations = authorizationRepository.findByDataType(dataType).stream()
                .map(oreSiAuthorization -> toGetAuthorizationResult(oreSiAuthorization, publicAuthorizations, authorizationsForUser))
                .collect(ImmutableSet.toImmutableSet());
        return authorizations;
    }

    public GetAuthorizationResult getAuthorization(AuthorizationRequest authorizationRequest, AuthorizationsResult authorizationsForUser) {
        Application application = repository.application().findApplication(authorizationRequest.getApplicationNameOrId());
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        UUID authorizationId = authorizationRequest.getAuthorizationId();
        final List<OreSiAuthorization> publicAuthorizations = authorizationRepository.findPublicAuthorizations();
        OreSiAuthorization oreSiAuthorization = authorizationRepository.findById(authorizationId);
        return toGetAuthorizationResult(oreSiAuthorization, publicAuthorizations, authorizationsForUser);
    }

    private GetAuthorizationResult toGetAuthorizationResult(OreSiAuthorization oreSiAuthorization, List<OreSiAuthorization> publicAuthorizations, AuthorizationsResult authorizationsForUser) {
        List<OreSiUser> all = userRepository.findAll();
        final List<Map<OperationType, List<Authorization>>> collectPublicAuthorizations = publicAuthorizations.stream()
                .map(pa -> pa.getAuthorizations())
                .collect(Collectors.toList());
        return new GetAuthorizationResult(
                oreSiAuthorization.getId(),
                oreSiAuthorization.getName(),
                getOreSIUSers(all, oreSiAuthorization.getOreSiUsers()),
                oreSiAuthorization.getApplication(),
                oreSiAuthorization.getDataType(),
                extractTimeRangeToFromAndTo(oreSiAuthorization.getAuthorizations()),
                collectPublicAuthorizations,
                authorizationsForUser
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
                LocalDate fromDay = null, toDay = null;
                if (authorization.getTimeScope() != null) {
                    Range<LocalDateTime> timeScopeRange = authorization.getTimeScope().getRange();
                    if (timeScopeRange.hasLowerBound()) {
                        fromDay = timeScopeRange.lowerEndpoint().toLocalDate();
                    } else {
                        fromDay = null;
                    }
                    if (timeScopeRange.hasUpperBound()) {
                        toDay = timeScopeRange.upperEndpoint().toLocalDate();
                    } else {
                        toDay = null;
                    }
                }
                authorizationsParsed.add(new AuthorizationParsed("not setting", authorization.getDataGroups(), Maps.transformValues(authorization.getRequiredAuthorizations(), Ltree::getSql), fromDay, toDay));
                transformedAuthorizations.put(operationTypeListEntry.getKey(), authorizationsParsed);
            }
        }
        return transformedAuthorizations;
    }

    public GetGrantableResult getGrantable(String applicationNameOrId, String dataType, AuthorizationsResult authorizationsForUser) {
        Application application = repository.application().findApplication(applicationNameOrId);
        Configuration configuration = application.getConfiguration();
        Preconditions.checkArgument(configuration.getDataTypes().containsKey(dataType));
        ImmutableSortedSet<GetGrantableResult.User> users = getGrantableUsers();
        ImmutableSortedSet<GetGrantableResult.DataGroup> dataGroups = getDataGroups(application, dataType);
        ImmutableSortedSet<GetGrantableResult.AuthorizationScope> authorizationScopes = getAuthorizationScopes(application, dataType);
        ImmutableSortedMap<String, GetGrantableResult.ColumnDescription> columnDescriptions = getColumnDescripton(configuration, dataType);
        return new GetGrantableResult(users, dataGroups, authorizationScopes, columnDescriptions, authorizationsForUser);
    }

    private ImmutableSortedMap<String, GetGrantableResult.ColumnDescription> getColumnDescripton(Configuration configuration, String dataType) {
        return ImmutableSortedMap.copyOf(Optional.ofNullable(configuration)
                .map(Configuration::getDataTypes)
                .map(dty -> dty.get(dataType))
                .map(Configuration.DataTypeDescription::getAuthorization)
                .map(Configuration.AuthorizationDescription::getColumnsDescription)
                .orElseGet(HashMap::new)
                .entrySet()
                .stream()
                .collect(Collectors.toMap(columDescription -> columDescription.getKey(), columDescription -> new GetGrantableResult.ColumnDescription(columDescription.getValue().isDisplay(), columDescription.getValue().getTitle(), columDescription.getValue().isWithPeriods(), columDescription.getValue().isWithDataGroups(), columDescription.getValue().getInternationalizationName()))));

    }

    private ImmutableSortedSet<GetGrantableResult.DataGroup> getDataGroups(Application application, String dataType) {
        ImmutableSortedSet<GetGrantableResult.DataGroup> dataGroups =
                Optional.of(application.getConfiguration().getDataTypes().get(dataType))
                        .map(Configuration.DataTypeDescription::getAuthorization)
                        .map(Configuration.AuthorizationDescription::getDataGroups)
                        .map(dg -> dg.entrySet().stream()
                                .map(dataGroupEntry -> new GetGrantableResult.DataGroup(dataGroupEntry.getKey(), dataGroupEntry.getValue().getLabel()))
                                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetGrantableResult.DataGroup::getId)))
                        )
                        .orElseGet(ImmutableSortedSet::of);
        return dataGroups;
    }

    private ImmutableSortedSet<GetGrantableResult.User> getGrantableUsers() {
        List<OreSiUser> allUsers = userRepository.findAll();
        ImmutableSortedSet<GetGrantableResult.User> users = allUsers.stream()
                .map(oreSiUserEntity -> new GetGrantableResult.User(oreSiUserEntity.getId(), oreSiUserEntity.getLogin()))
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetGrantableResult.User::getLabel)));
        return users;
    }

    private ImmutableSortedSet<GetGrantableResult.AuthorizationScope> getAuthorizationScopes(Application application, String dataType) {
        ImmutableMap<VariableComponentKey, ReferenceLineChecker> referenceLineCheckers = checkerFactory.getReferenceLineCheckers(application, dataType);
        return Optional.of(application.getConfiguration().getDataTypes().get(dataType))
                .map(Configuration.DataTypeDescription::getAuthorization)
                .map(Configuration.AuthorizationDescription::getAuthorizationScopes)
                .map(authorizationScopes -> authorizationScopes.entrySet().stream()
                        .map(
                                authorizationScopeEntry -> {
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
                        .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetGrantableResult.AuthorizationScope::getId)))
                )
                .orElseGet(ImmutableSortedSet::of);
    }

    private GetGrantableResult.AuthorizationScope.Option toOption(HierarchicalReferenceAsTree tree, ReferenceValue referenceValue) {
        ImmutableSortedSet<GetGrantableResult.AuthorizationScope.Option> options = tree.getChildren(referenceValue).stream()
                .map(child -> toOption(tree, child))
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetGrantableResult.AuthorizationScope.Option::getId)));
        return new GetGrantableResult.AuthorizationScope.Option(referenceValue.getHierarchicalKey().getSql(), referenceValue.getHierarchicalKey().getSql(), options);
    }

    public OreSiUserResult deleteRoleUser(OreSiRoleForUser roleForUser) {
        if (OreSiRole.superAdmin().getAsSqlRole().equals(roleForUser.getRole())) {
            return deleteAdminRoleUser(roleForUser);
        } else if (OreSiRole.applicationCreator().getAsSqlRole().equals(roleForUser.getRole())) {
            return deleteApplicationCreatorRoleUser(roleForUser);
        }
        throw new BadRoleException("cantDeleteRole", roleForUser.getRole());
    }

    private OreSiUserResult deleteApplicationCreatorRoleUser(OreSiRoleForUser oreSiUserRoleApplicationCreator) {
        boolean canAddApplicationCreatorRole = canAddApplicationCreatorRole(oreSiUserRoleApplicationCreator);
        if (canAddApplicationCreatorRole) {
            final OreSiUser user = authenticationService.deleteUserRightCreateApplication(UUID.fromString(oreSiUserRoleApplicationCreator.getUserId()), oreSiUserRoleApplicationCreator.getApplicationPattern());
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiUserRoleApplicationCreator.getUserId()));
        }
        throw new NotSuperAdminException();
    }

    private boolean canAddApplicationCreatorRole(OreSiRoleForUser oreSiUserRoleApplicationCreator) {
        boolean canAddApplicationCreatorRole = false;
        if (authenticationService.hasRole(OreSiRole.superAdmin())) {
            canAddApplicationCreatorRole = true;
        } else if (authenticationService.hasRole(OreSiRole.applicationCreator())) {
            final OreSiUser user = userRepository.findByLogin(oreSiUserRoleApplicationCreator.getUserId()).orElseGet(() -> userRepository.findById(UUID.fromString(oreSiUserRoleApplicationCreator.getUserId())));
            ;
            if (user.getAuthorizations().stream()
                    .anyMatch(p -> Pattern.compile(p)
                            .matcher(oreSiUserRoleApplicationCreator.getApplicationPattern())
                            .matches()
                    )) {
                canAddApplicationCreatorRole = true;
            } else {
                throw new NotApplicationCreatorRightsException(oreSiUserRoleApplicationCreator.getApplicationPattern(), user.getAuthorizations());
            }

        }
        return canAddApplicationCreatorRole;
    }

    private OreSiUserResult deleteAdminRoleUser(OreSiRoleForUser oreSiRoleForUserAdmin) {
        boolean canAddsupeadmin = false;
        if (authenticationService.hasRole(OreSiRole.superAdmin())) {
            OreSiUser user = authenticationService.deleteUserRightSuperadmin(UUID.fromString(oreSiRoleForUserAdmin.getUserId()));
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiRoleForUserAdmin.getUserId()));
        }
        throw new NotSuperAdminException();
    }

    public OreSiUserResult addRoleUser(OreSiRoleForUser roleForUser) {
        if (OreSiRole.superAdmin().getAsSqlRole().equals(roleForUser.getRole())) {
            return addAdminRoleUser(roleForUser);
        } else if (OreSiRole.applicationCreator().getAsSqlRole().equals(roleForUser.getRole())) {
            return addApplicationCreatorRoleUser(roleForUser);
        }
        throw new BadRoleException("cantSetRole", roleForUser.getRole());
    }

    private OreSiUserResult addApplicationCreatorRoleUser(OreSiRoleForUser oreSiUserRoleApplicationCreator) {
        boolean canAddApplicationCreatorRole = canAddApplicationCreatorRole(oreSiUserRoleApplicationCreator);
        if (canAddApplicationCreatorRole) {
            final OreSiUser user = authenticationService.addUserRightCreateApplication(UUID.fromString(oreSiUserRoleApplicationCreator.getUserId()), oreSiUserRoleApplicationCreator.getApplicationPattern());
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiUserRoleApplicationCreator.getUserId()));
        }
        throw new NotSuperAdminException();
    }

    private OreSiUserResult addAdminRoleUser(OreSiRoleForUser oreSiRoleForUserAdmin) {
        boolean canAddsupeadmin = false;
        if (authenticationService.hasRole(OreSiRole.superAdmin())) {
            OreSiUser user = authenticationService.addUserRightSuperadmin(UUID.fromString(oreSiRoleForUserAdmin.getUserId()));
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiRoleForUserAdmin.getUserId()));
        }
        throw new NotSuperAdminException();
    }


    public AuthorizationsResult getAuthorizationsForUser(String applicationNameOrUuid, String dataType, String userLoginOrId) {
        final OreSiUser user = userRepository.findByLogin(userLoginOrId).orElseGet(() -> userRepository.findById(UUID.fromString(userLoginOrId)));
        if (user == null) {
            throw new SiOreIllegalArgumentException("unknown_user", Map.of("login", userLoginOrId));
        }
        final boolean isAdministratorForDatatype = user.getAuthorizations().stream().anyMatch(s -> Pattern.compile(s).matcher(dataType).matches());

        final CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForRole(user.getId().toString());
        final Application application = repository.application().findApplication(applicationNameOrUuid);
        final List<OreSiAuthorization> publicAuthorizations = repository.getRepository(application.getId()).authorization().findPublicAuthorizations();
        final List<OreSiAuthorization> authorizations = repository.getRepository(application.getId()).authorization().findAuthorizations(UUID.fromString(rolesForCurrentUser.getCurrentUser()), application, dataType);
        Map<OperationType, List<AuthorizationParsed>> authorizationMap = new HashMap<>();
        List<String> attributes = application.getConfiguration().getRequiredAuthorizationsAttributes().stream().collect(Collectors.toList());
        authorizations.stream()
                .forEach(authorizationList -> {
                    authorizationList.getAuthorizations().entrySet()
                            .forEach(entry -> {
                                final OperationType key = entry.getKey();
                                entry.getValue().stream()
                                        .map(authorization -> new AuthorizationParsed(
                                                authorization.getPath(attributes),
                                                authorization.getDataGroups(),
                                                authorization.getRequiredAuthorizations().entrySet().stream()
                                                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getSql())),
                                                authorization.getTimeScope() == null || !authorization.getTimeScope().getRange().hasLowerBound() ? null : authorization.getTimeScope().getRange().lowerEndpoint().toLocalDate(),
                                                authorization.getTimeScope() == null || !authorization.getTimeScope().getRange().hasUpperBound() ? null : authorization.getTimeScope().getRange().upperEndpoint().toLocalDate()
                                        )).
                                        forEach(authorizationResult -> authorizationMap
                                                .computeIfAbsent(key, k -> new LinkedList<>())
                                                .add(authorizationResult));

                            });
                });
        final Map<OperationType, Map<String, List<AuthorizationParsed>>> authorizationByPath = Stream.concat(publicAuthorizations.stream(), authorizations.stream())
                .map(OreSiAuthorization::getAuthorizations)
                .flatMap(m -> m.entrySet().stream())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(authorization -> new AuthorizationParsed(
                                        authorization.getPath(attributes),
                                        authorization.getDataGroups(),
                                        authorization.getRequiredAuthorizations().entrySet().stream()
                                                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getSql())),
                                        authorization.getTimeScope() == null || !authorization.getTimeScope().getRange().hasLowerBound() ? null : authorization.getTimeScope().getRange().lowerEndpoint().toLocalDate(),
                                        authorization.getTimeScope() == null || !authorization.getTimeScope().getRange().hasUpperBound() ? null : authorization.getTimeScope().getRange().upperEndpoint().toLocalDate()
                                ))
                                .collect(Collectors.toMap(
                                        AuthorizationParsed::getPath,
                                        k -> {
                                            final LinkedList<AuthorizationParsed> authorizationParseds = new LinkedList<>();
                                            authorizationParseds.add(k);
                                            return authorizationParseds;
                                        })),
                        this::mergeAuthorizationsParsedMap
                ));
        return new AuthorizationsResult(authorizationMap, application.getName(), dataType, authorizationByPath, isAdministratorForDatatype);
    }

    private Map<String, List<AuthorizationParsed>> mergeAuthorizationsParsedMap(Map<String, List<AuthorizationParsed>> a, Map<String, List<AuthorizationParsed>> b) {
        for (String s : a.keySet()) {
            if (b.containsKey(s)) {
                List<AuthorizationParsed> auths = new LinkedList<>();
                for (AuthorizationParsed authorizationParsedA : a.get(s)) {
                    for (AuthorizationParsed authorizationParsedB : b.get(s)) {
                        LocalDate from = null;
                        LocalDate to = null;
                        if (authorizationParsedA.getDataGroups().equals(authorizationParsedB.getDataGroups())) {
                            if (authorizationParsedA.getFromDay() == null) {
                                if (authorizationParsedA.getToDay() == null) {
                                    auths.add(authorizationParsedA);
                                } else if (authorizationParsedB.getFromDay() == null || authorizationParsedB.getFromDay().isBefore(authorizationParsedA.getToDay())) {
                                    to = authorizationParsedB.getToDay() == null ?
                                            null :
                                            (authorizationParsedB.getToDay().isAfter(authorizationParsedA.getToDay()) ? authorizationParsedB.getToDay() : authorizationParsedA.getToDay());
                                    auths.add(new AuthorizationParsed(s, authorizationParsedA.getDataGroups(), authorizationParsedA.getRequiredAuthorizations(), from, to));
                                } else {
                                    auths.add(authorizationParsedA);
                                    auths.add(authorizationParsedB);
                                }
                            } else if (authorizationParsedB.getFromDay() == null) {
                                if (authorizationParsedB.getToDay() == null) {
                                    auths.add(authorizationParsedB);
                                } else if (authorizationParsedA.getFromDay().isBefore(authorizationParsedB.getToDay())) {
                                    to = authorizationParsedA.getToDay() == null ?
                                            null :
                                            (authorizationParsedB.getToDay().isAfter(authorizationParsedA.getToDay()) ? authorizationParsedB.getToDay() : authorizationParsedA.getToDay());
                                    auths.add(new AuthorizationParsed(s, authorizationParsedA.getDataGroups(), authorizationParsedA.getRequiredAuthorizations(), from, to));
                                } else {
                                    auths.add(authorizationParsedA);
                                    auths.add(authorizationParsedB);
                                }
                            } else {
                                auths.add(authorizationParsedA);
                                auths.add(authorizationParsedB);
                            }
                        }
                    }
                }
                b.put(s, auths);
            } else {
                b.put(s, a.get(s));
            }
        }
        return b;

    }
}