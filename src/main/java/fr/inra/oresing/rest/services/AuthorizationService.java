package fr.inra.oresing.rest.services;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.collect.ImmutableSortedSet;
import fr.inra.oresing.cache.MemoryCache;
import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.OreSiRoleForUser;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.additionalfiles.AuthorizationsAdditionalFilesResult;
import fr.inra.oresing.domain.additionalfiles.OperationAdditionalFileType;
import fr.inra.oresing.domain.additionalfiles.OreSiAdditionalFileAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.Configuration;
import fr.inra.oresing.domain.authorization.ApplicationUserResult;
import fr.inra.oresing.domain.authorization.AuthorizationParsed;
import fr.inra.oresing.domain.authorization.AuthorizationsForUserResult;
import fr.inra.oresing.domain.authorization.AuthorizationsResult;
import fr.inra.oresing.domain.authorization.GetGrantableResult;
import fr.inra.oresing.domain.authorization.privilegeassessor.*;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.CantSelfRevokeApplicationRoleException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationUserManagerRightsException;
import fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotOpenAdomAdminException;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.ApplicationAdminUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomainEnum;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomainEnum;
import fr.inra.oresing.domain.authorization.request.AuthorizationForScope;
import fr.inra.oresing.domain.authorization.request.AuthorizationRequest;
import fr.inra.oresing.domain.data.menu.MenuType;
import fr.inra.oresing.domain.data.menu.ReferenceScope;
import fr.inra.oresing.domain.exceptions.ExceptionMessage;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.exceptions.SiOreIllegalArgumentException;
import fr.inra.oresing.domain.exceptions.authorization.AuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.authorization.SiOreAuthorizationRequestException;
import fr.inra.oresing.domain.exceptions.role.role.BadApplicationRoleException;
import fr.inra.oresing.domain.exceptions.role.role.BadRoleException;
import fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRole;
import fr.inra.oresing.persistence.*;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.UpdateRolesOnAdditionalFilesManagement;
import fr.inra.oresing.rest.UpdateRolesOnManagement;
import fr.inra.oresing.rest.model.authorization.*;
import fr.inra.oresing.rest.model.authorization.exception.AuthorizationRequestError;
import fr.inra.oresing.rest.model.authorization.request.AuthorizationRequestBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.MultiValueMap;

import java.sql.Timestamp;
import java.util.*;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeApplicationDomainEnum.DATA_ACCESS;

@Slf4j
@Component
@Transactional(readOnly = true)
public class AuthorizationService implements fr.inra.oresing.domain.services.authorization.AuthorizationService {

    private final SqlService db;
    private final OreSiRepository repository;
    private final UserRepository userRepository;
    private ServiceContainer serviceContainer;

    /**
     * Flag d'activation du cache des authorization scopes ( cf.
     * {@link #scopesCache} ). Quand désactivé , chaque appel à
     * {@link #getAuthorizationScopes} relance la fonction SQL
     * {@code <schema>.getnodes(...)} ( ~5 s sur dataset 4M+ rows ).
     * Mode dégradé conservé pour debug / benchmarking.
     */
    @Value("${openadom.cache.authorization-scopes.enabled:true}")
    private boolean authorizationScopesCacheEnabled;

    // ─── Cache mémoire des scopes ────────────────────────────────────────────
    //
    // Audit OA_FULL_REVIEW (8/5/26) : la fonction SQL <schema>.getnodes() fait
    // un walk récursif des hierarchicalkey + LATERAL unnest + DISTINCT ON sur
    // referencevalue ( 4.3M rows sur si_acbb ) , coût mesuré ~4.9 s par appel.
    // Le résultat est :
    //  - indépendant du datatype courant ( arbre global pour tout l'app ) ;
    //  - indépendant des filtres/pagination /data/json ;
    //  - stable entre deux events ( import/delete , YAML edit , grant admin ).
    //
    // Politique d'invalidation :
    //  - explicite : invalidateAuthorizationScopesForApplication appelée par
    //    OreSiResources.saveData / deleteData / BundleResources / addAuthorization ,
    //    plus refresh manuel admin si besoin ;
    //  - filet : TTL 5 min ( ScOPES_CACHE_TTL_MS ) ;
    //  - LRU : éviction du plus ancien quand on dépasse SCOPES_CACHE_MAX_ENTRIES.
    //
    // Clé : userId::appName::menuType. Inclure l'userId est obligatoire car la
    // requête SQL est filtrée par RLS sur le rôle Postgres courant ( deux users
    // différents peuvent voir des arbres différents pour le même app/menu ).
    /**
     * Type stocké dans le cache : map de scopes par datatype. Pas de
     * gestion de timestamp ici - {@link MemoryCache} la porte en interne.
     */
    private record ScopesValue(Map<String, List<GetGrantableResult.ReferenceScope>> scopes) {}

    @Value("${openadom.cache.authorization-scopes.ttl-minutes:120}")
    private long scopesCacheTtlMinutes;

    @Value("${openadom.cache.authorization-scopes.max-entries:200}")
    private int scopesCacheMaxEntries;

    /**
     * Cache mémoire des scopes utilisateurs ; clé
     * {@code userId::appName::menuType}. Initialisé après construction
     * via {@link #initScopesCache} ( les @Value ne sont injectés
     * qu'après la construction de l'instance ).
     */
    private MemoryCache<String, ScopesValue> scopesCache;

    @jakarta.annotation.PostConstruct
    void initScopesCache() {
        this.scopesCache = new MemoryCache<>("authorizationScopes", scopesCacheMaxEntries, scopesCacheTtlMinutes);
    }

    public AuthorizationService(
            SqlService db,
            ServiceContainer serviceContainer,
            OreSiRepository repository,
            UserRepository userRepository) {
        this.db = db;
        this.serviceContainer = serviceContainer;
        this.repository = repository;
        this.userRepository = userRepository;
    }

    private static void removeAuthorizationAdditionalFilesThatCantBeModified(final Map.Entry<OperationAdditionalFileType, List<String>> authByTypeEntry, final Set<String> authorizationListForCurrentUser) {
        List<String> collect = authByTypeEntry.getValue().stream()
                .filter(authorizationListForCurrentUser::contains)
                .toList();
        authByTypeEntry.setValue(collect);
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

    private static ImmutableSortedMap<String, GetGrantableResult.ColumnDescription> getColumnDescription() {
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

    // Méthode utilitaire pour vérifier si un Ltree en contient un autre ou est égal
    private static boolean isLtreeContainedOrEqual(String containerLtree, String containedLtree) {
        // Dans PostgreSQL, 'a.b' @> 'a.b.c' est vrai (a.b contient a.b.c)
        // et 'a.b' @> 'a.b' est aussi vrai (égalité)
        return containerLtree.equals(containedLtree) || containedLtree.startsWith(containerLtree + ".");
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
                authorizationsParsed.computeIfAbsent(datatype, k -> new LinkedList<>())
                        .add(authorizationParsed);
            }
        }
    }

    @Transactional
    public void updateRoleForManagement(
            Application application,
            final Set<UUID> previousUsers,
            final OreSiAuthorization modifiedAuthorization) {
        UpdateRolesOnManagement updateRolesOnManagement = new UpdateRolesOnManagement(application, repository, db, serviceContainer.authenticationService());
        updateRolesOnManagement.init(previousUsers, modifiedAuthorization);
        updateRolesOnManagement.updateRoleForManagement();
    }

    @Transactional
    public void updateRoleForReferenceManagement(final Set<UUID> previousUsers, final OreSiAdditionalFileAuthorization modifiedAuthorization) {
        UpdateRolesOnAdditionalFilesManagement updateRolesOnManagement = new UpdateRolesOnAdditionalFilesManagement(repository, db, serviceContainer.authenticationService());
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
                "role carrying the policies of authorization %1$s for data of the application %2$s".formatted(
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
                "role carrying the policies of authorization %1$s for additionalFiles of the application %2$s".formatted(
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
        UUID currentUserId = OreSiApiRequestContext.getRequestUserId();
        final AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        return authorizationRepository.findAuthorizationsByUserId(currentUserId);
    }

    @Transactional
    public Authorizations addAuthorization(final Application application,
                                           final AuthorizationRequest authorizationRequest) {
        final AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();

        OreSiAuthorization previous = null;
        if (authorizationRequest.authorizationId() != null) {
            previous = authorizationRepository.findById(authorizationRequest.authorizationId());
        }
        final OreSiAuthorization entity = previous == null ?
                new OreSiAuthorization()
                : previous;
        // Pre-check unicite du nom au sein de l'application . Sans ce garde-fou ,
        // une tentative de creation avec un nom deja pris se solde par un 500
        // ( exception PG ou silencieux ) , et l'utilisateur reste devant un
        // formulaire qui n'a rien sauve sans aucun feedback . On remonte
        // explicitement {@code AUTHORIZATION_NAME_EXISTS} ( 422 ) que le
        // frontend transforme en toast + setFieldError sur le champ name .
        // Lors d'un update on exclut l'autorisation en cours pour autoriser
        // un "save" sans changement de nom .
        if (authorizationRepository.existsByName(authorizationRequest.name(),
                previous == null ? null : previous.getId())) {
            throw new SiOreAuthorizationRequestException(
                    AuthorizationRequestException.AUTHORIZATION_NAME_EXISTS,
                    Map.of("name", authorizationRequest.name())
            );
        }
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

    public Application getApplication(final String nameOrId) {
        serviceContainer.authenticationService().setRoleForClient();
        return repository.application().findApplication(nameOrId);
    }

    @Transactional
    public UUID revoke(
            final ApplicationAdminUser applicationAdminUser,
            final String applicationNameOrid,
            final AuthorizationRequest revokeAuthorizationRequest) {
        if (applicationAdminUser == null) {
            throw new NotApplicationUserManagerRightsException(applicationNameOrid);
        }
        Application application = getApplication(applicationNameOrid);

        // Charge l'autorisation pour s'assurer qu'elle existe en base : si
        // l'id est inconnu , {@code findById} leve une exception et le
        // controleur renvoie une 4xx ( comportement attendu ) . L'ancien
        // garde-fou {@code if ( filteredAuthorizations.isEmpty() ) return null ;}
        // a ete retire : il court-circuitait silencieusement la suppression
        // quand l'autorisation n'avait aucun scope coche ( cas typique d'une
        // autorisation creee a tort sans aucun referentiel ni datatype ) , la
        // row {@code OreSiAuthorization} restait alors en base alors que le
        // frontend recevait une 200 et croyait la suppression effectuee .
        // {@code UpdateRolesOnManagement#revoke} gere proprement le cas
        // scopes vides : {@code dropPolicies} et le {@code forEach} sur
        // {@code getOreSiUsers()} sont no-op , {@code authorizationRepository.delete}
        // supprime bien la row , et {@code db.dropRole} reussit car le role
        // PostgreSQL est cree systematiquement a la creation de l'autorisation
        // ( cf {@link #createRoleForAuthorization} ) , independamment du nombre
        // de scopes .
        repository.getRepository(application).authorization().findById(revokeAuthorizationRequest.authorizationId());

        return new UpdateRolesOnManagement(application, repository, db, serviceContainer.authenticationService()).revoke(revokeAuthorizationRequest);
    }

    public ImmutableSet<GetAuthorizationResult> getAuthorizations(
            final String applicationNameOrId,
            final AuthorizationsResult authorizationsForUser) {
        final Application application = repository.application().findApplication(applicationNameOrId);
        final AuthorizationRepository authorizationRepository = repository.getRepository(application).authorization();
        List<OreSiAuthorization> publicAuthorizations = authorizationRepository.findPublicAuthorizations();
        return authorizationRepository.findAll().stream()
                .map(oreSiAuthorization -> toGetAuthorizationResult(
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
                oreSiAuthorization,
                publicAuthorizations,
                authorizationsForUser);
    }

    private GetAuthorizationResult toGetAuthorizationResult(
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
                .collect(Collectors.toMap(Function.identity(), dataType -> getColumnDescription()));
        return new GetGrantableResult(
                users,
                referenceScopes,
                columnDescriptions,
                authorizationsForUser,
                publicAuthorizations
        );
    }

    public List<OreSiUser> getAllUsers() {
        return userRepository.findAll();
    }

    public ImmutableSortedSet<ApplicationUserResult> getGrantableUsers(Application application) {
        final List<OreSiUser> allUsers = userRepository.findAll();
        Map<String, List<String>> administratorRoles = userRepository.getRolesGrantedToRoles(
                ApplicationUserResult.getApplicationRoles(application)
        );
        return allUsers.stream()
                .map(user -> ApplicationUserResult.of(
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
                .filter(
                        node2 ->
                                Objects.equals(node.node_nk(), node2.parent_nk()) && Objects.equals(node.node_type(), node2.parent_type())
                )
                .map(node2 -> new ReferenceScope.TreeNode(
                        node2.node_nk(),
                        node2,
                        findChildren(node2, referenceScopeBykey)
                ))
                .toList();
    }

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private fr.inra.oresing.workflow.cascade.metrics.OpenadomCacheMetrics cacheMetrics;

    public Map<String, List<GetGrantableResult.ReferenceScope>> getAuthorizationScopes(final Application application, final MenuType menuType) {
        if (!authorizationScopesCacheEnabled) {
            return computeAuthorizationScopes(application, menuType);
        }

        final String userId = serviceContainer.authenticationService().getCurrentUser().getId().toString();
        final String cacheKey = userId + "::" + application.getName() + "::" + menuType.getType();

        ScopesValue cached = scopesCache.get(cacheKey);
        if (cached != null) {
            log.debug("authorizationScopes cache hit for {}", cacheKey);
            if (cacheMetrics != null) cacheMetrics.recordScopesHit();
            return cached.scopes();
        }

        log.info("authorizationScopes cache miss for {} , computing from SQL", cacheKey);
        if (cacheMetrics != null) cacheMetrics.recordScopesMiss();
        Map<String, List<GetGrantableResult.ReferenceScope>> result = computeAuthorizationScopes(application, menuType);
        scopesCache.put(cacheKey, new ScopesValue(result));
        return result;
    }

    /**
     * Calcul effectif des scopes via la fonction SQL {@code getnodes()}.
     * Extrait ici pour pouvoir être appelé soit en bypass de cache ( flag
     * désactivé ) soit en miss interne. Pas d'overload public pour ne pas
     * exposer la cassure de l'invariant cache aux callers.
     */
    private Map<String, List<GetGrantableResult.ReferenceScope>> computeAuthorizationScopes(
            final Application application, final MenuType menuType) {
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

    /**
     * Invalide les entrées de cache pour une application. À appeler après
     * tout événement susceptible de modifier le résultat de {@code getnodes()}
     * pour cette app : import / delete data , update YAML config , grant /
     * revoke d'autorisation.
     *
     * <p>L'app est identifiée par son nom ; toutes les entrées dont la clé
     * contient {@code ::appName::} ( quels que soient l'user et le menuType )
     * sont retirées.
     */
    public void invalidateAuthorizationScopesForApplication(String appName) {
        if (appName == null || scopesCache == null) return;
        final String marker = "::" + appName + "::";
        int removed = scopesCache.invalidateMatching(k -> k.contains(marker));
        log.info("authorizationScopes cache invalidated for app {} ( {} entries )", appName, removed);
        if (cacheMetrics != null) cacheMetrics.recordScopesInvalidate();
    }

    /**
     * Invalide tout le cache ( toutes apps , tous users , tous menus ).
     * Réservé aux cas extrêmes ( purge debug , redéploiement à chaud ).
     */
    public void invalidateAllAuthorizationScopes() {
        if (scopesCache != null) scopesCache.invalidateAll();
        log.info("All authorizationScopes caches invalidated");
    }

    /** Observabilité : taille courante du cache scopes. */
    public int getAuthorizationScopesCacheSize() {
        return scopesCache == null ? 0 : scopesCache.size();
    }

    /**
     * Observabilité : timestamp du dernier remplissage du cache scopes ,
     * ou {@code null} si jamais ecrit / invalidateAll . Affiche par l'UI
     * admin colonne "Derniere mise a jour" .
     */
    public java.time.Instant getAuthorizationScopesCacheLastWriteAt() {
        return scopesCache == null ? null : scopesCache.lastWriteAt();
    }

    /**
     * Observabilité : taille mémoire approximative du cache scopes via
     * sérialisation Jackson . Appelée uniquement par CacheSizeEstimator
     * sur demande admin , pas en hot path .
     */
    public long estimateAuthorizationScopesCacheSizeBytes(com.fasterxml.jackson.databind.ObjectMapper mapper) {
        return scopesCache == null ? 0L : scopesCache.estimateSizeBytes(mapper);
    }

    public boolean isAuthorizationScopesCacheEnabled() {
        return authorizationScopesCacheEnabled;
    }

    public int getAuthorizationScopesCacheMaxEntries() {
        return scopesCache == null ? scopesCacheMaxEntries : scopesCache.maxEntries();
    }

    public long getAuthorizationScopesCacheTtlMinutes() {
        return scopesCache == null ? scopesCacheTtlMinutes : scopesCache.ttlMinutes();
    }

    @Transactional
    public OreSiUserResult deleteSystemRoleUser(final OreSiRoleForUser roleForUser) {
        serviceContainer.authenticationService().setRoleAdmin();
        try {
            if (OreSiRole.openAdomAdmin().getAsSqlRole().equals(roleForUser.role())) {
                return deleteAdminRoleUser(roleForUser);
            } else if (OreSiRole.applicationCreator().getAsSqlRole().equals(roleForUser.role())) {
                return deleteApplicationCreatorRoleUser(roleForUser);
            }
            throw new BadRoleException("cantDeleteRole", roleForUser.role());
        } finally {
            // Restaure le role client sur la connexion Postgres meme en cas
            // d'exception, sinon le pool Hikari recycle une connexion en
            // openAdomAdmin pour la requete HTTP suivante ( bypass RLS ).
            serviceContainer.authenticationService().setRoleForClient();
        }
    }

    @Transactional
    public OreSiUserResult deleteApplicationRoleUser(final OreSiRoleForUser roleForUser, Application application) {
        serviceContainer.authenticationService().setRoleAdmin();
        try {
            if (OreSiRole.applicationManagerOf(application).getAsSqlRole().contains(roleForUser.role())) {
                return deleteApplicationManagerRoleUser(roleForUser, application);
            } else if (OreSiRole.userManagerOf(application).getAsSqlRole().contains(roleForUser.role())) {
                return deleteUserManagerRoleUser(roleForUser, application);
            }
            throw new BadApplicationRoleException("cantDeleteApplicationRole", roleForUser.role(), application);
        } finally {
            serviceContainer.authenticationService().setRoleForClient();
        }
    }

    private OreSiUserResult deleteApplicationCreatorRoleUser(final OreSiRoleForUser oreSiUserRoleApplicationCreator) {
        OreSiUser user = serviceContainer.authenticationService().deleteUserRightCreateApplication(UUID.fromString(oreSiUserRoleApplicationCreator.userId()), oreSiUserRoleApplicationCreator.applicationPattern());
        return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiUserRoleApplicationCreator.userId()));
    }

    private OreSiUserResult deleteApplicationManagerRoleUser(final OreSiRoleForUser oreSiRoleForApplicationManager, Application application) {
        CurrentUserRoles currentUserRoles = serviceContainer.authenticationService().getCurrentUserRoles();
        if (currentUserRoles.applicationManagerOf(application)) {
            // Bug #4 ticket #521 : un applicationManager ne peut pas
            // s'auto-révoquer ( risque de laisser une application orpheline
            // de gestionnaire ) , sauf s'il est aussi openAdomAdmin qui
            // pourra de toute facon revenir sur la modification .
            assertNotSelfRevoke(oreSiRoleForApplicationManager.userId(), currentUserRoles);
            final OreSiUser user = serviceContainer.authenticationService().deleteUserRightApplicationManager(UUID.fromString(oreSiRoleForApplicationManager.userId()), application);
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiRoleForApplicationManager.userId()));
        }
        throw new NotOpenAdomAdminException();
    }

    private OreSiUserResult deleteUserManagerRoleUser(final OreSiRoleForUser oreSiUserRoleUserManager, Application application) {
        CurrentUserRoles currentUserRoles = serviceContainer.authenticationService().getCurrentUserRoles();
        if (currentUserRoles.applicationManagerOf(application)) {
            // Bug #4 ticket #521 : meme garde-fou que pour applicationManager .
            assertNotSelfRevoke(oreSiUserRoleUserManager.userId(), currentUserRoles);
            OreSiUser user = serviceContainer.authenticationService().deleteUserRightUserManager(UUID.fromString(oreSiUserRoleUserManager.userId()), application);
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiUserRoleUserManager.userId()));
        }
        throw new NotOpenAdomAdminException();
    }

    /**
     * Garde-fou : empêche l'utilisateur courant de se révoquer lui-même
     * un rôle de gestion ( applicationManager ou userManager ) sur une
     * application . L'auto-révocation reste autorisée si l'utilisateur
     * courant est {@code openAdomAdmin} ( il a les moyens de revenir
     * sur la modification ) .
     *
     * @param targetUserId id de l'utilisateur cible de la révocation
     * @param currentUserRoles rôles de l'utilisateur courant
     * @throws CantSelfRevokeApplicationRoleException si l'utilisateur
     *         courant tente de se révoquer lui-même sans être openAdomAdmin
     */
    private void assertNotSelfRevoke(final String targetUserId, final CurrentUserRoles currentUserRoles) {
        if (currentUserRoles.isOpenAdomAdmin()) {
            return;
        }
        final UUID currentUserId = OreSiApiRequestContext.getRequestUserId();
        if (currentUserId != null && currentUserId.toString().equals(targetUserId)) {
            throw new CantSelfRevokeApplicationRoleException();
        }
    }

    private OreSiUserResult deleteAdminRoleUser(final OreSiRoleForUser oreSiRoleForUserAdmin) {
        CurrentUserRoles currentUserRoles = serviceContainer.authenticationService().getCurrentUserRoles();
        if (currentUserRoles.isOpenAdomAdmin()) {
            final OreSiUser user = serviceContainer.authenticationService().deleteUserRightopenAdomAdmin(UUID.fromString(oreSiRoleForUserAdmin.userId()));
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiRoleForUserAdmin.userId()));
        }
        throw new NotOpenAdomAdminException();
    }

    @Transactional
    public OreSiUserResult addSystemRoleUser(final OreSiRoleForUser roleForUser) {
        serviceContainer.authenticationService().setRoleAdmin();
        try {
            if (OreSiRole.openAdomAdmin().getAsSqlRole().equals(roleForUser.role())) {
                return addAdminRoleUser(roleForUser);
            } else if (OreSiRole.applicationCreator().getAsSqlRole().equals(roleForUser.role())) {
                return addApplicationCreatorRoleUser(roleForUser);
            }
            throw new BadRoleException("cantSetSystemRole", roleForUser.role());
        } finally {
            // Cf. deleteSystemRoleUser : finally obligatoire pour eviter de
            // laisser la connexion Postgres en openAdomAdmin apres exception
            // ( fuite de privilege via le pool Hikari ).
            serviceContainer.authenticationService().setRoleForClient();
        }
    }

    @Transactional
    public OreSiUserResult addApplicationRoleUser(final OreSiRoleForUser roleForUser, Application application) {
        serviceContainer.authenticationService().setRoleAdmin();
        try {
            if (OreSiRole.applicationManagerOf(application).getAsSqlRole().contains(roleForUser.role())) {
                return addApplicationManagerRoleUser(roleForUser, application);
            } else if (OreSiRole.userManagerOf(application).getAsSqlRole().contains(roleForUser.role())) {
                return addUserManagerRoleUser(roleForUser, application);
            }
            throw new BadApplicationRoleException("cantSetApplicationRole", roleForUser.role(), application);
        } finally {
            serviceContainer.authenticationService().setRoleForClient();
        }
    }

    private OreSiUserResult addApplicationCreatorRoleUser(final OreSiRoleForUser oreSiUserRoleApplicationCreator) {
        OreSiUser user = serviceContainer.authenticationService().addUserRightCreateApplication(UUID.fromString(oreSiUserRoleApplicationCreator.userId()), oreSiUserRoleApplicationCreator.applicationPattern());
        return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiUserRoleApplicationCreator.userId()));
    }

    private OreSiUserResult addApplicationManagerRoleUser(final OreSiRoleForUser oreSiUserRoleApplicationManager, Application application) {
        CurrentUserRoles currentUserRoles = serviceContainer.authenticationService().getCurrentUserRoles();
        if (currentUserRoles.applicationManagerOf(application)) {
            OreSiUser user = serviceContainer.authenticationService().addUserRightApplicationManager(UUID.fromString(oreSiUserRoleApplicationManager.userId()), application);
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiUserRoleApplicationManager.userId()));
        }
        throw new NotOpenAdomAdminException();
    }

    private OreSiUserResult addUserManagerRoleUser(final OreSiRoleForUser oreSiUserRoleUserManager, Application application) {
        CurrentUserRoles currentUserRoles = serviceContainer.authenticationService().getCurrentUserRoles();
        if (currentUserRoles.applicationManagerOf(application)) {
            OreSiUser user = serviceContainer.authenticationService().addUserRightUserManager(UUID.fromString(oreSiUserRoleUserManager.userId()), application);
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiUserRoleUserManager.userId()));
        }
        throw new NotOpenAdomAdminException();
    }

    private OreSiUserResult addAdminRoleUser(final OreSiRoleForUser oreSiRoleForUserAdmin) {
        CurrentUserRoles currentUserRoles = serviceContainer.authenticationService().getCurrentUserRoles();
        if (currentUserRoles.isOpenAdomAdmin()) {
            final OreSiUser user = serviceContainer.authenticationService().addUserRightopenAdomAdmin(UUID.fromString(oreSiRoleForUserAdmin.userId()));
            return new OreSiUserResult(user, userRepository.getRolesForRole(oreSiRoleForUserAdmin.userId()));
        }
        throw new NotOpenAdomAdminException();
    }

    public AuthorizationsResult getAuthorizationsForUserAndPublic(final String applicationNameOrUuid, final String userLoginOrId) {
        Application application = repository.application().findApplication(applicationNameOrUuid);
        CurrentUserRoles currentUserRoles = serviceContainer.authenticationService().getCurrentUserRoles(userLoginOrId);
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

    public ImmutableSet<GetAuthorizationAdditionalFilesResult> getAdditionalFilesuthorizations(final String applicationNameOrId, final AuthorizationsAdditionalFilesResult authorizationsForUser, final MultiValueMap<String, String> params) {
        final Application application = repository.application().findApplication(applicationNameOrId);
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

    private GetAuthorizationAdditionalFilesResult toGetAdditionalFilesAuthorizationResult(
            final OreSiAdditionalFileAuthorization oreSiAuthorization,
            final List<OreSiAdditionalFileAuthorization> publicAuthorizations,
            final AuthorizationsAdditionalFilesResult authorizationsForUser) {
        final List<OreSiUser> all = userRepository.findAll();
        return new GetAuthorizationAdditionalFilesResult(
                oreSiAuthorization.getId(),
                oreSiAuthorization.getName(),
                getOreSIUSers(all, oreSiAuthorization.getOreSiUsers()),
                oreSiAuthorization.getApplication(),
                oreSiAuthorization.getAdditionalFiles()
        );
    }
    public ImmutableSortedSet<GetGrantableResult.User> getGrantableUsers() {
        return userRepository.findAll().stream()
                .map(oreSiUserEntity -> new GetGrantableResult.User(oreSiUserEntity.getId(), oreSiUserEntity.getLogin()))
                .collect(ImmutableSortedSet.toImmutableSortedSet(Comparator.comparing(GetGrantableResult.User::label)));
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
                .map(authByTypeEntry -> {
                    if (!isApplicationCreator) {
                        removeAuthorizationAdditionalFilesThatCantBeModified(authByTypeEntry, authorizationListForCurrentUser);
                    }
                    return authByTypeEntry;
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
        repository.getRepository(application).authorizationAdditionalFiles().findPublicAuthorizations();
        List<OreSiAdditionalFileAuthorization> authorizations = repository.getRepository(application).authorizationAdditionalFiles()
                .findAuthorizations(UUID.fromString(Optional.ofNullable(rolesForCurrentUser).map(CurrentUserRoles::userLogin).orElse("")));
        final Map<OperationAdditionalFileType, List<String>> authorizationMap = new EnumMap<>(OperationAdditionalFileType.class);
        authorizations
                .forEach(authorizationList -> authorizationList.getAdditionalFiles().forEach((key, value) -> value.
                        forEach(authorizationResult -> authorizationMap
                                .computeIfAbsent(key, k -> new LinkedList<>())
                                .add(authorizationResult))));
        return new AuthorizationsAdditionalFilesResult(authorizationMap, application.getName(), isAdministrator);
    }

    public List<OreSiAdditionalFileAuthorization> findUserAdditionalFilesAuthorizationsForApplicationAndDataType(final Application application) {
        UUID currentUserId = OreSiApiRequestContext.getRequestUserId();
        final AuthorizationAdditionalFilesRepository authorizationRepository = repository.getRepository(application).authorizationAdditionalFiles();
        return authorizationRepository.findAuthorizations(currentUserId);
    }

    public CreateAuthorizationRequest createAuthorizationRequestWithDependantAuthorization(
            Application application,
            CreateAuthorizationRequest createAuthorizationRequest) {
        // Ticket #521 : l'enrichissement automatique du payload avec les
        // autorisations dépendantes ( {@code addDependantAuthorizations} )
        // est nécessaire pour que les requêtes SQL puissent résoudre les
        // FK croisées vers les référentiels dépendants ( cf
        // {@code BinaryFileService.getFilesOnRepository} qui itère sur
        // {@code requiredAuthorizations} - un payload sans extraction sur
        // un ref dépendant produirait un 5xx serveur en runtime ) .
        //
        // <p>Le contrat WYSIWYG vis-à-vis de l'utilisateur reste préservé
        // côté frontend via {@code buildAuthorization} (
        // {@code DataTypeAuthorizationInfoView.vue} ) qui n'envoie au backend
        // QUE ce qui est explicitement coché dans l'UI . Le backend ajoute
        // ensuite les dépendances RLS-nécessaires à l'écriture en base ,
        // sans que cela ne se transforme en "extractions fantômes" au
        // reload : à la lecture suivante , l'UI affiche bien ce qui a été
        // saisi initialement ( les extractions auto-ajoutées sont des
        // dépendances impliquées par les choix utilisateur , et restent
        // visibles tant qu'un droit racine les nécessite ) .
        //
        // <p>{@code addRequiredOperationTypes} applique en sus la hiérarchie
        // intra-ligne ( {@code delete > depot/publication > extraction} +
        // miroir auto {@code depot ↔ publication} ) , déléguée au helper
        // {@code OperationTypeHierarchy.normalize} .
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
        Function<String, Boolean> isVersionningStrategy = application::strategyIsVersionning;
        return Objects.requireNonNull(createAuthorizationRequest)
                .addRequiredOperationTypes(isVersionningStrategy)
                .addDependantAuthorizations(dependantsNodes);
    }

    public AuthorizationRequest createAuthorizationRequestToAuthorizationRequest(
            CreateAuthorizationRequest createAuthorizationRequestWithDependantAuthorization,
            Application application,
            List<UUID> userIds,
            List<OreSiAuthorization> authorizationsForCurrentUser,
            List<AuthorizationRequestError> errors) {
        return new AuthorizationRequestBuilder(
                application,
                userIds,
                authorizationsForCurrentUser,
                errors
        )
                .build(createAuthorizationRequestWithDependantAuthorization);
    }

    public OreSiUser getCurrentUser() {
        return userRepository.findById(OreSiApiRequestContext.getRequestUserId());
    }

    private AuthorizationsForApplicationUser getAuthorizationsForApplicationUser(Application application) {
        OreSiUser currentUser = getCurrentUser();
        CurrentUserRoles currentUserRoles = serviceContainer.authenticationService().getCurrentUserRoles();
        boolean isApplicationManager = currentUserRoles.applicationManagerOf(application);
        boolean isUserManager = currentUserRoles.userManagerOf(application);
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
        CurrentUserRoles currentUserRoles = serviceContainer.authenticationService().getCurrentUserRoles(currentUser.getId().toString());
        Set<String> applicationCreator = currentUser.getAuthorizations();
        return new AuthorizationsForSystemUser(currentUserRoles, applicationCreator);
    }

    @Override
    public PrivilegeAssessorDomainForSystem getPrivilegeAssessorForSystem(
            PrivilegeSystemDomainEnum privilegeSystemDomainEnum
    ) {
        return switch (privilegeSystemDomainEnum) {
            case SYSTEM_ADMINISTRATION -> {
                AuthorizationsForSystemUser authorizations = getAuthorizationsForSystemUser();
                yield PrivilegeAssessorBuilder.forSystem(
                        authorizations,
                        privilegeSystemDomainEnum
                );
            }
            case SYSTEM_USER_CONNECTED -> {
                AuthorizationsForSystemUser authorizations = getAuthorizationsForSystemUser();
                yield PrivilegeAssessorBuilder.forUser(
                        authorizations,
                        privilegeSystemDomainEnum
                );
            }
            case SYSTEM_USER_NOT_CONNECTED ->
                    throw new OreSiTechnicalException(ExceptionMessage.SYSTEM_USER_NOT_CONNECTED.toMessage());
            case AUTHENTICATION_MANAGEMENT -> null;
        };
    }

    public PrivilegeAssessorDomainForNotConnectedUser<PrivilegeSystemDomainEnum> getPrivilegeAssessorForNotConnecteduser(PrivilegeSystemDomainEnum privilegeDomain) {
        return PrivilegeAssessorBuilder.forNotConnectedUser(
                serviceContainer.authenticationService(),
                userRepository,
                privilegeDomain
        );
    }

    @Override
    public PrivilegeAssessorDomainForApplication getPrivilegeAssessorForApplication(
            PrivilegeApplicationDomainEnum privilegeApplicationDomainEnum,
            String applicationNameOrUuid
    ) {
        Application application = repository.application().findApplication(applicationNameOrUuid);
        AuthorizationsForApplicationUser authorizations = getAuthorizationsForApplicationUser(application);
        GetGrantableResult grantable = getGrantable(
                application.getName(),
                getAuthorizationsForUserAndPublic(
                        application.getName(),
                        serviceContainer.authenticationService().getCurrentUserRoles().userLogin()
                )
        );
        return PrivilegeAssessorBuilder.forApplication(
                authorizations,
                privilegeApplicationDomainEnum,
                application,
                grantable
        );
    }

    public Map<String, Map<AuthorizationsForUserResult.Roles, Boolean>> getAuthorizationsDataRights(
            final Application application,
            final Set<String> datatypes) {
        PrivilegeAssessorDomainForApplication<PrivilegeApplicationDomainEnum> privilegeAssessorForApplication = getPrivilegeAssessorForApplication(DATA_ACCESS, application.getName());
        return datatypes.stream()
                .map(dty -> getAuthorizationsDataRights(dty, privilegeAssessorForApplication))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    public Map.Entry<String, Map<AuthorizationsForUserResult.Roles, Boolean>> getAuthorizationsDataRights(
            final String dataName,
            PrivilegeAssessorDomainForApplication<PrivilegeApplicationDomainEnum> privilegeAssessorForApplication) {
        final Map<AuthorizationsForUserResult.Roles, Boolean> roleForDatatype = privilegeAssessorForApplication
                .getAuthorizationsForUser(dataName);
        return new AbstractMap.SimpleEntry<>(dataName, roleForDatatype);
    }

    public record Authorizations(OreSiAuthorization previous, OreSiAuthorization next) {
        public Set<UUID> getPreviousUsers() {
            return Optional.ofNullable(previous()).map(OreSiAuthorization::getOreSiUsers).orElseGet(Set::of);
        }
    }

}