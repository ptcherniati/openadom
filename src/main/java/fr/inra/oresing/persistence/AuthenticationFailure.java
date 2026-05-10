package fr.inra.oresing.persistence;
import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.LoginAdminResult;
import fr.inra.oresing.domain.user.CreateUserRequest;
/**
 * @deprecated Utiliser {@link fr.inra.oresing.domain.exceptions.AuthenticationFailure} directement.
 */
@Deprecated(forRemoval = true)
public class AuthenticationFailure extends fr.inra.oresing.domain.exceptions.AuthenticationFailure {
    public AuthenticationFailure(final String message, final CreateUserRequest userRequest) {
        super(message, userRequest);
    }
    public AuthenticationFailure(final String message, final OreSiUser loginResult) {
        super(message, loginResult);
    }
    public AuthenticationFailure(final String message, final LoginAdminResult loginAdminResult) {
        super(message, loginAdminResult);
    }
}
