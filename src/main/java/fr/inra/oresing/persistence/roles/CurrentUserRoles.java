package fr.inra.oresing.persistence.roles;

import fr.inra.oresing.model.OreSiUser;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Optional;

@Getter
public class CurrentUserRoles {
    @Setter
    Optional<OreSiUser> userOptional;
    String currentUser;
    List<String> memberOf;

    boolean isSuper;

    public CurrentUserRoles(String currentUser, List<String> memberOf, boolean isSuper) {
        this.currentUser = currentUser;
        this.memberOf = memberOf;
        this.isSuper = isSuper;
    }


}