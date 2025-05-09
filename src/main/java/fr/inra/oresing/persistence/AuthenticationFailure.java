package fr.inra.oresing.persistence;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import fr.inra.oresing.OreSiException;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.rest.CreateUserRequest;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;
import lombok.Getter;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Getter
@JsonIgnoreProperties({"suppressed", "stackTrace", "cause"})
public class AuthenticationFailure extends OreSiException {

    public static final String EXISTING_LOGIN = "EXISTING_LOGIN";
    public static final String EXISTING_EMAIL = "EXISTING_EMAIL";
    public static final String EXPIRED_VALIDATION_KEY = "EXPIRED_VALIDATION_KEY";
    public static final String STATE_ERROR = "STATE_ERROR";
    public static final String INACTIVE_ACCOUNT = "INACTIVE_ACCOUNT";
    public static final String PENDING_ACCOUNT = "PENDING_ACCOUNT";
    public static final String CLOSED_ACCOUNT = "CLOSED_ACCOUNT";
    public static final String INVALID_ACCOUNT = "INVALID_ACCOUNT";

    public static final String BAD_LOGIN_PASSWORD = "BAD_LOGIN_PASSWORD";
    public static final String BAD_VALIDATION_KEY = "BAD_VALIDATION_KEY";
    public static final String BAD_LOGIN_OR_EMAIL_PASSWORD = "BAD_LOGIN_OR_EMAIL_PASSWORD";
    public static final String BAD_PASSWORDS = "BAD_PASSWORDS";

    private final Map params;

    public AuthenticationFailure(String message, CreateUserRequest userRequest) {
        super(message);
        params = Optional.ofNullable(userRequest)
                .map(lr -> Map.of(
                                "login", lr.getLogin(),
                                "email", lr.getEmail()
                        )
                )
                .orElseGet(Map::of);
    }


    private static Map getParams(final LoginAdminResult loginAdminResult) {
        return Optional.ofNullable(loginAdminResult)
                .map(lr -> Map.of(
                        "login", Optional.ofNullable(lr.login()).orElse(""),
                        "email", Optional.ofNullable(lr.email()).orElse(""),
                        "id", Optional.ofNullable(lr.id()).map(UUID::toString).orElse(""),
                        "state", Optional.ofNullable(lr.state()).orElse(""),
                        "submissionScope", Optional.ofNullable(lr.authorizations()).map(as-> String.join(",", as)).orElse("")
                        )
                )
                .orElseGet(Map::of);
    }

    public AuthenticationFailure(final String message, final OreSiUser loginResult) {
        super(message);
        params =  Optional.ofNullable(loginResult)
                .map(lr -> Map.of(
                        "login", lr.getLogin(),
                        "email", lr.getEmail(),
                        "id", lr.getId(),
                        "state", "",
                        "submissionScope", Optional.ofNullable(lr.getAuthorizations()).map(as-> String.join(",", as)).orElse("")
                        )
                )
                .orElseGet(Map::of);
    }

    public AuthenticationFailure(final String message, final LoginAdminResult loginAdminResult) {
        super(message);
        params = getParams(loginAdminResult);
    }
}