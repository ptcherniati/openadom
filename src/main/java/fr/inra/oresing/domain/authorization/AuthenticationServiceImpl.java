package fr.inra.oresing.domain.authorization;

import fr.inra.oresing.domain.exceptions.AuthenticationFailure;
import fr.inra.oresing.domain.authorization.LoginAdminResult;

public interface AuthenticationServiceImpl {
    LoginAdminResult login(final String login, final String password) throws AuthenticationFailure;

    LoginAdminResult checkLoginPassword(String login, String password) throws AuthenticationFailure;
}