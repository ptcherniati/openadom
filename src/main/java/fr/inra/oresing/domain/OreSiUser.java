package fr.inra.oresing.domain;

import lombok.Getter;
import lombok.Setter;

import java.sql.Timestamp;
import java.util.*;

@Getter
@Setter
public class OreSiUser extends OreSiEntity {
    private String login;
    private String password;
    private String email;
    private Set<String> authorizations = new HashSet<>();
    private OreSiUserStates accountstate;
    private Map<String, Timestamp> chartes = new HashMap<>();

    public enum OreSiUserStates {
        idle, active, pending, closed
    }
}