package fr.inra.oresing.persistence;

import fr.inra.oresing.OreSiNg;
import fr.inra.oresing.model.Application;
import fr.inra.oresing.model.OreSiUser;
import fr.inra.oresing.model.ReferenceValue;
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

import java.util.UUID;

@RunWith(SpringRunner.class)
@SpringBootTest(classes = OreSiNg.class)
@TestExecutionListeners({SpringBootDependencyInjectionTestExecutionListener.class,
        FlywayTestExecutionListener.class})
@Rollback
@FlywayTest
public class AuthRespositoryTest {

    @Autowired
    private AuthRepository authRepository;

    @Test
    public void testSetRole() {
        authRepository.setRole("anonymous");
    }

    @Test
    public void testCreateAndLogin() {
        String login = "toto@codelutin.com";
        String password = "xxxx";
        OreSiUser user = authRepository.createUser(login, password);
        Assert.assertEquals(login, user.getName());
        user = authRepository.login(login, password);
        Assert.assertEquals(login, user.getName());
        authRepository.setRole(login);
        authRepository.resetRole();
        authRepository.removeUser(login);
    }

    @Test
    public void testCreateRefRole() {
        UUID refId = UUID.randomUUID();
        UUID appId = UUID.randomUUID();

        Application app = new Application();
        app.setId(appId);

        ReferenceValue ref = new ReferenceValue();
        ref.setId(refId);
        ref.setReferenceType("country");
        ref.setApplication(appId);

        authRepository.createRightForReference(ref);
        authRepository.removeRightForReference(ref);
    }
}
