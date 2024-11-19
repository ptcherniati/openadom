package fr.inra.oresing.rest;

import fr.inra.oresing.domain.OreSiAuthorization;

import java.util.List;
import java.util.Set;
import java.util.UUID;


public record DatatypeUpdateRoleForManagement(Set<UUID> previousUsers, OreSiAuthorization oreSiAuthorization,
                                              List<OreSiAuthorization> authorizationsForCurrentUser,
                                              boolean isAdminOnApplication) {
}