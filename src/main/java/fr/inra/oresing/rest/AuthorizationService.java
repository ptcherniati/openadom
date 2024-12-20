package fr.inra.oresing.rest;

import com.google.common.base.Preconditions;
import com.google.common.collect.*;
import fr.inra.oresing.domain.*;
import fr.inra.oresing.domain.additionalfiles.AuthorizationsAdditionalFilesResult;
import fr.inra.oresing.domain.additionalfiles.OreSiAdditionalFileAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.*;
import fr.inra.oresing.domain.application.configuration.Authorization;
import fr.inra.oresing.domain.authorization.privilegeassessor.*;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomain;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomain;
import fr.inra.oresing.domain.authorization.request.*;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.authentication.authentication.*;
import fr.inra.oresing.domain.exceptions.role.role.BadApplicationRoleException;
import fr.inra.oresing.domain.exceptions.role.role.BadRoleException;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import fr.inra.oresing.domain.repository.data.DataRepositoryForBuffer;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.persistence.data.read.DataRepositoryWithBuffer;
import fr.inra.oresing.rest.data.DataService;
import fr.inra.oresing.rest.model.authorization.*;
import fr.inra.oresing.rest.model.authorization.exception.AuthorizationRequestError;
import fr.inra.oresing.rest.model.authorization.request.AuthorizationRequestBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Component
@Transactional(readOnly = true)
public class AuthorizationService implements fr.inra.oresing.domain.services.authorization.AuthorizationService {

    @Autowired
    DataService referenceService;
    @Autowired
    private SqlService db;
    @Autowired
    private AuthenticationService authenticationService;
    @Autowired
    private OreSiRepository repository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private OreSiApiRequestContext request;
    @Autowired
    private AdditionalFileService additionalFileService;

    private static void testAuthorizationArguments(final Authorization authorizationDescription, final AuthorizationForScope authByType) {
        final Set<String> labels = Optional.ofNullable(authorizationDescription)
                .map(authorization -> authorization.authorizationScope().stream()
                        .map(AuthorizationScopeComponentData::data)
                        .collect(Collectors.toSet()))
                .orElseGet(Set::of);

        if (authByType instanceof AuthorizationNoRestriction) {
            return; // Pas de vérification nécessaire pour AuthorizationNoRestriction
        }

        if (authByType instanceof AuthorizationForReferenceScope authForReferenceScope) {
            Preconditions.checkArgument(labels.containsAll(authForReferenceScope.authorizationScope().keySet()));
        } else if (authByType instanceof AuthorizationForReferenceScopeAndTimeScope authForReferenceScopeAndTimeScope) {
            Preconditions.checkArgument(labels.containsAll(authForReferenceScopeAndTimeScope.authorizationScope().keySet()));
        } else if (authByType instanceof AuthorizationForTimeScope) {
            // Pas de vérification spécifique pour AuthorizationForTimeScope
        } else {
            throw new IllegalArgumentException("Type d'autorisation non reconnu");
        }
    }

    private static void removeAuthorizationAdditionalFilesThatCantBeModified(final Map.Entry<OperationAdditionalFileType, List<String>> authByTypeEntry, final Set<String> authorizationListForCurrentUser) {
        List<String> collect = authByTypeEntry.getValue().stream()
                .filter(authorizationListForCurrentUser::contains)
                .collect(Collectors.toList());
        authByTypeEntry.setValue(collect);
    }

    private static void addStoredAuthorizationReferencesThatCantBeModified(final OreSiReferenceAuthorization entity, final Set<String> authorizationListForCurrentUser, final Map<OperationReferenceType, List<String>> modifiedAuthorizations) {
        Optional.ofNullable(entity)
                .map(OreSiReferenceAuthorization::getReferences)
                .ifPresent(a -> a.forEach((key, value) -> {
                            List<String> collect = value.stream()
                                    .filter(authorizationListForCurrentUser::contains)
                                    .toList();
                            modifiedAuthorizations
                                    .computeIfAbsent(key, k -> new LinkedList<>())
                                    .addAll(collect);
                        })
                );
    }

    private static void addStoredAuthorizationAdditionalFilesThatCantBeModified(final OreSiAdditionalFileAuthorization entity, final Set<String> authorizationListForCurrentUser, final Map<OperationAdditionalFileType, List<String>> modifiedAuthorizations) {
        Optional.ofNullable(entity)
                .map(OreSiAdditionalFileAuthorization::getAdditionalFiles)
                .ifPresent(a -> a.forEach((key, value) -> {
                            List<String> collect = value.stream()
                                    .filter(authorizationListForCurrentUser::contains)
                                    .toList();
                            modifiedAuthorizations
                                    .computeIfAbsent(key, k -> new LinkedList<>())
                                    .addAll(collect);
                        })
                );
    }

    private static boolean testCanSetAuthorization(final AuthorizationForScope authorization, final AuthorizationForScope authorizationAdmin) {
        if (authorizationAdmin instanceof AuthorizationNoRestriction) {
            return true;
        }

        if (authorization instanceof AuthorizationNoRestriction) {
            return false;
        }

        Map<String, List<Ltree>> authorizationScope = authorization.authorizationScope();
        Map<String, List<Ltree>> authorizationAdminScope = authorizationAdmin.authorizationScope();

        if (authorizationScope == null || authorizationAdminScope == null) {
            return false;
        }

        return authorizationScope.entrySet().stream()
                .allMatch(entry -> {
                    String key = entry.getKey();
                    List<Ltree> authLtrees = entry.getValue();
                    List<Ltree> adminLtrees = authorizationAdminScope.get(key);

                    return adminLtrees != null && canSetAllLtrees(authLtrees, adminLtrees);
                });
    }

    private static boolean canSetAllLtrees(List<Ltree> authLtrees, List<Ltree> adminLtrees) {
        return authLtrees.stream()
                .allMatch(authLtree -> isLtreeContainedInAny(authLtree, adminLtrees));
    }

    private static boolean isLtreeContainedInAny(Ltree authLtree, List<Ltree> adminLtrees) {
        return adminLtrees.stream()
                .anyMatch(adminLtree -> isLtreeContainedOrEqual(adminLtree.getSql(), authLtree.getSql()));
    }

    private static Map<String, List<AuthorizationForScope>> collectPublicAuthorizations(final List<OreSiAuthorization> publicAuthorizations) {
        return publicAuthorizations.stream()
                .flatMap(auth -> auth.getAuthorizations().entrySet().stream())
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())
                ));
    }

    private static Set<OreSiUser> getOreSIUSers(final List<OreSiUser> users, final Set<UUID> usersId) {
        return users.stream()
                .filter(oreSiUser -> usersId.contains(oreSiUser.getId()))
                .collect(Collectors.toSet());
    }

    private static ImmutableSortedMap<String, GetGrantableResult.ColumnDescription> getColumnDescription(final Configuration configuration, final String dataName) {
        return ImmutableSortedMap.copyOf(
                GetGrantableResult.COLUMNS_DESCRIPTION
                        .entrySet()
                        .stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                columDescription -> new GetGrantableResult.ColumnDescription(
                                        columDescription.getValue().display(),
                                        columDescription.getValue().title(),
                                        columDescription.getValue().withPeriods(),
                                        columDescription.getValue().withDataGroups(),
                                        columDescription.getValue().forPublic(),
                                        columDescription.getValue().forRequest(),
                                        columDescription.getValue().internationalization()
                                )))
        );
    }

    @Transactional
    public void updateRoleForManagement(
            final Set<UUID> previousUsers,
            final OreSiAuthorization modifiedAuthorization) {
        UpdateRolesOnManagement updateRolesOnManagement = new UpdateRolesOnManagement(repository, db, authenticationService);
        updateRolesOnManagement.init(previousUsers, modifiedAuthorization);
        updateRolesOnManagement.updateRoleForManagement();
    }

    @Transactional
    public void updateRoleForReferenceManagement(final Set<UUID> previousUsers, final OreSiAdditionalFileAuthorization modifiedAuthorization) {
        UpdateRolesOnAdditionalFilesManagement updateRolesOnManagement = new UpdateRolesOnAdditionalFilesManagement(repository, db, authenticationService);
        updateRolesOnManagement.init(previousUsers, modifiedAuthorization);
        updateRolesOnManagement.updateRoleForManagement();
    }

    /**
     * create a role as a reader on application
     *
     * @param previousAuthorization The submissionScope that does not yet have an identifier
     * @param modifiedAuthorization The new submissionScope created from the previous Authorization information
     * @return the existing role for modifiedAuthorization
     */
    @Transactional
    public OreSiRightOnApplicationRole createRoleForAuthorization(final AuthorizationRequest previousAuthorization, final OreSiAuthorization modifiedAuthorization) {
        final UUID created = modifiedAuthorization.getId();
        final Application application = repository.application().findApplication(previousAuthorization.applicationId());
        final OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, created);
        final OreSiRightOnApplicationRole readerRoleOnApplication = OreSiRightOnApplicationRole.readerOn(application);
        db.createRole(oreSiRightOnApplicationRole,
                """
                        role carrying the policies of authorization %1$s for data of the application %2$s""".formatted(
                        modifiedAuthorization.getId(),
                        application.getName()
                ));
        db.addUserInRole(oreSiRightOnApplicationRole, readerRoleOnApplication);
        return oreSiRightOnApplicationRole;
    }

    /**
     * create a role as a reader on application
     *
     * @param previousAuthorization The submissionScope that does not yet have an identifier
     * @param modifiedAuthorization The new submissionScope created from the previous Authorization information
     * @return the existing role for modifiedAuthorization
     */
    @Transactional
    public OreSiRightOnApplicationRole createRoleForAuthorization(final CreateAdditionalFileAuthorizationRequest previousAuthorization, final OreSiAdditionalFileAuthorization modifiedAuthorization) {
        final UUID created = modifiedAuthorization.getId();
        final Application application = findApplication(previousAuthorization);
        final OreSiRightOnApplicationRole oreSiRightOnApplicationRole = OreSiRightOnApplicationRole.managementRole(application, created);
        final OreSiRightOnApplicationRole readerRoleOnApplication = OreSiRightOnApplicationRole.readerOn(application);
        db.createRole(oreSiRightOnApplicationRole,
                """
                        role carrying the policies of authorization %1$s for additionalFiles of the application %2$s""".formatted(
                        modifiedAuthorization.getId(),
                        application.getName()
                ));
        db.addUserInRole(oreSiRightOnApplicationRole, readerRoleOnApplication);
        return oreSiRightOnApplicationRole;
    }

    private Application findApplication(CreateAdditionalFileAuthorizationRequest previousAuthorization) {
        return repository.application().findApplication(previousAuthorization.getApplicationNameOrId());
    }

    public List<OreSiAuthorization> findUserAuthorizationsForApplication(final Application application) {
        UUID currentUserId = request.getRequestClient().id();
        final AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        return authorizationRepository.findAuthorizationsByUserId(currentUserId);
    }

    public record Authorizations(OreSiAuthorization previous, OreSiAuthorization next) {
        public Set<UUID> getPreviousUsers() {
            return Optional.ofNullable(previous()).map(OreSiAuthorization::getOreSiUsers).orElseGet(Set::of);
        }
    }

    @Transactional
    public Authorizations addAuthorization(final Application application,
                                           final AuthorizationRequest authorizationRequest,
                                           final List<OreSiAuthorization> authorizationsForCurrentUser,
                                           final boolean isApplicationCreator) {
        final AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();

        OreSiAuthorization previous = null;
        if (authorizationRequest.authorizationId() != null) {
            previous = authorizationRepository.findById(authorizationRequest.authorizationId());
        }
        final OreSiAuthorization entity = previous == null ?
                new OreSiAuthorization()
                : previous;
        final Map<String, AuthorizationForScope> authorizationsByDataType = authorizationRequest.buildAuthorizationsByDataname();

        Preconditions.checkArgument(
                authorizationsByDataType.keySet().stream()
                        .allMatch(application::existsData));

        entity.setName(authorizationRequest.name());
        entity.setDescription(authorizationRequest.description());
        entity.setOreSiUsers(authorizationRequest.userId());
        entity.setApplication(application.getId());
        entity.setAuthorizations(authorizationsByDataType);
        authorizationRepository.store(entity);
        return new Authorizations(previous, entity);
    }

    // Méthode utilitaire pour vérifier si un Ltree en contient un autre ou est égal
    private static boolean isLtreeContainedOrEqual(String containerLtree, String containedLtree) {
        // Dans PostgreSQL, 'a.b' @> 'a.b.c' est vrai (a.b contient a.b.c)
        // et 'a.b' @> 'a.b' est aussi vrai (égalité)
        return containerLtree.equals(containedLtree) || containedLtree.startsWith(containerLtree + ".");
    }

    /*private void addStoredAuthorizationThatCantBeModified(
            final OreSiAuthorization entity,
            final String datatype,
            final List<AuthorizationForScope> authorizationListForCurrentUser,
            final AuthorizationForScope modifiedAuthorizations) {
        Optional.ofNullable(entity)
                .map(e -> e.getAuthorizations())
                .map(map -> map.computeIfAbsent(datatype, k -> new LinkedList<>()))
                .ifPresent(authorizationForScopes -> authorizationForScopes.stream()
                        .filter(authorization -> {
                            return !testCanSetAuthorization(authorization, authorizationListForCurrentUser);
                        })
                        .forEach(authorizationForScope -> {
                            List<AuthorizationForScope> collect = authByTypeEntry.getValue().stream()
                                    .toList();
                            modifiedAuthorizations
                                    .computeIfAbsent(authByTypeEntry.getKey(), k -> new LinkedList<>())
                                    .addAll(collect);
                        })
                );
    }*/

    public Application getApplication(final String nameOrId) {
        // TODO filtre tag hidden boucle sur les reference et les datatypes
        authenticationService.setRoleForClient();
        // Application result = repo.application().findApplication(nameOrId);
        return repository.application().findApplication(nameOrId);
    }

    @Transactional
    public UUID revoke(final String applicationNameOrid, final AuthorizationRequest revokeAuthorizationRequest) {
        Application application = getApplication(applicationNameOrid);
        authenticationService.setRoleAdmin();
        CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForCurrentUser();
        authenticationService.setRoleForClient();
        boolean isApplicationCreator = rolesForCurrentUser.memberOf().contains(OreSiRightOnApplicationRole.adminOn(application).getAsSqlRole());

        if (!isApplicationCreator) {
            throw new NotApplicationCanSetRightsException(application.getName());
        }

        OreSiAuthorization oreSiAuthorization = repository.getRepository(application).authorization().findById(revokeAuthorizationRequest.authorizationId());
        Map<String, AuthorizationForScope> authorizationListForCurrentUser = getAuthorizationListForCurrentUser(application);

        Map<String, AuthorizationForScope> filteredAuthorizations = oreSiAuthorization.getAuthorizations().entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> validateAndGetAuthForScope(entry, isApplicationCreator, authorizationListForCurrentUser, application)
                ));

        if (filteredAuthorizations.isEmpty()) {
            return null;
        }

        return new UpdateRolesOnManagement(repository, db, authenticationService).revoke(revokeAuthorizationRequest);
    }

    private Map<String, AuthorizationForScope> getAuthorizationListForCurrentUser(Application application) {
        return findUserAuthorizationsForApplication(application).stream()
                .flatMap(auth -> auth.getAuthorizations().entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2));
    }

    private AuthorizationForScope validateAndGetAuthForScope(Map.Entry<String, AuthorizationForScope> entry,
                                                             boolean isApplicationCreator,
                                                             Map<String, AuthorizationForScope> authorizationListForCurrentUser,
                                                             Application application) {
        String datatype = entry.getKey();
        AuthorizationForScope authForScope = entry.getValue();

        if (!isApplicationCreator && !testCanSetAuthorization(authForScope, authorizationListForCurrentUser.get(datatype))) {
            throw new NotApplicationCanDeleteRightsException(application.getName(), datatype);
        }

        try {
            testAuthorizationArguments(application.findAuthorizations().get(datatype), authForScope);
        } catch (IllegalArgumentException e) {
            throw new NotApplicationCanDeleteRightsException(application.getName(), datatype);
        }

        return authForScope;
    }

    public ImmutableSet<GetAuthorizationResult> getAuthorizations(
            final String applicationNameOrId,
            final AuthorizationsResult authorizationsForUser) {
        final Application application = repository.application().findApplication(applicationNameOrId);
        final AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        List<OreSiAuthorization> publicAuthorizations = authorizationRepository.findPublicAuthorizations();
        return authorizationRepository.findAll().stream()
                .map(oreSiAuthorization -> toGetAuthorizationResult(
                        application.getConfiguration(),
                        oreSiAuthorization,
                        publicAuthorizations,
                        authorizationsForUser))
                .collect(ImmutableSet.toImmutableSet());
    }

    public GetAuthorizationResult getAuthorization(final AuthorizationRequest authorizationRequest, final AuthorizationsResult authorizationsForUser) {
        final Application application = repository.application().findApplication(authorizationRequest.applicationId());
        final AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        final UUID authorizationId = authorizationRequest.authorizationId();
        List<OreSiAuthorization> publicAuthorizations = authorizationRepository.findPublicAuthorizations();
        final OreSiAuthorization oreSiAuthorization = authorizationRepository.findById(authorizationId);
        return toGetAuthorizationResult(
                application.getConfiguration(),
                oreSiAuthorization,
                publicAuthorizations,
                authorizationsForUser);
    }

    private GetAuthorizationResult toGetAuthorizationResult(
            final Configuration configuration,
            final OreSiAuthorization oreSiAuthorization,
            final List<OreSiAuthorization> publicAuthorizations,
            final AuthorizationsResult authorizationsForUser) {
        final List<OreSiUser> all = userRepository.findAll();
        Map<String, List<AuthorizationParsed>> authorizationforPublic = collectPublicAuthorizations(publicAuthorizations)
                .entrySet().stream()
                .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue()
                                        .stream()
                                        .map(AuthorizationParsed::of)
                                        .toList()
                        )
                );
        Map<String, AuthorizationParsed> authorizationForId = oreSiAuthorization.getAuthorizations()
                .entrySet().stream()
                .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> AuthorizationParsed.of(entry.getValue())
                        )
                );
        return new GetAuthorizationResult(
                oreSiAuthorization.getId(),
                oreSiAuthorization.getName(),
                oreSiAuthorization.getDescription(),
                getOreSIUSers(all, oreSiAuthorization.getOreSiUsers()),
                authorizationForId,
                authorizationforPublic,
                authorizationsForUser
        );
    }

    private GetAuthorizationAdditionalFilesResult toGetAdditionalFilesAuthorizationResult(final OreSiAdditionalFileAuthorization oreSiAuthorization, final List<OreSiAdditionalFileAuthorization> publicAuthorizations, final AuthorizationsAdditionalFilesResult authorizationsForUser) {
        final List<OreSiUser> all = userRepository.findAll();
        Map<OperationAdditionalFileType, List<String>> userAdditionalFiles = authorizationsForUser.authorizationResults();
        boolean isAdministrator = authorizationsForUser.isAdministrator();
        Map<OperationAdditionalFileType, List<String>> additionalfiles = oreSiAuthorization.getAdditionalFiles().entrySet().stream()
                .filter(operationReferenceTypeListEntry -> isAdministrator || userAdditionalFiles.containsKey(OperationAdditionalFileType.admin))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        return new GetAuthorizationAdditionalFilesResult(
                oreSiAuthorization.getId(),
                oreSiAuthorization.getName(),
                getOreSIUSers(all, oreSiAuthorization.getOreSiUsers()),
                oreSiAuthorization.getApplication(),
                additionalfiles
        );
    }

    @Transactional(readOnly = true)
    public GetGrantableResult getGrantable(final String applicationNameOrId, final AuthorizationsResult authorizationsForUser) {
        final Application application = repository.application().findApplication(applicationNameOrId);
        final Configuration configuration = application.getConfiguration();
        final ImmutableSortedSet<ApplicationUserResult> users = getGrantableUsers(application);
        final AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        Map<String, List<AuthorizationForScope>> publicAuthorizations = collectPublicAuthorizations(authorizationRepository.findPublicAuthorizations());
        Preconditions.checkArgument(application.getData().stream()
                .allMatch(dataType -> configuration.dataDescription().containsKey(dataType)));
        Map<String, List<GetGrantableResult.ReferenceScope>> referenceScopes = configuration.dataDescription().keySet()
                .stream()
                .map(datatype -> new AbstractMap.SimpleEntry<String, List<GetGrantableResult.ReferenceScope>>(
                        datatype,
                        new LinkedList<>()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        referenceScopes.putAll(getAuthorizationScopes(application, MenuType.authorization));
        Map<String, SortedMap<String, GetGrantableResult.ColumnDescription>> columnDescriptions = application.getData().stream()
                .collect(Collectors.toMap(Function.identity(), dataType -> getColumnDescription(configuration, dataType)));
        return new GetGrantableResult(
                users,
                referenceScopes,
                columnDescriptions,
                authorizationsForUser,
                publicAuthorizations
        );
    }

    public ImmutableSortedSet<GetGrantableResult.User> getGrantableUsers() {
        final List<OreSiUser> allUsers = userRepository.findAll();
        return allUsers.stream()
                .map(oreSiUserEntity -> new GetGrantableResult.User(oreSiUserEntity.getId(), oreSiUserEntity.getLogin()))
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetGrantableResult.User::label)));
    }

    public ImmutableSortedSet<ApplicationUserResult> getGrantableUsers(Application application) {
        final List<OreSiUser> allUsers = userRepository.findAll();
        Map<String, List<String>> administratorRoles = userRepository.getRolesGrantedToRoles(
                ApplicationUserResult.getApplicationRoles(application)
        );
        return allUsers.stream()
                .map(user-> ApplicationUserResult.of(
                        application.getId(),
                        user,
                        administratorRoles,
                        application.getLastChartes()))
                .filter(ApplicationUserResult::isApplicationUser)
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(ApplicationUserResult::label)));
    }

    private Map<ReferenceScope.Context, List<ReferenceScope.TreeNode>> buildNodeTree(Object o) {
        return ((Map<ReferenceScope.Context, List<ReferenceScope.NodeDescription>>) o)
                .entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> buildRecursiveTreeFor(entry.getValue())));
    }

    private List<ReferenceScope.TreeNode> buildRecursiveTreeFor(List<ReferenceScope.NodeDescription> referenceScopeBykey) {
        return referenceScopeBykey
                .stream()
                .filter(ReferenceScope.NodeDescription::isRoot)
                .map(node -> new ReferenceScope.TreeNode(
                        node.node_nk(),
                        node,
                        findChildren(node, referenceScopeBykey)
                )
                )
                .filter(ReferenceScope.TreeNode::containsContextNode)
                .toList();
    }

    private List<ReferenceScope.TreeNode> findChildren(
            ReferenceScope.NodeDescription node,
            List<ReferenceScope.NodeDescription> referenceScopeBykey
    ) {
        return referenceScopeBykey
                .stream()
                .filter(node2 ->
                        Objects.equals(node.node_nk(), node2.parent_nk()) &&
                                (Objects.equals(node.node_type(), node2.parent_type()) ||//TODO error type_de_sites
                                        ("type_de_sites".equals(node.node_type()) && "type_de_sites".equals(node2.parent_type())))
                )
                .map(node2 -> new ReferenceScope.TreeNode(
                        node2.node_nk(),
                        node2,
                        findChildren(node2, referenceScopeBykey)
                ))
                .toList();
    }

    public Map<String, List<GetGrantableResult.ReferenceScope>> getAuthorizationScopes(final Application application, final MenuType menuType) {
        Map<ReferenceScope.Context, List<ReferenceScope.TreeNode>> nodesByContext =
                repository.getRepository(application).data()
                        .getNodesForMenu(menuType)
                        .stream()
                        .collect(
                                Collectors.collectingAndThen(
                                        Collectors.groupingBy(ReferenceScope.NodeDescription::context),
                                        this::buildNodeTree
                                )
                        );
        return nodesByContext.entrySet().stream()
                .map(entry -> new GetGrantableResult.ReferenceScope(entry.getKey(), entry.getValue()))
                .collect(Collectors.groupingBy(GetGrantableResult.ReferenceScope::datatype));
    }

    @Transactional
    public OreSiUserResult deleteSystemRoleUser(final OreSiRoleForUser roleForUser) {
        authenticationService.setRoleAdmin();
        if (OreSiRole.openAdomAdmin().getAsSqlRole().equals(roleForUser.role())) {
            return deleteAdminRoleUser(roleForUser);
        } else if (OreSiRole.applicationCreator().getAsSqlRole().equals(roleForUser.role())) {
            return deleteApplicationCreatorRoleUser(roleForUser);
        }
        throw new BadRoleException("cantDeleteRole", roleForUser.role());
    }
    @Transactional
    public OreSiUserResult deleteApplicationRoleUser(final OreSiRoleForUser roleForUser, Application application) {
        authenticationService.setRoleAdmin();
        if (OreSiRole.applicationManagerOf(application).getAsSqlRole().toString().contains(roleForUser.role())) {
            return deleteApplicationManagerRoleUser(roleForUser, application);
        } else if (OreSiRole.userManagerOf(application).getAsSqlRole().toString().contains(roleForUser.role())) {
            return deleteUserManagerRoleUser(roleForUser, application);
        }
        throw new BadApplicationRoleException("cantDeleteApplicationRole", roleForUser.role(), application);
    }

    private OreSiUserResult deleteApplicationCreatorRoleUser(final OreSiRoleForUser oreSiUserRoleApplicationCreator) {
        final boolean canAddApplicationCreatorRole = canAddApplicationCreatorRole(oreSiUserRoleApplicationCreator);
        if (canAddApplicationCreatorRole) {
            OreSiUser user = authenticationService.deleteUserRightCreateApplication(UUID.fromString(oreSiUserRoleApplicationCreator.userId()), oreSiUserRoleApplicationCreator.applicationPattern());
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiUserRoleApplicationCreator.userId()));
        }
        throw new NotopenAdomAdminException();
    }

    private boolean canAddApplicationCreatorRole(final OreSiRoleForUser oreSiUserRoleApplicationCreator) {
        boolean canAddApplicationCreatorRole = false;
        CurrentUserRoles currentUserRoles = authenticationService.getCurrentUserRoles();
        if (currentUserRoles.isOpenAdomAdmin()) {
            canAddApplicationCreatorRole = true;
        } else if (currentUserRoles.isApplicationCreator()) {
            OreSiUser user = userRepository.findByLogin(oreSiUserRoleApplicationCreator.userId()).orElseGet(() -> userRepository.findById(UUID.fromString(oreSiUserRoleApplicationCreator.userId())));
            if (user.getAuthorizations().stream()
                    .anyMatch(p -> Pattern.compile(p)
                            .matcher(oreSiUserRoleApplicationCreator.applicationPattern())
                            .matches()
                    )) {
                canAddApplicationCreatorRole = true;
            } else {
                throw new NotApplicationCreatorRightsException(oreSiUserRoleApplicationCreator.applicationPattern(), user.getAuthorizations());
            }

        }
        return canAddApplicationCreatorRole;
    }

    private OreSiUserResult deleteApplicationManagerRoleUser(final OreSiRoleForUser oreSiRoleForApplicationManager, Application application) {
        CurrentUserRoles currentUserRoles = authenticationService.getCurrentUserRoles();
        if (currentUserRoles.applicationManagerOf(application)) {
            final OreSiUser user = authenticationService.deleteUserRightApplicationManager(UUID.fromString(oreSiRoleForApplicationManager.userId()), application);
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiRoleForApplicationManager.userId()));
        }
        throw new NotopenAdomAdminException();
    }

    private OreSiUserResult deleteUserManagerRoleUser(final OreSiRoleForUser oreSiUserRoleUserManager, Application application) {
        CurrentUserRoles currentUserRoles = authenticationService.getCurrentUserRoles();
        if (currentUserRoles.applicationManagerOf(application)) {
            OreSiUser user = authenticationService.deleteUserRightUserManager(UUID.fromString(oreSiUserRoleUserManager.userId()), application);
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiUserRoleUserManager.userId()));
        }
        throw new NotopenAdomAdminException();
    }

    private OreSiUserResult deleteAdminRoleUser(final OreSiRoleForUser oreSiRoleForUserAdmin) {
        final boolean canAddsupeadmin = false;
        CurrentUserRoles currentUserRoles = authenticationService.getCurrentUserRoles();
        if (currentUserRoles.isOpenAdomAdmin()) {
            final OreSiUser user = authenticationService.deleteUserRightopenAdomAdmin(UUID.fromString(oreSiRoleForUserAdmin.userId()));
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiRoleForUserAdmin.userId()));
        }
        throw new NotopenAdomAdminException();
    }

    @Transactional
    public OreSiUserResult addSystemRoleUser(final OreSiRoleForUser roleForUser) {
        authenticationService.setRoleAdmin();
        if (OreSiRole.openAdomAdmin().getAsSqlRole().equals(roleForUser.role())) {
            return addAdminRoleUser(roleForUser);
        } else if (OreSiRole.applicationCreator().getAsSqlRole().equals(roleForUser.role())) {
            return addApplicationCreatorRoleUser(roleForUser);
        }
        throw new BadRoleException("cantSetSystemRole", roleForUser.role());
    }

    @Transactional
    public OreSiUserResult addApplicationRoleUser(final OreSiRoleForUser roleForUser, Application application) {
        authenticationService.setRoleAdmin();
        if (OreSiRole.applicationManagerOf(application).getAsSqlRole().toString().contains(roleForUser.role())) {
            return addApplicationManagerRoleUser(roleForUser, application);
        } else if (OreSiRole.userManagerOf(application).getAsSqlRole().toString().contains(roleForUser.role())) {
            return addUserManagerRoleUser(roleForUser, application);
        }
        throw new BadApplicationRoleException("cantSetApplicationRole", roleForUser.role(), application);
    }

    private OreSiUserResult addApplicationCreatorRoleUser(final OreSiRoleForUser oreSiUserRoleApplicationCreator) {
        final boolean canAddApplicationCreatorRole = canAddApplicationCreatorRole(oreSiUserRoleApplicationCreator);
        if (canAddApplicationCreatorRole) {
            OreSiUser user = authenticationService.addUserRightCreateApplication(UUID.fromString(oreSiUserRoleApplicationCreator.userId()), oreSiUserRoleApplicationCreator.applicationPattern());
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiUserRoleApplicationCreator.userId()));
        }
        throw new NotopenAdomAdminException();
    }

    private OreSiUserResult addApplicationManagerRoleUser(final OreSiRoleForUser oreSiUserRoleApplicationManager, Application application) {
        CurrentUserRoles currentUserRoles = authenticationService.getCurrentUserRoles();
        if (currentUserRoles.applicationManagerOf(application)) {
            OreSiUser user = authenticationService.addUserRightApplicationManager(UUID.fromString(oreSiUserRoleApplicationManager.userId()), application);
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiUserRoleApplicationManager.userId()));
        }
        throw new NotopenAdomAdminException();
    }

    private OreSiUserResult addUserManagerRoleUser(final OreSiRoleForUser oreSiUserRoleUserManager, Application application) {
        CurrentUserRoles currentUserRoles = authenticationService.getCurrentUserRoles();
        if (currentUserRoles.applicationManagerOf(application)) {
            OreSiUser user = authenticationService.addUserRightUserManager(UUID.fromString(oreSiUserRoleUserManager.userId()), application);
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiUserRoleUserManager.userId()));
        }
        throw new NotopenAdomAdminException();
    }

    private OreSiUserResult addAdminRoleUser(final OreSiRoleForUser oreSiRoleForUserAdmin) {
        final boolean canAddsupeadmin = false;
        CurrentUserRoles currentUserRoles = authenticationService.getCurrentUserRoles();
        if (currentUserRoles.isOpenAdomAdmin()) {
            final OreSiUser user = authenticationService.addUserRightopenAdomAdmin(UUID.fromString(oreSiRoleForUserAdmin.userId()));
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiRoleForUserAdmin.userId()));
        }
        throw new NotopenAdomAdminException();
    }

    public boolean isApplicationCreator(final Application application, final UUID userId) {
        OreSiUser user = getUser(userId.toString());
        return user.getAuthorizations().stream().anyMatch(s -> Pattern.compile(s).matcher(application.getName()).matches());
    }

    private OreSiUser getUser(final String userLoginOrId) {
        OreSiUser user = userRepository.findByLogin(userLoginOrId).orElseGet(() -> userRepository.findById(UUID.fromString(userLoginOrId)));
        if (user == null) {
            throw new SiOreIllegalArgumentException("unknown_user", Map.of("login", userLoginOrId));
        }
        return user;
    }

    public AuthorizationsResult getAuthorizationsForUserAndPublic(final String applicationNameOrUuid, final String userLoginOrId) {
        Application application = repository.application().findApplication(applicationNameOrUuid);
        CurrentUserRoles currentUserRoles = authenticationService.getCurrentUserRoles(userLoginOrId);
        OreSiUser user = currentUserRoles.user();
        boolean isApplicationManager = currentUserRoles.applicationManagerOf(application);
        boolean isUserManager = currentUserRoles.userManagerOf(application);
        boolean isApplicationCreator = currentUserRoles.isApplicationCreator();
        Optional<Timestamp> timestampOptional = Optional.ofNullable(user)
                .map(OreSiUser::getChartes)
                .map(chertes -> chertes.get(application.getId().toString()));
        boolean isApplicationUser = timestampOptional.isPresent();
        boolean isActiveApplicationUser = timestampOptional
                .map(application.getLastChartes()::before)
                .orElse(false);

        // Public authorizations restent inchangées
        Map<String, AuthorizationParsed> publicAuthorizations = repository.getRepository(application).authorization().findPublicAuthorizations()
                .stream()
                .map(OreSiAuthorization::getAuthorizations)
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> AuthorizationParsed.of(entry.getValue()),
                        (existing, replacement) -> existing // En cas de doublon, on garde la première autorisation
                ));

        // User authorizations sont maintenant regroupées dans une liste pour chaque clé
        Map<String, List<AuthorizationParsed>> userAuthorizations = repository.getRepository(application)
                .authorization()
                .findAuthorizationsByUserId(currentUserRoles.userId())
                .stream()
                .map(OreSiAuthorization::getAuthorizations)
                .map(Map::entrySet)
                .flatMap(Set::stream)
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(
                                entry -> AuthorizationParsed.of(entry.getValue()),
                                Collectors.toList()
                        )
                ));

        return new AuthorizationsResult(
                userAuthorizations,
                publicAuthorizations,
                application.getName(),
                isApplicationCreator,
                isApplicationManager,
                isUserManager,
                isApplicationUser,
                isActiveApplicationUser
        );
    }

    @Transactional
    public UUID revokeAdditionalFiles(final String applicationNameOrId, final UUID authorizationId) {
        /*UpdateRolesOnAdditionalFilesManagement updateRolesOnManagement = new UpdateRolesOnAdditionalFilesManagement(repository, db, authenticationService);
        Application application = getApplication(applicationNameOrId);
        CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForCurrentUser();
        boolean isApplicationCreator = rolesForCurrentUser.memberOf().contains(OreSiRightOnApplicationRole.adminOn(application).getAsSqlRole());
        UUID requestUserId = request.getRequestUserId();
        final List<OreSiAuthorization> authorizationsForCurrentUser = findUserAuthorizationsForApplication(application);
        if (!isApplicationCreator && authorizationsForCurrentUser.stream().allMatch(
                a -> a.getAuthorizations().get(application.getName()).get(OperationType.admin).isEmpty()
        )) {
            throw new NotApplicationCanSetRightsReferencesException(application.getName());
        }
        final OreSiAdditionalFileAuthorization oreSiAuthorization = repository.getRepository(application).authorizationAdditionalFiles().findById(authorizationId);
        List<AuthorizationForScope> authorizationListForCurrentUser = authorizationsForCurrentUser.stream()
                .map(OreSiAuthorization::getAuthorizations)
                .filter(operationTypeListMap -> operationTypeListMap.containsKey(OperationType.admin))
                .map(operationTypeListMap -> operationTypeListMap.get(OperationType.admin))
                .flatMap(List::stream)
                .collect(Collectors.toList());

        Map<OperationAdditionalFileType, List<String>> filteredAuthorizations = oreSiAuthorization.getAdditionalFiles().entrySet().stream()
                .peek(authByTypeEntry -> {
                    if (!isApplicationCreator) {
                        boolean canRemoveEntry = new HashSet<>(authorizationListForCurrentUser).containsAll(authByTypeEntry.getValue());
                        if (!canRemoveEntry) {
                            throw new NotApplicationCanDeleteReferencesRightsException(application.getName(), authorizationListForCurrentUser);
                        }
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (filteredAuthorizations.isEmpty()) {
            return null;
        }
        return updateRolesOnManagement.revoke(application, authorizationId);*/
        return null;
    }

    public ImmutableSet<GetAuthorizationAdditionalFilesResult> getAdditionalFilesuthorizations(final String applicationNameOrId, final AuthorizationsAdditionalFilesResult authorizationsForUser, final MultiValueMap<String, String> params) {
        final Application application = repository.application().findApplication(applicationNameOrId);
        final ImmutableSortedSet<GetGrantableResult.User> users = getGrantableUsers();
        final AuthorizationAdditionalFilesRepository authorizationRepository = repository.getRepository(application).authorizationAdditionalFiles();
        List<OreSiAdditionalFileAuthorization> publicAuthorizations = authorizationRepository.findPublicAuthorizations();
        final long offset = Optional.ofNullable(params)
                .map(map -> map.get("offset"))
                .map(l -> l.isEmpty() ? "0" : l.getFirst())
                .map(Long::parseLong)
                .orElse(0L);
        final long limit = Optional.ofNullable(params)
                .map(map -> map.get("limit")).filter(l -> !l.isEmpty()).map(l -> Long.parseLong(l.getFirst())).orElse(Long.MAX_VALUE);
        final String user = Optional.ofNullable(params)
                .map(map -> map.get("userId"))
                .map(l -> l.isEmpty() ? null : l.getFirst())
                .filter(s -> !"null".equals(s))
                .orElse(null);
        final String authorizationId = Optional.ofNullable(params)
                .map(map -> map.get("authorizationId"))
                .map(l -> l.isEmpty() ? null : l.getFirst())
                .filter(s -> !"null".equals(s))
                .orElse(null);

        return authorizationRepository.findAll().stream()
                .skip(offset)
                .limit(limit)
                .filter(oreSiReferenceAuthorization ->
                        (user == null || oreSiReferenceAuthorization.getOreSiUsers().stream().anyMatch(uuid -> uuid.toString().equals(user)))
                                && (authorizationId == null || oreSiReferenceAuthorization.getId().toString().equals(authorizationId))
                )
                .map(oreSiAuthorization -> toGetAdditionalFilesAuthorizationResult(oreSiAuthorization, publicAuthorizations, authorizationsForUser))
                .collect(ImmutableSet.toImmutableSet());
    }

    @Transactional
    public OreSiAdditionalFileAuthorization addAdditionalFileAuthorizations(final Application application, final CreateAdditionalFileAuthorizationRequest authorizations, final List<OreSiAdditionalFileAuthorization> authorizationsForCurrentUser, final boolean isApplicationCreator) {
        final AuthorizationAdditionalFilesRepository authorizationAdditionalFilesRepository = repository.getRepository(application).authorizationAdditionalFiles();
        final OreSiAdditionalFileAuthorization entity = authorizations.getUuid() == null ?
                new OreSiAdditionalFileAuthorization()
                : authorizationAdditionalFilesRepository.findById(authorizations.getUuid());

        final Map<OperationAdditionalFileType, List<String>> authorizationsByType = authorizations.getAdditionalFiles();

        for (final List<String> references : authorizationsByType.values()) {
            for (final String reference : references) {
                Preconditions.checkArgument(application.getConfiguration().additionalFiles().containsKey(reference));
            }
        }

        Set<String> authorizationListForCurrentUser = authorizationsForCurrentUser.stream()
                .map(OreSiAdditionalFileAuthorization::getAdditionalFiles)
                .filter(operationTypeListMap -> operationTypeListMap.containsKey(OperationAdditionalFileType.admin))
                .map(operationTypeListMap -> operationTypeListMap.get(OperationAdditionalFileType.admin))
                .flatMap(List::stream)
                .collect(Collectors.toSet());

        Map<OperationAdditionalFileType, List<String>> modifiedAuthorizations = authorizationsByType.entrySet().stream()
                .peek(authByTypeEntry -> {
                    if (!isApplicationCreator) {
                        removeAuthorizationAdditionalFilesThatCantBeModified(authByTypeEntry, authorizationListForCurrentUser);
                    }
                })
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!isApplicationCreator) {
            addStoredAuthorizationAdditionalFilesThatCantBeModified(entity, authorizationListForCurrentUser, modifiedAuthorizations);
        }
        entity.setName(authorizations.getName());
        entity.setOreSiUsers(authorizations.getUsersId());
        entity.setApplication(application.getId());
        entity.setAdditionalFiles(authorizations.getAdditionalFiles());
        authorizationAdditionalFilesRepository.store(entity);
        return entity;
    }

    public AuthorizationsAdditionalFilesResult getAdditionalFilesAuthorizationsForUser(final String applicationNameOrUuid, final String userId) {
        OreSiUser user = userRepository.findByLogin(userId).orElseGet(() -> userRepository.findById(UUID.fromString(userId)));
        if (user == null) {
            throw new SiOreIllegalArgumentException("unknown_user", Map.of("login", userId));
        }
        Application application = repository.application().findApplication(applicationNameOrUuid);
        boolean isAdministrator = user.getAuthorizations().stream().anyMatch(s -> Pattern.compile(s).matcher(application.getName()).matches());

        CurrentUserRoles rolesForCurrentUser = userRepository.getRolesForRole(user.getId().toString());
        List<OreSiAdditionalFileAuthorization> publicAuthorizations = repository.getRepository(application).authorizationAdditionalFiles().findPublicAuthorizations();
        List<OreSiAdditionalFileAuthorization> authorizations = repository.getRepository(application).authorizationAdditionalFiles()
                .findAuthorizations(UUID.fromString(Optional.ofNullable(rolesForCurrentUser).map(CurrentUserRoles::userLogin).orElse("")), application);
        final Map<OperationAdditionalFileType, List<String>> authorizationMap = new EnumMap<>(OperationAdditionalFileType.class);
        final List<String> attributes = new ArrayList<>(application.getConfiguration().requiredAuthorizationsAttributes());

        authorizations
                .forEach(authorizationList -> authorizationList.getAdditionalFiles().forEach((key, value) -> value.
                        forEach(authorizationResult -> authorizationMap
                                .computeIfAbsent(key, k -> new LinkedList<>())
                                .add(authorizationResult))));
        return new AuthorizationsAdditionalFilesResult(authorizationMap, application.getName(), isAdministrator);
    }

    public List<OreSiAdditionalFileAuthorization> findUserAdditionalFilesAuthorizationsForApplication(final Application application) {
        UUID currentUserId = request.getRequestClient().id();
        final AuthorizationAdditionalFilesRepository authorizationRepository = repository.getRepository(application).authorizationAdditionalFiles();
        return authorizationRepository.findAuthorizations(currentUserId, application);
    }

    public List<OreSiAdditionalFileAuthorization> findUserAdditionalFilesAuthorizationsForApplicationAndDataType(final Application application) {
        UUID currentUserId = request.getRequestClient().id();
        final AuthorizationAdditionalFilesRepository authorizationRepository = repository.getRepository(application).authorizationAdditionalFiles();
        return authorizationRepository.findAuthorizations(currentUserId, application);
    }

    public CreateAuthorizationRequest createAuthorizationRequestWithDependantAuthorization(
            Application application,
            CreateAuthorizationRequest createAuthorizationRequest) {
        Set<String> dependantsNodes = Optional.ofNullable(createAuthorizationRequest)
                .map(CreateAuthorizationRequest::authorizationsWithRestriction)
                .map(Map::keySet)
                .map(application::findDependentNodes)
                .map(HashSet::new)
                .orElseGet(HashSet::new);
        Optional.ofNullable(createAuthorizationRequest)
                .map(CreateAuthorizationRequest::authorizationForAll)
                .map(Map::keySet)
                .map(application::findDependentNodes)
                .ifPresent(dependantsNodes::addAll);
        return createAuthorizationRequest.addDependantAuthorizations(dependantsNodes);

    }

    public AuthorizationRequest createAuthorizationRequestToAuthorizationRequest(
            CreateAuthorizationRequest createAuthorizationRequestWithDependantAuthorization,
            Application application,
            List<UUID> userIds,
            List<OreSiAuthorization> authorizationsForCurrentUser,
            List<AuthorizationRequestError> errors) {
        DataRepositoryForBuffer dataRepositoryWithBuffer = new DataRepositoryWithBuffer(application, repository.getRepository(application).data());
        return new AuthorizationRequestBuilder(
                application,
                userIds,
                authorizationsForCurrentUser,
                errors
        )
                .build(createAuthorizationRequestWithDependantAuthorization, dataRepositoryWithBuffer);
    }

    public static void authorizationsToParsedAuthorizations(
            List<OreSiAuthorization> authorizations,
            Map<String, List<AuthorizationParsed>> authorizationsParsed
    ) {
        for (OreSiAuthorization authorization : authorizations) {
            for (Map.Entry<String, AuthorizationForScope> authorizationEntry : Optional.ofNullable(authorization.getAuthorizations())
                    .orElseGet(HashMap::new)
                    .entrySet()) {
                String datatype = authorizationEntry.getKey();
                AuthorizationForScope authorizationToParse = authorizationEntry.getValue();
                AuthorizationParsed authorizationParsed = AuthorizationParsed.of(authorizationToParse);
                authorizationsParsed.computeIfAbsent(datatype, k->new LinkedList<>())
                        .add(authorizationParsed);
            }
        }
    }


    public OreSiUser getCurrentUser() {
        return userRepository.findById(request.getRequestClient().id());
    }

    private AuthorizationsForApplicationUser getAuthorizationsForApplicationUser(Application application) {
        OreSiUser currentUser = getCurrentUser();
        CurrentUserRoles currentUserRoles = authenticationService.getCurrentUserRoles();
        boolean isApplicationManager = currentUserRoles.applicationManagerOf(application);
        boolean isUserManager = currentUserRoles.userManagerOf(application);
        currentUserRoles.applicationRoles().get(application.getId().toString());
        AuthorizationsResult authorizationsForUserAndPublic = getAuthorizationsForUserAndPublic(application.getName(), currentUser.getLogin());
        return new AuthorizationsForApplicationUser(
                currentUserRoles.applicationRoles().get(application.getId().toString()),
                application,
                isApplicationManager,
                isUserManager,
                authorizationsForUserAndPublic.userAuthorization(),
                authorizationsForUserAndPublic.publicAuthorization()
        );
    }

    private AuthorizationsForSystemUser getAuthorizationsForSystemUser() {
        OreSiUser currentUser = getCurrentUser();
        CurrentUserRoles currentUserRoles = authenticationService.getCurrentUserRoles(currentUser.getId().toString());
        Set<String> applicationCreator = currentUser.getAuthorizations();
        return new AuthorizationsForSystemUser(currentUserRoles, applicationCreator);
    }
    public PrivilegeAssessorDomainForSystem getPrivilegeAssessorForSystem(
            PrivilegeSystemDomain privilegeDomain
    ) {
        AuthorizationsForSystemUser authorizations = getAuthorizationsForSystemUser();
        return PrivilegeAssessorBuilder.forSystem(
                authorizations,
                privilegeDomain
        );
    }

    public PrivilegeAssessorDomainForApplication getPrivilegeAssessorForApplication(
            PrivilegeApplicationDomain privilegeDomain,
            Application application
    ) {
        AuthorizationsForApplicationUser authorizations = getAuthorizationsForApplicationUser(application);
        GetGrantableResult grantable = getGrantable(
                application.getName(),
                getAuthorizationsForUserAndPublic(
                        application.getName(),
                        authenticationService.getCurrentUserRoles().userLogin()
                )
        );
        return PrivilegeAssessorBuilder.forApplication(
                authorizations,
                privilegeDomain,
                application,
                grantable
        );
    }
}
