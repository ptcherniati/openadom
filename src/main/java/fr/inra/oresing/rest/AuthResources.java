package fr.inra.oresing.rest;

import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.AuthRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;

@RestController
@RequestMapping("/api/v1")
public class AuthResources {

    @Autowired
    private AuthHelper authHelper;

    @Autowired
    protected AuthRepository authRepository;

    @PostMapping(value = "/login", produces = MediaType.APPLICATION_JSON_VALUE)
    public OreSiUser login(HttpServletResponse response, @RequestParam("login") String login, @RequestParam("password") String password) throws Throwable {
        OreSiUser result = authRepository.login(login, password);
        // l'authentification a fonctionne, on change dans le context
        OreSiContext.setUser(result);
        authHelper.refreshCookie(response);
        return result;
    }

    @DeleteMapping(value = "/logout")
    public ResponseEntity logout() {
        OreSiContext.reset();
        return ResponseEntity.ok().build();
    }

}