package fr.inra.oresing.domain.authorization.privilegeassessor.role;

import fr.inra.oresing.domain.application.Application;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;

class ApplicationManagerTest {

    @Mock
    private Application mockApplication;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    @DisplayName("canUpdateApplication devrait retourner le même type d'objet générique")
    void canUpdateApplicationShouldReturnSameGenericType() {
        // Arrangement
        ApplicationAdminUser adminUser = new ApplicationAdminUser(mockApplication);
        ApplicationManagerUser managerUser = new ApplicationManagerUser(mockApplication);
        
        // Action
        ApplicationManager adminResult = adminUser.canUpdateApplication();
        ApplicationManager managerResult = managerUser.canUpdateApplication();
        
        // Assertion
        assertNotNull(adminResult, "Le résultat pour AdminUser ne devrait pas être null");
        assertNotNull(managerResult, "Le résultat pour ManagerUser ne devrait pas être null");
        
        assertEquals(adminUser, adminResult, "AdminUser.canUpdateApplication devrait retourner l'instance elle-même");
        assertEquals(managerUser, managerResult, "ManagerUser.canUpdateApplication devrait retourner l'instance elle-même");
    }
    
    @Test
    @DisplayName("canUpdateApplication devrait permettre un casting générique précis")
    void canUpdateApplicationShouldAllowPreciseGenericCasting() {
        // Arrangement
        ApplicationAdminUser adminUser = new ApplicationAdminUser(mockApplication);
        ApplicationManagerUser managerUser = new ApplicationManagerUser(mockApplication);
        
        // Action et assertion
        ApplicationAdminUser castedAdmin = adminUser.canUpdateApplication();
        ApplicationManagerUser castedManager = managerUser.canUpdateApplication();
        
        // Vérifier le type exact
        assertTrue(castedAdmin instanceof ApplicationAdminUser, "castedAdmin devrait être de type ApplicationAdminUser");
        assertTrue(castedManager instanceof ApplicationManagerUser, "castedManager devrait être de type ApplicationManagerUser");
        
        // Vérifier l'identité des objets
        assertSame(adminUser, castedAdmin, "canUpdateApplication devrait retourner l'objet original");
        assertSame(managerUser, castedManager, "canUpdateApplication devrait retourner l'objet original");
    }
    
    @Test
    @DisplayName("ApplicationManager devrait avoir un traitement polymorphique à travers canUpdateApplication")
    void applicationManagerShouldHavePolymorphicBehavior() {
        // Arrangement
        ApplicationManager adminManager = new ApplicationAdminUser(mockApplication);
        ApplicationManager regularManager = new ApplicationManagerUser(mockApplication);
        
        // Action - Utiliser la méthode générique pour obtenir le type spécifique
        ApplicationAdminUser recoveredAdmin = adminManager.<ApplicationAdminUser>canUpdateApplication();
        ApplicationManagerUser recoveredManager = regularManager.<ApplicationManagerUser>canUpdateApplication();
        
        // Assertion
        assertNotNull(recoveredAdmin, "Le admin récupéré ne devrait pas être null");
        assertNotNull(recoveredManager, "Le manager récupéré ne devrait pas être null");
        
        // Vérifier que l'interface et l'implémentation sont préservées
        assertEquals(ApplicationAdminUser.class, recoveredAdmin.getClass(), 
                "Le type récupéré devrait être ApplicationAdminUser");
        assertEquals(ApplicationManagerUser.class, recoveredManager.getClass(), 
                "Le type récupéré devrait être ApplicationManagerUser");
    }
    
    @Test
    @DisplayName("canUpdateApplication devrait gérer correctement le type générique dans une fonction utilitaire")
    void canUpdateApplicationShouldHandleGenericTypeInUtilityFunction() {
        // Arrangement
        ApplicationManager adminManager = new ApplicationAdminUser(mockApplication);
        ApplicationManager regularManager = new ApplicationManagerUser(mockApplication);
        
        // Action
        boolean adminCanWrite = checkManagerWriteCapability(adminManager);
        boolean regularCanWrite = checkManagerWriteCapability(regularManager);
        
        // Assertion
        assertTrue(adminCanWrite, "L'admin devrait avoir des capacités d'écriture");
        assertTrue(regularCanWrite, "Le manager régulier devrait avoir des capacités d'écriture");
    }
    
    /**
     * Fonction utilitaire pour tester la conversion de type générique
     */
    private <AM extends ApplicationManager> boolean checkManagerWriteCapability(AM manager) {
        // Utiliser canUpdateApplication pour récupérer le même type
        AM updatedManager = manager.canUpdateApplication();
        
        // Vérifier si c'est aussi un ApplicationDataWriter
        return updatedManager instanceof ApplicationDataWriter;
    }
}