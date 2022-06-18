package fr.inra.oresing.persistence.roles;

import lombok.Getter;

import java.util.List;

@Getter
public class CurrentUserRoles {
    String currentUser;
    List<String> memberOf;

    boolean isSuper;

    public CurrentUserRoles(String currentUser, List<String> memberOf, boolean isSuper) {
        this.currentUser = currentUser;
        this.memberOf = memberOf;
        this.isSuper = isSuper;
    }
}