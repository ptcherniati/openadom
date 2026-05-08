package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.AuthorizationRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires pour {@link RightsRequestAuthorizationGranter} ( #487 Phase 4 ).
 *
 * <p>Couverture :
 * <ul>
 *   <li>plusieurs autorisations cochées → toutes mises à jour avec le requester</li>
 *   <li>autorisation introuvable → log + skip, autres autorisations traitées</li>
 *   <li>requester déjà membre → idempotence, aucun store</li>
 *   <li>oreSiUsers null sur l'autorisation → initialisé puis store</li>
 *   <li>liste vide / null → no-op, aucun appel repo</li>
 *   <li>ID null mêlé à des IDs valides → ignoré sans interrompre</li>
 *   <li>contrats de paramètres ( application / requesterId non null )</li>
 * </ul>
 */
class RightsRequestAuthorizationGranterTest {

    private OreSiRepository oreSiRepository;
    private OreSiRepository.RepositoryForApplication repositoryForApplication;
    private AuthorizationRepository authorizationRepository;

    private RightsRequestAuthorizationGranter granter;

    private Application application;
    private UUID requesterId;

    @BeforeEach
    void setUp() {
        oreSiRepository = mock(OreSiRepository.class);
        repositoryForApplication = mock(OreSiRepository.RepositoryForApplication.class);
        authorizationRepository = mock(AuthorizationRepository.class);

        application = new Application();
        application.setId(UUID.randomUUID());
        application.setName("ticket_507");

        when(oreSiRepository.getRepository(application)).thenReturn(repositoryForApplication);
        when(repositoryForApplication.authorization()).thenReturn(authorizationRepository);

        granter = new RightsRequestAuthorizationGranter(oreSiRepository);
        requesterId = UUID.randomUUID();
    }

    @Test
    @DisplayName("Plusieurs autorisations : ajoute le requester dans chacune et persiste")
    void grantAll_multipleAuthorizations_addsRequesterAndPersists() {
        final UUID auth1Id = UUID.randomUUID();
        final UUID auth2Id = UUID.randomUUID();
        final UUID existingMemberA = UUID.randomUUID();
        final UUID existingMemberB = UUID.randomUUID();

        final OreSiAuthorization auth1 = newAuthorization(auth1Id, "lecteurs_meteo_2025", new HashSet<>(Set.of(existingMemberA)));
        final OreSiAuthorization auth2 = newAuthorization(auth2Id, "editeurs_2025", new HashSet<>(Set.of(existingMemberB)));
        when(authorizationRepository.findById(auth1Id)).thenReturn(auth1);
        when(authorizationRepository.findById(auth2Id)).thenReturn(auth2);

        final RightsRequestAuthorizationGranter.GrantReport report = granter.grantAll(application, requesterId, List.of(auth1Id, auth2Id));

        assertThat(report.granted()).containsExactly(auth1Id, auth2Id);
        assertThat(report.alreadyMember()).isEmpty();
        assertThat(report.missing()).isEmpty();

        final ArgumentCaptor<OreSiAuthorization> stored = ArgumentCaptor.forClass(OreSiAuthorization.class);
        verify(authorizationRepository, times(2)).store(stored.capture());

        final List<OreSiAuthorization> persisted = stored.getAllValues();
        assertThat(persisted.get(0).getOreSiUsers()).containsExactlyInAnyOrder(existingMemberA, requesterId);
        assertThat(persisted.get(1).getOreSiUsers()).containsExactlyInAnyOrder(existingMemberB, requesterId);
    }

    @Test
    @DisplayName("Autorisation introuvable : skip + log warn, autres autorisations traitées")
    void grantAll_missingAuthorization_skipsAndContinues() {
        final UUID missingId = UUID.randomUUID();
        final UUID presentId = UUID.randomUUID();
        final OreSiAuthorization present = newAuthorization(presentId, "extracteurs_full", new HashSet<>());
        when(authorizationRepository.findById(missingId)).thenReturn(null);
        when(authorizationRepository.findById(presentId)).thenReturn(present);

        final RightsRequestAuthorizationGranter.GrantReport report = granter.grantAll(application, requesterId, List.of(missingId, presentId));

        assertThat(report.granted()).containsExactly(presentId);
        assertThat(report.missing()).containsExactly(missingId);
        assertThat(report.alreadyMember()).isEmpty();
        verify(authorizationRepository, times(1)).store(any());
    }

    @Test
    @DisplayName("Requester déjà membre : idempotent, aucun store, comptabilisé en alreadyMember")
    void grantAll_requesterAlreadyMember_isIdempotent() {
        final UUID authId = UUID.randomUUID();
        final OreSiAuthorization auth = newAuthorization(authId, "lecteurs_meteo_2025", new HashSet<>(Set.of(requesterId)));
        when(authorizationRepository.findById(authId)).thenReturn(auth);

        final RightsRequestAuthorizationGranter.GrantReport report = granter.grantAll(application, requesterId, List.of(authId));

        assertThat(report.granted()).isEmpty();
        assertThat(report.alreadyMember()).containsExactly(authId);
        assertThat(report.missing()).isEmpty();
        verify(authorizationRepository, never()).store(any());
    }

    @Test
    @DisplayName("oreSiUsers null sur l'autorisation : initialisé avec le requester puis persisté")
    void grantAll_oreSiUsersNull_initializesAndStores() {
        final UUID authId = UUID.randomUUID();
        final OreSiAuthorization auth = newAuthorization(authId, "lecteurs_meteo_2025", null);
        when(authorizationRepository.findById(authId)).thenReturn(auth);

        final RightsRequestAuthorizationGranter.GrantReport report = granter.grantAll(application, requesterId, List.of(authId));

        assertThat(report.granted()).containsExactly(authId);
        final ArgumentCaptor<OreSiAuthorization> stored = ArgumentCaptor.forClass(OreSiAuthorization.class);
        verify(authorizationRepository).store(stored.capture());
        assertThat(stored.getValue().getOreSiUsers()).containsExactly(requesterId);
    }

    @Test
    @DisplayName("Liste vide ou null : no-op, aucun accès au repository")
    void grantAll_emptyOrNull_isNoop() {
        final RightsRequestAuthorizationGranter.GrantReport empty = granter.grantAll(application, requesterId, List.of());
        final RightsRequestAuthorizationGranter.GrantReport nullList = granter.grantAll(application, requesterId, null);

        assertThat(empty.totalProcessed()).isZero();
        assertThat(nullList.totalProcessed()).isZero();
        verify(authorizationRepository, never()).findById(any());
        verify(authorizationRepository, never()).store(any());
    }

    @Test
    @DisplayName("ID null mêlé à des IDs valides : ignoré, autres traités")
    void grantAll_nullIdInList_isSkipped() {
        final UUID validId = UUID.randomUUID();
        final OreSiAuthorization auth = newAuthorization(validId, "extracteurs_full", new HashSet<>());
        when(authorizationRepository.findById(validId)).thenReturn(auth);

        final RightsRequestAuthorizationGranter.GrantReport report = granter.grantAll(application, requesterId, java.util.Arrays.asList(null, validId));

        assertThat(report.granted()).containsExactly(validId);
        verify(authorizationRepository, never()).findById(null);
    }

    @Test
    @DisplayName("Contrats : application null ou requesterId null → NPE explicite")
    void grantAll_nullArguments_throwsNpe() {
        assertThatNullPointerException()
                .isThrownBy(() -> granter.grantAll(null, requesterId, List.of()))
                .withMessageContaining("application");
        assertThatNullPointerException()
                .isThrownBy(() -> granter.grantAll(application, null, List.of()))
                .withMessageContaining("requesterId");
    }

    private OreSiAuthorization newAuthorization(final UUID id, final String name, final Set<UUID> oreSiUsers) {
        final OreSiAuthorization auth = new OreSiAuthorization();
        auth.setId(id);
        auth.setName(name);
        auth.setApplication(application.getId());
        auth.setOreSiUsers(oreSiUsers);
        return auth;
    }
}
