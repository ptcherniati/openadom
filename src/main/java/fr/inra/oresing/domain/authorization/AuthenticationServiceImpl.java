package fr.inra.oresing.domain.authorization;

import fr.inra.oresing.domain.exceptions.AuthenticationFailure;

public interface AuthenticationServiceImpl {
    LoginAdminResult login(final String login, final String password) throws AuthenticationFailure;

    LoginAdminResult checkLoginPassword(String login, String password) throws AuthenticationFailure;

    /**
     * Variante contextuelle de {@link #checkLoginPassword(String, String)} :
     * le code d'erreur emis si le mot de passe ne matche pas depend de
     * {@code context} - cf {@link LoginPasswordCheckContext} .
     */
    LoginAdminResult checkLoginPassword(String login, String password, LoginPasswordCheckContext context) throws AuthenticationFailure;
}