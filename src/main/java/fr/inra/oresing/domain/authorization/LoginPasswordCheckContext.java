package fr.inra.oresing.domain.authorization;

/**
 * Contexte d'appel a {@code AuthenticationServiceImpl.checkLoginPassword} .
 * Dicte le code d'erreur emis si le mot de passe ne matche pas :
 *
 * <ul>
 *   <li>{@link #LOGIN} : flow {@code POST /login} -&gt; throw
 *       {@code BAD_LOGIN_PASSWORD} ( -&gt; 401 -&gt; auto-logout client ,
 *       comportement attendu sur la page de login ) ;</li>
 *   <li>{@link #UPDATE} : flow {@code PUT /users} ou un utilisateur deja
 *       authentifie saisit son mot de passe actuel pour confirmer une
 *       mutation ( changement d'email ou de mot de passe ) -&gt; throw
 *       {@code BAD_CURRENT_PASSWORD} ( -&gt; 422 metier , evite l'auto-logout
 *       global declenche par le {@code Fetcher.ts} frontend sur 401 ) .</li>
 * </ul>
 *
 * <p>Place dans le package {@code domain.authorization} pour eviter une
 * dependance circulaire entre l'interface {@code AuthenticationServiceImpl}
 * et l'impl concrete {@code persistence.AuthenticationService} .
 */
public enum LoginPasswordCheckContext {
    LOGIN,
    UPDATE,
}
