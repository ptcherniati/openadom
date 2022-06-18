package fr.inra.oresing.rest;

import fr.inra.oresing.model.OreSiAuthorization;
import lombok.Value;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Value
public class DatatypeUpdateRoleForManagement {
    Set<UUID> previousUsers;
    OreSiAuthorization oreSiAuthorization;
    List<OreSiAuthorization> authorizationsForCurrentUser;
    boolean isAdminOnApplication;
}