package fr.inra.oresing;

import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.domain.repository.authorization.role.OreSiRightOnApplicationRole;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

@org.junit.jupiter.api.Tag("core.basic")
public class ApplicationTest {
    @Test
     void test() {
        Application application = new Application();
        final UUID id = UUID.randomUUID();
        application.setId(id);
        Assertions.assertEquals("%s_writer".formatted(id), OreSiRightOnApplicationRole.writerOn(application).getAsSqlRole());
        UUID uuid = UUID.randomUUID();
        Assertions.assertEquals("%s_mgt_%s".formatted(id, uuid.toString().split("-")[0]), OreSiRightOnApplicationRole.managementRole(application, uuid).getAsSqlRole());
    }
}