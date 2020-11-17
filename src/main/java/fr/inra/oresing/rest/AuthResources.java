package fr.inra.oresing.rest;

import fr.inra.oresing.OreSiUserRequestClient;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
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
        OreSiUser oreSiUser = authRepository.login(login, password);
        // l'authentification a fonctionné, on change dans le context
        OreSiUserRole userRole = authRepository.getUserRole(oreSiUser);
        OreSiUserRequestClient requestClient = new OreSiUserRequestClient();
        requestClient.setId(oreSiUser.getId());
        requestClient.setRole(userRole);
        authHelper.refreshCookie(response, requestClient);
        OreSiApiRequestContext.get().setRequestClient(requestClient);
        return oreSiUser;
    }

    @DeleteMapping(value = "/logout")
    public ResponseEntity logout() {
        OreSiApiRequestContext.reset();
        return ResponseEntity.ok().build();
    }

}