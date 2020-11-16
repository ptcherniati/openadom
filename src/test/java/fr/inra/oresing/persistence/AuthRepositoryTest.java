package fr.inra.oresing.persistence;

import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.OreSiUserRole;
import fr.inra.oresing.model.OreSiUser;
import org.flywaydb.test.FlywayTestExecutionListener;
import org.flywaydb.test.annotation.FlywayTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.SpringBootDependencyInjectionTestExecutionListener;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.junit4.SpringRunner;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OreSiNg.class)
@TestExecutionListeners({SpringBootDependencyInjectionTestExecutionListener.class,
        FlywayTestExecutionListener.class})
@Rollback
@FlywayTest
public class AuthRepositoryTest {

    @Autowired
    private AuthRepository authRepository;

    @Test
    public void testSetRole() {
        authRepository.setRole(OreSiUserRole.anonymous());
    }

    @Test
    public void testCreateAndLogin() throws Throwable {
        String login = "toto@codelutin.com";
        String password = "xxxx";
        OreSiUser user = authRepository.createUser(login, password);
        Assert.assertEquals(login, user.getLogin());
        user = authRepository.login(login, password);
        Assert.assertEquals(login, user.getLogin());
        authRepository.setRole(OreSiUserRole.forUser(user));
        authRepository.resetRole();
        authRepository.removeUser(user.getId());
    }
}
