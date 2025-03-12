package fr.inra.oresing.domain.authorization;

import fr.inra.oresing.persistence.AuthenticationFailure;
import fr.inra.oresing.rest.model.authorization.LoginAdminResult;

public interface AuthenticationService {
    LoginAdminResult login(final String login, final String password) throws AuthenticationFailure;

    LoginAdminResult checkLoginPassword(String login, String password) throws AuthenticationFailure;
}
