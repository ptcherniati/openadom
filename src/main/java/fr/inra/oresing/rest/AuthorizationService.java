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
import fr.inra.oresing.rest.exceptions.authentication.*;
import fr.inra.oresing.rest.exceptions.role.BadRoleException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;
import org.testcontainers.shaded.com.google.common.base.Preconditions;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
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

    @Autowired
    private OreSiApiRequestContext request;

    public void updateRoleForManagement(Set<UUID> previousUsers, OreSiAuthorization modifiedAuthorization) {
        final UpdateRolesOnManagement updateRolesOnManagement = new UpdateRolesOnManagement(repository, db, authenticationService);
        updateRolesOnManagement.init(previousUsers, modifiedAuthorization);
        updateRolesOnManagement.updateRoleForManagement();
    }

    public void updateRoleForReferenceManagement(Set<UUID> previousUsers, OreSiReferenceAuthorization modifiedAuthorization) {
        final UpdateRolesOnReferencesManagement updateRolesOnManagement = new UpdateRolesOnReferencesManagement(repository, db, authenticationService);
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

    /**
     * create a role as a reader on application
     *
     * @param previousAuthorization The authorization that does not yet have an identifier
     * @param modifiedAuthorization The new authorization created from the previous Authorization information
     * @return the existing role for modifiedAuthorization
     */
    public OreSiRightOnApplicationRole createRoleForAuthorization(CreateReferenceAuthorizationRequest previousAuthorization, OreSiReferenceAuthorization modifiedAuthorization) {
        UUID created = modifiedAuthorization.getId();
        Application application = repository.application().findApplication(previousAuthorization.getApplicationNameOrId());
        OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, created);
        OreSiRightOnApplicationRole readerRoleOnApplication = OreSiRightOnApplicationRole.readerOn(application);
        db.createRole(oreSiRightOnApplicationRole);
        db.addUserInRole(oreSiRightOnApplicationRole, readerRoleOnApplication);
        return oreSiRightOnApplicationRole;
    }

    public List<OreSiAuthorization> findUserAuthorizationsForApplication(Application application) {
        final UUID currentUserId = request.getRequestClient().getId();
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        return authorizationRepository.findAuthorizationsByUserId(currentUserId);
    }

    public List<OreSiReferenceAuthorization> findUserReferencesAuthorizationsForApplication(Application application) {
        final UUID currentUserId = request.getRequestClient().getId();
        AuthorizationReferencesRepository authorizationRepository = repository.getRepository(application).authorizationReferences();
        return authorizationRepository.findAuthorizations(currentUserId, application);
    }

    public List<OreSiReferenceAuthorization> findUserReferencesAuthorizationsForApplicationAndDataType(Application application) {
        final UUID currentUserId = request.getRequestClient().getId();
        AuthorizationReferencesRepository authorizationRepository = repository.getRepository(application).authorizationReferences();
        return authorizationRepository.findAuthorizations(currentUserId, application);
    }

    public OreSiAuthorization addAuthorization(Application application, CreateAuthorizationRequest authorizations, List<OreSiAuthorization> authorizationsForCurrentUser, boolean isApplicationCreator) {
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        OreSiAuthorization entity = authorizations.getUuid() == null ?
                new OreSiAuthorization()
                : authorizationRepository.findById(authorizations.getUuid());

        Map<String, Map<OperationType, List<Authorization>>> authorizationsByType = authorizations.getAuthorizations();

        Preconditions.checkArgument(
                authorizationsByType.keySet().stream()
                        .allMatch(dataType -> application.getConfiguration().getDataTypes().containsKey(dataType)));

        final Map<String, Configuration.AuthorizationDescription> authorizationDescriptionByDatatype = application.getConfiguration().getDataTypes()
                .entrySet().stream()
                .filter(e -> e.getValue().getAuthorization() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getAuthorization()));

        final Map<String, List<Authorization>> authorizationListForCurrentUserByDatatype = new HashMap<>();
        for (OreSiAuthorization oreSiAuthorization : authorizationsForCurrentUser) {
            final Map<String, Map<OperationType, List<Authorization>>> oreSiAuthorizationByDatatype = oreSiAuthorization.getAuthorizations();
            for (Map.Entry<String, Map<OperationType, List<Authorization>>> entry : oreSiAuthorizationByDatatype.entrySet()) {
                final String dataType = entry.getKey();
                final Map<OperationType, List<Authorization>> authorizationsbyType = entry.getValue();
                if (authorizationsbyType.containsKey(OperationType.admin)) {
                    authorizationListForCurrentUserByDatatype
                            .computeIfAbsent(dataType, k -> new LinkedList<>())
                            .addAll(authorizationsbyType.get(OperationType.admin));
                }
            }
        }

        final Map<String, Map<OperationType, List<Authorization>>> modifiedAuthorizationsByDatatype = authorizationsByType.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().entrySet().stream()
                        .map(authByTypeEntry -> {
                            if (!isApplicationCreator) {
                                removeAuthorizationThatCantBeModified(authByTypeEntry, authorizationListForCurrentUserByDatatype.get(e.getKey()));
                            }
                            return authByTypeEntry;
                        })
                        .filter(authByType -> {
                            try {
                                testAuthorizationArguments(authorizationDescriptionByDatatype.get(e.getKey()), authByType);
                                return true;
                            } catch (IllegalArgumentException illegalArgumentExceptione) {
                                return false;
                            }
                        })
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
        if (!isApplicationCreator) {
            authorizationListForCurrentUserByDatatype.entrySet().stream()
                    .forEach(stringListEntry -> addStoredAuthorizationThatCantBeModified(
                            entity,
                            stringListEntry.getKey(),
                            authorizationListForCurrentUserByDatatype.get(stringListEntry.getKey()),
                            modifiedAuthorizationsByDatatype.get(stringListEntry.getKey())));
        }
        entity.setName(authorizations.getName());
        entity.setOreSiUsers(authorizations.getUsersId());
        entity.setApplication(application.getId());
        entity.setAuthorizations(authorizations.getAuthorizations());
        authorizationRepository.store(entity);
        return entity;
    }

    public OreSiReferenceAuthorization addReferenceAuthorizations(Application application, CreateReferenceAuthorizationRequest authorizations, List<OreSiReferenceAuthorization> authorizationsForCurrentUser, boolean isApplicationCreator) {
        AuthorizationReferencesRepository authorizationReferencesRepository = repository.getRepository(application).authorizationReferences();
        OreSiReferenceAuthorization entity = authorizations.getUuid() == null ?
                new OreSiReferenceAuthorization()
                : authorizationReferencesRepository.findById(authorizations.getUuid());

        Map<OperationReferenceType, List<String>> authorizationsByType = authorizations.getReferences();

        for (List<String> references : authorizationsByType.values()) {
            for (String reference : references) {
                Preconditions.checkArgument(application.getConfiguration().getReferences().containsKey(reference));
            }
        }

        final Set<String> authorizationListForCurrentUser = authorizationsForCurrentUser.stream()
                .map(oreSiAuthorization -> oreSiAuthorization.getReferences())
                .filter(operationTypeListMap -> operationTypeListMap.containsKey(OperationReferenceType.admin))
                .map(operationTypeListMap -> operationTypeListMap.get(OperationReferenceType.admin))
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        final Map<OperationReferenceType, List<String>> modifiedAuthorizations = authorizationsByType.entrySet().stream()
                .map(authByTypeEntry -> {
                    if (!isApplicationCreator) {
                        removeAuthorizationReferencesThatCantBeModified(authByTypeEntry, authorizationListForCurrentUser);
                    }
                    return authByTypeEntry;
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!isApplicationCreator) {
            addStoredAuthorizationReferencesThatCantBeModified(entity, authorizationListForCurrentUser, modifiedAuthorizations);
        }
        entity.setName(authorizations.getName());
        entity.setOreSiUsers(authorizations.getUsersId());
        entity.setApplication(application.getId());
        entity.setReferences(authorizations.getReferences());
        authorizationReferencesRepository.store(entity);
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

    private void removeAuthorizationReferencesThatCantBeModified(Map.Entry<OperationReferenceType, List<String>> authByTypeEntry, Set<String> authorizationListForCurrentUser) {
        final List<String> collect = authByTypeEntry.getValue().stream()
                .filter(reference -> authorizationListForCurrentUser.contains(reference))
                .collect(Collectors.toList());
        authByTypeEntry.setValue(collect);
    }

    private void addStoredAuthorizationThatCantBeModified(OreSiAuthorization entity, String datatype, List<Authorization> authorizationListForCurrentUser, Map<OperationType, List<Authorization>> modifiedAuthorizations) {
        Optional.ofNullable(entity)
                .map(e -> e.getAuthorizations())
                .map(map -> map.computeIfAbsent(datatype, k -> new HashMap<>()))
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

    private void addStoredAuthorizationReferencesThatCantBeModified(OreSiReferenceAuthorization entity, Set<String> authorizationListForCurrentUser, Map<OperationReferenceType, List<String>> modifiedAuthorizations) {
        Optional.ofNullable(entity)
                .map(e -> e.getReferences())
                .ifPresent(a -> a.entrySet().stream()
                        .forEach(authByTypeEntry -> {
                            final List<String> collect = authByTypeEntry.getValue().stream()
                                    .filter(authorizationListForCurrentUser::contains)
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
        List<OreSiAuthorization> authorizationsForCurrentUser = findUserAuthorizationsForApplication(application);
        if (!isApplicationCreator && !authorizationsForCurrentUser.stream().anyMatch(
                a -> !a.getAuthorizations().get(OperationType.admin).isEmpty()
        )) {
            throw new NotApplicationCanSetRightsException(application.getName());
        }
        OreSiAuthorization oreSiAuthorization = repository.getRepository(application).authorization().findById(revokeAuthorizationRequest.getAuthorizationId());
        final Map<String, List<Authorization>> authorizationListForCurrentUserByDatatype = new HashMap<>();
        for (OreSiAuthorization authorization : authorizationsForCurrentUser) {
            for (Map.Entry<String, Map<OperationType, List<Authorization>>> entry : authorization.getAuthorizations().entrySet()) {
                String datatype = entry.getKey();
                final Map<OperationType, List<Authorization>> operationTypeListMap = entry.getValue();
                authorizationListForCurrentUserByDatatype
                        .computeIfAbsent(datatype, k -> new LinkedList<>())
                        .addAll(operationTypeListMap.get(OperationType.admin));
            }
        }
        final Map<String, Configuration.AuthorizationDescription> authorizationDescriptionByDatatype = application.getConfiguration().getDataTypes().entrySet()
                .stream()
                .filter(entry -> entry.getValue().getAuthorization() != null)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getAuthorization()));


        final Map<String, Map<OperationType, List<Authorization>>> filteredAuthorizationsByDatatype = oreSiAuthorization.getAuthorizations().entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey,
                        k -> k.getValue().entrySet().stream()
                                .map(authByTypeEntry -> {
                                    String datatype = k.getKey();
                                    if (!isApplicationCreator) {
                                        final boolean canRemoveEntry = authByTypeEntry.getValue().stream()
                                                .allMatch(authorization -> testCanSetAuthorization(authorization, authorizationListForCurrentUserByDatatype.get(datatype)));
                                        if (!canRemoveEntry) {
                                            throw new NotApplicationCanDeleteRightsException(application.getName(), datatype);
                                        }
                                    }
                                    return authByTypeEntry;
                                })
                                .map(authByType -> {
                                    String datatype = k.getKey();
                                    try {
                                        testAuthorizationArguments(authorizationDescriptionByDatatype.get(datatype), authByType);
                                    } catch (IllegalArgumentException e) {
                                        throw new NotApplicationCanDeleteRightsException(application.getName(), datatype);
                                    }
                                    return authByType;
                                })
                                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue))));
        if (filteredAuthorizationsByDatatype.isEmpty()) {
            return null;
        }
        return updateRolesOnManagement.revoke(revokeAuthorizationRequest);
    }

    public UUID revokeReferencesAuthorization(String applicationNameOrId, UUID authorizationId) {
        final UpdateRolesOnReferencesManagement updateRolesOnManagement = new UpdateRolesOnReferencesManagement(repository, db, authenticationService);
        final Application application = oreSiService.getApplication(applicationNameOrId);
        final CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForCurrentUser();
        final boolean isApplicationCreator = rolesForCurrentUser.getMemberOf().contains(OreSiRightOnApplicationRole.adminOn(application).getAsSqlRole());
        final UUID requestUserId = request.getRequestUserId();
        List<OreSiReferenceAuthorization> authorizationsForCurrentUser = findUserReferencesAuthorizationsForApplication(application);
        if (!isApplicationCreator && !authorizationsForCurrentUser.stream().anyMatch(
                a -> !a.getReferences().get(OperationType.admin).isEmpty()
        )) {
            throw new NotApplicationCanSetRightsReferencesException(application.getName());
        }
        OreSiReferenceAuthorization oreSiAuthorization = repository.getRepository(application).authorizationReferences().findById(authorizationId);
        final List<String> authorizationListForCurrentUser = authorizationsForCurrentUser.stream()
                .map(authorization -> authorization.getReferences())
                .filter(operationTypeListMap -> operationTypeListMap.containsKey(OperationType.admin))
                .map(operationTypeListMap -> operationTypeListMap.get(OperationType.admin))
                .flatMap(List::stream)
                .collect(Collectors.toList());

        final Map<OperationReferenceType, List<String>> filteredAuthorizations = oreSiAuthorization.getReferences().entrySet().stream()
                .map(authByTypeEntry -> {
                    if (!isApplicationCreator) {
                        final boolean canRemoveEntry = authByTypeEntry.getValue().stream()
                                .allMatch(authorization -> authorizationListForCurrentUser.contains(authorization));
                        if (!canRemoveEntry) {
                            throw new NotApplicationCanDeleteReferencesRightsException(application.getName(), authorizationListForCurrentUser);
                        }
                    }
                    return authByTypeEntry;
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (filteredAuthorizations.isEmpty()) {
            return null;
        }
        return updateRolesOnManagement.revoke(application, authorizationId);
    }

    public ImmutableSet<GetAuthorizationResult> getAuthorizations(String applicationNameOrId, AuthorizationsResult authorizationsForUser) {
        Application application = repository.application().findApplication(applicationNameOrId);
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        final List<OreSiAuthorization> publicAuthorizations = authorizationRepository.findPublicAuthorizations();
        return authorizationRepository.findAll().stream()
                .map(oreSiAuthorization -> toGetAuthorizationResult(oreSiAuthorization, publicAuthorizations, authorizationsForUser))
                .collect(ImmutableSet.toImmutableSet());
    }

    public ImmutableSet<GetAuthorizationReferencesResult> getReferencesAuthorizations(String applicationNameOrId, AuthorizationsReferencesResult authorizationsForUser, MultiValueMap<String, String> params) {
        Application application = repository.application().findApplication(applicationNameOrId);
        ImmutableSortedSet<GetGrantableResult.User> users = getGrantableUsers();
        AuthorizationReferencesRepository authorizationRepository = repository.getRepository(application).authorizationReferences();
        final List<OreSiReferenceAuthorization> publicAuthorizations = authorizationRepository.findPublicAuthorizations();
        long offset = Optional.ofNullable(params)
                .map(map -> map.get("offset"))
                .map(l -> l.isEmpty() ? "0" : l.get(0))
                .map(os -> Long.parseLong(os))
                .orElse(0L);
        long limit = Optional.ofNullable(params)
                .map(map -> map.get("limit"))
                .map(l -> l.isEmpty() ? Long.MAX_VALUE : Long.parseLong(l.get(0)))
                .orElse(Long.MAX_VALUE);
        String user = Optional.ofNullable(params)
                .map(map -> map.get("userId"))
                .map(l -> l.isEmpty() ? (String) null : l.get(0))
                .filter(s -> !"null".equals(s))
                .orElse((String) null);
        String authorizationId = Optional.ofNullable(params)
                .map(map -> map.get("authorizationId"))
                .map(l -> l.isEmpty() ? null : l.get(0))
                .filter(s -> !"null".equals(s))
                .orElse((String) null);

        ImmutableSet<GetAuthorizationReferencesResult> authorizations = authorizationRepository.findAll().stream()
                .skip(offset)
                .limit(limit)
                .filter(oreSiReferenceAuthorization ->
                        (user == null || oreSiReferenceAuthorization.getOreSiUsers().stream().anyMatch(uuid -> uuid.toString().equals(user)))
                                && (authorizationId == null || oreSiReferenceAuthorization.getId().toString().equals(authorizationId))
                )
                .map(oreSiAuthorization -> toGetReferencesAuthorizationResult(oreSiAuthorization, publicAuthorizations, authorizationsForUser))
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
        final Map<String, Map<OperationType, List<Authorization>>> collectPublicAuthorizations = collectPublicAuthorizations(publicAuthorizations);
        return new GetAuthorizationResult(
                oreSiAuthorization.getId(),
                oreSiAuthorization.getName(),
                getOreSIUSers(all, oreSiAuthorization.getOreSiUsers()),
                oreSiAuthorization.getApplication(),
                extractTimeRangeToFromAndTo(oreSiAuthorization.getAuthorizations()),
                collectPublicAuthorizations,
                authorizationsForUser
        );
    }

    private static Map<String, Map<OperationType, List<Authorization>>> collectPublicAuthorizations(List<OreSiAuthorization> publicAuthorizations) {
        final Map<String, Map<OperationType, List<Authorization>>> collectPublicAuthorizations = new HashMap<>();
        for (OreSiAuthorization publicAuthorization : publicAuthorizations) {
            for (Map.Entry<String, Map<OperationType, List<Authorization>>> entry : publicAuthorization.getAuthorizations().entrySet()) {
                String datatype = entry.getKey();
                final Map<OperationType, List<Authorization>> operationTypeListMap = collectPublicAuthorizations
                        .computeIfAbsent(datatype, k -> new HashMap<>());
                publicAuthorizations.stream()
                        .map(pa -> pa.getAuthorizations().get(datatype))
                        .forEach(map -> map.entrySet().forEach(operationTypeListEntry ->
                                operationTypeListMap
                                        .computeIfAbsent(operationTypeListEntry.getKey(), k -> new LinkedList<>())
                                        .addAll(operationTypeListEntry.getValue())));
            }
        }
        return collectPublicAuthorizations;
    }

    private GetAuthorizationReferencesResult toGetReferencesAuthorizationResult(OreSiReferenceAuthorization oreSiAuthorization, List<OreSiReferenceAuthorization> publicAuthorizations, AuthorizationsReferencesResult authorizationsForUser) {
        List<OreSiUser> all = userRepository.findAll();
        final Map<OperationReferenceType, List<String>> userReferences = authorizationsForUser.getAuthorizationResults();
        final boolean isAdministrator = authorizationsForUser.getIsAdministrator();
        final Map<OperationReferenceType, List<String>> references = oreSiAuthorization.getReferences().entrySet().stream()
                .filter(operationReferenceTypeListEntry -> isAdministrator || userReferences.containsKey(OperationReferenceType.admin))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new GetAuthorizationReferencesResult(
                oreSiAuthorization.getId(),
                oreSiAuthorization.getName(),
                getOreSIUSers(all, oreSiAuthorization.getOreSiUsers()),
                oreSiAuthorization.getApplication(),
                references
        );
    }

    private Set<OreSiUser> getOreSIUSers(List<OreSiUser> users, Set<UUID> usersId) {
        return users.stream()
                .filter(oreSiUser -> usersId.contains(oreSiUser.getId()))
                .collect(Collectors.toSet());
    }


    private Map<String, Map<OperationType, List<AuthorizationParsed>>> extractTimeRangeToFromAndTo(Map<String, Map<OperationType, List<Authorization>>> authorizations) {
        Map<String, Map<OperationType, List<AuthorizationParsed>>> transformedAuthorizations = new HashMap<>();
        for (Map.Entry<String, Map<OperationType, List<Authorization>>> authorizationEntry : authorizations.entrySet()) {
            String datatype = authorizationEntry.getKey();
            for (Map.Entry<OperationType, List<Authorization>> operationTypeListEntry : authorizationEntry.getValue().entrySet()) {
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
                    transformedAuthorizations
                            .computeIfAbsent(datatype, k -> new HashMap<>())
                            .put(operationTypeListEntry.getKey(), authorizationsParsed);
                }
            }
        }
        return transformedAuthorizations;
    }

    public GetGrantableResult getGrantable(String applicationNameOrId, AuthorizationsResult authorizationsForUser) {
        Application application = repository.application().findApplication(applicationNameOrId);
        Configuration configuration = application.getConfiguration();
        ImmutableSortedSet<GetGrantableResult.User> users = getGrantableUsers();
        AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        final Map<String, Map<OperationType, List<Authorization>>> publicAuthorizations = collectPublicAuthorizations(authorizationRepository.findPublicAuthorizations());
        Preconditions.checkArgument(application.getDataType().stream()
                .allMatch(dataType -> configuration.getDataTypes().containsKey(dataType)));
        final Map<String, Set<GetGrantableResult.DataGroup>> dataGroups = application.getDataType().stream()
                .collect(Collectors.toMap(Function.identity(), dataType -> getDataGroups(application, dataType)));
        final Map<String, Set<GetGrantableResult.AuthorizationScope>> authorizationScopes = application.getDataType().stream()
                .collect(Collectors.toMap(Function.identity(), dataType -> getAuthorizationScopes(application, dataType)));
        final Map<String, SortedMap<String, GetGrantableResult.ColumnDescription>> columnDescriptions = application.getDataType().stream()
                .collect(Collectors.toMap(Function.identity(), dataType -> getColumnDescripton(configuration, dataType)));

        return new GetGrantableResult(
                users,
                dataGroups,
                authorizationScopes,
                columnDescriptions,
                authorizationsForUser,
                publicAuthorizations
        );
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

    public ImmutableSortedSet<GetGrantableResult.User> getGrantableUsers() {
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


    public AuthorizationsResult getAuthorizationsForUser(String applicationNameOrUuid, String userLoginOrId) {
        final OreSiUser user = userRepository.findByLogin(userLoginOrId).orElseGet(() -> userRepository.findById(UUID.fromString(userLoginOrId)));
        if (user == null) {
            throw new SiOreIllegalArgumentException("unknown_user", Map.of("login", userLoginOrId));
        }
        final Application application = repository.application().findApplication(applicationNameOrUuid);
        final boolean isAdministrator = user.getAuthorizations().stream().anyMatch(s -> Pattern.compile(s).matcher(application.getName()).matches());

        final CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForRole(user.getId().toString());
        final List<OreSiAuthorization> publicAuthorizations = repository.getRepository(application.getId()).authorization().findPublicAuthorizations();
        final List<OreSiAuthorization> authorizations = repository.getRepository(application.getId()).authorization()
                .findAuthorizationsByUserId(UUID.fromString(rolesForCurrentUser.getCurrentUser()));
        final Map<String, Map<OperationType, List<AuthorizationParsed>>> authorizationMapByDatatype = new HashMap<>();
        final Map<String, Map<OperationType, Map<String, List<AuthorizationParsed>>>> authorizationByDatatypeAndPath = new HashMap<>();
        List<String> attributes = application.getConfiguration().getRequiredAuthorizationsAttributes().stream().collect(Collectors.toList());
        for (OreSiAuthorization authorization : authorizations) {
            for (Map.Entry<String, Map<OperationType, List<Authorization>>> authorizationEntry : authorization.getAuthorizations().entrySet()) {
                String datatype = authorizationEntry.getKey();
                authorizationEntry.getValue().entrySet()
                        .forEach(entry -> {
                            final OperationType key = entry.getKey();
                            entry.getValue().stream()
                                    .map(authorizationToParse -> new AuthorizationParsed(
                                            authorizationToParse.getPath(attributes),
                                            authorizationToParse.getDataGroups(),
                                            authorizationToParse.getRequiredAuthorizations().entrySet().stream()
                                                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getSql())),
                                            authorizationToParse.getTimeScope() == null || !authorizationToParse.getTimeScope().getRange().hasLowerBound() ? null : authorizationToParse.getTimeScope().getRange().lowerEndpoint().toLocalDate(),
                                            authorizationToParse.getTimeScope() == null || !authorizationToParse.getTimeScope().getRange().hasUpperBound() ? null : authorizationToParse.getTimeScope().getRange().upperEndpoint().toLocalDate()
                                    )).
                                    forEach(authorizationResult -> authorizationMapByDatatype
                                            .computeIfAbsent(datatype, k -> new HashMap<>())
                                            .computeIfAbsent(key, k -> new LinkedList<>())
                                            .add(authorizationResult));

                        });
                authorizationEntry.getValue().entrySet()
                        .forEach(entry -> {
                            final OperationType key = entry.getKey();
                            entry.getValue().stream()
                                    .map(authorizationToParse -> new AuthorizationParsed(
                                            authorizationToParse.getPath(attributes),
                                            authorizationToParse.getDataGroups(),
                                            authorizationToParse.getRequiredAuthorizations().entrySet().stream()
                                                    .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().getSql())),
                                            authorizationToParse.getTimeScope() == null || !authorizationToParse.getTimeScope().getRange().hasLowerBound() ? null : authorizationToParse.getTimeScope().getRange().lowerEndpoint().toLocalDate(),
                                            authorizationToParse.getTimeScope() == null || !authorizationToParse.getTimeScope().getRange().hasUpperBound() ? null : authorizationToParse.getTimeScope().getRange().upperEndpoint().toLocalDate()
                                    ))
                                    .forEach(authorizationParsed -> {
                                        authorizationByDatatypeAndPath
                                                .computeIfAbsent(datatype, k -> new HashMap<>())
                                                .computeIfAbsent(key, k -> new HashMap<>())
                                                .computeIfAbsent(authorizationParsed.getPath(), k -> new LinkedList<>())
                                                .add(authorizationParsed);
                                    });
                        });
            }
        }
        return new AuthorizationsResult(authorizationMapByDatatype, application.getName(), authorizationByDatatypeAndPath, isAdministrator);
    }


    public AuthorizationsReferencesResult getReferencesAuthorizationsForUser(String applicationNameOrUuid, String userId) {
        final OreSiUser user = userRepository.findByLogin(userId).orElseGet(() -> userRepository.findById(UUID.fromString(userId)));
        if (user == null) {
            throw new SiOreIllegalArgumentException("unknown_user", Map.of("login", userId));
        }
        final Application application = repository.application().findApplication(applicationNameOrUuid);
        final boolean isAdministrator = user.getAuthorizations().stream().anyMatch(s -> Pattern.compile(s).matcher(application.getName()).matches());

        final CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForRole(user.getId().toString());
        final List<OreSiReferenceAuthorization> publicAuthorizations = repository.getRepository(application.getId()).authorizationReferences().findPublicAuthorizations();
        final List<OreSiReferenceAuthorization> authorizations = repository.getRepository(application.getId()).authorizationReferences().findAuthorizations(UUID.fromString(rolesForCurrentUser.getCurrentUser()), application);
        Map<OperationReferenceType, List<String>> authorizationMap = new HashMap<>();
        List<String> attributes = application.getConfiguration().getRequiredAuthorizationsAttributes().stream().collect(Collectors.toList());

        authorizations.stream()
                .forEach(authorizationList -> {
                    authorizationList.getReferences().entrySet()
                            .forEach(entry -> {
                                final OperationReferenceType key = entry.getKey();
                                entry.getValue().stream().
                                        forEach(authorizationResult -> authorizationMap
                                                .computeIfAbsent(key, k -> new LinkedList<>())
                                                .add(authorizationResult));

                            });
                });
        return new AuthorizationsReferencesResult(authorizationMap, application.getName(), isAdministrator);
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