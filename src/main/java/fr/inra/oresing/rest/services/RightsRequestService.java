package fr.inra.oresing.rest.services;

import com.google.common.collect.ImmutableSortedSet;
import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.application.configuration.RightRequestDescription;
import fr.inra.oresing.domain.authorization.AuthorizationParsed;
import fr.inra.oresing.domain.authorization.GetGrantableResult;
import fr.inra.oresing.domain.authorization.request.AuthorizationRequest;
import fr.inra.oresing.domain.exceptions.OreSiTechnicalException;
import fr.inra.oresing.domain.rightsrequest.RightsRequest;
import fr.inra.oresing.domain.rightsrequest.TreatmentDecision;
import fr.inra.oresing.mail.rightsrequest.RightsRequestNotificationService;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.RightsRequestRepository;
import fr.inra.oresing.persistence.RightsRequestSearchHelper;
import fr.inra.oresing.persistence.UserRepository;
import fr.inra.oresing.rest.OreSiApiRequestContext;
import fr.inra.oresing.rest.model.authorization.exception.AuthorizationRequestError;
import fr.inra.oresing.rest.model.rightsrequest.*;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.*;

@Slf4j
@Component
@Transactional(readOnly = true)
public class RightsRequestService {

    @Setter
    private ServiceContainer serviceContainer;

    private final OreSiRepository repository;
    private final RightsRequestNotificationService notificationService;
    private final UserRepository userRepository;
    private final RightsRequestAuthorizationGranter authorizationGranter;

    public RightsRequestService(OreSiRepository repository,
                                ServiceContainer serviceContainer,
                                RightsRequestNotificationService notificationService,
                                UserRepository userRepository,
                                RightsRequestAuthorizationGranter authorizationGranter) {
        this.repository = repository;
        this.serviceContainer = serviceContainer;
        this.notificationService = notificationService;
        this.userRepository = userRepository;
        this.authorizationGranter = authorizationGranter;
    }

    void addRightsRequest(final Application app, final String refType, final MultipartFile file, final UUID fileId) {
        RightsRequestRepository rightsRequestRepository = repository.getRepository(app).rightsRequestRepository();
    }

    List<RightsRequest> findRightsRequests(final Application application, final RightsRequestInfos rightsRequestInfos) {
        RightsRequestSearchHelper rightsRequestSearchHelper = new RightsRequestSearchHelper(application, rightsRequestInfos);
        String where = rightsRequestSearchHelper.buildWhereRequest();
        serviceContainer.authenticationService().setRoleForClient();
        return repository
                .getRepository(application)
                .rightsRequestRepository().findByCriteria(rightsRequestSearchHelper);
    }

    private Application getApplication(final String nameOrId) {
        serviceContainer.authenticationService().setRoleForClient();
        return repository.application().findApplication(nameOrId);
    }

    public GetRightsRequestResult findRightsRequest(final String nameOrId, final RightsRequestInfos rightsRequestInfos) {
        Application application = serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(nameOrId);
        final RightRequestDescription description = application.getConfiguration().rightsRequest();
        List<RightsRequest> rightsRequests = serviceContainer.rightsRequestService().findRightsRequests(application, rightsRequestInfos);
        List<RightsRequestResult> rightsRequestResult = rightsRequests.stream()
                .map(rightsRequest ->
                        getRightsRequestResult(rightsRequest, application)
                )
                .toList();
        ImmutableSortedSet<GetGrantableResult.User> grantableUsers = serviceContainer.authorizationService().getGrantableUsers();
        return new GetRightsRequestResult(grantableUsers, rightsRequestResult, description);
    }

    private RightsRequestResult getRightsRequestResult(final RightsRequest rightsRequest, final Application application) {
        Map<String, List<AuthorizationParsed>> authorizationsParsed = new HashMap<>();
        AuthorizationService.authorizationsToParsedAuthorizations(
                List.of(rightsRequest.getRightsRequest()),
                authorizationsParsed);
        return new RightsRequestResult(
                rightsRequest,
                authorizationsParsed,
                resolveTreatedByLogin(rightsRequest.getTreatedBy()),
                resolveUserEmail(rightsRequest.getUser())
        );
    }

    /**
     * Résout l'email du demandeur ( affichage UI uniquement ). Renvoie
     * {@code null} si l'utilisateur a été supprimé.
     */
    private String resolveUserEmail(final UUID userId) {
        if (userId == null) {
            return null;
        }
        try {
            return Optional.ofNullable(userRepository.findById(userId))
                    .map(OreSiUser::getEmail)
                    .orElse(null);
        } catch (final Exception e) {
            log.warn("Cannot resolve requester email for user {} : {}", userId, e.toString());
            return null;
        }
    }

    /**
     * Résout le login du gestionnaire ayant traité une demande à partir de
     * son UUID. Renvoie {@code null} si la demande n'a pas été traitée ou
     * si l'utilisateur n'est plus en base ( cas de suppression ).
     */
    private String resolveTreatedByLogin(final UUID treatedBy) {
        if (treatedBy == null) {
            return null;
        }
        try {
            return Optional.ofNullable(userRepository.findById(treatedBy))
                    .map(OreSiUser::getLogin)
                    .orElse(null);
        } catch (final Exception e) {
            log.warn("Cannot resolve treatedBy login for user {} : {}", treatedBy, e.toString());
            return null;
        }
    }


    @Transactional()
    public UUID createOrUpdate(final CreateRightsRequestRequest createRightsRequestRequest, final String nameOrId) {
        serviceContainer.authenticationService().setRoleForClient();
        final Application application = serviceContainer.applicationService().getApplicationOrApplicationAccordingToRights(nameOrId);

        RightsRequest rightsRequest = Optional.of(createRightsRequestRequest)
                .map(CreateRightsRequestRequest::id)
                .map(id -> repository.getRepository(application).rightsRequestRepository().findById(id))
                .orElseGet(RightsRequest::new);
        rightsRequest.setRightsRequestForm(createRightsRequestRequest.fields());
        rightsRequest.setApplication(application.getId());
        rightsRequest.setComment(createRightsRequestRequest.comment());
        rightsRequest.setSetted(createRightsRequestRequest.setted());
        rightsRequest.setId(rightsRequest.getId() == null ? UUID.randomUUID() : rightsRequest.getId());
        OreSiAuthorization authorizations = Optional.of(createRightsRequestRequest)
                .map(CreateRightsRequestRequest::rightsRequest)
                .map(authorization -> {
                    List<AuthorizationRequestError> errors = new ArrayList<>();
                    AuthorizationRequest authorizationRequestToAuthorizationRequest = serviceContainer.authorizationService().createAuthorizationRequestToAuthorizationRequest(
                            authorization,
                            application,
                            List.of(serviceContainer.authorizationService().getCurrentUser().getId()),
                            List.of(),
                            errors
                    );
                    OreSiAuthorization oreSiAuthorization = new OreSiAuthorization();
                    oreSiAuthorization.setId(rightsRequest.getId());
                    oreSiAuthorization.setApplication(application.getId());
                    oreSiAuthorization.setAuthorizations(authorizationRequestToAuthorizationRequest.buildAuthorizationsByDataname());
                    return oreSiAuthorization;
                })
                .orElse(null);
        rightsRequest.setRightsRequest(authorizations);
        rightsRequest.setUser(rightsRequest.getUser() == null ? OreSiApiRequestContext.getRequestUserId() : rightsRequest.getUser());
        if (authorizations != null) {
            authorizations.setOreSiUsers(Set.of(rightsRequest.getUser()));
        }
        serviceContainer.authenticationService().setRoleForClient();
        final UUID storedRequestId = repository.getRepository(application).rightsRequestRepository().store(rightsRequest);

        // #487 Phase 1 : notification du demandeur + des gestionnaires de l'application.
        // Strict scope : seuls les applicationManager / userManager de cette application
        // sont destinataires (cf. RightsRequestNotificationService). Fire-and-forget,
        // toute erreur est journalisée mais ne fait pas échouer la création de la demande.
        try {
            final OreSiUser requester = serviceContainer.authenticationService().getCurrentUser();
            notificationService.notifyRequestSubmitted(
                    application,
                    storedRequestId,
                    requester,
                    rightsRequest.getComment(),
                    LocaleContextHolder.getLocale()
            );
        } catch (final Exception e) {
            log.error("Rights request notification dispatch failed for requestId={} : {}",
                    storedRequestId, e.toString(), e);
        }

        return storedRequestId;
    }

    /**
     * Marque une demande de droits comme traitée et associe le gestionnaire
     * courant au traitement ( #487 Phase 3 ).
     *
     * <p>Périmètre actuel : on persiste uniquement le marqueur {@code setted}
     * et l'identifiant du gestionnaire ; la {@code updateDate} est mise à
     * jour automatiquement par le trigger SQL. L'attribution effective des
     * autorisations sélectionnées et l'envoi du mail de notification au
     * demandeur sont prévus dans une étape ultérieure.</p>
     *
     * @return la demande mise à jour, sérialisée pour l'affichage frontend
     * @throws OreSiTechnicalException si la demande est introuvable
     */
    @Transactional()
    public RightsRequestResult treat(final String nameOrId, final UUID requestId,
                                     final TreatRightsRequestRequest body) {
        serviceContainer.authenticationService().setRoleForClient();
        final Application application = serviceContainer.applicationService()
                .getApplicationOrApplicationAccordingToRights(nameOrId);

        // Garde-fou metier ( #487 Phase 4 ) : seul un applicationManager ou un
        // userManager de l'application cible peut valider une demande de droits.
        // Le controleur n'expose que `@PreAuthorize("isAuthenticated()")` ; sans
        // cette verification cote service, n'importe quel utilisateur authentifie
        // pourrait s'auto-attribuer les autorisations listees dans le payload
        // ( linkedAuthorizationIds ) via la phase 4 ( grant effectif ).
        // openAdomAdmin reste autorise par convention ( administration globale ).
        final fr.inra.oresing.domain.repository.authorization.role.CurrentUserRoles currentUserRoles =
                serviceContainer.authenticationService().getCurrentUserRoles();
        final boolean isManager = currentUserRoles.applicationManagerOf(application)
                || currentUserRoles.userManagerOf(application)
                || currentUserRoles.isOpenAdomAdmin();
        if (!isManager) {
            throw new fr.inra.oresing.domain.authorization.privilegeassessor.exception.NotApplicationUserManagerRightsException(
                    application.getName());
        }

        final RightsRequestRepository rightsRequestRepository = repository.getRepository(application).rightsRequestRepository();

        final RightsRequest rightsRequest = Optional.ofNullable(rightsRequestRepository.findById(requestId))
                .orElseThrow(() -> new OreSiTechnicalException(
                        "Rights request " + requestId + " not found for application " + application.getName()));

        // Verrou strict ( #487 ) : une demande deja traitee ne peut plus
        // jamais etre re-validee, ni cote frontend ( icone oeil seulement ),
        // ni cote API. Renvoi explicite plutot que de laisser passer un
        // overwrite silencieux des champs treatmentDecision / treatedBy /
        // updateDate, qui falsifierait l'audit.
        if (rightsRequest.isSetted()) {
            throw new OreSiTechnicalException(
                    "Rights request " + requestId + " has already been treated and cannot be modified");
        }

        rightsRequest.setSetted(true);
        final OreSiUser currentUser = serviceContainer.authenticationService().getCurrentUser();
        if (currentUser != null) {
            rightsRequest.setTreatedBy(currentUser.getId());
        }
        // Décision normalisée + payload du traitement persistés tels quels :
        // ils permettent au frontend de réafficher la page en consultation
        // seule sans avoir à les recalculer.
        final TreatmentDecision decision = TreatmentDecision.fromNullable(body.status());
        rightsRequest.setTreatmentDecision(decision.name());
        rightsRequest.setTreatmentComment(body.treatmentComment());
        rightsRequest.setTreatmentMailSubject(body.suppressMail() ? null : body.mailSubject());
        rightsRequest.setTreatmentMailBody(body.suppressMail() ? null : body.mailBody());
        rightsRequest.setLinkedAuthorizationIds(
                decision == TreatmentDecision.REJECTED || body.linkedAuthorizationIds() == null
                        ? List.of()
                        : List.copyOf(body.linkedAuthorizationIds()));
        rightsRequestRepository.store(rightsRequest);
        final RightsRequest updated = rightsRequestRepository.findById(requestId);

        // Phase 4 ( #487 ) : sur APPROVED, ajoute le demandeur dans la liste
        // des bénéficiaires de chaque autorisation cochée par le gestionnaire.
        // Délégué à un service dédié ( idempotence, tolérance aux suppressions,
        // audit ). S'exécute dans la transaction parente : un échec ici annule
        // le marquage setted=true et empêche l'envoi des mails ci-dessous.
        if (decision == TreatmentDecision.APPROVED) {
            authorizationGranter.grantAll(application, updated.getUser(), updated.getLinkedAuthorizationIds());
        }

        // Notifications fire-and-forget ( un échec n'invalide pas la
        // persistance du traitement, cf. RightsRequestNotificationService ) :
        //  - mail au demandeur ( contenu = texte saisi par le gestionnaire )
        //  - mail à tous les applicationManager / userManager de l'application
        //    ( template d'audit, traçabilité interne ).
        try {
            final OreSiUser requester = userRepository.findById(updated.getUser());
            notificationService.notifyRequestTreated(
                    application,
                    requester,
                    currentUser,
                    decision,
                    body.mailSubject(),
                    body.mailBody(),
                    body.suppressMail(),
                    LocaleContextHolder.getLocale()
            );
        } catch (final Exception e) {
            log.error("Treatment notification dispatch failed for requestId={} : {}",
                    requestId, e.toString(), e);
        }

        return getRightsRequestResult(updated, application);
    }
}