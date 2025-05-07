package fr.inra.oresing.rest.model.data;

import fr.inra.oresing.domain.OreSiUser;

import java.util.UUID;

public record UserDescriptionResult(UUID id, String login, String email) {
    public static UserDescriptionResult of(OreSiUser user) {
        return new UserDescriptionResult(
                user.getId(),
                user.getLogin(),
                user.getEmail()
        );
    }
}
