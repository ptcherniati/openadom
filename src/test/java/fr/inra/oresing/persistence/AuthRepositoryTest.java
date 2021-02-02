package fr.inra.oresing.persistence;

import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.persistence.roles.OreSiRole;
import fr.inra.oresing.persistence.roles.OreSiUserRole;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.SpringBootDependencyInjectionTestExecutionListener;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.context.support.DirtiesContextTestExecutionListener;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OreSiNg.class)
@TestPropertySource(locations = "classpath:/application-tests.properties")
@AutoConfigureMockMvc
@TestExecutionListeners({SpringBootDependencyInjectionTestExecutionListener.class, DirtiesContextTestExecutionListener.class})
public class AuthRepositoryTest {

    @Autowired
    private AuthRepository authRepository;

    @Test
    public void testSetRole() {
        authRepository.setRole(OreSiRole.anonymous());
    }

    @Test
    public void testCreateAndLogin() throws Throwable {
        String login = "toto@codelutin.com";
        String password = "xxxx";
        OreSiUser user = authRepository.createUser(login, password);
        Assert.assertEquals(login, user.getLogin());
        user = authRepository.login(login, password);
        Assert.assertEquals(login, user.getLogin());
        OreSiUserRole userRole = authRepository.getUserRole(user);
        authRepository.setRole(userRole);
        authRepository.resetRole();
        authRepository.removeUser(user.getId());
    }
}
