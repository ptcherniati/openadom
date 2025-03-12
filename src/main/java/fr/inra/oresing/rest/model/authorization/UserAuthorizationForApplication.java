package fr.inra.oresing.rest.model.authorization;

import java.util.Set;
import java.util.UUID;


public record UserAuthorizationForApplication(String applicationName,
                                              UUID id,
                                              String login,
                                              String email,
                                              String state,
                                              Boolean applicationManager,
                                              boolean userManager,
                                              Set<String> authorizations,
                                              boolean isValidCharte,
                                              boolean isApplicationUser) {
}