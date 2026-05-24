package fr.inra.oresing.domain.exceptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fr.inra.oresing.OreSiException;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.LoginAdminResult;
import fr.inra.oresing.domain.user.CreateUserRequest;
import lombok.Getter;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Getter
@JsonIgnoreProperties({"suppressed", "stackTrace", "cause"})
public class AuthenticationFailure extends OreSiException {

    public static final String EXISTING_LOGIN = "EXISTING_LOGIN";
    public static final String EXISTING_EMAIL = "EXISTING_EMAIL";
    public static final String STATE_ERROR = "STATE_ERROR";
    public static final String INACTIVE_ACCOUNT = "INACTIVE_ACCOUNT";
    public static final String PENDING_ACCOUNT = "PENDING_ACCOUNT";
    public static final String CLOSED_ACCOUNT = "CLOSED_ACCOUNT";
    public static final String INVALID_ACCOUNT = "INVALID_ACCOUNT";

    public static final String BAD_LOGIN_PASSWORD = "BAD_LOGIN_PASSWORD";
    /**
     * Mot de passe actuel KO dans un flow d'update de compte ( ex
     * changement d'email / mot de passe utilisateur deja authentifie ) .
     * Distinct de {@link #BAD_LOGIN_PASSWORD} qui est specifique au
     * login : meme erreur metier mais code HTTP different ( 422 vs 401 )
     * pour eviter que le frontend {@code Fetcher.ts} declenche son
     * auto-logout global sur 401 quand l'utilisateur entre juste un
     * mauvais mot de passe dans un formulaire d'edition .
     */
    public static final String BAD_CURRENT_PASSWORD = "BAD_CURRENT_PASSWORD";
    public static final String BAD_VALIDATION_KEY = "BAD_VALIDATION_KEY";
    public static final String BAD_LOGIN_OR_EMAIL_PASSWORD = "BAD_LOGIN_OR_EMAIL_PASSWORD";
    public static final String BAD_PASSWORDS = "BAD_PASSWORDS";
    /**
     * Phase 1 d'un changement d'email appelee sans changement effectif :
     * email cible identique a l'email courant , aucun mot de passe a changer ,
     * aucune cle de validation . L'appel est un no-op et NE DOIT PAS provoquer
     * d'envoi de mail ni de toast de succes cote frontend . On remonte donc
     * une erreur metier explicite ( 422 ) pour que le client affiche un
     * message clair plutot que de croire qu'un mail a ete envoye .
     */
    public static final String EMAIL_UNCHANGED = "EMAIL_UNCHANGED";
    public static final String CONSTANT_LOGIN = "login";
    public static final String CONSTANT_EMAIL = "email";
    public static final String CONSTANT_ID = "id";
    public static final String CONSTANT_STATE = "state";
    public static final String CONSTANT_SUBMISSION_SCOPE = "submissionScope";

    private final Map<String, ? extends Serializable> params;

    public AuthenticationFailure(String message, CreateUserRequest userRequest) {
        super(message);
        // {@code Map.of(...)} refuse les valeurs null -> NPE si le payload ne
        // contient pas a la fois login ET email ( ex flow forgot-password ou
        // login est null ) . On utilise un HashMap mutable et on n'insere que
        // les champs effectivement renseignes pour eviter ce piege .
        final java.util.HashMap<String, Serializable> built = new java.util.HashMap<>();
        Optional.ofNullable(userRequest).ifPresent(lr -> {
            if (lr.getLogin() != null) built.put(CONSTANT_LOGIN, lr.getLogin());
            if (lr.getEmail() != null) built.put(CONSTANT_EMAIL, lr.getEmail());
        });
        params = built;
    }


    public AuthenticationFailure(final String message, final OreSiUser loginResult) {
        super(message);
        params = Optional.ofNullable(loginResult)
                .map(lr -> Map.of(
                                CONSTANT_LOGIN, lr.getLogin(),
                                CONSTANT_EMAIL, lr.getEmail(),
                                CONSTANT_ID, lr.getId(),
                                CONSTANT_STATE, "",
                                CONSTANT_SUBMISSION_SCOPE, Optional.ofNullable(lr.getAuthorizations()).map(as -> String.join(",", as)).orElse("")
                        )
                )
                .orElseGet(Map::of);
    }

    public AuthenticationFailure(final String message, final LoginAdminResult loginAdminResult) {
        super(message);
        params = getParams(loginAdminResult);
    }

    private static Map<String, String> getParams(final LoginAdminResult loginAdminResult) {
        return Optional.ofNullable(loginAdminResult)
                .map(lr -> Map.of(
                                CONSTANT_LOGIN, Optional.ofNullable(lr.login()).orElse(""),
                                CONSTANT_EMAIL, Optional.ofNullable(lr.email()).orElse(""),
                                CONSTANT_ID, Optional.ofNullable(lr.id()).map(UUID::toString).orElse(""),
                                CONSTANT_STATE, Optional.ofNullable(lr.state()).orElse(""),
                                CONSTANT_SUBMISSION_SCOPE, Optional.ofNullable(lr.authorizations()).map(as -> String.join(",", as)).orElse("")
                        )
                )
                .orElseGet(Map::of);
    }
}