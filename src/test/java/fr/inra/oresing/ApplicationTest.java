package fr.inra.oresing;

import fr.inra.oresing.model.Application;
import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import org.junit.Test;

import java.util.UUID;

public class ApplicationTest {
    @Test
    public void test(){
        final Application application = new Application();
        application.setId(UUID.randomUUID());
        System.out.println(OreSiRightOnApplicationRole.writerOn(application).getAsSqlRole());
        System.out.println(OreSiRightOnApplicationRole.writerOn(application).getAsSqlRole());
        System.out.println(OreSiRightOnApplicationRole.writerOn(application).getAsSqlRole());
        System.out.println(OreSiRightOnApplicationRole.writerOn(application).getAsSqlRole());
        final UUID uuid = UUID.randomUUID();
        System.out.println(OreSiRightOnApplicationRole.managementRole(application, uuid).getAsSqlRole());
        System.out.println(OreSiRightOnApplicationRole.managementRole(application, uuid).getAsSqlRole());
        System.out.println(OreSiRightOnApplicationRole.managementRole(application, uuid).getAsSqlRole());
        System.out.println(OreSiRightOnApplicationRole.managementRole(application, uuid).getAsSqlRole());
    }
}