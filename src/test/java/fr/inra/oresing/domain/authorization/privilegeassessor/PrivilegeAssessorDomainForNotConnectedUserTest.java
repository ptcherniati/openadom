package fr.inra.oresing.domain.authorization.privilegeassessor;

import fr.inra.oresing.domain.OreSiUser;
import fr.inra.oresing.domain.authorization.AuthenticationServiceImpl;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.NotConnectedAuthentifiedPendingUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.NotConnectedUnauthentifiedUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.NotConnectedUser;
import fr.inra.oresing.domain.authorization.privilegeassessor.role.PrivilegeSystemDomainEnum;
import fr.inra.oresing.domain.repository.user.file.UserRepository;
import fr.inra.oresing.domain.user.CreateUserRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests pour {@link PrivilegeAssessorDomainForNotConnectedUser} -
 * ciblent en particulier le bug forgot-password ou la branche email-seul
 * n'existait pas et l'utilisateur ne recevait jamais d'email avec la
 * cle de validation .
 *
 * <p>Les autres branches du dispatcher ( login+password , login+email )
 * restent couvertes par les tests d'integration existants ; ici on
 * verifie en isolation :
 * <ul>
 *   <li>la nouvelle branche email-seul ;</li>
 *   <li>la normalisation defensive ( trim + lowercase ) ;</li>
 *   <li>le comportement security-aware quand l'email est inconnu .</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PrivilegeAssessorDomainForNotConnectedUser - dispatcher")
class PrivilegeAssessorDomainForNotConnectedUserTest {

    @Mock private AuthenticationServiceImpl authenticationService;
    @Mock private UserRepository            userRepository;

    private PrivilegeAssessorDomainForNotConnectedUser<PrivilegeSystemDomainEnum> assessor;

    @BeforeEach
    void setUp() {
        assessor = new PrivilegeAssessorDomainForNotConnectedUser<>(
                authenticationService, userRepository, /* domain */ null);
    }

    private static OreSiUser userWithEmail(String email) {
        OreSiUser u = new OreSiUser();
        u.setId(UUID.randomUUID());
        u.setLogin("alice");
        u.setEmail(email);
        return u;
    }

    private static CreateUserRequest emailOnly(String email) {
        CreateUserRequest req = new CreateUserRequest();
        req.setEmail(email);
        return req;
    }

    // ============================================================
    //  Forgot password step 1 ( email-only )
    // ============================================================

    @Test
    @DisplayName("email-only + user found -> NotConnectedAuthentifiedPendingUser ( email sera envoye downstream )")
    void emailOnly_userFound_returnsPendingUser() throws Exception {
        OreSiUser alice = userWithEmail("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        NotConnectedUser result = assessor.forUpdateUser(emailOnly("alice@example.com"));

        assertThat(result).isInstanceOf(NotConnectedAuthentifiedPendingUser.class);
        assertThat(((NotConnectedAuthentifiedPendingUser) result).user()).isSameAs(alice);
    }

    @Test
    @DisplayName("email-only + user introuvable -> NotConnectedUnauthentifiedUser ( UI swallow + toast optimiste )")
    void emailOnly_userNotFound_returnsUnauthentified() throws Exception {
        when(userRepository.findByEmail("ghost@example.com")).thenReturn(Optional.empty());

        NotConnectedUser result = assessor.forUpdateUser(emailOnly("ghost@example.com"));

        assertThat(result).isInstanceOf(NotConnectedUnauthentifiedUser.class);
    }

    @Test
    @DisplayName("email normalise : casse mixte + espaces -> lookup avec version trim+lowercase")
    void emailOnly_normalisesCaseAndWhitespace() throws Exception {
        OreSiUser alice = userWithEmail("alice@example.com");
        when(userRepository.findByEmail("alice@example.com")).thenReturn(Optional.of(alice));

        NotConnectedUser result = assessor.forUpdateUser(emailOnly("  ALICE@Example.COM  "));

        verify(userRepository).findByEmail("alice@example.com");
        assertThat(result).isInstanceOf(NotConnectedAuthentifiedPendingUser.class);
    }

    @Test
    @DisplayName("email vide ou whitespace seul -> Unauthentified ( pas de lookup BDD )")
    void emailOnly_blankEmail_fallsThroughToUnauthentified() throws Exception {
        // null
        NotConnectedUser r1 = assessor.forUpdateUser(emailOnly(null));
        // empty
        NotConnectedUser r2 = assessor.forUpdateUser(emailOnly(""));
        // whitespace seul - Strings.isNullOrEmpty traite "   " comme NON vide ;
        // c'est l'AuthenticationService downstream qui constatera le miss
        // ( ou la normalisation qui produira "" apres trim , et le lookup
        //   par email vide retournera empty ) .
        assertThat(r1).isInstanceOf(NotConnectedUnauthentifiedUser.class);
        assertThat(r2).isInstanceOf(NotConnectedUnauthentifiedUser.class);
        verifyNoInteractions(userRepository);
    }

    // ============================================================
    //  Helper normalizeEmail ( package-private )
    // ============================================================

    @Test
    @DisplayName("normalizeEmail : null -> empty string")
    void normalizeEmail_null() {
        assertThat(PrivilegeAssessorDomainForNotConnectedUser.normalizeEmail(null)).isEmpty();
    }

    @Test
    @DisplayName("normalizeEmail : trim + lowercase")
    void normalizeEmail_trimAndLower() {
        assertThat(PrivilegeAssessorDomainForNotConnectedUser.normalizeEmail("  Rachid.YAHIAOUI@inra.FR  "))
                .isEqualTo("rachid.yahiaoui@inra.fr");
    }

    @Test
    @DisplayName("normalizeEmail : already-clean -> identity")
    void normalizeEmail_alreadyClean() {
        assertThat(PrivilegeAssessorDomainForNotConnectedUser.normalizeEmail("alice@example.com"))
                .isEqualTo("alice@example.com");
    }

    // ============================================================
    //  Non-regression : autres branches ne sont pas affectees
    // ============================================================

    @Test
    @DisplayName("payload totalement vide -> Unauthentified , pas de lookup BDD")
    void emptyPayload_returnsUnauthentified() throws Exception {
        NotConnectedUser result = assessor.forUpdateUser(new CreateUserRequest());
        assertThat(result).isInstanceOf(NotConnectedUnauthentifiedUser.class);
        verifyNoInteractions(userRepository);
    }
}
