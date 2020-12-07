package fr.inra.oresing.model;

import fr.inra.oresing.persistence.roles.OreSiRightOnApplicationRole;
import lombok.AllArgsConstructor;

import java.util.UUID;

/**
 * Enum des droit que l'on peut donner a un utilisateur
 * TODO: il faudrait revoir son implantation pour mieux gerer les differentes
 * possibilites (on ne peut pas mettre plusieurs actions, INSERT ne fonctionne pas, ...)
 * et la facon de faire est trop complique
 */
@AllArgsConstructor
public enum ApplicationRight {

    ADMIN,
    WRITER,
    DATA_WRITER,
    READER,
    RESTRICTED_READER;

    public OreSiRightOnApplicationRole getRole(UUID appId) {
        return OreSiRightOnApplicationRole.forRightOnApplication(appId, this);
    }
}
