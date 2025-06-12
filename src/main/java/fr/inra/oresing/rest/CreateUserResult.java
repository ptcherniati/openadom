package fr.inra.oresing.rest;

import fr.inra.oresing.domain.OreSiUser;

import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;

public record CreateUserResult(
        UUID userId,
        String login,
        String email,
        OreSiUser.OreSiUserStates accountState,
        Map<String, Timestamp> chartes) {

    public static CreateUserResult of(OreSiUser user) {
        return new CreateUserResult(user.getId(), user.getLogin(), user.getEmail(), user.getAccountstate(), user.getChartes());
    }
}