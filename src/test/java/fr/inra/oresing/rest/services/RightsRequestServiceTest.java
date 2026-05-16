package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.mail.rightsrequest.RightsRequestNotificationService;
import fr.inra.oresing.persistence.OreSiRepository;
import fr.inra.oresing.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour {@link RightsRequestService}.
 *
 * <p>Couvre : resolveUserEmail (null / trouvé / non trouvé / exception),
 * resolveTreatedByLogin (null / trouvé / non trouvé / exception).
 */
@Tag("domain.model")
@DisplayName("RightsRequestService — résolution email / login")
class RightsRequestServiceTest {

    private UserRepository userRepository;
    private RightsRequestService service;

    @BeforeEach
    void setUp() {
        OreSiRepository repository            = mock(OreSiRepository.class);
        ServiceContainer serviceContainer     = mock(ServiceContainer.class);
        RightsRequestNotificationService ns   = mock(RightsRequestNotificationService.class);
        RightsRequestAuthorizationGranter rrag = mock(RightsRequestAuthorizationGranter.class);
        userRepository = mock(UserRepository.class);

        service = new RightsRequestService(repository, serviceContainer, ns, userRepository, rrag);
    }

    // ─── resolveUserEmail ─────────────────────────────────────────────────────

    @Test
    @DisplayName("resolveUserEmail avec userId null retourne null sans appeler userRepository")
    void resolveUserEmail_null_returnsNull() throws Exception {
        String result = resolveUserEmail(null);

        assertThat(result).isNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("resolveUserEmail retourne l'email si l'utilisateur est trouvé")
    void resolveUserEmail_userFound_returnsEmail() throws Exception {
        UUID userId = UUID.randomUUID();
        OreSiUser user = new OreSiUser();
        user.setId(userId);
        user.setEmail("alice@example.com");

        when(userRepository.findById(userId)).thenReturn(user);

        String result = resolveUserEmail(userId);

        assertThat(result).isEqualTo("alice@example.com");
    }

    @Test
    @DisplayName("resolveUserEmail retourne null si l'utilisateur n'existe plus en base")
    void resolveUserEmail_userNotFound_returnsNull() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(null);

        String result = resolveUserEmail(userId);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolveUserEmail retourne null si userRepository lève une exception (utilisateur supprimé)")
    void resolveUserEmail_exception_returnsNullAndLogs() throws Exception {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenThrow(new RuntimeException("DB error"));

        String result = resolveUserEmail(userId);

        assertThat(result).isNull();
    }

    // ─── resolveTreatedByLogin ────────────────────────────────────────────────

    @Test
    @DisplayName("resolveTreatedByLogin avec treatedBy null retourne null sans appeler userRepository")
    void resolveTreatedByLogin_null_returnsNull() throws Exception {
        String result = resolveTreatedByLogin(null);

        assertThat(result).isNull();
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("resolveTreatedByLogin retourne le login si l'utilisateur est trouvé")
    void resolveTreatedByLogin_userFound_returnsLogin() throws Exception {
        UUID treatedBy = UUID.randomUUID();
        OreSiUser user = new OreSiUser();
        user.setId(treatedBy);
        user.setLogin("bob");

        when(userRepository.findById(treatedBy)).thenReturn(user);

        String result = resolveTreatedByLogin(treatedBy);

        assertThat(result).isEqualTo("bob");
    }

    @Test
    @DisplayName("resolveTreatedByLogin retourne null si le gestionnaire n'existe plus en base")
    void resolveTreatedByLogin_userNotFound_returnsNull() throws Exception {
        UUID treatedBy = UUID.randomUUID();
        when(userRepository.findById(treatedBy)).thenReturn(null);

        String result = resolveTreatedByLogin(treatedBy);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("resolveTreatedByLogin retourne null si userRepository lève une exception")
    void resolveTreatedByLogin_exception_returnsNullAndLogs() throws Exception {
        UUID treatedBy = UUID.randomUUID();
        when(userRepository.findById(treatedBy)).thenThrow(new RuntimeException("DB error"));

        String result = resolveTreatedByLogin(treatedBy);

        assertThat(result).isNull();
    }

    // ─── helpers reflection ───────────────────────────────────────────────────

    private String resolveUserEmail(UUID userId) throws Exception {
        Method m = RightsRequestService.class
                .getDeclaredMethod("resolveUserEmail", UUID.class);
        m.setAccessible(true);
        return (String) m.invoke(service, userId);
    }

    private String resolveTreatedByLogin(UUID treatedBy) throws Exception {
        Method m = RightsRequestService.class
                .getDeclaredMethod("resolveTreatedByLogin", UUID.class);
        m.setAccessible(true);
        return (String) m.invoke(service, treatedBy);
    }
}
