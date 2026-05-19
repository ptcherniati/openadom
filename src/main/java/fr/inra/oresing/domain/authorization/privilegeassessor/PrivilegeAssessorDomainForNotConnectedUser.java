package fr.inra.oresing.domain.authorization.privilegeassessor;


import com.google.common.base.Strings;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.AuthenticationServiceImpl;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.*;
import fr.inra.oresing.domain.repository.user.file.UserRepository;
import fr.inra.oresing.domain.exceptions.AuthenticationFailure;
import fr.inra.oresing.domain.user.CreateUserRequest;
import fr.inra.oresing.domain.authorization.LoginAdminResult;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record PrivilegeAssessorDomainForNotConnectedUser<P extends PrivilegeSystemDomainEnum>(
        AuthenticationServiceImpl authenticationService,
        UserRepository userRepository,
        P domain
) implements PrivilegeAssessorDomain {

    public LoginAdminResult forLoginPassword(String login, String password) throws AuthenticationFailure {
        return authenticationService().login(login, password);
    }

    /**
     * Dispatch d'une requete {@code PUT /users} vers le bon
     * {@link NotConnectedUser} en fonction du contenu du payload .
     *
     * <p>Branches supportees :
     * <ul>
     *   <li>{@code login + password [+ verificationKey]} : activation / modif
     *       compte connecte ;</li>
     *   <li>{@code login + email [+ charte / verificationKey]} : changement
     *       d'email , signature de charte , ou reinitialisation password
     *       avec cle ;</li>
     *   <li>{@code email seul} : step 1 "mot de passe oublie" - envoi de la
     *       cle de validation par email ( cf {@link #dispatchForgotPasswordStep1} ) ;</li>
     *   <li>autre : {@link NotConnectedUnauthentifiedUser} ( -&gt; 401 ) .</li>
     * </ul>
     */
    public NotConnectedUser forUpdateUser(CreateUserRequest createUserRequest) throws AuthenticationFailure {
        final String login = createUserRequest.getLogin();
        final String password = createUserRequest.getPassword();
        final String email = createUserRequest.getEmail();
        final String verificationKey = createUserRequest.getVerificationKey();
        final String charte = createUserRequest.getCharte();
        if (!Strings.isNullOrEmpty(login) && !Strings.isNullOrEmpty(password)) {
            final LoginAdminResult loginAdminResult = authenticationService().checkLoginPassword(login, password);
            final OreSiUser user = userRepository.findById(loginAdminResult.id());
            if (Strings.isNullOrEmpty(verificationKey)) {
                return new NotConnectedAuthentifiedActiveUser(user, createUserRequest);
            } else if (OreSiUser.OreSiUserStates.active == OreSiUser.OreSiUserStates.valueOf(loginAdminResult.state())) {
                return new NotConnectedAuthentifiedActiveUser(user, createUserRequest);
            } else {
                return new NotConnectedAuthentifiedIdleUser(user, createUserRequest);
            }
        } else if (!Strings.isNullOrEmpty(login) && !Strings.isNullOrEmpty(email)) {
            final Optional<OreSiUser> loginResult =
                    userRepository.findByLoginAndEmail(login, email);
            if (loginResult.isEmpty()) {
                throw new AuthenticationFailure(
                        AuthenticationFailure.INVALID_ACCOUNT,
                        new LoginAdminResult(null,
                                login,
                                email,
                                "",
                                null,
                                Set.of(),
                                Map.of()
                        ));
            }
            if (!Strings.isNullOrEmpty(charte)) {
                return new NotConnectedAuthentifiedActiveUserNotSignedCharte(loginResult.get(), createUserRequest, charte);
            } else if (!Strings.isNullOrEmpty(verificationKey)) {
                return new NotConnectedAuthentifiedMissingPasswordUser(loginResult.get(), createUserRequest);
            } else {
                return new NotConnectedAuthentifiedPendingUser(loginResult.get());
            }
        } else if (!Strings.isNullOrEmpty(email)) {
            return dispatchForgotPasswordStep1(createUserRequest, email);
        }
        return new NotConnectedUnauthentifiedUser(createUserRequest);
    }

    public NotConnectedUnauthentifiedUserForCreate forCreateUser() {
        return new NotConnectedUnauthentifiedUserForCreate();
    }

    // ---------------------------------------------------------------- //
    //  helpers                                                         //
    // ---------------------------------------------------------------- //

    /**
     * Step 1 du flux "mot de passe oublie" : seul l'email a ete saisi
     * par l'utilisateur dans la modal de reinitialisation . On lookup le
     * user par email ( case + whitespace insensitive cote helper ) , puis :
     * <ul>
     *   <li>user trouve -&gt; {@link NotConnectedAuthentifiedPendingUser}
     *       qui declenche l'envoi d'un {@link AuthenticationFailure} via
     *       {@code AuthenticationService.sendValidationKey} avec une cle
     *       de validation a usage unique ;</li>
     *   <li>user introuvable -&gt; {@link NotConnectedUnauthentifiedUser}
     *       qui throw . Le frontend gere ce throw en affichant tout de
     *       meme le toast optimiste "email envoye" ( strategie security
     *       through obscurity standard : ne pas leaker l'existence d'un
     *       compte par la difference de comportement UI ) .</li>
     * </ul>
     *
     * <p><b>Dette technique connue</b> ( task TECH-DEBT issue suivante ) :
     * timing-attack possible ( latence DB+SMTP &gt; latence DB-miss seul ) ,
     * pas de rate-limit anti-spam , pas d'audit log "qui a demande quel
     * reset depuis quelle IP" . Voir refacto sealed-interface
     * {@code UpdateUserIntent} pour traiter ces 3 sujets ensemble .
     *
     * @param request payload original ( reuse pour la branche unauthentified )
     * @param email   email saisi ( non null , non vide - garde verifiee par
     *                le caller )
     */
    private NotConnectedUser dispatchForgotPasswordStep1(CreateUserRequest request, String email) {
        final String normalized = normalizeEmail(email);
        return userRepository.findByEmail(normalized)
                .<NotConnectedUser>map(NotConnectedAuthentifiedPendingUser::new)
                .orElseGet(() -> new NotConnectedUnauthentifiedUser(request));
    }

    /**
     * Normalisation defensive avant lookup en base : trim () + lowercase
     * pour matcher les emails saisis avec espaces , casse mixte , etc .
     * Bien que la requete SQL applique deja {@code lower()} cote BDD ,
     * cette normalisation locale evite les write-side bugs et garde la
     * convention metier "email est stocke / compare en lowercase" .
     *
     * <p>Package-private pour permettre des tests unitaires sans avoir
     * a passer par tout le dispatcher .
     */
    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
