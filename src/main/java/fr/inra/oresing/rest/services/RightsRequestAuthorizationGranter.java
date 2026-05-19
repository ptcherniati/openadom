package fr.inra.oresing.rest.services;

import fr.inra.oresing.domain.OreSiAuthorization;
import fr.inra.oresing.domain.application.Application;
import fr.inra.oresing.persistence.AuthorizationRepository;
import fr.inra.oresing.persistence.OreSiRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Attribue effectivement les autorisations sélectionnées par le gestionnaire
 * lors d'une validation de demande de droits ( #487 Phase 4 ).
 *
 * <p>Pour chaque {@link OreSiAuthorization} listée par son identifiant,
 * ajoute le demandeur dans le {@code Set<UUID> oreSiUsers}. La modification
 * est persistée via le {@link AuthorizationRepository} de l'application.</p>
 *
 * <h3>Garanties</h3>
 * <ul>
 *   <li><b>Idempotence</b> : si le demandeur est déjà membre, aucun store
 *       n'est émis ( {@link Set#add(Object)} renvoie {@code false} ).</li>
 *   <li><b>Tolérance aux suppressions</b> : si une autorisation listée
 *       a été supprimée entre la soumission et le traitement de la demande,
 *       elle est ignorée avec un {@code log.warn}, sans interrompre les
 *       autres attributions.</li>
 *   <li><b>Atomicité transactionnelle</b> : la propagation
 *       {@link Propagation#MANDATORY} impose un {@code @Transactional}
 *       parent ; si l'un des stores échoue, l'ensemble du traitement
 *       ( marquage {@code setted=true}, persistance de la décision,
 *       attributions précédentes ) est annulé par le rollback de la
 *       transaction parente.</li>
 * </ul>
 *
 * <h3>Conception</h3>
 * <p>Service séparé de {@link RightsRequestService} pour respecter la
 * Single Responsibility et permettre des tests unitaires isolés du flux
 * complet de traitement ( aucun besoin de mocker repository de demandes,
 * notifications, authentification, etc. ).</p>
 *
 * <p>Évolutions prévues sans modification de cette classe : ajout d'un
 * journal d'audit fin ( who-granted-what-when ), pré-vérification de scope
 * croisée avec la demande, propagation d'évènements applicatifs.</p>
 */
@Slf4j
@Component
public class RightsRequestAuthorizationGranter {

    private final OreSiRepository repository;

    public RightsRequestAuthorizationGranter(final OreSiRepository repository) {
        this.repository = repository;
    }

    /**
     * Ajoute le demandeur dans la liste des bénéficiaires de chaque
     * autorisation listée.
     *
     * @param application              application cible ( détermine le schéma )
     * @param requesterId              UUID du demandeur à ajouter
     * @param linkedAuthorizationIds   identifiants des autorisations cochées
     *                                 par le gestionnaire ; {@code null} ou
     *                                 vide est traité comme un no-op
     * @return rapport synthétique des actions effectuées ( utile pour les
     *         logs applicatifs et les tests )
     * @throws NullPointerException si {@code application} ou
     *                              {@code requesterId} est {@code null}
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public GrantReport grantAll(final Application application,
                                 final UUID requesterId,
                                 final List<UUID> linkedAuthorizationIds) {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(requesterId, "requesterId");

        if (linkedAuthorizationIds == null || linkedAuthorizationIds.isEmpty()) {
            return GrantReport.empty();
        }

        final AuthorizationRepository authRepo = repository.getRepository(application).authorization();
        final List<UUID> granted = new ArrayList<>();
        final List<UUID> alreadyMember = new ArrayList<>();
        final List<UUID> missing = new ArrayList<>();

        for (final UUID authId : linkedAuthorizationIds) {
            if (authId != null) {
                final OreSiAuthorization auth = authRepo.findById(authId);
                if (auth == null) {
                    log.warn("Skip authorization {} on application {} : not found ( deleted between submission and treatment )",
                            authId, application.getName());
                    missing.add(authId);
                } else {
                    final Set<UUID> users = auth.getOreSiUsers() == null
                            ? new HashSet<>()
                            : new HashSet<>(auth.getOreSiUsers());
                    if (!users.add(requesterId)) {
                        alreadyMember.add(authId);
                    } else {
                        auth.setOreSiUsers(users);
                        authRepo.store(auth);
                        granted.add(authId);
                        log.info("Granted authorization {} ( {} ) to user {} on application {} via rights request",
                                authId, auth.getName(), requesterId, application.getName());
                    }
                }
            }
        }
        return new GrantReport(List.copyOf(granted), List.copyOf(alreadyMember), List.copyOf(missing));
    }

    /**
     * Synthèse d'une exécution de {@link #grantAll}.
     *
     * @param granted        autorisations dont la liste de bénéficiaires
     *                       a été modifiée et persistée
     * @param alreadyMember  autorisations où le demandeur était déjà
     *                       présent ( aucune modification, idempotent )
     * @param missing        autorisations introuvables ( supprimées entre
     *                       la soumission et le traitement de la demande )
     */
    public record GrantReport(List<UUID> granted, List<UUID> alreadyMember, List<UUID> missing) {

        public static GrantReport empty() {
            return new GrantReport(List.of(), List.of(), List.of());
        }

        public int totalProcessed() {
            return granted.size() + alreadyMember.size() + missing.size();
        }
    }
}