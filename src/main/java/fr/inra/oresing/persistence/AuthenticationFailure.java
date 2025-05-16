package fr.inra.oresing.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fr.inra.oresing.OreSiException;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.rest.CreateUserRequest;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
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
    public static final String BAD_VALIDATION_KEY = "BAD_VALIDATION_KEY";
    public static final String BAD_LOGIN_OR_EMAIL_PASSWORD = "BAD_LOGIN_OR_EMAIL_PASSWORD";
    public static final String BAD_PASSWORDS = "BAD_PASSWORDS";
    public static final String CONSTANT_LOGIN = "login";
    public static final String CONSTANT_EMAIL = "email";
    public static final String CONSTANT_ID = "id";
    public static final String CONSTANT_STATE = "state";
    public static final String CONSTANT_SUBMISSION_SCOPE = "submissionScope";

    private final Map<String, ? extends Serializable> params;

    public AuthenticationFailure(String message, CreateUserRequest userRequest) {
        super(message);
        params = Optional.ofNullable(userRequest)
                .map(lr -> Map.of(
                                CONSTANT_LOGIN, lr.getLogin(),
                                CONSTANT_EMAIL, lr.getEmail()
                        )
                )
                .orElseGet(Map::of);
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

    private static Map getParams(final LoginAdminResult loginAdminResult) {
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